package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Validate framed streams once to a private disk spool, then index TAR or expose stream data. */
final class FramedArchiveReader implements ArchiveReader {
    private final Path spool;
    private final boolean owner;
    private final BooleanSupplier cancel;
    private ArchiveReader tar;
    private RandomAccessFile file;
    private List<Entry> entries;

    FramedArchiveReader(
            Path source, boolean tarball, ArchiveEngine.Format format, BooleanSupplier cancel)
            throws IOException {
        this.cancel = cancel;
        owner = true;
        spool = Files.createTempFile("onezip-frame-", ".tmp");
        try {
            String name;
            long size = 0;
            try (InputStream in = open(source, format);
                    OutputStream out = ArchiveIo.output(spool, false)) {
                name = in instanceof GzipInputStream gzip ? gzip.originalName() : null;
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) != -1) {
                    ReadSupport.check(cancel);
                    out.write(b, 0, n);
                    size = ReadSupport.add(size, n);
                }
            }
            if (tarball) {
                tar = new TarArchiveReader(spool, cancel);
                entries = tar.entries();
            } else {
                if (name == null || name.isEmpty()) {
                    name = source.getFileName().toString();
                    name = name.replaceFirst("(?i)\\.(gzip|gz|lz4|xz|bz2|bzip2)$", "");
                    if (name.isEmpty()) name = "data";
                }
                // RFC1952 FNAME is a basename. Discard directory prefixes from foreign producers.
                name = name.replace('\\', '/');
                name = name.substring(name.lastIndexOf('/') + 1);
                if (name.isBlank()
                        || name.equals(".")
                        || name.equals("..")
                        || name.indexOf(':') >= 0) throw new IOException("GZIP 原始檔名無效");
                entries = List.of(new Entry(name, false, size));
                file = new RandomAccessFile(spool.toFile(), "r");
            }
        } catch (IOException | RuntimeException ex) {
            try {
                close();
            } catch (IOException close) {
                ex.addSuppressed(close);
            }
            throw ex;
        }
    }

    private static InputStream open(Path source, ArchiveEngine.Format format) throws IOException {
        InputStream raw = ArchiveIo.input(source);
        try {
            return switch (format) {
                case GZIP, TAR_GZIP -> new GzipInputStream(raw);
                case LZ4, TAR_LZ4 -> new Lz4InputStream(raw);
                case XZ, TAR_XZ -> new XzInputStream(raw);
                case BZIP2, TAR_BZIP2 -> new Bzip2InputStream(raw);
                default -> throw new IOException("Unsupported stream format");
            };
        } catch (IOException | RuntimeException ex) {
            try {
                raw.close();
            } catch (IOException close) {
                ex.addSuppressed(close);
            }
            throw ex;
        }
    }

    private FramedArchiveReader(FramedArchiveReader source) throws IOException {
        spool = source.spool;
        owner = false;
        cancel = source.cancel;
        entries = source.entries;
        if (source.tar != null) tar = source.tar.fork();
        else file = new RandomAccessFile(spool.toFile(), "r");
    }

    public ArchiveReader fork() throws IOException {
        return new FramedArchiveReader(this);
    }

    public List<Entry> entries() {
        return entries;
    }

    public InputStream open(Entry e) throws IOException {
        if (tar != null) return tar.open(e);
        if (e != entries.getFirst()) throw new IOException("Unknown GZIP entry");
        return ReadSupport.verified(
                ReadSupport.region(file, 0, e.size()), e.size(), null, false, cancel);
    }

    public void close() throws IOException {
        try {
            if (tar != null) tar.close();
            if (file != null) file.close();
        } finally {
            if (owner) Files.deleteIfExists(spool);
        }
    }
}
