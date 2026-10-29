package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** RFC1952 framing over our RFC1951 decoder, including concatenated members. */
public final class GzipInputStream extends InputStream {
    private final PushbackInputStream in;
    private DeflateInputStream decoder;
    private Crc32 crc;
    private long size;
    private boolean ended, closed;
    private String originalName;

    public GzipInputStream(InputStream source) throws IOException {
        in = new PushbackInputStream(Objects.requireNonNull(source), 16400);
        try {
            header(true);
        } catch (IOException | RuntimeException ex) {
            try {
                in.close();
            } catch (IOException close) {
                ex.addSuppressed(close);
            }
            throw ex;
        }
    }

    public String originalName() {
        return originalName;
    }

    private boolean header(boolean first) throws IOException {
        int a = in.read();
        if (a < 0) {
            if (first) throw new EOFException("GZIP 標頭遺失");
            ended = true;
            return false;
        }
        Crc32 h = new Crc32();
        h.update(a);
        if (a != 31 || hbyte(h) != 139 || hbyte(h) != 8) throw new IOException("無效的 GZIP 簽名或壓縮方法");
        int flags = hbyte(h);
        if ((flags & 224) != 0) throw new IOException("保留的 GZIP flags");
        for (int i = 0; i < 6; i++) hbyte(h);
        if ((flags & 4) != 0) {
            int n = hbyte(h) | (hbyte(h) << 8);
            for (int i = 0; i < n; i++) hbyte(h);
        }
        if ((flags & 8) != 0) {
            String name = text(h);
            if (first) originalName = name;
        }
        if ((flags & 16) != 0) text(h);
        if ((flags & 2) != 0 && ReadSupport.le(in, 2) != (h.value() & 65535))
            throw new IOException("GZIP 標頭 CRC 不符");
        crc = new Crc32();
        size = 0;
        decoder = new DeflateInputStream(in, true);
        return true;
    }

    private int hbyte(Crc32 h) throws IOException {
        int b = ReadSupport.u8(in);
        h.update(b);
        return b;
    }

    private String text(Crc32 h) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (int n = 0; n < 65536; n++) {
            int v = hbyte(h);
            if (v == 0) return b.toString(StandardCharsets.ISO_8859_1);
            b.write(v);
        }
        throw new IOException("GZIP 文字標頭過長");
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        return read(b, 0, 1) < 0 ? -1 : b[0] & 255;
    }

    @Override
    public int read(byte[] b, int o, int n) throws IOException {
        Objects.checkFromIndexSize(o, n, b.length);
        if (closed) throw new IOException("Stream closed");
        if (n == 0) return 0;
        while (!ended) {
            int k = decoder.read(b, o, n);
            if (k >= 0) {
                crc.update(b, o, k);
                size = ReadSupport.add(size, k);
                return k;
            }
            long expected = ReadSupport.le(in, 4), length = ReadSupport.le(in, 4);
            if (expected != crc.value() || length != (size & 0xffffffffL))
                throw new IOException("GZIP CRC／ISIZE 不符");
            if (!header(false)) break;
        }
        return -1;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            in.close();
        }
    }
}
