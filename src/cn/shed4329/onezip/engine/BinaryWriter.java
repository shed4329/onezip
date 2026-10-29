package cn.shed4329.onezip.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

final class BinaryWriter extends ByteArrayOutputStream {
    void le(long value, int bytes) {
        for (int i = 0; i < bytes; ++i) {
            write((int) value);
            value >>>= 8;
        }
    }

    void uint64(long value) {
        if (value < 0) throw new IllegalArgumentException("Negative 7z integer");
        int mask = 0x80, first = 0;
        for (int extra = 0; extra < 8; ++extra) {
            if (value < (1L << (7 * (extra + 1))) && extra < 8) {
                first |= (int) (value >>> (8 * extra));
                write(first);
                le(value, extra);
                return;
            }
            first |= mask;
            mask >>>= 1;
            if (extra == 7) {
                write(first);
                le(value, 8);
                return;
            }
        }
    }

    void append(byte[] data) {
        write(data, 0, data.length);
    }

    void to(OutputStream out) throws IOException {
        writeTo(out);
    }
}
