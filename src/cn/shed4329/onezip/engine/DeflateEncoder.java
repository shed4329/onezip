package cn.shed4329.onezip.engine;

import java.io.*;
import java.lang.invoke.*;
import java.nio.ByteOrder;
import java.util.Arrays;

/** RFC1951: rolling LZ history, lazy parsing and measured dynamic/fixed/stored choice. */
final class DeflateEncoder extends OutputStream {
    static final int WINDOW = 32768, BLOCK = 65535;
    private static final VarHandle WORD =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final int[] LENGTH = {
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115,
        131, 163, 195, 227, 258
    };
    private static final int[] LB = {
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
    };
    private static final int[] DIST = {
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537,
        2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577
    };
    private static final int[] DB = {
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12,
        13, 13
    };
    private static final int[] ORDER = {
        16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15
    };
    private static final int[] FIXED = new int[288], LC = new int[259], DC = new int[32769];

    static {
        for (int s = 0; s < 288; s++) {
            int n = s <= 143 ? 8 : s <= 255 ? 9 : s <= 279 ? 7 : 8,
                    c =
                            s <= 143
                                    ? 0x30 + s
                                    : s <= 255
                                            ? 0x190 + s - 144
                                            : s <= 279 ? s - 256 : 0xc0 + s - 280;
            FIXED[s] = ((Integer.reverse(c) >>> (32 - n)) << 4) | n;
        }
        for (int n = 3, i = 0; n < LC.length; n++) {
            while (i + 1 < LENGTH.length && LENGTH[i + 1] <= n) i++;
            LC[n] = i;
        }
        for (int n = 1, i = 0; n < DC.length; n++) {
            while (i + 1 < DIST.length && DIST[i + 1] <= n) i++;
            DC[n] = i;
        }
    }

    static final class Workspace {
        byte[] input, encoded;
        int[] head, previous, tokens;
        final int[] literals = new int[286],
                distances = new int[30],
                codeFrequency = new int[19],
                sequence = new int[316],
                rleSymbol = new int[320],
                rleExtra = new int[320],
                rleBits = new int[320];
        final DeflateHuffman literalTree = new DeflateHuffman(286),
                distanceTree = new DeflateHuffman(30),
                codeTree = new DeflateHuffman(19);

        void initialize() {
            if (input == null) {
                input = new byte[WINDOW + BLOCK + 4];
                encoded = new byte[131072];
                head = new int[65536];
                previous = new int[WINDOW];
                tokens = new int[BLOCK];
            }
        }
    }

    private final OutputStream out;
    private final Workspace w;
    private final int depth, lazy;
    private final boolean fast, storeOnly;
    private int used,
            history,
            base,
            indexedUntil,
            bits,
            bitCount,
            encodedUsed,
            tokenCount,
            extraBits,
            rleCount,
            nl,
            nd,
            nc;
    private boolean finished;

    DeflateEncoder(OutputStream out, int level) {
        this(out, level, new Workspace(), null);
    }

    DeflateEncoder(OutputStream out, int level, Workspace workspace) {
        this(out, level, workspace, null);
    }

    private DeflateEncoder(OutputStream out, int level, Workspace workspace, byte[] prefix) {
        this.out = out;
        w = workspace;
        w.initialize();
        int selected = level < 0 ? 5 : level;
        Arrays.fill(w.head, -1);
        depth =
                selected <= 1
                        ? 8
                        : selected <= 3 ? 24 : selected <= 5 ? 64 : selected <= 7 ? 128 : 256;
        fast = selected <= 3;
        storeOnly = selected == 0;
        lazy = selected < 5 ? 0 : selected <= 5 ? 32 : selected <= 7 ? 64 : 128;
        if (prefix != null) {
            history = Math.min(prefix.length, WINDOW);
            System.arraycopy(prefix, prefix.length - history, w.input, 0, history);
        }
    }

    static ChunkedOutputStream fragment(byte[] data, byte[] prefix, int level, Workspace workspace)
            throws IOException {
        ChunkedOutputStream bytes = new ChunkedOutputStream(Math.addExact(data.length, 131072));
        DeflateEncoder encoder = new DeflateEncoder(bytes, level, workspace, prefix);
        encoder.write(data);
        encoder.block();
        encoder.put(0, 3);
        encoder.align();
        encoder.storedHeader(0);
        encoder.flushEncoded();
        bytes.close();
        return bytes;
    }

    @Override
    public void write(int b) throws IOException {
        if (finished) throw new IOException("DEFLATE stream finished");
        w.input[history + used++] = (byte) b;
        if (used == BLOCK) block();
    }

    @Override
    public void write(byte[] b, int off, int length) throws IOException {
        java.util.Objects.checkFromIndexSize(off, length, b.length);
        if (finished) throw new IOException("DEFLATE stream finished");
        while (length != 0) {
            int n = Math.min(length, BLOCK - used);
            System.arraycopy(b, off, w.input, history + used, n);
            used += n;
            off += n;
            length -= n;
            if (used == BLOCK) block();
        }
    }

    private int word(int pos) {
        return (int) WORD.get(w.input, pos) & 0xffffff;
    }

    private int hash(int pos) {
        return (word(pos) * 0x1e35a7bd) >>> 16;
    }

    private void index(int pos) {
        int hash = hash(pos), absolute = base + pos;
        w.previous[absolute & (WINDOW - 1)] = w.head[hash];
        w.head[hash] = absolute;
    }

    private long find(int pos, int end) {
        if (pos + 2 >= end) return 0;
        int absolute = base + pos,
                link = w.head[hash(pos)],
                best = 2,
                distance = 0,
                limit = Math.min(258, end - pos),
                prefix = word(pos);
        for (int count = 0;
                link >= 0 && absolute - link <= WINDOW && count < depth;
                count++, link = w.previous[link & (WINDOW - 1)]) {
            int candidate = link - base;
            if (w.input[candidate + best] != w.input[pos + best] || word(candidate) != prefix)
                continue;
            int
                    mismatch =
                            Arrays.mismatch(
                                    w.input,
                                    pos,
                                    pos + limit,
                                    w.input,
                                    candidate,
                                    candidate + limit),
                    length = mismatch < 0 ? limit : mismatch;
            if (length > best) {
                best = length;
                distance = pos - candidate;
            }
            if (best == limit) break;
        }
        return ((long) best << 32) | distance;
    }

    private void tokenize() throws IOException {
        // Full indexing at levels 5+ survives block shifts through absolute ring links.
        // Sparse fast levels retain their previous prefix rebuild policy. Rebase long
        // streams occasionally so links cannot overflow a signed Java int.
        if (fast || base >= (1 << 29)) {
            Arrays.fill(w.head, -1);
            base = indexedUntil = 0;
        }
        Arrays.fill(w.literals, 0);
        Arrays.fill(w.distances, 0);
        tokenCount = extraBits = 0;
        int end = history + used;
        for (int pos = Math.max(0, indexedUntil - base); pos < history && pos + 2 < end; pos++) {
            if ((pos & 4095) == 0) ReadSupport.check(null);
            index(pos);
        }
        long cached = 0;
        boolean ready = false;
        for (int pos = history, nextCheck = history; pos < end; ) {
            if (pos >= nextCheck) {
                ReadSupport.check(null);
                nextCheck = pos + 4096;
            }
            long match = ready ? cached : find(pos, end);
            ready = false;
            int length = (int) (match >>> 32), distance = (int) match;
            if (pos + 2 < end) index(pos);
            if (length >= 3 && length < lazy && pos + 3 < end) {
                long next = find(pos + 1, end);
                if ((int) (next >>> 32) > length) {
                    w.tokens[tokenCount++] = w.input[pos] & 255;
                    w.literals[w.input[pos] & 255]++;
                    pos++;
                    cached = next;
                    ready = true;
                    continue;
                }
            }
            if (length >= 3) {
                w.tokens[tokenCount++] = (length << 16) | distance;
                int lc = LC[length], dc = DC[distance];
                w.literals[257 + lc]++;
                w.distances[dc]++;
                extraBits += LB[lc] + DB[dc];
                int first = fast && length >= 32 ? Math.max(pos + 1, pos + length - 2) : pos + 1;
                for (int p = first; p < pos + length && p + 2 < end; p++) index(p);
                pos += length;
            } else {
                int value = w.input[pos++] & 255;
                w.tokens[tokenCount++] = value;
                w.literals[value]++;
            }
        }
        indexedUntil = base + Math.max(0, end - 2);
        w.literals[256]++;
    }

    private void rle(int symbol, int extra, int count) {
        w.rleSymbol[rleCount] = symbol;
        w.rleExtra[rleCount] = extra;
        w.rleBits[rleCount++] = count;
        w.codeFrequency[symbol]++;
    }

    private int dynamicCost() {
        w.literalTree.build(w.literals, 15);
        w.distanceTree.build(w.distances, 15);
        nl = 286;
        while (nl > 257 && w.literalTree.lengths[nl - 1] == 0) nl--;
        nd = 30;
        while (nd > 1 && w.distanceTree.lengths[nd - 1] == 0) nd--;
        System.arraycopy(w.literalTree.lengths, 0, w.sequence, 0, nl);
        System.arraycopy(w.distanceTree.lengths, 0, w.sequence, nl, nd);
        Arrays.fill(w.codeFrequency, 0);
        rleCount = 0;
        for (int pos = 0; pos < nl + nd; ) {
            int value = w.sequence[pos], end = pos + 1;
            while (end < nl + nd && w.sequence[end] == value) end++;
            int run = end - pos;
            if (value == 0) {
                while (run >= 11) {
                    int n = Math.min(run, 138);
                    rle(18, n - 11, 7);
                    run -= n;
                }
                if (run >= 3) {
                    rle(17, run - 3, 3);
                    run = 0;
                }
                while (run-- > 0) rle(0, 0, 0);
            } else {
                rle(value, 0, 0);
                run--;
                while (run >= 3) {
                    int n = Math.min(run, 6);
                    rle(16, n - 3, 2);
                    run -= n;
                }
                while (run-- > 0) rle(value, 0, 0);
            }
            pos = end;
        }
        w.codeTree.build(w.codeFrequency, 7);
        nc = 19;
        while (nc > 4 && w.codeTree.lengths[ORDER[nc - 1]] == 0) nc--;
        int cost = 3 + 14 + nc * 3 + extraBits;
        for (int i = 0; i < 286; i++) cost += w.literals[i] * w.literalTree.lengths[i];
        for (int i = 0; i < 30; i++) cost += w.distances[i] * w.distanceTree.lengths[i];
        for (int i = 0; i < rleCount; i++)
            cost += w.codeTree.lengths[w.rleSymbol[i]] + w.rleBits[i];
        return cost;
    }

    private void block() throws IOException {
        if (used == 0) return;
        ReadSupport.check(null);
        flushEncoded();
        if (storeOnly) {
            put(0, 3);
            align();
            storedHeader(used);
            System.arraycopy(w.input, history, w.encoded, encodedUsed, used);
            encodedUsed += used;
        } else {
            tokenize();
            int fixed = 3 + extraBits;
            for (int i = 0; i < 286; i++) fixed += w.literals[i] * (FIXED[i] & 15);
            for (int f : w.distances) fixed += f * 5;
            int stored = 3 + ((8 - ((bitCount + 3) & 7)) & 7) + 32 + used * 8,
                    dynamic = used >= 64 ? dynamicCost() : Integer.MAX_VALUE;
            if (stored <= fixed && stored <= dynamic) {
                put(0, 3);
                align();
                storedHeader(used);
                System.arraycopy(w.input, history, w.encoded, encodedUsed, used);
                encodedUsed += used;
            } else {
                boolean custom = dynamic < fixed;
                put(custom ? 4 : 2, 3);
                if (custom) {
                    put(nl - 257, 5);
                    put(nd - 1, 5);
                    put(nc - 4, 4);
                    for (int i = 0; i < nc; i++) put(w.codeTree.lengths[ORDER[i]], 3);
                    for (int i = 0; i < rleCount; i++) {
                        symbol(w.codeTree.codes, w.rleSymbol[i]);
                        put(w.rleExtra[i], w.rleBits[i]);
                    }
                }
                int[] literalCodes = custom ? w.literalTree.codes : FIXED;
                for (int i = 0; i < tokenCount; i++) {
                    int token = w.tokens[i];
                    if (token < 256) symbol(literalCodes, token);
                    else {
                        int length = token >>> 16,
                                distance = token & 65535,
                                lc = LC[length],
                                dc = DC[distance];
                        symbol(literalCodes, 257 + lc);
                        put(length - LENGTH[lc], LB[lc]);
                        if (custom) symbol(w.distanceTree.codes, dc);
                        else put(Integer.reverse(dc) >>> 27, 5);
                        put(distance - DIST[dc], DB[dc]);
                    }
                }
                symbol(literalCodes, 256);
            }
        }
        flushEncoded();
        int keep = Math.min(WINDOW, history + used);
        System.arraycopy(w.input, history + used - keep, w.input, 0, keep);
        base += history + used - keep;
        history = keep;
        used = 0;
    }

    private void symbol(int[] codes, int value) {
        int entry = codes[value];
        put(entry >>> 4, entry & 15);
    }

    private void put(int value, int count) {
        bits |= value << bitCount;
        bitCount += count;
        while (bitCount >= 8) {
            w.encoded[encodedUsed++] = (byte) bits;
            bits >>>= 8;
            bitCount -= 8;
        }
    }

    private void align() {
        if (bitCount != 0) {
            w.encoded[encodedUsed++] = (byte) bits;
            bits = bitCount = 0;
        }
    }

    private void storedHeader(int size) {
        w.encoded[encodedUsed++] = (byte) size;
        w.encoded[encodedUsed++] = (byte) (size >>> 8);
        w.encoded[encodedUsed++] = (byte) ~size;
        w.encoded[encodedUsed++] = (byte) (~size >>> 8);
    }

    private void flushEncoded() throws IOException {
        if (encodedUsed != 0) {
            out.write(w.encoded, 0, encodedUsed);
            encodedUsed = 0;
        }
    }

    @Override
    public void flush() throws IOException {
        if (!finished) {
            block();
            put(0, 3);
            align();
            storedHeader(0);
            flushEncoded();
        }
        out.flush();
    }

    void finish() throws IOException {
        if (finished) return;
        block();
        put(3, 3);
        symbol(FIXED, 256);
        align();
        flushEncoded();
        finished = true;
    }
}
