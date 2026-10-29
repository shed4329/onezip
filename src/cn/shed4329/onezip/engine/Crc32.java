package cn.shed4329.onezip.engine;

/** IEEE CRC-32 written in Java; no JDK archive API or native compression calls. */
public final class Crc32 {
    /** Creates an empty CRC accumulator; instances are confined to one caller thread. */
    public Crc32() {}

    // A flat slicing-by-16 table avoids dependent CRC updates every eight bytes
    // and repeated inner-array loads; it also gives C2 one bounded lookup array.
    private static final int[] TABLE = new int[16 * 256];

    static {
        for (int i = 0; i < 256; ++i) {
            int c = i;
            for (int j = 0; j < 8; ++j) c = (c >>> 1) ^ ((c & 1) == 0 ? 0 : 0xedb88320);
            TABLE[i] = c;
        }
        for (int t = 1; t < 16; t++)
            for (int i = 0; i < 256; i++) {
                int c = TABLE[(t - 1) * 256 + i];
                TABLE[t * 256 + i] = (c >>> 8) ^ TABLE[c & 255];
            }
    }

    private int value = -1;

    public void update(int b) {
        value = (value >>> 8) ^ TABLE[(value ^ b) & 255];
    }

    public void update(byte[] bytes, int offset, int length) {
        java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
        int c = value, end = offset + length;
        while (offset <= end - 16) {
            c ^=
                    (bytes[offset] & 255)
                            | ((bytes[offset + 1] & 255) << 8)
                            | ((bytes[offset + 2] & 255) << 16)
                            | (bytes[offset + 3] << 24);
            // Keep the independent lookups in a balanced XOR tree rather than
            // a sixteen-term dependency chain on the next CRC state.
            int a =
                    (TABLE[3840 + (c & 255)] ^ TABLE[3584 + ((c >>> 8) & 255)])
                            ^ (TABLE[3328 + ((c >>> 16) & 255)] ^ TABLE[3072 + (c >>> 24)]);
            int b =
                    (TABLE[2816 + (bytes[offset + 4] & 255)]
                                    ^ TABLE[2560 + (bytes[offset + 5] & 255)])
                            ^ (TABLE[2304 + (bytes[offset + 6] & 255)]
                                    ^ TABLE[2048 + (bytes[offset + 7] & 255)]);
            int d =
                    (TABLE[1792 + (bytes[offset + 8] & 255)]
                                    ^ TABLE[1536 + (bytes[offset + 9] & 255)])
                            ^ (TABLE[1280 + (bytes[offset + 10] & 255)]
                                    ^ TABLE[1024 + (bytes[offset + 11] & 255)]);
            int e =
                    (TABLE[768 + (bytes[offset + 12] & 255)]
                                    ^ TABLE[512 + (bytes[offset + 13] & 255)])
                            ^ (TABLE[256 + (bytes[offset + 14] & 255)]
                                    ^ TABLE[bytes[offset + 15] & 255]);
            c = (a ^ b) ^ (d ^ e);
            offset += 16;
        }
        while (offset <= end - 8) {
            c ^=
                    (bytes[offset] & 255)
                            | ((bytes[offset + 1] & 255) << 8)
                            | ((bytes[offset + 2] & 255) << 16)
                            | (bytes[offset + 3] << 24);
            c =
                    TABLE[1792 + (c & 255)]
                            ^ TABLE[1536 + ((c >>> 8) & 255)]
                            ^ TABLE[1280 + ((c >>> 16) & 255)]
                            ^ TABLE[1024 + (c >>> 24)]
                            ^ TABLE[768 + (bytes[offset + 4] & 255)]
                            ^ TABLE[512 + (bytes[offset + 5] & 255)]
                            ^ TABLE[256 + (bytes[offset + 6] & 255)]
                            ^ TABLE[bytes[offset + 7] & 255];
            offset += 8;
        }
        while (offset < end) c = (c >>> 8) ^ TABLE[(c ^ bytes[offset++]) & 255];
        value = c;
    }

    public long value() {
        return Integer.toUnsignedLong(~value);
    }

    public static long of(byte[] bytes) {
        Crc32 c = new Crc32();
        c.update(bytes, 0, bytes.length);
        return c.value();
    }
}
