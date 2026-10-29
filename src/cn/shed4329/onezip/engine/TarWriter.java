package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** USTAR regular files/directories; POSIX PAX path records for Unicode and long names. */
final class TarWriter implements AutoCloseable {
    private final OutputStream out;
    private long remaining, padding;
    private boolean active, finished;

    TarWriter(OutputStream out) {
        this.out = out;
    }

    private static boolean pax(String name) {
        return name.length() > 100 || !StandardCharsets.US_ASCII.newEncoder().canEncode(name);
    }

    private static byte[] record(String key, String value) {
        byte[] body = (key + "=" + value + "\n").getBytes(StandardCharsets.UTF_8);
        int n = body.length + 2;
        for (; ; ) {
            int next = Integer.toString(n).length() + 1 + body.length;
            if (next == n) break;
            n = next;
        }
        return (n + " " + key + "=" + value + "\n").getBytes(StandardCharsets.UTF_8);
    }

    static long entryBytes(String name, long size) throws IOException {
        long n = ReadSupport.add(512, ReadSupport.add(size, (512 - size % 512) % 512));
        if (pax(name)) {
            long p = record("path", name).length;
            n = ReadSupport.add(n, 512 + p + (512 - p % 512) % 512);
        }
        return n;
    }

    OutputStream begin(String name, boolean directory, long size, long mtime) throws IOException {
        if (active || finished || size < 0) throw new IOException("TAR entry state invalid");
        if (directory && size != 0) throw new IOException("TAR directory contains data");
        if (pax(name)) {
            byte[] p = record("path", name);
            header("PaxHeaders/entry", p.length, 'x', mtime);
            out.write(p);
            zeros((512 - p.length % 512) % 512);
            header("PaxEntry", size, directory ? '5' : '0', mtime);
        } else header(name, size, directory ? '5' : '0', mtime);
        active = true;
        remaining = size;
        padding = (512 - size % 512) % 512;
        return new OutputStream() {
            boolean closed;

            public void write(int b) throws IOException {
                if (closed || remaining == 0) throw new IOException("TAR size exceeded");
                out.write(b);
                remaining--;
            }

            public void write(byte[] b, int o, int n) throws IOException {
                java.util.Objects.checkFromIndexSize(o, n, b.length);
                if (closed || n > remaining) throw new IOException("TAR size exceeded");
                out.write(b, o, n);
                remaining -= n;
            }

            public void close() throws IOException {
                if (closed) return;
                closed = true;
                if (remaining != 0) throw new EOFException("TAR source size changed");
                zeros(padding);
                active = false;
            }
        };
    }

    private void header(String name, long size, int type, long mtime) throws IOException {
        byte[] h = new byte[512];
        byte[] n = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(n, 0, h, 0, n.length);
        number(h, 100, 8, type == '5' ? 0755 : 0644);
        number(h, 108, 8, 0);
        number(h, 116, 8, 0);
        number(h, 124, 12, size);
        number(h, 136, 12, Math.max(0, mtime));
        Arrays.fill(h, 148, 156, (byte) 32);
        h[156] = (byte) type;
        System.arraycopy("ustar\0".getBytes(StandardCharsets.US_ASCII), 0, h, 257, 6);
        h[263] = '0';
        h[264] = '0';
        long sum = 0;
        for (byte b : h) sum += b & 255;
        String s = String.format(java.util.Locale.ROOT, "%06o", sum);
        System.arraycopy(s.getBytes(StandardCharsets.US_ASCII), 0, h, 148, 6);
        h[154] = 0;
        h[155] = 32;
        out.write(h);
    }

    private static void number(byte[] h, int o, int n, long value) {
        String s = Long.toOctalString(value);
        if (s.length() < n) {
            Arrays.fill(h, o, o + n - 1, (byte) '0');
            byte[] b = s.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(b, 0, h, o + n - 1 - b.length, b.length);
        } else {
            for (int i = o + n - 1; i >= o; i--) {
                h[i] = (byte) value;
                value >>>= 8;
            }
            h[o] |= (byte) 128;
        }
    }

    private void zeros(long n) throws IOException {
        out.write(new byte[(int) n]);
    }

    void finish() throws IOException {
        if (finished) return;
        if (active) throw new IOException("TAR entry still open");
        zeros(1024);
        finished = true;
    }

    public void close() throws IOException {
        try {
            finish();
        } finally {
            out.close();
        }
    }
}
