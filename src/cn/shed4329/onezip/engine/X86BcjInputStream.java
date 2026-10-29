package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.Objects;

/** Reverses the standard x86 BCJ E8/E9 branch-address transform with five-byte lookahead. */
final class X86BcjInputStream extends InputStream {
    private static final boolean[] ALLOWED = {true, true, true, false, true, false, false, false};
    private static final int[] MASK_BITS = {0, 1, 2, 2, 3, 3, 3, 3};
    private final InputStream in;
    private final byte[] queue = new byte[5];
    private final long start;
    private int head, count, skip, mask;
    private long offset, previous = -5;
    private boolean eof;

    X86BcjInputStream(InputStream in, long start) {
        this.in = in;
        this.start = start;
    }

    private int at(int index) {
        return queue[(head + index) % 5] & 255;
    }

    private void put(int index, int value) {
        queue[(head + index) % 5] = (byte) value;
    }

    private static boolean signByte(int b) {
        return b == 0 || b == 255;
    }

    @Override
    public int read() throws IOException {
        while (count < 5 && !eof) {
            int b = in.read();
            if (b < 0) eof = true;
            else {
                put(count, b);
                count++;
            }
        }
        if (count == 0) return -1;
        if (skip > 0) --skip;
        else if (count == 5 && (at(0) & 254) == 232) {
            long distance = offset - previous;
            boolean candidate = true;
            if (distance > 3) mask = 0;
            else {
                mask = (mask << ((int) distance - 1)) & 7;
                if (mask != 0 && (!ALLOWED[mask] || signByte(at(4 - MASK_BITS[mask]))))
                    candidate = false;
            }
            previous = offset;
            if (candidate && signByte(at(4))) {
                int value = at(1) | (at(2) << 8) | (at(3) << 16) | (at(4) << 24), decoded;
                for (; ; ) {
                    decoded = value - (int) (start + offset + 5);
                    if (mask == 0) break;
                    int shift = MASK_BITS[mask] * 8;
                    if (!signByte((decoded >>> (24 - shift)) & 255)) break;
                    value = decoded ^ (int) ((1L << (32 - shift)) - 1);
                }
                put(1, decoded);
                put(2, decoded >>> 8);
                put(3, decoded >>> 16);
                put(4, ((decoded >>> 24) & 1) == 0 ? 0 : 255);
                skip = 4;
            } else mask = (mask << 1) | 1;
        }
        int b = at(0);
        head = (head + 1) % 5;
        count--;
        offset++;
        return b;
    }

    @Override
    public int read(byte[] b, int o, int n) throws IOException {
        Objects.checkFromIndexSize(o, n, b.length);
        if (n == 0) return 0;
        int k = 0;
        for (; k < n; k++) {
            if ((k & 4095) == 0) ReadSupport.check(null);
            int value = read();
            if (value < 0) break;
            b[o + k] = (byte) value;
        }
        return k == 0 ? -1 : k;
    }
}
