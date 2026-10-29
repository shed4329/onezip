package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.Arrays;
import java.util.Objects;

/** Independent RFC 1951 decoder: stored, fixed and dynamic canonical Huffman blocks. */
public final class DeflateInputStream extends InputStream {
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
    private static final Tree FIXED_LITERAL, FIXED_DISTANCE;

    static {
        try {
            int[] l = new int[288], d = new int[32];
            Arrays.fill(l, 0, 144, 8);
            Arrays.fill(l, 144, 256, 9);
            Arrays.fill(l, 256, 280, 7);
            Arrays.fill(l, 280, 288, 8);
            Arrays.fill(d, 5);
            FIXED_LITERAL = new Tree(l, false);
            FIXED_DISTANCE = new Tree(d, false);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final InputStream in;
    private final boolean framed;
    // Entry streams are bounded by ZipArchiveReader. Bulk refill avoids a synchronized
    // BufferedInputStream.read() call for every compressed byte.
    private final byte[] input = new byte[16384];
    private int inputPos, inputEnd;
    private final byte[] window = new byte[32768];
    private int bits, count, pos, history, pending, distance, stored;
    private boolean blockEnd = true, last, ended, closed;
    private Tree literals, distances;

    public DeflateInputStream(InputStream in) {
        this(in, false);
    }

    /** GZIP framing owns the bytes after BFINAL; restore all speculative lookahead. */
    DeflateInputStream(InputStream in, boolean framed) {
        this.in = Objects.requireNonNull(in);
        this.framed = framed;
        if (framed && !(in instanceof PushbackInputStream))
            throw new IllegalArgumentException("Pushback required");
    }

    private void endBlock() throws IOException {
        if (framed) {
            PushbackInputStream p = (PushbackInputStream) in;
            p.unread(input, inputPos, inputEnd - inputPos);
            int padding = count & 7;
            bits >>>= padding;
            count -= padding;
            for (int i = count / 8 - 1; i >= 0; i--) p.unread((bits >>> (8 * i)) & 255);
            count = 0;
            inputPos = inputEnd;
        } else if (count >= 8 || inputPos < inputEnd) throw new IOException("DEFLATE 尾端含多餘資料");
        ended = true;
    }

    private void checkOpen() throws IOException {
        if (closed) throw new IOException("Stream closed");
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            in.close();
        }
    }

    private int byteValue() throws IOException {
        if (inputPos == inputEnd) {
            inputEnd = in.read(input);
            inputPos = 0;
            if (inputEnd < 0) {
                inputEnd = 0;
                return -1;
            }
            if (inputEnd == 0) return in.read();
        }
        return input[inputPos++] & 255;
    }

    private int bits(int n) throws IOException {
        while (count < n) {
            int b = byteValue();
            if (b < 0) throw new EOFException("壓縮資料截斷");
            bits |= b << count;
            count += 8;
        }
        int v = bits & ((1 << n) - 1);
        bits >>>= n;
        count -= n;
        return v;
    }

    private void lookahead(int n) throws IOException {
        while (count < n) {
            int b = byteValue();
            if (b < 0) break;
            bits |= b << count;
            count += 8;
        }
    }

    private void block() throws IOException {
        last = bits(1) != 0;
        int type = bits(2);
        blockEnd = false;
        if (type == 0) {
            int padding = count & 7;
            bits >>>= padding;
            count -= padding;
            stored = bits(16);
            if ((bits(16) ^ stored) != 65535) throw new IOException("DEFLATE LEN/NLEN 不符");
            if (stored == 0) blockEnd = true;
        } else if (type == 1) {
            literals = FIXED_LITERAL;
            distances = FIXED_DISTANCE;
        } else if (type == 2) {
            int nl = bits(5) + 257, nd = bits(5) + 1, nc = bits(4) + 4;
            if (nl > 286) throw new IOException("無效的 DEFLATE HLIT");
            int[] order = {16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15},
                    cl = new int[19];
            for (int i = 0; i < nc; i++) cl[order[i]] = bits(3);
            Tree codes = new Tree(cl, false);
            int[] lengths = new int[nl + nd];
            for (int i = 0; i < lengths.length; ) {
                int s = codes.decode(this);
                if (s < 16) lengths[i++] = s;
                else {
                    int run, value = 0;
                    if (s == 16) {
                        if (i == 0) throw new IOException("無效的 Huffman 重複碼");
                        run = bits(2) + 3;
                        value = lengths[i - 1];
                    } else run = s == 17 ? bits(3) + 3 : bits(7) + 11;
                    if (run > lengths.length - i) throw new IOException("Huffman 重複碼越界");
                    Arrays.fill(lengths, i, i + run, value);
                    i += run;
                }
            }
            if (lengths[256] == 0) throw new IOException("DEFLATE 缺少結束碼");
            literals = new Tree(Arrays.copyOf(lengths, nl), true);
            distances = new Tree(Arrays.copyOfRange(lengths, nl, lengths.length), true);
        } else throw new IOException("保留的 DEFLATE 區塊類型");
    }

    @Override
    public int read() throws IOException {
        checkOpen();
        for (; ; ) {
            if (pending > 0) {
                --pending;
                return emit(window[(pos - distance) & 32767] & 255);
            }
            if (ended) return -1;
            if (blockEnd) {
                if (last) {
                    endBlock();
                    return -1;
                }
                block();
                continue;
            }
            if (stored > 0) {
                int b = byteValue();
                if (b < 0) throw new EOFException("DEFLATE 儲存區塊截斷");
                if (--stored == 0) blockEnd = true;
                return emit(b);
            }
            int s = literals.decode(this);
            if (s < 256) return emit(s);
            if (s == 256) {
                blockEnd = true;
                continue;
            }
            if (s > 285) throw new IOException("保留的 DEFLATE 長度碼");
            pending = LENGTH[s - 257] + bits(LB[s - 257]);
            int d = distances.decode(this);
            if (d >= 30) throw new IOException("保留的 DEFLATE 距離碼");
            distance = DIST[d] + bits(DB[d]);
            if (distance > history) throw new IOException("DEFLATE 距離超出字典");
        }
    }

    private int emit(int b) {
        window[pos] = (byte) b;
        pos = (pos + 1) & 32767;
        if (history < 32768) history++;
        return b;
    }

    private void append(byte[] b, int o, int n) {
        int left = n;
        while (left > 0) {
            int k = Math.min(left, window.length - pos);
            System.arraycopy(b, o, window, pos, k);
            pos = (pos + k) & 32767;
            o += k;
            left -= k;
        }
        history = Math.min(window.length, history + n);
    }

    private void repeat(byte[] b, int o, int n) {
        int start = (pos - distance) & 32767;
        if (distance == 1) Arrays.fill(b, o, o + n, window[start]);
        else {
            int seed = Math.min(n, distance), first = Math.min(seed, window.length - start);
            System.arraycopy(window, start, b, o, first);
            if (first < seed) System.arraycopy(window, 0, b, o + first, seed - first);
            for (int copied = seed; copied < n; ) {
                int k = Math.min(copied, n - copied);
                System.arraycopy(b, o, b, o + copied, k);
                copied += k;
            }
        }
        append(b, o, n);
    }

    @Override
    public int read(byte[] b, int o, int n) throws IOException {
        Objects.checkFromIndexSize(o, n, b.length);
        checkOpen();
        if (n == 0) return 0;
        int done = 0, nextCheck = 0;
        while (done < n) {
            if (done >= nextCheck) {
                ReadSupport.check(null);
                nextCheck = done + 4096;
            }
            if (pending > 0) {
                int k = Math.min(pending, n - done);
                repeat(b, o + done, k);
                pending -= k;
                done += k;
            } else if (stored > 0) {
                int k = Math.min(stored, n - done);
                if (inputPos < inputEnd) {
                    k = Math.min(k, inputEnd - inputPos);
                    System.arraycopy(input, inputPos, b, o + done, k);
                    inputPos += k;
                } else {
                    k = in.read(b, o + done, k);
                    if (k < 0) throw new EOFException("DEFLATE 儲存區塊截斷");
                }
                append(b, o + done, k);
                stored -= k;
                done += k;
                if (stored == 0) blockEnd = true;
            } else {
                int v = read();
                if (v < 0) break;
                b[o + done++] = (byte) v;
            }
        }
        return done == 0 ? -1 : done;
    }

    /**
     * Canonical codes decoded in transmitted MSB order, while surrounding integers are LSB first.
     */
    private static final class Tree {
        final int[] counts = new int[16], symbols, fast = new int[512], secondary;
        final int max;

        Tree(int[] lengths, boolean singleAllowed) throws IOException {
            int m = 0, n = 0;
            for (int len : lengths) {
                if (len < 0 || len > 15) throw new IOException("Huffman 長度無效");
                if (len > 0) {
                    counts[len]++;
                    n++;
                    m = Math.max(m, len);
                }
            }
            max = m;
            symbols = new int[n];
            int left = 1;
            for (int len = 1; len <= 15; len++) {
                left = (left << 1) - counts[len];
                if (left < 0) throw new IOException("Huffman 樹過度訂閱");
            }
            if (left != 0 && !(singleAllowed && n <= 1 && m <= 1))
                throw new IOException("Huffman 樹不完整");
            int[] offsets = new int[16];
            for (int i = 1; i < 15; i++) offsets[i + 1] = offsets[i] + counts[i];
            for (int i = 0; i < lengths.length; i++)
                if (lengths[i] != 0) symbols[offsets[lengths[i]]++] = i;
            int[] suffixBits = new int[512];
            int code = 0, index = 0;
            for (int len = 1; len <= max; len++) {
                for (int j = 0; j < counts[len]; j++) {
                    int reversed = Integer.reverse(code + j) >>> (32 - len),
                            entry = (symbols[index + j] << 4) | len;
                    if (len <= 9) {
                        for (int slot = reversed; slot < 512; slot += 1 << len) fast[slot] = entry;
                    } else
                        suffixBits[reversed & 511] = Math.max(suffixBits[reversed & 511], len - 9);
                }
                index += counts[len];
                code = (code + counts[len]) << 1;
            }
            int size = 0;
            for (int prefix = 0; prefix < 512; prefix++)
                if (suffixBits[prefix] > 0) {
                    fast[prefix] = -((size << 4) | suffixBits[prefix]);
                    size += 1 << suffixBits[prefix];
                }
            secondary = new int[size];
            code = 0;
            index = 0;
            for (int len = 1; len <= max; len++) {
                if (len > 9)
                    for (int j = 0; j < counts[len]; j++) {
                        int reversed = Integer.reverse(code + j) >>> (32 - len),
                                pointer = -fast[reversed & 511],
                                offset = pointer >>> 4,
                                limit = 1 << (pointer & 15),
                                entry = (symbols[index + j] << 4) | len;
                        for (int slot = reversed >>> 9; slot < limit; slot += 1 << (len - 9))
                            secondary[offset + slot] = entry;
                    }
                index += counts[len];
                code = (code + counts[len]) << 1;
            }
        }

        int decode(DeflateInputStream reader) throws IOException {
            reader.lookahead(Math.min(max, 9));
            int entry = fast[reader.bits & 511];
            if (entry < 0) {
                int pointer = -entry, width = pointer & 15;
                reader.lookahead(9 + width);
                entry = secondary[(pointer >>> 4) + ((reader.bits >>> 9) & ((1 << width) - 1))];
            }
            int length = entry & 15;
            if (length != 0 && length <= reader.count) {
                reader.bits >>>= length;
                reader.count -= length;
                return entry >>> 4;
            }
            int code = 0, first = 0, index = 0;
            for (int len = 1; len <= max; len++) {
                code = (code << 1) | reader.bits(1);
                int n = counts[len];
                if (code - first < n) return symbols[index + code - first];
                index += n;
                first = (first + n) << 1;
            }
            throw new IOException("無效的 Huffman 碼");
        }
    }
}
