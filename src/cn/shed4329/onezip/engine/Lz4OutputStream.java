package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.Objects;

/** Standard LZ4 frame writer: independent 256KiB blocks, XXH32 block/content checksums. */
public final class Lz4OutputStream extends OutputStream {
    private final OutputStream out;
    private final byte[] buffer = new byte[256 * 1024];
    private final int[] table = new int[65536];
    private final XxHash32 content = new XxHash32();
    private final long expected;
    private final int level;
    private long written;
    private int used;
    private boolean finished, closed;

    public Lz4OutputStream(OutputStream out, int level, long size) throws IOException {
        if (level < -1 || level > 9 || size < -1)
            throw new IllegalArgumentException("Invalid LZ4 options");
        this.out = Objects.requireNonNull(out);
        this.level = level;
        expected = size;
        little(0x184d2204L);
        ByteArrayOutputStream descriptor = new ByteArrayOutputStream();
        descriptor.write(size >= 0 ? 0x7c : 0x74);
        descriptor.write(0x50);
        if (size >= 0) for (int i = 0; i < 8; i++) descriptor.write((int) (size >>> (8 * i)) & 255);
        byte[] header = descriptor.toByteArray();
        out.write(header);
        out.write((int) (XxHash32.hash(header, 0, header.length) >>> 8) & 255);
    }

    private void writable(int n) throws IOException {
        if (closed || finished) throw new IOException("LZ4 stream finished");
        if (expected >= 0 && n > expected - written)
            throw new IOException("LZ4 source exceeds declared size");
    }

    @Override
    public void write(int b) throws IOException {
        byte[] p = {(byte) b};
        write(p, 0, 1);
    }

    @Override
    public void write(byte[] b, int o, int n) throws IOException {
        Objects.checkFromIndexSize(o, n, b.length);
        writable(n);
        while (n > 0) {
            ReadSupport.check(null);
            int k = Math.min(n, buffer.length - used);
            System.arraycopy(b, o, buffer, used, k);
            used += k;
            o += k;
            n -= k;
            written = ReadSupport.add(written, k);
            if (used == buffer.length) block();
        }
    }

    private void little(long n) throws IOException {
        for (int i = 0; i < 4; i++) {
            out.write((int) n & 255);
            n >>>= 8;
        }
    }

    private void block() throws IOException {
        if (used == 0) return;
        content.update(buffer, 0, used);
        byte[] encoded = level == 0 ? null : Lz4Block.encode(buffer, used, table);
        boolean compressed = encoded != null && encoded.length < used;
        byte[] payload = compressed ? encoded : buffer;
        int length = compressed ? encoded.length : used;
        little((compressed ? 0 : 0x80000000L) | length);
        out.write(payload, 0, length);
        little(XxHash32.hash(payload, 0, length));
        used = 0;
    }

    public void finish() throws IOException {
        if (closed) throw new IOException("Stream closed");
        if (finished) return;
        if (expected >= 0 && written != expected)
            throw new EOFException("LZ4 source shorter than declared size");
        block();
        little(0);
        little(content.value());
        finished = true;
    }

    @Override
    public void flush() throws IOException {
        if (closed) throw new IOException("Stream closed");
        if (!finished) block();
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (!closed)
            try {
                finish();
            } finally {
                closed = true;
                out.close();
            }
    }
}
