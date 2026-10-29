package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Self-written RFC1952 header/trailer and CRC around the independent DEFLATE encoder. */
public final class GzipOutputStream extends OutputStream {
    private final OutputStream out;
    private final DeflateOutputStream encoder;
    private final Crc32 crc = new Crc32();
    private long size;
    private boolean finished, closed;

    public GzipOutputStream(OutputStream out, int level) throws IOException {
        this(out, level, 1, -1, null);
    }

    public GzipOutputStream(OutputStream out, int level, int threads, long expected, String name)
            throws IOException {
        this.out = Objects.requireNonNull(out);
        if (level < -1 || level > 9 || threads < 0 || threads > 64 || expected < -1)
            throw new IllegalArgumentException("Invalid GZIP options");
        if (name != null
                && (!StandardCharsets.ISO_8859_1.newEncoder().canEncode(name)
                        || name.indexOf('\0') >= 0)) name = null;
        out.write(
                new byte[] {
                    31,
                    (byte) 139,
                    8,
                    (byte) (name == null ? 0 : 8),
                    0,
                    0,
                    0,
                    0,
                    (byte) (level == 9 ? 2 : level == 1 ? 4 : 0),
                    (byte) 255
                });
        if (name != null) {
            out.write(name.getBytes(StandardCharsets.ISO_8859_1));
            out.write(0);
        }
        encoder =
                expected < 0
                        ? new DeflateOutputStream(out, level)
                        : new DeflateOutputStream(out, level, threads, expected);
    }

    private void writable() throws IOException {
        if (closed || finished) throw new IOException("GZIP stream finished");
    }

    @Override
    public void write(int b) throws IOException {
        writable();
        encoder.write(b);
        crc.update(b);
        size = ReadSupport.add(size, 1);
    }

    @Override
    public void write(byte[] b, int o, int n) throws IOException {
        Objects.checkFromIndexSize(o, n, b.length);
        writable();
        encoder.write(b, o, n);
        crc.update(b, o, n);
        size = ReadSupport.add(size, n);
    }

    @Override
    public void flush() throws IOException {
        if (closed) throw new IOException("Stream closed");
        encoder.flush();
    }

    public void finish() throws IOException {
        if (closed) throw new IOException("Stream closed");
        if (finished) return;
        encoder.finish();
        little(crc.value());
        little(size);
        finished = true;
    }

    private void little(long v) throws IOException {
        for (int i = 0; i < 4; i++) {
            out.write((int) v & 255);
            v >>>= 8;
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        try {
            finish();
        } finally {
            closed = true;
            encoder.close();
        }
    }
}
