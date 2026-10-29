package cn.shed4329.onezip.engine.lzma;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Independent Java LZMA/LZMA2 encoder, reconstructed from the observed token loop and the public
 * LZMA2 format. Normal levels use a bounded price-based parser; fast levels retain greedy
 * selection. No native libraries or Ark code loaded. Dictionary is a bounded 2 MiB sliding window
 * across input staging blocks. Compressed chunks stop before the 64 KiB payload limit, preserving
 * useful compression instead of discarding the entire input block. Not claimed bit-identical to an
 * external reference encoder.
 */
public final class ArkLzma2OutputStream extends OutputStream {
    // Plain Java array views; the JIT can lower these to unaligned integer loads.
    // No native archive API, off-heap access or compression implementation is used.
    private static final VarHandle INT_LE =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle LONG_LE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    public static final int BLOCK_SIZE = 2 * 1024 * 1024;
    private final OutputStream out;
    private final int level;
    private byte[] block = new byte[65536];
    private final Workspace workspace;
    private boolean ownsWorkspace = true;
    private int used;
    private int history;
    private int positionBase;
    private boolean encoderStarted, dictionaryStarted, needProperties = true;
    private boolean finished;
    private boolean closed;
    private IOException failure;
    private int positionBits = 2;

    public ArkLzma2OutputStream(OutputStream out, int level) {
        this(out, level, new Workspace());
    }

    /** One archive writer can reuse match-finder/probability storage across sequential entries. */
    public static final class Workspace {
        /** Creates lazy reusable storage; one encoder may own it at a time. */
        public Workspace() {}

        private Encoder encoder;
        private boolean inUse;
    }

    public ArkLzma2OutputStream(OutputStream out, int level, Workspace workspace) {
        if (workspace.inUse) throw new IllegalStateException("LZMA workspace already in use");
        workspace.inUse = true;
        this.workspace = workspace;
        this.out = out;
        this.level = level < 0 ? 5 : Math.min(level, 9);
    }

    @Override
    public void write(int value) throws IOException {
        byte[] one = {(byte) value};
        write(one, 0, 1);
    }

    @Override
    public void write(byte[] data, int offset, int length) throws IOException {
        java.util.Objects.checkFromIndexSize(offset, length, data.length);
        ensureOpen();
        try {
            while (length != 0) {
                checkInterrupted();
                int limit = history + BLOCK_SIZE;
                if (used == block.length && block.length < limit)
                    block = Arrays.copyOf(block, Math.min(limit, block.length * 2));
                int n = Math.min(length, block.length - used);
                System.arraycopy(data, offset, block, used, n);
                used += n;
                offset += n;
                length -= n;
                if (used - history == BLOCK_SIZE) encodeBlock();
            }
        } catch (IOException ex) {
            failure = ex;
            throw ex;
        }
    }

    private void encodeBlock() throws IOException {
        if (used == history) return;
        checkInterrupted();
        if (level == 0) {
            writeRaw(0, used, !dictionaryStarted);
            dictionaryStarted = true;
            used = 0;
            return;
        }
        if (workspace.encoder == null) workspace.encoder = new Encoder();
        Encoder encoder = workspace.encoder;
        encoder.positionMask = (1 << positionBits) - 1;
        boolean fresh = !encoderStarted;
        encoder.begin(block, used, level, history, positionBase, fresh);
        if (fresh && history != 0) encoder.indexHistory(history);
        encoderStarted = true;
        boolean resetDictionary = !dictionaryStarted;
        while (encoder.position < used) {
            checkInterrupted();
            int start = encoder.position;
            int probe = Math.min(65536, used - start);
            if (encoder.likelyIncompressible(probe)) {
                encoder.skipRaw(probe);
                writeRaw(start, probe, resetDictionary);
                resetDictionary = false;
                dictionaryStarted = true;
                continue;
            }
            int packed = encoder.encodeChunk(), unpacked = encoder.position - start;
            // Include the property/header difference when comparing to raw chunks.
            if (packed + (needProperties ? 3 : 2) < unpacked) {
                int u = unpacked - 1, p = packed - 1;
                int control =
                        resetDictionary
                                ? 0xe0
                                : needProperties ? 0xc0 : encoder.resetForChunk ? 0xa0 : 0x80;
                out.write(control | (u >>> 16));
                out.write(u >>> 8);
                out.write(u);
                out.write(p >>> 8);
                out.write(p);
                if (needProperties) out.write(positionBits * 45 + 3); // lc=3, lp=0.
                out.write(encoder.rc.bytes, 0, packed);
                needProperties = false;
            } else {
                writeRaw(start, unpacked, resetDictionary);
                encoder.modelValid = false;
            }
            resetDictionary = false;
            dictionaryStarted = true;
        }
        // Retain prior bytes for dictionary references, independently of how
        // frequently callers write or flush. Only the new input is encoded.
        int keep = Math.min(BLOCK_SIZE, used), discard = used - keep;
        System.arraycopy(block, discard, block, 0, keep);
        positionBase = (positionBase + discard) & 3;
        used = history = keep;
    }

    private void writeRaw(int start, int length, boolean reset) throws IOException {
        while (length != 0) {
            checkInterrupted();
            int n = Math.min(65536, length);
            out.write(reset ? 1 : 2);
            out.write((n - 1) >>> 8);
            out.write(n - 1);
            out.write(block, start, n);
            start += n;
            length -= n;
            reset = false;
        }
    }

    private void release() {
        if (ownsWorkspace) {
            workspace.inUse = false;
            ownsWorkspace = false;
        }
    }

    public void finish() throws IOException {
        finish(true);
    }

    private void finish(boolean terminate) throws IOException {
        if (failure != null) throw failure;
        if (finished) return;
        if (closed) throw new IOException("LZMA2 stream closed");
        try {
            encodeBlock();
            if (terminate) out.write(0);
            finished = true;
        } catch (IOException ex) {
            failure = ex;
            throw ex;
        } finally {
            release();
        }
    }

    /** Internal worker fragment: owns input, retains decoder history, resets only models. */
    static byte[] fragment(byte[] input, int history, long offset, int level, Workspace workspace)
            throws IOException {
        if (level == 0 || history < 0 || history > BLOCK_SIZE || input.length <= history)
            throw new IllegalArgumentException("Invalid LZMA2 fragment");
        java.io.ByteArrayOutputStream packed = new java.io.ByteArrayOutputStream();
        try (ArkLzma2OutputStream stream = new ArkLzma2OutputStream(packed, level, workspace)) {
            stream.block = input;
            stream.used = input.length;
            stream.history = history;
            stream.positionBase = (int) (offset - history) & 3;
            stream.dictionaryStarted = offset != 0;
            stream.positionBits = textLike(input, history) ? 0 : 2;
            stream.finish(false);
        }
        return packed.toByteArray();
    }

    private static boolean textLike(byte[] input, int start) {
        int length = Math.min(4096, input.length - start), controls = 0;
        if (length < 256) return false;
        for (int i = start; i < start + length; i++) {
            int value = input[i] & 255;
            if ((value < 32 && value != 9 && value != 10 && value != 13) || value == 127)
                controls++;
        }
        return controls * 128 <= length;
    }

    @Override
    public void flush() throws IOException {
        ensureOpen();
        try {
            encodeBlock();
            out.flush();
        } catch (IOException ex) {
            failure = ex;
            throw ex;
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        try {
            if (failure == null) finish();
        } finally {
            release();
            closed = true;
            out.close();
        }
    }

    private void ensureOpen() throws IOException {
        if (failure != null) throw failure;
        if (closed || finished) throw new IOException("LZMA2 stream already finished");
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("操作已取消");
    }

    private static int[] probabilities(int count) {
        int[] result = new int[count];
        Arrays.fill(result, 1024);
        return result;
    }

    /** Adaptive binary range coder: 11-bit probabilities, update shift 5. */
    private static final class RangeEncoder {
        final byte[] bytes = new byte[65536];
        int used;
        int range = -1;
        long low;
        int cache, cacheSize;

        void reset() {
            used = 0;
            range = -1;
            low = 0;
            cache = cacheSize = 0;
        }

        void emit(int value) {
            if (used == bytes.length)
                throw new IllegalStateException("LZMA2 chunk budget exceeded");
            bytes[used++] = (byte) value;
        }

        void shiftLow() {
            long low32 = low & 0xffffffffL;
            int carry = (int) (low >>> 32);
            low = (low32 << 8) & 0xffffffffL;
            if (low32 < 0xff000000L || carry != 0) {
                emit(cache + carry);
                cache = (int) (low32 >>> 24);
                while (cacheSize != 0) {
                    emit(carry - 1);
                    --cacheSize;
                }
            } else ++cacheSize;
        }

        void bit(int[] probs, int index, int bit) {
            int prob = probs[index];
            int bound = (range >>> 11) * prob;
            if (bit == 0) {
                range = bound;
                probs[index] = prob + ((2048 - prob) >>> 5);
            } else {
                low += Integer.toUnsignedLong(bound);
                range -= bound;
                probs[index] = prob - (prob >>> 5);
            }
            normalize();
        }

        void normalize() {
            if ((range & 0xff000000) == 0) {
                range <<= 8;
                shiftLow();
            }
        }

        void tree(int[] probs, int start, int bits, int value) {
            int node = 1;
            for (int i = bits - 1; i >= 0; --i) {
                int bit = (value >>> i) & 1;
                bit(probs, start + node, bit);
                node = (node << 1) | bit;
            }
        }

        void reverseTree(int[] probs, int start, int bits, int value) {
            int node = 1;
            for (int i = 0; i < bits; ++i) {
                int bit = (value >>> i) & 1;
                bit(probs, start + node, bit);
                node = (node << 1) | bit;
            }
        }

        void direct(int value, int bits) {
            for (int i = bits - 1; i >= 0; --i) {
                range >>>= 1;
                if (((value >>> i) & 1) != 0) low += Integer.toUnsignedLong(range);
                normalize();
            }
        }

        int finish() {
            for (int i = 0; i < 5; ++i) shiftLow();
            return used;
        }
    }

    private static final class LengthEncoder {
        final int[] choice = probabilities(2),
                low = probabilities(16 * 8),
                mid = probabilities(16 * 8),
                high = probabilities(256);

        void reset() {
            Arrays.fill(choice, 1024);
            Arrays.fill(low, 1024);
            Arrays.fill(mid, 1024);
            Arrays.fill(high, 1024);
        }

        void encode(RangeEncoder rc, int symbol, int posState) {
            if (symbol < 8) {
                rc.bit(choice, 0, 0);
                rc.tree(low, posState * 8, 3, symbol);
            } else {
                rc.bit(choice, 0, 1);
                symbol -= 8;
                if (symbol < 8) {
                    rc.bit(choice, 1, 0);
                    rc.tree(mid, posState * 8, 3, symbol);
                } else {
                    rc.bit(choice, 1, 1);
                    rc.tree(high, 0, 8, symbol - 8);
                }
            }
        }
    }

    private static final class Encoder {
        byte[] input;
        int size, depth, position, twoCandidate, threeCandidate, positionBase, niceLength;
        boolean costMode, balancedMode;
        int positionMask = 3;
        boolean modelValid, resetForChunk;
        final int[] head = new int[1 << 20],
                head2 = new int[65536],
                head3 = new int[65536],
                samples = new int[4096];
        int[] previous = new int[0];
        final int[] isMatch = probabilities(12 * 16),
                isRep = probabilities(12),
                g0 = probabilities(12),
                g1 = probabilities(12),
                g2 = probabilities(12),
                repLong = probabilities(12 * 16);
        final int[] literal = probabilities(8 * 0x300),
                slots = probabilities(4 * 64),
                footer = probabilities(114),
                align = probabilities(16);
        final int[] reps = {1, 1, 1, 1};
        final LengthEncoder normalLength = new LengthEncoder(), repeatLength = new LengthEncoder();
        final RangeEncoder rc = new RangeEncoder();
        int state;

        void begin(
                byte[] input, int size, int level, int history, int positionBase, boolean reset) {
            int discard = this.size - history;
            this.input = input;
            this.size = size;
            this.positionBase = positionBase;
            position = history;
            if (reset) modelValid = false;
            costMode = level >= 5;
            balancedMode = level == 5;
            niceLength = level >= 7 ? 64 : 32;
            depth = level <= 1 ? 8 : level <= 3 ? 24 : level <= 5 ? 64 : level <= 7 ? 128 : 256;
            if (previous.length < size) previous = Arrays.copyOf(previous, size);
            if (reset) {
                Arrays.fill(head, -1);
                Arrays.fill(head2, -1);
                Arrays.fill(head3, -1);
            } else {
                // Preserve the same chains as rebuilding the retained window,
                // using sequential array passes instead of random hash writes.
                if (discard != 0) {
                    for (int i = 0; i < head.length; i++) head[i] = Math.max(-1, head[i] - discard);
                    for (int i = 0; i < head3.length; i++)
                        head3[i] = Math.max(-1, head3[i] - discard);
                    for (int i = 0; i < head2.length; i++)
                        head2[i] = Math.max(-1, head2[i] - discard);
                    System.arraycopy(previous, discard, previous, 0, history);
                    for (int i = 0; i < history; i++)
                        previous[i] = Math.max(-1, previous[i] - discard);
                }
                // These positions could not all be hashed before bytes beyond
                // the previous input end arrived; no full-word chain exists yet.
                indexRange(Math.max(0, history - 3), history);
            }
        }

        int hash4(int pos) {
            return (((int) INT_LE.get(input, pos)) * 0x9e3779b1) >>> 12;
        }

        int hash3Word(int v) {
            return ((((v & 255) * 251 + ((v >>> 8) & 255)) * 251) + ((v >>> 16) & 255)) & 65535;
        }

        int hash3(int pos) {
            return (((input[pos] & 255) * 251 + (input[pos + 1] & 255)) * 251
                            + (input[pos + 2] & 255))
                    & 65535;
        }

        int insert(int pos) {
            twoCandidate = threeCandidate = -1;
            if (pos + 1 < size) {
                int h2 = (input[pos] & 255) | ((input[pos + 1] & 255) << 8);
                twoCandidate = head2[h2];
                head2[h2] = pos;
            }
            if (pos + 2 >= size) return -1;
            if (pos + 3 >= size) {
                int h3 = hash3(pos);
                threeCandidate = head3[h3];
                head3[h3] = pos;
                return -1;
            }
            int word = (int) INT_LE.get(input, pos), h3 = hash3Word(word);
            threeCandidate = head3[h3];
            head3[h3] = pos;
            int hash = (word * 0x9e3779b1) >>> 12, candidate = head[hash];
            previous[pos] = candidate;
            head[hash] = pos;
            return candidate;
        }

        void indexWord(int word, int pos) {
            head2[word & 65535] = pos;
            int h = (word * 0x9e3779b1) >>> 12;
            previous[pos] = head[h];
            head[h] = pos;
            head3[hash3Word(word)] = pos;
        }

        void indexRange(int start, int end) {
            int pos = start;
            for (; pos + 3 < end && pos + 7 < size; pos += 4) {
                long word = (long) LONG_LE.get(input, pos);
                indexWord((int) word, pos);
                indexWord((int) (word >>> 8), pos + 1);
                indexWord((int) (word >>> 16), pos + 2);
                indexWord((int) (word >>> 24), pos + 3);
            }
            for (; pos < end; pos++) {
                if (pos + 3 < size) indexWord((int) INT_LE.get(input, pos), pos);
                else {
                    if (pos + 2 < size) head3[hash3(pos)] = pos;
                    if (pos + 1 < size)
                        head2[(input[pos] & 255) | ((input[pos + 1] & 255) << 8)] = pos;
                }
            }
        }

        private void indexFourWord(int word, int pos) {
            int h = (word * 0x9e3779b1) >>> 12;
            previous[pos] = head[h];
            head[h] = pos;
        }

        void indexHistory(int end) {
            // Four-byte chains require every predecessor. Short hash tables only
            // retain their latest slot: build them backwards and write once.
            int pos = 0;
            for (; pos + 3 < end && pos + 7 < size; pos += 4) {
                long word = (long) LONG_LE.get(input, pos);
                indexFourWord((int) word, pos);
                indexFourWord((int) (word >>> 8), pos + 1);
                indexFourWord((int) (word >>> 16), pos + 2);
                indexFourWord((int) (word >>> 24), pos + 3);
            }
            for (; pos < end && pos + 3 < size; pos++)
                indexFourWord((int) INT_LE.get(input, pos), pos);
            int missing2 = head2.length, missing3 = head3.length;
            for (pos = end - 1; pos >= 0 && (missing2 != 0 || missing3 != 0); pos--) {
                if (pos + 3 < size) {
                    int word = (int) INT_LE.get(input, pos),
                            h2 = word & 65535,
                            h3 = hash3Word(word);
                    if (head2[h2] < 0) {
                        head2[h2] = pos;
                        missing2--;
                    }
                    if (head3[h3] < 0) {
                        head3[h3] = pos;
                        missing3--;
                    }
                } else {
                    if (pos + 1 < size) {
                        int h = (input[pos] & 255) | ((input[pos + 1] & 255) << 8);
                        if (head2[h] < 0) {
                            head2[h] = pos;
                            missing2--;
                        }
                    }
                    if (pos + 2 < size) {
                        int h = hash3(pos);
                        if (head3[h] < 0) {
                            head3[h] = pos;
                            missing3--;
                        }
                    }
                }
            }
        }

        boolean same3(int a, int b) {
            return input[a] == input[b]
                    && input[a + 1] == input[b + 1]
                    && input[a + 2] == input[b + 2];
        }

        boolean same4(int a, int b) {
            return same3(a, b) && input[a + 3] == input[b + 3];
        }

        boolean likelyIncompressible(int length) {
            return likelyIncompressibleAt(position, length);
        }

        boolean likelyIncompressibleAt(int start, int length) {
            if (length < 8192) return false;
            Arrays.fill(samples, -1);
            int hits = 0, end = start + length - 4, step = Math.max(1, length / 256);
            for (int pos = start; pos <= end; pos += step) {
                int hash = hash4(pos), local = samples[hash & 4095];
                boolean match = local >= 0 && same4(pos, local);
                samples[hash & 4095] = pos;
                for (int candidate = head[hash], n = 0;
                        !match && candidate >= 0 && pos - candidate <= BLOCK_SIZE && n < 4;
                        n++, candidate = previous[candidate]) match = same4(pos, candidate);
                int third = head3[hash3(pos)];
                if (!match && third >= 0 && pos - third <= BLOCK_SIZE) match = same3(pos, third);
                if (match && ++hits >= 4) return false;
            }
            return true;
        }

        void skipRaw(int length) {
            int end = position + length;
            indexRange(position, end);
            position = end;
            modelValid = false;
        }

        int matchLength(int pos, int distance, int limit) {
            if (distance > pos || distance > BLOCK_SIZE) return 0;
            int candidate = pos - distance;
            if (input[pos] != input[candidate]) return 0;
            if (limit >= 8) {
                long difference =
                        (long) LONG_LE.get(input, pos) ^ (long) LONG_LE.get(input, candidate);
                if (difference != 0) return Long.numberOfTrailingZeros(difference) >>> 3;
                if (limit == 8) return 8;
                int mismatch =
                        Arrays.mismatch(
                                input,
                                pos + 8,
                                pos + limit,
                                input,
                                candidate + 8,
                                candidate + limit);
                return mismatch < 0 ? limit : mismatch + 8;
            }
            int length = 1;
            while (length < limit && input[pos + length] == input[candidate + length]) length++;
            return length;
        }

        int encodeChunk() throws InterruptedIOException {
            resetForChunk = !modelValid;
            if (resetForChunk) {
                for (int[] p :
                        new int[][] {
                            isMatch, isRep, g0, g1, g2, repLong, literal, slots, footer, align
                        }) Arrays.fill(p, 1024);
                Arrays.fill(reps, 1);
                normalLength.reset();
                repeatLength.reset();
                state = 0;
            }
            // LZMA2 limits PACKED data to 64 KiB, but UNPACKED data to 2 MiB.
            // Expand highly compressible runs without blindly pulling later
            // noise into range coding. The 1/16 growth threshold is our bounded
            // throughput policy, not an asserted reconstruction of Ark's parser.
            rc.reset();
            int start = position,
                    pos = start,
                    nextCheck = pos,
                    hardEnd = Math.min(size, pos + BLOCK_SIZE);
            int end = Math.min(hardEnd, pos + 65536);
            // Reserve >4 KiB for the last token, carry chain and five-byte flush.
            while (pos < hardEnd && rc.used + rc.cacheSize < 60 * 1024) {
                if (pos == end) {
                    if ((rc.used + rc.cacheSize) * 16 >= pos - start) break;
                    int next = Math.min(65536, hardEnd - pos);
                    if (likelyIncompressibleAt(pos, next)) break;
                    end += next;
                }
                if (pos >= nextCheck) {
                    checkInterrupted();
                    nextCheck = pos + 4096;
                }
                int limit = Math.min(273, end - pos);
                int candidate = insert(pos),
                        candidate3 = threeCandidate,
                        length = 1,
                        code = -1,
                        repLength = 0,
                        repIndex = 0;
                if (costMode) {
                    pos += planAndEmit(pos, end, candidate, twoCandidate, candidate3);
                    continue;
                }
                for (int i = 0; i < 4; ++i) {
                    int n = matchLength(pos, reps[i], limit);
                    if (n > repLength) {
                        repLength = n;
                        repIndex = i;
                    }
                }
                int bestLength = 0, bestDistance = 0;
                if (limit >= 3
                        && candidate3 >= 0
                        && pos - candidate3 <= BLOCK_SIZE
                        && same3(pos, candidate3)) {
                    bestLength = 3;
                    bestDistance = pos - candidate3;
                }
                // A new match can win only if it is > repLength + 1. Using that
                // lower bound prunes losing candidates without changing tokens.
                if (repLength >= 2) bestLength = Math.max(bestLength, repLength + 1);
                for (int count = 0;
                        candidate >= 0 && pos - candidate <= BLOCK_SIZE && count < depth;
                        ++count) {
                    if (bestLength >= limit) break;
                    if (bestLength != 0
                            && input[pos + bestLength] != input[candidate + bestLength]) {
                        candidate = previous[candidate];
                        continue;
                    }
                    int n = matchLength(pos, pos - candidate, limit);
                    if (n > bestLength) {
                        bestLength = n;
                        bestDistance = pos - candidate;
                    }
                    if (n == limit) break;
                    candidate = previous[candidate];
                }
                if (repLength >= 2 && repLength + 1 >= bestLength) {
                    length = repLength;
                    code = repIndex;
                } else if (bestLength >= 3) {
                    length = bestLength;
                    code = bestDistance - 1 + 4;
                } else if (pos >= reps[0] && input[pos] == input[pos - reps[0]]) code = 0;
                emitToken(pos, length, code);
                indexRange(pos + 1, pos + length);
                pos += length;
            }
            position = pos;
            modelValid = true;
            return rc.finish();
        }

        // Prices use 16 units/bit and the observed 128-bin quantization.
        // A 256-byte plan can always be emitted inside the existing 4 KiB
        // packed reserve; no lookahead hash entries survive an uncommitted plan.
        private static final int PLAN = 256, OPT_SIZE = PLAN + 2 * 273 + 2, INF = 1 << 28;
        private static final int[] BIT_PRICES = makeBitPrices();
        final int[] optPrice = new int[OPT_SIZE + 1],
                optState = new int[OPT_SIZE + 1],
                optPrev = new int[OPT_SIZE + 1];
        final int[] optR0 = new int[OPT_SIZE + 1],
                optR1 = new int[OPT_SIZE + 1],
                optR2 = new int[OPT_SIZE + 1],
                optR3 = new int[OPT_SIZE + 1];
        final int[] edgeLengthA = new int[OPT_SIZE + 1],
                edgeCodeA = new int[OPT_SIZE + 1],
                edgeLengthB = new int[OPT_SIZE + 1],
                edgeCodeB = new int[OPT_SIZE + 1],
                edgeLengthC = new int[OPT_SIZE + 1],
                edgeCodeC = new int[OPT_SIZE + 1];
        final int[] planLength = new int[PLAN + 3],
                planCode = new int[PLAN + 3],
                distanceForLength = new int[274],
                repLengths = new int[4];
        final int[] lengthPrices = new int[8 * 272], lengthEpoch = new int[8 * 272];
        final int[] lengthReady = new int[8];
        final int[] distancePrices = new int[1024],
                distanceKeys = new int[1024],
                distanceEpoch = new int[1024];
        int priceEpoch, lastReach;

        static int[] makeBitPrices() {
            int[] prices = new int[128];
            for (int bin = 0; bin < prices.length; bin++) {
                long mantissa = bin * 16L + 8;
                int exponent = 0;
                for (int precision = 0; precision < 4; precision++) {
                    long square = mantissa * mantissa;
                    int shift = Math.max(0, 64 - Long.numberOfLeadingZeros(square) - 16);
                    mantissa = square >>> shift;
                    exponent = (exponent << 1) + shift;
                }
                prices[bin] = 161 - exponent;
            }
            return prices;
        }

        static int bitPrice(int probability, int bit) {
            return BIT_PRICES[(probability ^ (bit == 0 ? 0 : 0x7f0)) >>> 4];
        }

        static int treePrice(int[] probabilities, int base, int bits, int value) {
            int price = 0, node = 1;
            for (int i = bits - 1; i >= 0; i--) {
                int bit = (value >>> i) & 1;
                price += bitPrice(probabilities[base + node], bit);
                node = node * 2 + bit;
            }
            return price;
        }

        static int reversePrice(int[] probabilities, int base, int bits, int value) {
            int price = 0, node = 1;
            for (int i = 0; i < bits; i++) {
                int bit = (value >>> i) & 1;
                price += bitPrice(probabilities[base + node], bit);
                node = node * 2 + bit;
            }
            return price;
        }

        int lengthPrice(boolean repeat, int ps, int length) {
            int key = ((repeat ? 4 : 0) + ps) * 272 + length - 2;
            if (lengthEpoch[key] == priceEpoch) return lengthPrices[key];
            LengthEncoder model = repeat ? repeatLength : normalLength;
            int symbol = length - 2, price;
            if (symbol < 8)
                price = bitPrice(model.choice[0], 0) + treePrice(model.low, ps * 8, 3, symbol);
            else if (symbol < 16)
                price =
                        bitPrice(model.choice[0], 1)
                                + bitPrice(model.choice[1], 0)
                                + treePrice(model.mid, ps * 8, 3, symbol - 8);
            else
                price =
                        bitPrice(model.choice[0], 1)
                                + bitPrice(model.choice[1], 1)
                                + treePrice(model.high, 0, 8, symbol - 16);
            lengthEpoch[key] = priceEpoch;
            return lengthPrices[key] = price;
        }

        int readyLengthPrices(boolean repeat, int ps, int length) {
            int row = (repeat ? 4 : 0) + ps;
            for (int n = Math.max(2, lengthReady[row] + 1); n <= length; n++)
                lengthPrice(repeat, ps, n);
            lengthReady[row] = Math.max(lengthReady[row], length);
            return row * 272 - 2;
        }

        int distancePrice(int length, int distance) {
            int value = distance - 1, kind = Math.min(length - 2, 3), key = (value << 2) | kind;
            int hash = (key * 0x9e3779b1) >>> 22;
            if (distanceEpoch[hash] == priceEpoch && distanceKeys[hash] == key)
                return distancePrices[hash];
            int slot =
                    value < 4
                            ? value
                            : ((31 - Integer.numberOfLeadingZeros(value)) << 1)
                                    + ((value >>> (30 - Integer.numberOfLeadingZeros(value))) & 1);
            int price = treePrice(slots, kind * 64, 6, slot);
            if (slot >= 4) {
                int bits = (slot >>> 1) - 1,
                        base = (2 | (slot & 1)) << bits,
                        reduced = value - base;
                if (slot < 14) price += reversePrice(footer, base - slot - 1, bits, reduced);
                else price += (bits - 4) * 16 + reversePrice(align, 0, 4, reduced & 15);
            }
            distanceKeys[hash] = key;
            distanceEpoch[hash] = priceEpoch;
            return distancePrices[hash] = price;
        }

        static int nextState(int old, int length, int code) {
            return code < 0
                    ? (old < 4 ? 0 : old < 10 ? old - 3 : old - 6)
                    : code >= 4
                            ? (old < 7 ? 7 : 10)
                            : length == 1 ? (old < 7 ? 9 : 11) : (old < 7 ? 8 : 11);
        }

        int literalPrice(int pos, int old, int recent) {
            int context = pos == 0 ? 0 : (input[pos - 1] & 255) >>> 5;
            int value = input[pos] & 255, node = 1, price = 0;
            boolean matched = old >= 7;
            int match = matched ? input[pos - recent] & 255 : 0;
            for (int i = 7; i >= 0; i--) {
                int bit = (value >>> i) & 1, index = node;
                if (matched) {
                    int matchBit = (match >>> i) & 1;
                    index += 0x100 + (matchBit << 8);
                    if (bit != matchBit) matched = false;
                }
                price += bitPrice(literal[context * 0x300 + index], bit);
                node = node * 2 + bit;
            }
            return price;
        }

        int repeatPrefixPrice(int old, int ps, int code, boolean shortRep) {
            int index = old * 16 + ps;
            int price = bitPrice(isMatch[index], 1) + bitPrice(isRep[old], 1);
            if (code == 0)
                return price + bitPrice(g0[old], 0) + bitPrice(repLong[index], shortRep ? 0 : 1);
            price += bitPrice(g0[old], 1) + bitPrice(g1[old], code == 1 ? 0 : 1);
            return code > 1 ? price + bitPrice(g2[old], code == 2 ? 0 : 1) : price;
        }

        int tokenPrice(int pos, int old, int recent, int length, int code) {
            int ps = (positionBase + pos) & positionMask, index = old * 16 + ps;
            if (code < 0) return bitPrice(isMatch[index], 0) + literalPrice(pos, old, recent);
            if (code >= 4)
                return bitPrice(isMatch[index], 1)
                        + bitPrice(isRep[old], 0)
                        + lengthPrice(false, ps, length)
                        + distancePrice(length, code - 3);
            int price = repeatPrefixPrice(old, ps, code, length == 1);
            return length == 1 ? price : price + lengthPrice(true, ps, length);
        }

        int candidates(int pos, int limit, int candidate, int candidate2, int candidate3) {
            // The frontier writes every slot from 4 through its returned maximum.
            // Only the optional short candidates can leave holes; callers never
            // inspect a slot above that maximum.
            distanceForLength[2] = distanceForLength[3] = 0;
            int longest = 1;
            // A conservative default: limit ASCII-prefix chains, while retaining
            // the original effort for non-ASCII byte prefixes. Short UTF-8 seeds
            // carry fewer complete characters and regressed with a universal cap.
            // This is an empirical budget policy, not a text decoder/classifier.
            int budget =
                    balancedMode && limit >= 4 && (((int) INT_LE.get(input, pos) & 0x80808080) == 0)
                            ? 16
                            : depth;
            if (limit >= 2
                    && candidate2 >= 0
                    && pos - candidate2 <= BLOCK_SIZE
                    && input[pos] == input[candidate2]
                    && input[pos + 1] == input[candidate2 + 1]) {
                distanceForLength[2] = pos - candidate2;
                longest = 2;
            }
            if (limit >= 3
                    && candidate3 >= 0
                    && pos - candidate3 <= BLOCK_SIZE
                    && same3(pos, candidate3)) {
                distanceForLength[3] = pos - candidate3;
                longest = 3;
            }
            for (int count = 0;
                    candidate >= 0 && pos - candidate <= BLOCK_SIZE && count < budget;
                    count++, candidate = previous[candidate]) {
                if (longest >= limit) break;
                if (longest >= 2 && input[pos + longest] != input[candidate + longest]) continue;
                int length = matchLength(pos, pos - candidate, limit);
                if (length > longest) {
                    for (int n = Math.max(2, longest + 1); n <= length; n++)
                        distanceForLength[n] = pos - candidate;
                    longest = length;
                }
            }
            return longest;
        }

        int recent(int node, int index) {
            return switch (index) {
                case 0 -> optR0[node];
                case 1 -> optR1[node];
                case 2 -> optR2[node];
                default -> optR3[node];
            };
        }

        void relax(
                int from,
                int to,
                int price,
                int lengthA,
                int codeA,
                int lengthB,
                int codeB,
                int lengthC,
                int codeC) {
            if (price >= optPrice[to]) return;
            if (to > lastReach) lastReach = to;
            optPrice[to] = price;
            optPrev[to] = from;
            edgeLengthA[to] = lengthA;
            edgeCodeA[to] = codeA;
            edgeLengthB[to] = lengthB;
            edgeCodeB[to] = codeB;
            edgeLengthC[to] = lengthC;
            edgeCodeC[to] = codeC;
            int st = optState[from],
                    r0 = optR0[from],
                    r1 = optR1[from],
                    r2 = optR2[from],
                    r3 = optR3[from];
            for (int token = 0; token < 3; token++) {
                int length = token == 0 ? lengthA : token == 1 ? lengthB : lengthC;
                if (length == 0) break;
                int code = token == 0 ? codeA : token == 1 ? codeB : codeC;
                st = nextState(st, length, code);
                if (code >= 4) {
                    r3 = r2;
                    r2 = r1;
                    r1 = r0;
                    r0 = code - 3;
                } else if (code > 0) {
                    int chosen = code == 1 ? r1 : code == 2 ? r2 : r3;
                    if (code >= 3) r3 = r2;
                    if (code >= 2) r2 = r1;
                    r1 = r0;
                    r0 = chosen;
                }
            }
            optState[to] = st;
            optR0[to] = r0;
            optR1[to] = r1;
            optR2[to] = r2;
            optR3[to] = r3;
        }

        void composite(
                int cur, int pos, int horizon, int firstLength, int firstCode, int firstPrice) {
            int middle = cur + firstLength, tail = middle + (firstCode < 0 ? 0 : 1);
            if (tail + 2 > horizon) return;
            int old = optState[cur], initialRep = optR0[cur], r0 = initialRep;
            int afterFirst = nextState(old, firstLength, firstCode), st = afterFirst;
            if (firstCode >= 4) r0 = firstCode - 3;
            else if (firstCode >= 0) r0 = recent(cur, firstCode);
            if (firstCode >= 0) st = nextState(st, 1, -1);
            int tailPos = pos + tail - cur,
                    length = matchLength(tailPos, r0, Math.min(273, horizon - tail));
            if (length < 2) return;
            // Most hypothetical compound tails are not matches. Check their
            // bytes before walking either literal probability tree; this does
            // not change any candidate, price, model update or tie-break.
            int price = optPrice[cur] + firstPrice;
            if (firstCode >= 0) price += tokenPrice(pos + firstLength, afterFirst, r0, 1, -1);
            price += tokenPrice(tailPos, st, r0, length, 0);
            if (firstCode < 0)
                relax(cur, tail + length, price, firstLength, firstCode, length, 0, 0, 0);
            else relax(cur, tail + length, price, firstLength, firstCode, 1, -1, length, 0);
        }

        int planAndEmit(int start, int end, int firstCandidate, int firstTwo, int firstThree)
                throws InterruptedIOException {
            int work = Math.min(PLAN, end - start),
                    horizon = Math.min(OPT_SIZE, end - start),
                    firstLimit = Math.min(273, end - start);
            int bestRep = 0, bestRepLength = 0;
            for (int i = 0; i < 4; i++) {
                int n = matchLength(start, reps[i], firstLimit);
                repLengths[i] = n;
                if (n > bestRepLength) {
                    bestRep = i;
                    bestRepLength = n;
                }
            }
            if (bestRepLength >= niceLength) {
                emitToken(start, bestRepLength, bestRep);
                indexRange(start + 1, start + bestRepLength);
                return bestRepLength;
            }
            int longest = candidates(start, firstLimit, firstCandidate, firstTwo, firstThree);
            if (longest >= niceLength) {
                int code = distanceForLength[longest] + 3;
                emitToken(start, longest, code);
                indexRange(start + 1, start + longest);
                return longest;
            }
            if (++priceEpoch == 0) {
                Arrays.fill(lengthEpoch, 0);
                Arrays.fill(distanceEpoch, 0);
                priceEpoch = 1;
            }
            Arrays.fill(lengthReady, 1);
            Arrays.fill(optPrice, 0, horizon + 1, INF);
            optPrice[0] = 0;
            optState[0] = state;
            optR0[0] = reps[0];
            optR1[0] = reps[1];
            optR2[0] = reps[2];
            optR3[0] = reps[3];
            int endpoint = work, niceCode = -1, niceRun = 0;
            lastReach = 0;
            for (int cur = 0; cur < work; cur++) {
                if ((cur & 63) == 0) checkInterrupted();
                int pos = start + cur, limit = Math.min(273, end - pos);
                if (cur != 0) {
                    int candidate = insert(pos);
                    bestRep = 0;
                    bestRepLength = 0;
                    for (int i = 0; i < 4; i++) {
                        int n = matchLength(pos, recent(cur, i), limit);
                        repLengths[i] = n;
                        if (n > bestRepLength) {
                            bestRep = i;
                            bestRepLength = n;
                        }
                    }
                    if (bestRepLength >= niceLength) {
                        endpoint = cur;
                        niceRun = bestRepLength;
                        niceCode = bestRep;
                        break;
                    }
                    longest = candidates(pos, limit, candidate, twoCandidate, threeCandidate);
                    if (longest >= niceLength) {
                        endpoint = cur;
                        niceRun = longest;
                        niceCode = distanceForLength[longest] + 3;
                        break;
                    }
                }
                int old = optState[cur],
                        r0 = optR0[cur],
                        base = optPrice[cur],
                        ps = (positionBase + pos) & positionMask;
                int literalCost = tokenPrice(pos, old, r0, 1, -1);
                relax(cur, cur + 1, base + literalCost, 1, -1, 0, 0, 0, 0);
                if (pos >= r0 && input[pos] == input[pos - r0])
                    relax(cur, cur + 1, base + tokenPrice(pos, old, r0, 1, 0), 1, 0, 0, 0, 0, 0);
                for (int rep = 0; rep < 4; rep++) {
                    int length = Math.min(repLengths[rep], horizon - cur);
                    if (length >= 2) {
                        int prefix = base + repeatPrefixPrice(old, ps, rep, false);
                        int lengthBase = readyLengthPrices(true, ps, length);
                        for (int n = 2; n <= length; n++)
                            relax(
                                    cur,
                                    cur + n,
                                    prefix + lengthPrices[lengthBase + n],
                                    n,
                                    rep,
                                    0,
                                    0,
                                    0,
                                    0);
                        composite(
                                cur,
                                pos,
                                horizon,
                                length,
                                rep,
                                prefix - base + lengthPrices[lengthBase + length]);
                    }
                }
                int matchEnd = Math.min(longest, horizon - cur);
                int lastMatchPrice = 0;
                if (matchEnd >= 2) {
                    int matchPrefix =
                            base + bitPrice(isMatch[old * 16 + ps], 1) + bitPrice(isRep[old], 0);
                    int lengthBase = readyLengthPrices(false, ps, matchEnd),
                            lastCode = -1,
                            lastKind = -1,
                            distCost = 0;
                    for (int length = 2; length <= matchEnd; length++)
                        if (distanceForLength[length] != 0) {
                            int code = distanceForLength[length] + 3;
                            int kind = Math.min(length - 2, 3);
                            if (code != lastCode || kind != lastKind) {
                                distCost = distancePrice(length, code - 3);
                                lastCode = code;
                                lastKind = kind;
                            }
                            int price = matchPrefix + lengthPrices[lengthBase + length] + distCost;
                            relax(cur, cur + length, price, length, code, 0, 0, 0, 0);
                            if (length == matchEnd) lastMatchPrice = price - base;
                        }
                }
                composite(cur, pos, horizon, 1, -1, literalCost);
                if (matchEnd >= 2 && distanceForLength[matchEnd] != 0)
                    composite(
                            cur,
                            pos,
                            horizon,
                            matchEnd,
                            distanceForLength[matchEnd] + 3,
                            lastMatchPrice);
            }
            // Match/compound tails may end beyond the processed-node budget.
            // Choose a natural cheap endpoint instead of splitting at byte256.
            if (niceRun == 0)
                for (int node = work + 1; node <= lastReach; node++)
                    if (optPrice[node] <= optPrice[endpoint]) endpoint = node;
            int count = 0;
            for (int node = endpoint; node != 0; node = optPrev[node]) {
                if (edgeLengthC[node] != 0) {
                    planLength[count] = edgeLengthC[node];
                    planCode[count++] = edgeCodeC[node];
                }
                if (edgeLengthB[node] != 0) {
                    planLength[count] = edgeLengthB[node];
                    planCode[count++] = edgeCodeB[node];
                }
                planLength[count] = edgeLengthA[node];
                planCode[count++] = edgeCodeA[node];
            }
            int pos = start;
            for (int i = count - 1; i >= 0; i--) {
                emitToken(pos, planLength[i], planCode[i]);
                pos += planLength[i];
            }
            if (niceRun != 0) {
                emitToken(pos, niceRun, niceCode);
                indexRange(pos + 1, pos + niceRun);
                pos += niceRun;
            } else if (endpoint > work) indexRange(start + work, pos);
            return pos - start;
        }

        void emitToken(int pos, int length, int code) {
            int ps = (positionBase + pos) & positionMask;
            rc.bit(isMatch, state * 16 + ps, code == -1 ? 0 : 1);
            if (code == -1) {
                emitLiteral(pos);
                state = state < 4 ? 0 : state < 10 ? state - 3 : state - 6;
            } else if (code < 4) {
                rc.bit(isRep, state, 1);
                if (code == 0) {
                    rc.bit(g0, state, 0);
                    rc.bit(repLong, state * 16 + ps, length == 1 ? 0 : 1);
                } else {
                    rc.bit(g0, state, 1);
                    rc.bit(g1, state, code == 1 ? 0 : 1);
                    if (code != 1) rc.bit(g2, state, code == 2 ? 0 : 1);
                    int distance = reps[code];
                    for (int i = code; i > 0; --i) reps[i] = reps[i - 1];
                    reps[0] = distance;
                }
                if (length == 1) state = state < 7 ? 9 : 11;
                else {
                    repeatLength.encode(rc, length - 2, ps);
                    state = state < 7 ? 8 : 11;
                }
            } else {
                rc.bit(isRep, state, 0);
                normalLength.encode(rc, length - 2, ps);
                int distanceMinusOne = code - 4;
                int slot =
                        distanceMinusOne < 4
                                ? distanceMinusOne
                                : ((31 - Integer.numberOfLeadingZeros(distanceMinusOne)) << 1)
                                        + ((distanceMinusOne
                                                        >>> (30
                                                                - Integer.numberOfLeadingZeros(
                                                                        distanceMinusOne)))
                                                & 1);
                rc.tree(slots, Math.min(length - 2, 3) * 64, 6, slot);
                if (slot >= 4) {
                    int bits = (slot >>> 1) - 1, base = (2 | (slot & 1)) << bits;
                    int reduced = distanceMinusOne - base;
                    if (slot < 14) rc.reverseTree(footer, base - slot - 1, bits, reduced);
                    else {
                        rc.direct(reduced >>> 4, bits - 4);
                        rc.reverseTree(align, 0, 4, reduced & 15);
                    }
                }
                reps[3] = reps[2];
                reps[2] = reps[1];
                reps[1] = reps[0];
                reps[0] = distanceMinusOne + 1;
                state = state < 7 ? 7 : 10;
            }
        }

        void emitLiteral(int pos) {
            int context = pos == 0 ? 0 : (input[pos - 1] & 255) >>> 5;
            int value = input[pos] & 255, symbol = 1;
            boolean matched = state >= 7;
            int matchByte = matched ? input[pos - reps[0]] & 255 : 0;
            for (int i = 7; i >= 0; --i) {
                int bit = (value >>> i) & 1, index = symbol;
                if (matched) {
                    int matchBit = (matchByte >>> i) & 1;
                    index += 0x100 + (matchBit << 8);
                    if (bit != matchBit) matched = false;
                }
                rc.bit(literal, context * 0x300 + index, bit);
                symbol = (symbol << 1) | bit;
            }
        }
    }
}
