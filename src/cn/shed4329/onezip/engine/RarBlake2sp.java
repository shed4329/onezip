package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.*;

/** Unkeyed BLAKE2sp-256 for the RAR5 hash extra field (RFC 7693 core). */
final class RarBlake2sp {
    private static final int[] IV = {
        0x6a09e667,
        0xbb67ae85,
        0x3c6ef372,
        0xa54ff53a,
        0x510e527f,
        0x9b05688c,
        0x1f83d9ab,
        0x5be0cd19
    };
    private static final int[][] SIGMA = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
        {14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3},
        {11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4},
        {7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8},
        {9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13},
        {2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9},
        {12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11},
        {13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10},
        {6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5},
        {10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0}
    };

    static final class Node {
        final int[] h = IV.clone();
        final byte[] buffer = new byte[64];
        final boolean last;
        int used;
        long count;

        Node(int offset, boolean root, boolean last) {
            this.last = last;
            h[0] ^= 0x02080020;
            h[2] ^= offset;
            h[3] ^= (root ? 1 : 0) << 16 | 32 << 24;
        }

        void update(byte[] b, int o, int n) {
            while (n > 0) {
                if (used == 64) {
                    count += 64;
                    compress(false);
                    used = 0;
                }
                int k = Math.min(n, 64 - used);
                System.arraycopy(b, o, buffer, used, k);
                used += k;
                o += k;
                n -= k;
            }
        }

        void compress(boolean finish) {
            int[] m = new int[16], v = new int[16];
            for (int i = 0; i < 16; i++) {
                int p = i * 4;
                m[i] =
                        (buffer[p] & 255)
                                | ((buffer[p + 1] & 255) << 8)
                                | ((buffer[p + 2] & 255) << 16)
                                | (buffer[p + 3] << 24);
            }
            System.arraycopy(h, 0, v, 0, 8);
            System.arraycopy(IV, 0, v, 8, 8);
            v[12] ^= (int) count;
            v[13] ^= (int) (count >>> 32);
            if (finish) {
                v[14] = ~v[14];
                if (last) v[15] = ~v[15];
            }
            for (int[] s : SIGMA) {
                g(v, 0, 4, 8, 12, m[s[0]], m[s[1]]);
                g(v, 1, 5, 9, 13, m[s[2]], m[s[3]]);
                g(v, 2, 6, 10, 14, m[s[4]], m[s[5]]);
                g(v, 3, 7, 11, 15, m[s[6]], m[s[7]]);
                g(v, 0, 5, 10, 15, m[s[8]], m[s[9]]);
                g(v, 1, 6, 11, 12, m[s[10]], m[s[11]]);
                g(v, 2, 7, 8, 13, m[s[12]], m[s[13]]);
                g(v, 3, 4, 9, 14, m[s[14]], m[s[15]]);
            }
            for (int i = 0; i < 8; i++) h[i] ^= v[i] ^ v[i + 8];
        }

        byte[] finish() {
            count += used;
            Arrays.fill(buffer, used, 64, (byte) 0);
            compress(true);
            byte[] out = new byte[32];
            for (int i = 0; i < 32; i++) out[i] = (byte) (h[i / 4] >>> (8 * (i % 4)));
            return out;
        }
    }

    static void g(int[] v, int a, int b, int c, int d, int x, int y) {
        v[a] += v[b] + x;
        v[d] = Integer.rotateRight(v[d] ^ v[a], 16);
        v[c] += v[d];
        v[b] = Integer.rotateRight(v[b] ^ v[c], 12);
        v[a] += v[b] + y;
        v[d] = Integer.rotateRight(v[d] ^ v[a], 8);
        v[c] += v[d];
        v[b] = Integer.rotateRight(v[b] ^ v[c], 7);
    }

    final Node[] nodes = new Node[8];
    long bytes;

    RarBlake2sp() {
        for (int i = 0; i < 8; i++) nodes[i] = new Node(i, false, i == 7);
    }

    void update(byte[] b, int o, int n) {
        while (n > 0) {
            int k = Math.min(n, 64 - (int) (bytes % 64));
            nodes[(int) ((bytes / 64) % 8)].update(b, o, k);
            bytes += k;
            o += k;
            n -= k;
        }
    }

    byte[] finish() {
        Node root = new Node(0, true, true);
        for (Node n : nodes) root.update(n.finish(), 0, 32);
        return root.finish();
    }

    static InputStream verified(InputStream in, byte[] expected) {
        return new FilterInputStream(in) {
            final RarBlake2sp hash = new RarBlake2sp();
            boolean done;
            final byte[] one = new byte[1];

            void check() throws IOException {
                if (!done) {
                    done = true;
                    if (!Arrays.equals(hash.finish(), expected))
                        throw RarCodec.bad("BLAKE2sp 校驗不符");
                }
            }

            public int read() throws IOException {
                int b = in.read();
                if (b < 0) check();
                else {
                    one[0] = (byte) b;
                    hash.update(one, 0, 1);
                }
                return b;
            }

            public int read(byte[] b, int o, int n) throws IOException {
                int k = in.read(b, o, n);
                if (k < 0) check();
                else hash.update(b, o, k);
                return k;
            }
        };
    }
}
