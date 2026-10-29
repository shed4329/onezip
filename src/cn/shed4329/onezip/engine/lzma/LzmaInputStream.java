package cn.shed4329.onezip.engine.lzma;

import java.io.*;
import java.util.Arrays;
import java.util.Objects;

/** Independent LZMA1/LZMA2 decoder: adaptive range coding and a circular LZ dictionary. */
public final class LzmaInputStream extends InputStream {
    public static final int MAX_DICTIONARY = 256 * 1024 * 1024;
    private final InputStream in;
    private final Dictionary dictionary;
    private final boolean lzma2;
    private Core core;
    private Range range;
    private byte[] chunkBytes;
    private final byte[] single = new byte[1];
    private long remaining;
    private boolean raw, ended, needDictionary = true, needProperties = true;

    /** LZMA2 dictionary property byte, as stored in a 7z coder. */
    public LzmaInputStream(InputStream in, int property) throws IOException {
        if (property < 0 || property > 40) throw new IOException("無效的 LZMA2 字典屬性");
        long size =
                property == 40
                        ? 0xffffffffL
                        : (long) (2 | (property & 1)) << ((property >>> 1) + 11);
        this.in = in;
        dictionary = new Dictionary(size);
        lzma2 = true;
    }

    /** Raw LZMA1, with the five-byte coder properties supplied by the container. */
    public LzmaInputStream(InputStream in, byte[] properties, long unpackSize) throws IOException {
        if (properties.length != 5 || unpackSize < 0) throw new IOException("無效的 LZMA 屬性");
        long size = 0;
        for (int i = 1; i < 5; i++) size |= (long) (properties[i] & 255) << ((i - 1) * 8);
        this.in = in;
        dictionary = new Dictionary(size);
        lzma2 = false;
        core = new Core(properties[0] & 255, false);
        range = new Range(in);
        remaining = unpackSize;
    }

    private static int byteValue(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException("LZMA 資料截斷");
        return b;
    }

    private void nextChunk() throws IOException {
        int control = byteValue(in);
        if (control == 0) {
            ended = true;
            return;
        }
        if (control < 0x80) {
            if (control != 1 && control != 2) throw new IOException("無效的 LZMA2 控制位元組");
            if (control == 1) {
                dictionary.reset();
                needDictionary = false;
                needProperties = true;
            } else if (needDictionary) throw new IOException("LZMA2 缺少字典重設");
            // Raw control 2 preserves the LZMA probability model; only the
            // dictionary/position advances. A following control 0x80 is valid.
            remaining = ((byteValue(in) << 8) | byteValue(in)) + 1;
            raw = true;
            return;
        }
        if (control >= 0xe0) {
            dictionary.reset();
            needDictionary = false;
        } else if (needDictionary) throw new IOException("LZMA2 缺少字典重設");
        remaining = (((control & 31) << 16) | (byteValue(in) << 8) | byteValue(in)) + 1;
        int packed = ((byteValue(in) << 8) | byteValue(in)) + 1;
        if (control >= 0xc0) {
            core = new Core(byteValue(in), true);
            needProperties = false;
        } else {
            if (needProperties) throw new IOException("LZMA2 缺少編碼屬性");
            if (control >= 0xa0) core.reset();
        }
        if (chunkBytes == null) chunkBytes = new byte[65536];
        if (in.readNBytes(chunkBytes, 0, packed) != packed) throw new EOFException("LZMA2 區塊截斷");
        range = new Range(chunkBytes, packed);
        raw = false;
    }

    private void finishRange(boolean allowEndMarker) throws IOException {
        if (core.pending != 0) throw new IOException("LZMA 匹配長度超出區塊");
        if (range.code != 0) {
            if (!allowEndMarker || core.next(range, dictionary) != -1 || range.code != 0)
                throw new IOException("LZMA 結束狀態不符");
        }
        if (range.hasExtra()) throw new IOException("LZMA 串流含多餘資料");
    }

    @Override
    public int read() throws IOException {
        return read(single, 0, 1) < 0 ? -1 : single[0] & 255;
    }

    @Override
    public int read(byte[] b, int o, int n) throws IOException {
        Objects.checkFromIndexSize(o, n, b.length);
        if (n == 0) return 0;
        int done = 0, nextCheck = 0;
        while (done < n) {
            if (done >= nextCheck) {
                if (Thread.currentThread().isInterrupted())
                    throw new InterruptedIOException("操作已取消");
                nextCheck = done + 4096;
            }
            if (ended) break;
            if (remaining == 0) {
                if (!lzma2) {
                    finishRange(true);
                    ended = true;
                    break;
                }
                if (range != null && !raw) finishRange(false);
                nextChunk();
                if (ended) break;
            }
            int count = (int) Math.min(Math.min(n - done, remaining), 65536);
            if (raw) {
                int k = in.read(b, o + done, count);
                if (k < 0) throw new EOFException("LZMA2 raw 區塊截斷");
                dictionary.append(b, o + done, k);
                remaining -= k;
                done += k;
            } else if (core.pending > 0) {
                count = Math.min(count, core.pending);
                dictionary.repeat(core.reps[0], b, o + done, count);
                core.pending -= count;
                remaining -= count;
                done += count;
            } else {
                int value = core.next(range, dictionary);
                if (value < 0) throw new IOException("LZMA 過早結束");
                b[o + done++] = (byte) value;
                remaining--;
            }
        }
        return done == 0 ? -1 : done;
    }

    private static int[] probs(int size) {
        int[] p = new int[size];
        Arrays.fill(p, 1024);
        return p;
    }

    private static final class Dictionary {
        final byte[] bytes;
        int cursor, full;
        long position;

        Dictionary(long size) throws IOException {
            if (size > MAX_DICTIONARY) throw new IOException("LZMA 字典超過 256 MiB 上限");
            bytes = new byte[(int) Math.max(4096, size)];
        }

        void reset() {
            cursor = full = 0;
            position = 0;
        }

        void put(int value) {
            bytes[cursor++] = (byte) value;
            if (cursor == bytes.length) cursor = 0;
            if (full < bytes.length) full++;
            position++;
        }

        void append(byte[] source, int offset, int length) {
            int left = length;
            while (left > 0) {
                int n = Math.min(left, bytes.length - cursor);
                System.arraycopy(source, offset, bytes, cursor, n);
                offset += n;
                cursor += n;
                if (cursor == bytes.length) cursor = 0;
                left -= n;
            }
            full = Math.min(bytes.length, full + length);
            position += length;
        }

        void repeat(long distance, byte[] output, int offset, int length) throws IOException {
            if (distance < 1 || distance > full) throw new IOException("LZMA 距離超出字典");
            int start = cursor - (int) distance;
            if (start < 0) start += bytes.length;
            if (distance == 1) Arrays.fill(output, offset, offset + length, bytes[start]);
            else {
                int seed = Math.min((int) distance, length),
                        first = Math.min(seed, bytes.length - start);
                System.arraycopy(bytes, start, output, offset, first);
                if (first < seed) System.arraycopy(bytes, 0, output, offset + first, seed - first);
                for (int copied = seed; copied < length; ) {
                    int n = Math.min(copied, length - copied);
                    System.arraycopy(output, offset, output, offset + copied, n);
                    copied += n;
                }
            }
            append(output, offset, length);
        }

        int previous() {
            return full == 0 ? 0 : bytes[cursor == 0 ? bytes.length - 1 : cursor - 1] & 255;
        }

        int get(long distance) throws IOException {
            if (distance < 1 || distance > full) throw new IOException("LZMA 距離超出字典");
            int index = cursor - (int) distance;
            if (index < 0) index += bytes.length;
            return bytes[index] & 255;
        }
    }

    private static final class Range {
        final InputStream in;
        final byte[] bytes;
        int cursor, limit, range = -1, code;

        Range(InputStream in) throws IOException {
            this.in = in;
            bytes = new byte[8192];
            init();
        }

        Range(byte[] bytes, int length) throws IOException {
            in = null;
            this.bytes = bytes;
            limit = length;
            init();
        }

        int nextByte() throws IOException {
            if (cursor == limit) {
                if (in == null) throw new EOFException("LZMA range 資料截斷");
                limit = in.read(bytes);
                cursor = 0;
                if (limit < 0) throw new EOFException("LZMA range 資料截斷");
            }
            return bytes[cursor++] & 255;
        }

        void init() throws IOException {
            if (nextByte() != 0) throw new IOException("無效的 LZMA range header");
            for (int i = 0; i < 4; i++) code = (code << 8) | nextByte();
            if (code == -1) throw new IOException("無效的 LZMA range code");
        }

        boolean hasExtra() throws IOException {
            return cursor < limit || (in != null && in.read() != -1);
        }

        void normalize() throws IOException {
            if ((range & 0xff000000) == 0) {
                range <<= 8;
                code = (code << 8) | nextByte();
            }
        }

        int bit(int[] p, int index) throws IOException {
            int probability = p[index], bound = (range >>> 11) * probability, bit;
            // XOR maps unsigned order onto signed order, retaining exact 32-bit overflow.
            if ((code ^ Integer.MIN_VALUE) < (bound ^ Integer.MIN_VALUE)) {
                range = bound;
                p[index] = probability + ((2048 - probability) >>> 5);
                bit = 0;
            } else {
                range -= bound;
                code -= bound;
                p[index] = probability - (probability >>> 5);
                bit = 1;
            }
            if ((range & 0xff000000) == 0) {
                range <<= 8;
                code = (code << 8) | nextByte();
            }
            return bit;
        }

        /** Keep arithmetic state local through one literal instead of spilling it per bit. */
        int matchedLiteral(int[] probabilities, int base, int match) throws IOException {
            // Eight bits consume at most eight input bytes. At a chunk/refill
            // boundary nextByte() retains the exact truncation checks.
            int value = 1, r = range, c = code, input = cursor;
            boolean buffered = limit - input >= 8;
            do {
                int mb = (match >>> 7) & 1;
                match = (match << 1) & 255;
                int index = base + ((1 + mb) << 8) + value,
                        probability = probabilities[index],
                        bound = (r >>> 11) * probability,
                        bit;
                if ((c ^ Integer.MIN_VALUE) < (bound ^ Integer.MIN_VALUE)) {
                    r = bound;
                    probabilities[index] = probability + ((2048 - probability) >>> 5);
                    bit = 0;
                } else {
                    r -= bound;
                    c -= bound;
                    probabilities[index] = probability - (probability >>> 5);
                    bit = 1;
                }
                if ((r & 0xff000000) == 0) {
                    r <<= 8;
                    c = (c << 8) | (buffered ? (bytes[input++] & 255) : nextByte());
                }
                value = (value << 1) | bit;
                if (mb != bit) break;
            } while (value < 256);
            while (value < 256) {
                int index = base + value,
                        probability = probabilities[index],
                        bound = (r >>> 11) * probability;
                if ((c ^ Integer.MIN_VALUE) < (bound ^ Integer.MIN_VALUE)) {
                    r = bound;
                    probabilities[index] = probability + ((2048 - probability) >>> 5);
                    value <<= 1;
                } else {
                    r -= bound;
                    c -= bound;
                    probabilities[index] = probability - (probability >>> 5);
                    value = (value << 1) | 1;
                }
                if ((r & 0xff000000) == 0) {
                    r <<= 8;
                    c = (c << 8) | (buffered ? (bytes[input++] & 255) : nextByte());
                }
            }
            if (buffered) cursor = input;
            range = r;
            code = c;
            return value - 256;
        }

        int literal(int[] probabilities, int base) throws IOException {
            // Unmatched literals benefit from removing data-dependent bit branches;
            // matched literals above retain the cheaper predictable branch path.
            int value = 1, r = range, c = code, input = cursor;
            boolean buffered = limit - input >= 8;
            while (value < 256) {
                int index = base + value,
                        probability = probabilities[index],
                        bound = (r >>> 11) * probability;
                int
                        mask =
                                (int)
                                        ((Integer.toUnsignedLong(c) - Integer.toUnsignedLong(bound))
                                                >> 63),
                        high = ~mask;
                r = (bound & mask) | ((r - bound) & high);
                c -= bound & high;
                probabilities[index] =
                        probability
                                + (((2048 - probability) >>> 5) & mask)
                                - ((probability >>> 5) & high);
                value = (value << 1) | (mask + 1);
                if ((r & 0xff000000) == 0) {
                    r <<= 8;
                    c = (c << 8) | (buffered ? (bytes[input++] & 255) : nextByte());
                }
            }
            if (buffered) cursor = input;
            range = r;
            code = c;
            return value - 256;
        }

        int tree(int[] p, int start, int bits) throws IOException {
            int node = 1;
            for (int i = 0; i < bits; i++) node = (node << 1) | bit(p, start + node);
            return node - (1 << bits);
        }

        int reverse(int[] p, int start, int bits) throws IOException {
            int node = 1, value = 0;
            for (int i = 0; i < bits; i++) {
                int b = bit(p, start + node);
                node = (node << 1) | b;
                value |= b << i;
            }
            return value;
        }

        long direct(int bits) throws IOException {
            long value = 0;
            for (int i = 0; i < bits; i++) {
                range >>>= 1;
                int b = 0;
                if ((code ^ Integer.MIN_VALUE) >= (range ^ Integer.MIN_VALUE)) {
                    code -= range;
                    b = 1;
                }
                value = (value << 1) | b;
                normalize();
            }
            return value;
        }
    }

    private static final class Length {
        final int[] choice = probs(2), low = probs(16 * 8), mid = probs(16 * 8), high = probs(256);

        void reset() {
            Arrays.fill(choice, 1024);
            Arrays.fill(low, 1024);
            Arrays.fill(mid, 1024);
            Arrays.fill(high, 1024);
        }

        int decode(Range r, int ps) throws IOException {
            if (r.bit(choice, 0) == 0) return r.tree(low, ps * 8, 3) + 2;
            if (r.bit(choice, 1) == 0) return r.tree(mid, ps * 8, 3) + 10;
            return r.tree(high, 0, 8) + 18;
        }
    }

    private static final class Core {
        final int lc, lp, pb;
        final int[] isMatch = probs(12 * 16),
                isRep = probs(12),
                g0 = probs(12),
                g1 = probs(12),
                g2 = probs(12),
                repLong = probs(12 * 16);
        final int[] literal, slots = probs(4 * 64), footer = probs(114), align = probs(16);
        final long[] reps = {1, 1, 1, 1};
        final Length length = new Length(), repLength = new Length();
        int state, pending;

        Core(int property, boolean lzma2) throws IOException {
            if (property >= 225) throw new IOException("無效的 LZMA lc/lp/pb");
            lc = property % 9;
            int rest = property / 9;
            lp = rest % 5;
            pb = rest / 5;
            if (lc + lp > (lzma2 ? 4 : 12)) throw new IOException("LZMA literal 模型超出上限");
            literal = probs(0x300 << (lc + lp));
        }

        void reset() {
            for (int[] p :
                    new int[][] {
                        isMatch, isRep, g0, g1, g2, repLong, literal, slots, footer, align
                    }) Arrays.fill(p, 1024);
            Arrays.fill(reps, 1);
            length.reset();
            repLength.reset();
            state = pending = 0;
        }

        int next(Range r, Dictionary d) throws IOException {
            if (pending != 0) {
                --pending;
                int b = d.get(reps[0]);
                d.put(b);
                return b;
            }
            int ps = (int) d.position & ((1 << pb) - 1);
            if (r.bit(isMatch, state * 16 + ps) == 0) {
                int context =
                        (((int) d.position & ((1 << lp) - 1)) << lc) + (d.previous() >>> (8 - lc));
                int b =
                        state >= 7
                                ? r.matchedLiteral(literal, context * 0x300, d.get(reps[0]))
                                : r.literal(literal, context * 0x300);
                state = state < 4 ? 0 : state < 10 ? state - 3 : state - 6;
                d.put(b);
                return b;
            }
            if (r.bit(isRep, state) == 1) {
                if (r.bit(g0, state) == 0) {
                    if (r.bit(repLong, state * 16 + ps) == 0) {
                        state = state < 7 ? 9 : 11;
                        int b = d.get(reps[0]);
                        d.put(b);
                        return b;
                    }
                } else {
                    int index;
                    if (r.bit(g1, state) == 0) index = 1;
                    else index = r.bit(g2, state) == 0 ? 2 : 3;
                    long distance = reps[index];
                    for (int i = index; i > 0; i--) reps[i] = reps[i - 1];
                    reps[0] = distance;
                }
                pending = repLength.decode(r, ps);
                state = state < 7 ? 8 : 11;
            } else {
                pending = length.decode(r, ps);
                int slot = r.tree(slots, Math.min(pending - 2, 3) * 64, 6);
                long distance = slot;
                if (slot >= 4) {
                    int bits = (slot >>> 1) - 1;
                    distance = (long) (2 | (slot & 1)) << bits;
                    if (slot < 14) distance += r.reverse(footer, (int) distance - slot - 1, bits);
                    else distance += (r.direct(bits - 4) << 4) + r.reverse(align, 0, 4);
                }
                if (distance == 0xffffffffL) {
                    pending = 0;
                    return -1;
                }
                reps[3] = reps[2];
                reps[2] = reps[1];
                reps[1] = reps[0];
                reps[0] = distance + 1;
                state = state < 7 ? 7 : 10;
            }
            --pending;
            int b = d.get(reps[0]);
            d.put(b);
            return b;
        }
    }
}
