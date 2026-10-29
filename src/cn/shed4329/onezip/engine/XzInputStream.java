package cn.shed4329.onezip.engine;

import cn.shed4329.onezip.engine.lzma.LzmaInputStream;

import java.io.*;
import java.util.*;

/**
 * XZ container decoder, LZMA2 + optional Delta/x86 filters; all structural and payload checks
 * retained.
 */
public final class XzInputStream extends InputStream {
    private final XzSupport.Counting in;
    private final List<long[]> records = new ArrayList<>();
    private int checkId, headerSize;
    private InputStream decoder;
    private XzSupport.Check check;
    private long packedStart, unpacked, declaredPacked, declaredUnpacked;
    private boolean stream, ended, closed;
    private final byte[] one = new byte[1];

    public XzInputStream(InputStream source) throws IOException {
        in = new XzSupport.Counting(new BufferedInputStream(Objects.requireNonNull(source), 65536));
        try {
            header(ReadSupport.u8(in));
        } catch (IOException | RuntimeException ex) {
            try {
                in.close();
            } catch (IOException e) {
                ex.addSuppressed(e);
            }
            throw ex;
        }
    }

    private void header(int first) throws IOException {
        if (first != (XzSupport.MAGIC[0] & 255)
                || !Arrays.equals(
                        ReadSupport.bytes(in, 5), Arrays.copyOfRange(XzSupport.MAGIC, 1, 6)))
            throw new IOException("Invalid XZ signature");
        byte[] flags = ReadSupport.bytes(in, 2);
        if (flags[0] != 0 || (flags[1] & 0xf0) != 0)
            throw new IOException("Unsupported XZ stream flags");
        if (ReadSupport.le(in, 4) != XzSupport.crc32(flags, 0, 2))
            throw new IOException("XZ header CRC mismatch");
        checkId = flags[1] & 255;
        new XzSupport.Check(checkId);
        records.clear();
        stream = true;
    }

    private boolean nextStream() throws IOException {
        long zeros = 0;
        int b;
        while ((b = in.read()) == 0) {
            zeros++;
            ReadSupport.check(null);
        }
        if (zeros % 4 != 0) throw new IOException("XZ stream padding alignment invalid");
        if (b < 0) {
            ended = true;
            return false;
        }
        header(b);
        return true;
    }

    private boolean block() throws IOException {
        while (!ended) {
            ReadSupport.check(null);
            if (!stream && !nextStream()) return false;
            int first = ReadSupport.u8(in);
            if (first == 0) {
                index();
                stream = false;
                continue;
            }
            if (records.size() >= ReadSupport.MAX_ENTRIES)
                throw new IOException("Too many XZ blocks");
            headerSize = (first + 1) * 4;
            byte[] h = new byte[headerSize];
            h[0] = (byte) first;
            if (in.readNBytes(h, 1, h.length - 1) != h.length - 1)
                throw new EOFException("XZ block header truncated");
            if (XzSupport.bits(h, h.length - 4, 4) != XzSupport.crc32(h, 0, h.length - 4))
                throw new IOException("XZ block header CRC mismatch");
            ByteArrayInputStream fields = new ByteArrayInputStream(h, 1, h.length - 5);
            int flags = ReadSupport.u8(fields);
            if ((flags & 0x3c) != 0) throw new IOException("XZ block reserved flags");
            int filters = (flags & 3) + 1;
            declaredPacked = (flags & 64) != 0 ? XzSupport.vli(fields) : -1;
            declaredUnpacked = (flags & 128) != 0 ? XzSupport.vli(fields) : -1;
            long[] ids = new long[filters];
            byte[][] props = new byte[filters][];
            for (int i = 0; i < filters; i++) {
                ids[i] = XzSupport.vli(fields);
                int n = ReadSupport.count(XzSupport.vli(fields), 1024);
                props[i] = ReadSupport.bytes(fields, n);
            }
            for (int b; (b = fields.read()) != -1; )
                if (b != 0) throw new IOException("XZ block header padding invalid");
            if (ids[filters - 1] != 0x21 || props[filters - 1].length != 1)
                throw new IOException("Unsupported XZ final filter (requires LZMA2)");
            packedStart = in.position;
            InputStream packed = in;
            if (declaredPacked >= 0)
                packed =
                        new FilterInputStream(in) {
                            long left = declaredPacked;

                            public int read() throws IOException {
                                if (left == 0) return -1;
                                int b = in.read();
                                if (b < 0) throw new EOFException();
                                left--;
                                return b;
                            }

                            public int read(byte[] b, int o, int n) throws IOException {
                                Objects.checkFromIndexSize(o, n, b.length);
                                if (n == 0) return 0;
                                if (left == 0) return -1;
                                int k = in.read(b, o, (int) Math.min(n, left));
                                if (k < 0) throw new EOFException();
                                left -= k;
                                return k;
                            }
                        };
            decoder = new LzmaInputStream(packed, props[filters - 1][0] & 255);
            for (int i = filters - 2; i >= 0; i--) {
                if (ids[i] == 3 && props[i].length == 1)
                    decoder = delta(decoder, (props[i][0] & 255) + 1);
                else if (ids[i] == 4 && (props[i].length == 0 || props[i].length == 4))
                    decoder =
                            new X86BcjInputStream(
                                    decoder,
                                    props[i].length == 0 ? 0 : XzSupport.bits(props[i], 0, 4));
                else throw new IOException("Unsupported XZ preprocessing filter: " + ids[i]);
            }
            check = new XzSupport.Check(checkId);
            unpacked = 0;
            return true;
        }
        return false;
    }

    private static InputStream delta(InputStream input, int distance) {
        return new FilterInputStream(input) {
            final byte[] history = new byte[256];
            int position;

            public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) < 0 ? -1 : one[0] & 255;
            }

            public int read(byte[] b, int o, int n) throws IOException {
                Objects.checkFromIndexSize(o, n, b.length);
                if (n == 0) return 0;
                int k = in.read(b, o, n);
                for (int i = 0; i < k; i++) {
                    b[o + i] = (byte) (b[o + i] + history[(position - distance) & 255]);
                    history[position++ & 255] = b[o + i];
                }
                return k;
            }
        };
    }

    private void finishBlock() throws IOException {
        long packed = in.position - packedStart;
        if (declaredPacked >= 0 && declaredPacked != packed
                || declaredUnpacked >= 0 && declaredUnpacked != unpacked)
            throw new IOException("XZ block size mismatch");
        for (long i = packed; i % 4 != 0; i++)
            if (ReadSupport.u8(in) != 0) throw new IOException("XZ block padding invalid");
        if (!Arrays.equals(ReadSupport.bytes(in, check.size()), check.finish()))
            throw new IOException("XZ payload check mismatch");
        records.add(
                new long[] {
                    ReadSupport.add(ReadSupport.add(headerSize, packed), check.size()), unpacked
                });
        decoder = null;
    }

    private void index() throws IOException {
        Crc32 crc = new Crc32();
        crc.update(0);
        long start = in.position - 1;
        InputStream checked =
                new InputStream() {
                    public int read() throws IOException {
                        int b = ReadSupport.u8(in);
                        crc.update(b);
                        return b;
                    }
                };
        long count = XzSupport.vli(checked);
        if (count != records.size()) throw new IOException("XZ index block count mismatch");
        for (long[] r : records) {
            ReadSupport.check(null);
            if (XzSupport.vli(checked) != r[0] || XzSupport.vli(checked) != r[1])
                throw new IOException("XZ index record mismatch");
        }
        while ((in.position - start) % 4 != 0)
            if (checked.read() != 0) throw new IOException("XZ index padding invalid");
        if (ReadSupport.le(in, 4) != crc.value()) throw new IOException("XZ index CRC mismatch");
        long size = in.position - start;
        byte[] footer = ReadSupport.bytes(in, 12);
        if (XzSupport.bits(footer, 0, 4) != XzSupport.crc32(footer, 4, 6))
            throw new IOException("XZ footer CRC mismatch");
        if ((XzSupport.bits(footer, 4, 4) + 1) * 4 != size
                || footer[8] != 0
                || (footer[9] & 255) != checkId
                || footer[10] != 'Y'
                || footer[11] != 'Z') throw new IOException("XZ footer/index mismatch");
    }

    @Override
    public int read() throws IOException {
        return read(one, 0, 1) < 0 ? -1 : one[0] & 255;
    }

    @Override
    public int read(byte[] b, int o, int n) throws IOException {
        Objects.checkFromIndexSize(o, n, b.length);
        if (closed) throw new IOException("Stream closed");
        if (n == 0) return 0;
        while (!ended) {
            if (decoder == null && !block()) break;
            int k = decoder.read(b, o, n);
            if (k < 0) {
                finishBlock();
                continue;
            }
            check.update(b, o, k);
            unpacked = ReadSupport.add(unpacked, k);
            if (declaredUnpacked >= 0 && unpacked > declaredUnpacked)
                throw new IOException("XZ output exceeds declared size");
            return k;
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
