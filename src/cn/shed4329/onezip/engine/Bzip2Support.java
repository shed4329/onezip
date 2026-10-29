package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.*;

/** Standard BZIP2 MSB-first bits, non-reflected CRC32 and cyclic BWT; no codec delegation. */
final class Bzip2Support {
    static final long BLOCK = 0x314159265359L, END = 0x177245385090L;

    static final class Crc {
        static final int[] TABLE = new int[256];

        static {
            for (int i = 0; i < 256; i++) {
                int v = i << 24;
                for (int j = 0; j < 8; j++) v = (v << 1) ^ ((v & 0x80000000) == 0 ? 0 : 0x04c11db7);
                TABLE[i] = v;
            }
        }

        int value = -1;

        void update(int b) {
            value = (value << 8) ^ TABLE[((value >>> 24) ^ b) & 255];
        }

        int finish() {
            return ~value;
        }
    }

    static final class BitsOut {
        final OutputStream out;
        long bits;
        int count;

        BitsOut(OutputStream out) {
            this.out = out;
        }

        void write(int n, long value) throws IOException {
            bits = (bits << n) | (value & ((1L << n) - 1));
            count += n;
            while (count >= 8) {
                count -= 8;
                out.write((int) (bits >>> count) & 255);
            }
            bits &= (1L << count) - 1;
        }

        void magic(long v) throws IOException {
            write(24, v >>> 24);
            write(24, v);
        }

        void align() throws IOException {
            if (count > 0) write(8 - count, 0);
        }
    }

    static final class BitsIn {
        final InputStream in;
        long bits;
        int count;

        BitsIn(InputStream in) {
            this.in = in;
        }

        int read(int n) throws IOException {
            while (count < n) {
                bits = (bits << 8) | ReadSupport.u8(in);
                count += 8;
            }
            count -= n;
            int v = (int) ((bits >>> count) & ((1L << n) - 1));
            bits &= (1L << count) - 1;
            return v;
        }

        long magic() throws IOException {
            return ((long) read(24) << 24) | read(24);
        }

        void align() throws IOException {
            if (bits != 0) throw new IOException("BZIP2 nonzero trailing padding");
            bits = 0;
            count = 0;
        }
    }

    record Bwt(byte[] last, int original) {}

    static Bwt transform(byte[] b, int n) throws IOException {
        int[] order = new int[n],
                rank = new int[n],
                shifted = new int[n],
                next = new int[n],
                counts = new int[Math.max(n, 256)];
        for (int i = 0; i < n; i++) counts[b[i] & 255]++;
        for (int i = 1; i < 256; i++) counts[i] += counts[i - 1];
        for (int i = n - 1; i >= 0; i--) order[--counts[b[i] & 255]] = i;
        int classes = 1;
        rank[order[0]] = 0;
        for (int i = 1; i < n; i++) {
            if (b[order[i]] != b[order[i - 1]]) classes++;
            rank[order[i]] = classes - 1;
        }
        for (int width = 1; width < n && classes < n; width *= 2) {
            ReadSupport.check(null);
            for (int i = 0; i < n; i++) {
                int p = order[i] - width;
                shifted[i] = p < 0 ? p + n : p;
            }
            Arrays.fill(counts, 0, classes, 0);
            for (int p : shifted) counts[rank[p]]++;
            for (int i = 1; i < classes; i++) counts[i] += counts[i - 1];
            for (int i = n - 1; i >= 0; i--) {
                int p = shifted[i];
                order[--counts[rank[p]]] = p;
            }
            int fresh = 1;
            next[order[0]] = 0;
            for (int i = 1; i < n; i++) {
                int p = order[i], q = order[i - 1], p2 = p + width, q2 = q + width;
                if (p2 >= n) p2 -= n;
                if (q2 >= n) q2 -= n;
                if (rank[p] != rank[q] || rank[p2] != rank[q2]) fresh++;
                next[p] = fresh - 1;
            }
            int[] tmp = rank;
            rank = next;
            next = tmp;
            classes = fresh;
        }
        byte[] last = new byte[n];
        int original = 0;
        for (int i = 0; i < n; i++) {
            int p = order[i];
            if (p == 0) original = i;
            last[i] = b[p == 0 ? n - 1 : p - 1];
        }
        return new Bwt(last, original);
    }

    static int[] codes(int[] lengths) {
        int[] count = new int[21], next = new int[21], codes = new int[lengths.length];
        for (int n : lengths) count[n]++;
        int code = 0;
        for (int n = 1; n <= 20; n++) {
            code = (code + count[n - 1]) << 1;
            next[n] = code;
        }
        for (int i = 0; i < codes.length; i++) codes[i] = next[lengths[i]]++;
        return codes;
    }

    static final class Tree {
        final int[] count = new int[21], first = new int[21], index = new int[21], symbols;
        final int max;

        Tree(int[] lengths) throws IOException {
            int largest = 0;
            for (int n : lengths) {
                if (n < 1 || n > 20) throw new IOException("BZIP2 Huffman length invalid");
                count[n]++;
                largest = Math.max(largest, n);
            }
            max = largest;
            int left = 1, code = 0, offset = 0;
            for (int n = 1; n <= 20; n++) {
                left = (left << 1) - count[n];
                if (left < 0) throw new IOException("BZIP2 oversubscribed Huffman tree");
                code = (code + count[n - 1]) << 1;
                first[n] = code;
                index[n] = offset;
                offset += count[n];
            }
            symbols = new int[lengths.length];
            int[] cursor = index.clone();
            for (int i = 0; i < lengths.length; i++) symbols[cursor[lengths[i]]++] = i;
        }

        int read(BitsIn in) throws IOException {
            int code = 0;
            for (int n = 1; n <= max; n++) {
                code = (code << 1) | in.read(1);
                int p = code - first[n];
                if (p >= 0 && p < count[n]) return symbols[index[n] + p];
            }
            throw new IOException("BZIP2 invalid Huffman code");
        }
    }
}
