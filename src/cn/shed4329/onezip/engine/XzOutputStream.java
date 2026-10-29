package cn.shed4329.onezip.engine;

import cn.shed4329.onezip.engine.lzma.ArkLzma2OutputStream;

import java.io.*;
import java.util.Objects;

/** Standard XZ stream with one streaming LZMA2 block, CRC64, checked Index and Footer. */
public final class XzOutputStream extends OutputStream {
    private final OutputStream out;
    private final int level;
    private final long expected;
    private final XzSupport.Check check = new XzSupport.Check(4);
    private ArkLzma2OutputStream encoder;
    private long written, packed;
    private boolean finished, closed;

    public XzOutputStream(OutputStream out, int level, long size) throws IOException {
        if (level < -1 || level > 9 || size < -1)
            throw new IllegalArgumentException("Invalid XZ options");
        this.out = Objects.requireNonNull(out);
        this.level = level;
        expected = size;
        out.write(XzSupport.MAGIC);
        XzSupport.crc(out, new byte[] {0, 4});
    }

    private void begin() throws IOException {
        if (encoder != null) return;
        XzSupport.crc(
                out,
                new byte[] {2, 0, 0x21, 1, 18, 0, 0, 0}); // 12-byte block header, LZMA2 / 2MiB.
        encoder =
                new ArkLzma2OutputStream(
                        new OutputStream() {
                            public void write(int b) throws IOException {
                                out.write(b);
                                packed = ReadSupport.add(packed, 1);
                            }

                            public void write(byte[] b, int o, int n) throws IOException {
                                out.write(b, o, n);
                                packed = ReadSupport.add(packed, n);
                            }

                            public void flush() throws IOException {
                                out.flush();
                            }
                        },
                        level);
    }

    private void writable(int n) throws IOException {
        if (closed || finished) throw new IOException("XZ stream finished");
        if (expected >= 0 && n > expected - written)
            throw new IOException("XZ source exceeds declared size");
    }

    @Override
    public void write(int b) throws IOException {
        byte[] one = {(byte) b};
        write(one, 0, 1);
    }

    @Override
    public void write(byte[] b, int o, int n) throws IOException {
        Objects.checkFromIndexSize(o, n, b.length);
        writable(n);
        if (n == 0) return;
        ReadSupport.check(null);
        begin();
        encoder.write(b, o, n);
        check.update(b, o, n);
        written = ReadSupport.add(written, n);
    }

    @Override
    public void flush() throws IOException {
        if (closed) throw new IOException("Stream closed");
        if (encoder != null && !finished) encoder.flush();
        out.flush();
    }

    public void finish() throws IOException {
        if (closed) throw new IOException("Stream closed");
        if (finished) return;
        if (expected >= 0 && written != expected)
            throw new EOFException("XZ source shorter than declared size");
        if (encoder != null) {
            encoder.finish();
            for (long i = packed; i % 4 != 0; i++) out.write(0);
            out.write(check.finish());
        }
        ByteArrayOutputStream index = new ByteArrayOutputStream();
        index.write(0);
        XzSupport.vli(index, encoder == null ? 0 : 1);
        if (encoder != null) {
            XzSupport.vli(index, ReadSupport.add(20, packed));
            XzSupport.vli(index, written);
        }
        while (index.size() % 4 != 0) index.write(0);
        XzSupport.crc(out, index.toByteArray());
        ByteArrayOutputStream footer = new ByteArrayOutputStream();
        XzSupport.little(footer, index.size() / 4, 4);
        footer.write(0);
        footer.write(4);
        byte[] f = footer.toByteArray();
        XzSupport.little(out, XzSupport.crc32(f, 0, f.length), 4);
        out.write(f);
        out.write('Y');
        out.write('Z');
        finished = true;
    }

    @Override
    public void close() throws IOException {
        if (!closed)
            try {
                finish();
            } finally {
                closed = true;
                try {
                    if (encoder != null) encoder.close();
                } finally {
                    out.close();
                }
            }
    }
}
