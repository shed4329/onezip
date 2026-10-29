package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.Objects;

/**
 * LZ4 frames with linked/independent blocks, concatenation, skippable frames and full XXH32
 * validation.
 */
public final class Lz4InputStream extends InputStream {
    private final InputStream in;
    private byte[] packed, decoded;
    private int max, flags, pos, end, history;
    private boolean frame, ended, closed, seen;
    private long expected, total;
    private XxHash32 content;

    public Lz4InputStream(InputStream in) throws IOException {
        this.in = Objects.requireNonNull(in);
        try {
            header();
        } catch (IOException | RuntimeException e) {
            try {
                in.close();
            } catch (IOException close) {
                e.addSuppressed(close);
            }
            throw e;
        }
    }

    private boolean header() throws IOException {
        for (; ; ) {
            ReadSupport.check(null);
            int first = in.read();
            if (first < 0) {
                if (!seen) throw new EOFException("LZ4 frame missing");
                ended = true;
                return false;
            }
            long magic = first | (ReadSupport.le(in, 3) << 8);
            seen = true;
            if ((magic & 0xfffffff0L) == 0x184d2a50L) {
                long n = ReadSupport.le(in, 4);
                byte[] skip = new byte[65536];
                while (n > 0) {
                    ReadSupport.check(null);
                    int k = in.read(skip, 0, (int) Math.min(n, skip.length));
                    if (k < 0) throw new EOFException("LZ4 skippable frame truncated");
                    n -= k;
                }
                continue;
            }
            if (magic != 0x184d2204L)
                throw new IOException(
                        "Unsupported LZ4 signature (legacy/raw blocks are not frames)");
            ByteArrayOutputStream descriptor = new ByteArrayOutputStream();
            flags = ReadSupport.u8(in);
            int bd = ReadSupport.u8(in);
            descriptor.write(flags);
            descriptor.write(bd);
            if ((flags & 0xc2) != 0x40 || (bd & 0x8f) != 0 || ((bd >>> 4) & 7) < 4)
                throw new IOException("Invalid LZ4 descriptor");
            int code = (bd >>> 4) & 7;
            max = 1 << (8 + 2 * code);
            expected = -1;
            if ((flags & 8) != 0) {
                byte[] size = ReadSupport.bytes(in, 8);
                descriptor.write(size);
                expected = 0;
                for (int i = 0; i < 8; i++) expected |= (long) (size[i] & 255) << (8 * i);
                if (expected < 0) throw new IOException("LZ4 content size exceeds signed long");
            }
            boolean dictionary = (flags & 1) != 0;
            if (dictionary) descriptor.write(ReadSupport.bytes(in, 4));
            byte[] h = descriptor.toByteArray();
            if (ReadSupport.u8(in) != ((XxHash32.hash(h, 0, h.length) >>> 8) & 255))
                throw new IOException("LZ4 header checksum mismatch");
            if (dictionary) throw new IOException("External LZ4 dictionaries are unsupported");
            packed = new byte[max];
            decoded = new byte[max + 65536];
            pos = end = 65536;
            history = 0;
            total = 0;
            content = new XxHash32();
            frame = true;
            return true;
        }
    }

    private boolean block() throws IOException {
        while (!ended) {
            ReadSupport.check(null);
            if (!frame && !header()) return false;
            long word = ReadSupport.le(in, 4);
            if (word == 0) {
                if ((flags & 4) != 0 && ReadSupport.le(in, 4) != content.value())
                    throw new IOException("LZ4 content checksum mismatch");
                if (expected >= 0 && total != expected)
                    throw new IOException("LZ4 content size mismatch");
                frame = false;
                continue;
            }
            int n = (int) (word & 0x7fffffffL);
            if (n > max) throw new IOException("LZ4 block size exceeds descriptor");
            int keep = (flags & 32) != 0 ? 0 : Math.min(65536, history + end - 65536);
            if (keep > 0) System.arraycopy(decoded, end - keep, decoded, 65536 - keep, keep);
            history = keep;
            if (in.readNBytes(packed, 0, n) != n) throw new EOFException("LZ4 block truncated");
            if ((flags & 16) != 0 && ReadSupport.le(in, 4) != XxHash32.hash(packed, 0, n))
                throw new IOException("LZ4 block checksum mismatch");
            int length;
            if ((word & 0x80000000L) != 0) {
                System.arraycopy(packed, 0, decoded, 65536, n);
                length = n;
            } else length = Lz4Block.decode(packed, n, decoded, 65536, max, history);
            pos = 65536;
            end = pos + length;
            total = ReadSupport.add(total, length);
            if (expected >= 0 && total > expected)
                throw new IOException("LZ4 decoded size exceeds descriptor");
            content.update(decoded, pos, length);
            if (length > 0) return true;
        }
        return false;
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
        if (pos == end && !block()) return -1;
        int k = Math.min(n, end - pos);
        System.arraycopy(decoded, pos, b, o, k);
        pos += k;
        return k;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            in.close();
        }
    }
}
