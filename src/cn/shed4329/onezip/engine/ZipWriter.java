package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** ZIP32 container writer with our DEFLATE, CRC32 and AE-2 framing. */
final class ZipWriter implements AutoCloseable {
    private record Entry(
            byte[] name,
            byte[] extra,
            boolean directory,
            int flags,
            int method,
            long crc,
            long size,
            long packed,
            long offset,
            int time,
            int date) {}

    private final OutputStream out;
    private final List<Entry> entries = new ArrayList<>();
    private final int level;
    private final char[] password;
    private final DeflateEncoder.Workspace workspace;
    private final ZipCompressionPool pool;
    private long position;
    private boolean finished;

    ZipWriter(Path path, int level, char[] password) throws IOException {
        this(path, level, password, (ZipCompressionPool) null);
    }

    ZipWriter(Path path, int level, char[] password, ZipCompressionPool pool) throws IOException {
        this(ArchiveIo.output(path, true), level, password, new DeflateEncoder.Workspace(), pool);
    }

    ZipWriter(Path path, int level, char[] password, DeflateEncoder.Workspace workspace)
            throws IOException {
        this(ArchiveIo.output(path, true), level, password, workspace, null);
    }

    ZipWriter(OutputStream raw, int level, char[] password, DeflateEncoder.Workspace workspace) {
        this(raw, level, password, workspace, null);
    }

    private ZipWriter(
            OutputStream raw,
            int level,
            char[] password,
            DeflateEncoder.Workspace workspace,
            ZipCompressionPool pool) {
        this.workspace = workspace;
        this.pool = pool;
        out =
                new OutputStream() {
                    public void write(int b) throws IOException {
                        raw.write(b);
                        ++position;
                    }

                    public void write(byte[] b, int o, int n) throws IOException {
                        raw.write(b, o, n);
                        position += n;
                    }

                    public void flush() throws IOException {
                        raw.flush();
                    }

                    public void close() throws IOException {
                        raw.close();
                    }
                };
        this.level = level;
        this.password = password == null ? new char[0] : password.clone();
    }

    private void emit(BinaryWriter b) throws IOException {
        b.to(out);
    }

    /** A closed one-entry local record + payload + descriptor, without a central directory. */
    record Fragment(ChunkedOutputStream bytes, Entry entry) {}

    Fragment fragment(ChunkedOutputStream bytes) throws IOException {
        if (entries.size() != 1 || finished)
            throw new IOException("Expected one completed ZIP entry");
        bytes.close();
        return new Fragment(bytes, entries.getFirst());
    }

    void append(Fragment fragment) throws IOException {
        if (finished || entries.size() >= 65534)
            throw new IOException("ZIP entry count exceeds ZIP32 limit");
        Entry e = fragment.entry;
        long size = fragment.bytes.size(), offset = position;
        if (size >= 0xffffffffL - position) throw new IOException("ZIP32 archive too large");
        fragment.bytes.writeTo(out);
        entries.add(
                new Entry(
                        e.name,
                        e.extra,
                        e.directory,
                        e.flags,
                        e.method,
                        e.crc,
                        e.size,
                        e.packed,
                        offset,
                        e.time,
                        e.date));
    }

    OutputStream begin(String name, boolean directory, long size) throws IOException {
        return begin(name, directory, size, null);
    }

    OutputStream begin(String name, boolean directory, long size, ArchiveTimes times)
            throws IOException {
        if (entries.size() >= 65534 || size >= 0xffffffffL || position >= 0xffffffffL)
            throw new IOException("此 Java ZIP 寫入器目前支援 ZIP32（單檔小於 4 GiB、最多 65534 個項目）");
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65535) throw new IOException("ZIP entry name too long");
        boolean encrypted = !directory && password.length != 0;
        int actual = directory || level == 0 ? 0 : 8,
                method = encrypted ? 99 : actual,
                flags = 0x808 | (encrypted ? 1 : 0);
        BinaryWriter extra = new BinaryWriter();
        var localTime =
                times == null ? java.time.LocalDateTime.of(1980, 1, 1, 0, 0) : times.dosLocal();
        int dosTime =
                (localTime.getHour() << 11)
                        | (localTime.getMinute() << 5)
                        | (localTime.getSecond() / 2);
        int dosDate =
                ((localTime.getYear() - 1980) << 9)
                        | (localTime.getMonthValue() << 5)
                        | localTime.getDayOfMonth();
        if (times != null) times.appendZipExtra(extra);
        if (encrypted) {
            extra.le(0x9901, 2);
            extra.le(7, 2);
            extra.le(2, 2);
            extra.write('A');
            extra.write('E');
            extra.write(3);
            extra.le(actual, 2);
        }
        byte[] ex = extra.toByteArray();
        long offset = position;
        BinaryWriter local = new BinaryWriter();
        local.le(0x04034b50, 4);
        local.le(20, 2);
        local.le(flags, 2);
        local.le(method, 2);
        local.le(dosTime, 2);
        local.le(dosDate, 2);
        local.le(0, 12);
        local.le(bytes.length, 2);
        local.le(ex.length, 2);
        local.append(bytes);
        local.append(ex);
        emit(local);
        return new OutputStream() {
            final long start = position;
            final Crc32 crc = new Crc32();
            final ZipAesOutputStream aes = encrypted ? new ZipAesOutputStream(out, password) : null;
            final OutputStream sink = aes == null ? out : aes;
            final DeflateOutputStream encoder =
                    actual == 8
                            ? new DeflateOutputStream(sink, level, pool, size, workspace)
                            : null;
            long written;
            boolean closed;

            public void write(int b) throws IOException {
                if (closed) throw new IOException("ZIP entry closed");
                if (encoder != null) encoder.write(b);
                else sink.write(b);
                crc.update(b);
                written++;
            }

            public void write(byte[] b, int o, int n) throws IOException {
                if (closed) throw new IOException("ZIP entry closed");
                if (encoder != null) encoder.write(b, o, n);
                else sink.write(b, o, n);
                crc.update(b, o, n);
                written += n;
            }

            public void close() throws IOException {
                if (closed) return;
                if (encoder != null) encoder.finish();
                if (aes != null) aes.finish();
                long packed = position - start;
                if (written != size || packed >= 0xffffffffL)
                    throw new IOException("來源大小已改變或超過 ZIP32 限制: " + name);
                long checksum = encrypted ? 0 : crc.value();
                BinaryWriter dd = new BinaryWriter();
                dd.le(0x08074b50, 4);
                dd.le(checksum, 4);
                dd.le(packed, 4);
                dd.le(written, 4);
                emit(dd);
                entries.add(
                        new Entry(
                                bytes, ex, directory, flags, method, checksum, written, packed,
                                offset, dosTime, dosDate));
                closed = true;
            }
        };
    }

    void finish() throws IOException {
        if (finished) return;
        long start = position;
        for (Entry e : entries) {
            BinaryWriter c = new BinaryWriter();
            c.le(0x02014b50, 4);
            c.le(20, 2);
            c.le(20, 2);
            c.le(e.flags, 2);
            c.le(e.method, 2);
            c.le(e.time, 2);
            c.le(e.date, 2);
            c.le(e.crc, 4);
            c.le(e.packed, 4);
            c.le(e.size, 4);
            c.le(e.name.length, 2);
            c.le(e.extra.length, 2);
            c.le(0, 6);
            c.le(e.directory ? 0x10 : 0, 4);
            c.le(e.offset, 4);
            c.append(e.name);
            c.append(e.extra);
            emit(c);
        }
        long size = position - start;
        if (position >= 0xffffffffL) throw new IOException("ZIP32 archive too large");
        BinaryWriter end = new BinaryWriter();
        end.le(0x06054b50, 4);
        end.le(0, 4);
        end.le(entries.size(), 2);
        end.le(entries.size(), 2);
        end.le(size, 4);
        end.le(start, 4);
        end.le(0, 2);
        emit(end);
        finished = true;
    }

    public void close() throws IOException {
        Arrays.fill(password, '\0');
        out.close();
    }
}
