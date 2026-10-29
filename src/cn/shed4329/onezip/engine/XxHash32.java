package cn.shed4329.onezip.engine;

import java.util.Objects;

/** Incremental XXH32, seed zero, implemented with Java's defined modulo-2^32 arithmetic. */
final class XxHash32 {
    private static final int P1 = 0x9e3779b1,
            P2 = 0x85ebca77,
            P3 = 0xc2b2ae3d,
            P4 = 0x27d4eb2f,
            P5 = 0x165667b1;
    private int a = P1 + P2, b = P2, c, d = -P1, used;
    private long length;
    private final byte[] tail = new byte[16];

    static int little(byte[] p, int o) {
        return (p[o] & 255) | ((p[o + 1] & 255) << 8) | ((p[o + 2] & 255) << 16) | (p[o + 3] << 24);
    }

    private static int round(int acc, int lane) {
        return Integer.rotateLeft(acc + lane * P2, 13) * P1;
    }

    private void stripe(byte[] p, int o) {
        a = round(a, little(p, o));
        b = round(b, little(p, o + 4));
        c = round(c, little(p, o + 8));
        d = round(d, little(p, o + 12));
    }

    void update(byte[] p, int o, int n) {
        Objects.checkFromIndexSize(o, n, p.length);
        length += n;
        if (used + n < 16) {
            System.arraycopy(p, o, tail, used, n);
            used += n;
            return;
        }
        if (used > 0) {
            int k = 16 - used;
            System.arraycopy(p, o, tail, used, k);
            stripe(tail, 0);
            o += k;
            n -= k;
            used = 0;
        }
        while (n >= 16) {
            stripe(p, o);
            o += 16;
            n -= 16;
        }
        System.arraycopy(p, o, tail, 0, n);
        used = n;
    }

    long value() {
        int v =
                length >= 16
                        ? Integer.rotateLeft(a, 1)
                                + Integer.rotateLeft(b, 7)
                                + Integer.rotateLeft(c, 12)
                                + Integer.rotateLeft(d, 18)
                        : P5;
        v += (int) length;
        int i = 0;
        for (; i + 4 <= used; i += 4) v = Integer.rotateLeft(v + little(tail, i) * P3, 17) * P4;
        for (; i < used; i++) v = Integer.rotateLeft(v + (tail[i] & 255) * P5, 11) * P1;
        v ^= v >>> 15;
        v *= P2;
        v ^= v >>> 13;
        v *= P3;
        v ^= v >>> 16;
        return Integer.toUnsignedLong(v);
    }

    static long hash(byte[] p, int o, int n) {
        XxHash32 h = new XxHash32();
        h.update(p, o, n);
        return h.value();
    }
}
