package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Checked binary reads and bounded streams. No archive or codec delegation. */
final class ReadSupport {
    static final int MAX_HEADER = 64 * 1024 * 1024, MAX_ENTRIES = 1_000_000;

    static int u8(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException("壓縮資料截斷");
        return b;
    }

    static long le(InputStream in, int n) throws IOException {
        long v = 0;
        for (int i = 0; i < n; i++) v |= (long) u8(in) << (8 * i);
        if (n == 8 && v < 0) throw new IOException("大小超出支援範圍");
        return v;
    }

    static byte[] bytes(InputStream in, int n) throws IOException {
        byte[] b = in.readNBytes(n);
        if (b.length != n) throw new EOFException("壓縮資料截斷");
        return b;
    }

    static long add(long a, long b) throws IOException {
        if (a < 0 || b < 0 || a > Long.MAX_VALUE - b) throw new IOException("壓縮檔大小溢位");
        return a + b;
    }

    static int count(long n, int max) throws IOException {
        if (n < 0 || n > max) throw new IOException("壓縮檔超出支援的記憶體／項目上限: " + n);
        return (int) n;
    }

    static void check(BooleanSupplier stop) throws IOException {
        if (Thread.currentThread().isInterrupted() || (stop != null && stop.getAsBoolean()))
            throw new InterruptedIOException("操作已取消");
    }

    static InputStream region(RandomAccessFile file, long offset, long length) throws IOException {
        if (add(offset, length) > file.length()) throw new EOFException("壓縮資料範圍超出檔案");
        file.seek(offset);
        return new InputStream() {
            long remaining = length;

            public int read() throws IOException {
                if (remaining == 0) return -1;
                int b = file.read();
                if (b < 0) throw new EOFException();
                --remaining;
                return b;
            }

            public int read(byte[] b, int o, int n) throws IOException {
                Objects.checkFromIndexSize(o, n, b.length);
                if (n == 0) return 0;
                if (remaining == 0) return -1;
                int k = file.read(b, o, (int) Math.min(n, remaining));
                if (k < 0) throw new EOFException();
                remaining -= k;
                return k;
            }
        };
    }

    /** Checks exact output size, optional CRC, and optionally the enclosing stream's EOF. */
    static InputStream verified(
            InputStream in,
            long size,
            Long expectedCrc,
            boolean requireEof,
            BooleanSupplier cancel) {
        return new InputStream() {
            long remaining = size;
            final Crc32 crc = expectedCrc == null ? null : new Crc32();
            boolean verified;
            int untilCheck;

            private void finish() throws IOException {
                if (verified) return;
                check(cancel);
                if (requireEof && in.read() != -1) throw new IOException("解壓大小超過標頭宣告");
                if (crc != null && crc.value() != expectedCrc) throw new IOException("壓縮資料 CRC 不符");
                verified = true;
            }

            public int read() throws IOException {
                if (untilCheck-- <= 0) {
                    check(cancel);
                    untilCheck = 4095;
                }
                if (remaining == 0) {
                    finish();
                    return -1;
                }
                int b = in.read();
                if (b < 0) throw new EOFException("解壓大小小於標頭宣告");
                if (crc != null) crc.update(b);
                remaining--;
                return b;
            }

            public int read(byte[] b, int o, int n) throws IOException {
                Objects.checkFromIndexSize(o, n, b.length);
                if (n == 0) return 0;
                check(cancel);
                if (remaining == 0) {
                    finish();
                    return -1;
                }
                int k = in.read(b, o, (int) Math.min(n, remaining));
                if (k < 0) throw new EOFException("解壓大小小於標頭宣告");
                if (crc != null) crc.update(b, o, k);
                remaining -= k;
                untilCheck = 4095;
                return k;
            }
        };
    }

    static final class Cursor extends ByteArrayInputStream {
        Cursor(byte[] b) {
            super(b);
        }

        int byteValue() throws IOException {
            return u8(this);
        }

        long little(int n) throws IOException {
            return le(this, n);
        }

        long number() throws IOException {
            int first = byteValue(), mask = 0x80;
            long value = 0;
            for (int i = 0; i < 8; i++, mask >>>= 1) {
                if ((first & mask) == 0) return value | ((long) (first & (mask - 1)) << (8 * i));
                value |= (long) byteValue() << (8 * i);
            }
            if (value < 0) throw new IOException("7z 整數溢位");
            return value;
        }

        int count() throws IOException {
            return ReadSupport.count(number(), MAX_ENTRIES);
        }

        Cursor property() throws IOException {
            return new Cursor(bytes(this, ReadSupport.count(number(), MAX_HEADER)));
        }

        void expect(int id) throws IOException {
            if (byteValue() != id) throw new IOException("無效的 7z 標頭欄位，預期 " + id);
        }

        void end() throws IOException {
            if (available() != 0) throw new IOException("標頭含未解析資料");
        }

        String utf16() throws IOException {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            for (; ; ) {
                int a = byteValue(), c = byteValue();
                if ((a | c) == 0) return b.toString(StandardCharsets.UTF_16LE);
                b.write(a);
                b.write(c);
            }
        }
    }
}
