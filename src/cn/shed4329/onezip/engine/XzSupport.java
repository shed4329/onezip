package cn.shed4329.onezip.engine;

import java.io.*;
import java.security.*;

/** XZ canonical VLIs and integrity checks, independent of any archive library. */
final class XzSupport {
    static final byte[] MAGIC = {(byte) 0xfd, '7', 'z', 'X', 'Z', 0};

    static long vli(InputStream in) throws IOException {
        long v = 0;
        for (int i = 0; i < 9; i++) {
            int b = ReadSupport.u8(in);
            v |= (long) (b & 127) << (i * 7);
            if ((b & 128) == 0) {
                if (i > 0 && b == 0) throw new IOException("XZ noncanonical VLI");
                return v;
            }
        }
        throw new IOException("XZ VLI overflow");
    }

    static void vli(OutputStream out, long v) throws IOException {
        if (v < 0) throw new IOException("XZ VLI negative");
        while (v >= 128) {
            out.write((int) v | 128);
            v >>>= 7;
        }
        out.write((int) v);
    }

    static void little(OutputStream out, long v, int n) throws IOException {
        for (int i = 0; i < n; i++) {
            out.write((int) v & 255);
            v >>>= 8;
        }
    }

    static long crc32(byte[] b, int o, int n) {
        Crc32 crc = new Crc32();
        crc.update(b, o, n);
        return crc.value();
    }

    static long bits(byte[] b, int o, int n) {
        long v = 0;
        for (int i = 0; i < n; i++) v |= (long) (b[o + i] & 255) << (8 * i);
        return v;
    }

    static void crc(OutputStream out, byte[] b) throws IOException {
        out.write(b);
        little(out, crc32(b, 0, b.length), 4);
    }

    static final class Check {
        private static final long[] TABLE = new long[256];

        static {
            for (int i = 0; i < 256; i++) {
                long v = i;
                for (int j = 0; j < 8; j++)
                    v = (v >>> 1) ^ ((v & 1) == 0 ? 0 : 0xc96c5795d7870f42L);
                TABLE[i] = v;
            }
        }

        final int id;
        final Crc32 crc;
        final MessageDigest sha;
        long crc64 = -1L;

        Check(int id) throws IOException {
            this.id = id;
            crc = id == 1 ? new Crc32() : null;
            try {
                sha = id == 10 ? MessageDigest.getInstance("SHA-256") : null;
            } catch (NoSuchAlgorithmException e) {
                throw new IOException(e);
            }
            if (id != 0 && id != 1 && id != 4 && id != 10)
                throw new IOException("Unsupported XZ check: " + id);
        }

        int size() {
            return id == 0 ? 0 : id == 1 ? 4 : id == 4 ? 8 : 32;
        }

        void update(byte[] b, int o, int n) {
            if (crc != null) crc.update(b, o, n);
            if (sha != null) sha.update(b, o, n);
            if (id == 4)
                for (int i = o, e = o + n; i < e; i++)
                    crc64 = (crc64 >>> 8) ^ TABLE[((int) crc64 ^ b[i]) & 255];
        }

        byte[] finish() {
            if (sha != null) return sha.digest();
            byte[] b = new byte[size()];
            long v = id == 1 ? crc.value() : ~crc64;
            for (int i = 0; i < b.length; i++) {
                b[i] = (byte) v;
                v >>>= 8;
            }
            return b;
        }
    }

    static final class Counting extends FilterInputStream {
        long position;

        Counting(InputStream in) {
            super(in);
        }

        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) position = ReadSupport.add(position, 1);
            return b;
        }

        public int read(byte[] b, int o, int n) throws IOException {
            int k = in.read(b, o, n);
            if (k > 0) position = ReadSupport.add(position, k);
            return k;
        }
    }
}
