package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/**
 * File-oriented facade for the independent Java archive encoders and decoders.
 *
 * <p>Each operation owns its readers, bounded workers and temporary output. Compression publishes
 * its private staging file only after successful encoding. Extraction refuses existing files and
 * removes only paths created by the failing operation; existing directories and files are retained.
 *
 * <p>Callbacks may execute on codec workers and must be thread-safe. A cancellation supplier is
 * polled throughout planning and transfer; interrupting the calling thread also requests abort. The
 * caller owns supplied password arrays and must clear them when no longer needed.
 */
public final class ArchiveEngine {
    /** Creates a stateless facade; each operation owns its resources independently. */
    public ArchiveEngine() {}

    /** Writable archive/container combinations; RAR variants are read-only. */
    public enum Format {
        ZIP,
        SEVEN_Z,
        TAR,
        GZIP,
        TAR_GZIP,
        LZ4,
        TAR_LZ4,
        XZ,
        TAR_XZ,
        BZIP2,
        TAR_BZIP2
    }

    /** Returns whether a writable format contains a TAR stream. */
    public static boolean tarFormat(Format format) {
        return format == Format.TAR
                || format == Format.TAR_GZIP
                || format == Format.TAR_LZ4
                || format == Format.TAR_XZ
                || format == Format.TAR_BZIP2;
    }

    /** Returns whether the format requires exactly one regular input file. */
    public static boolean singleStreamFormat(Format format) {
        return format == Format.GZIP
                || format == Format.LZ4
                || format == Format.XZ
                || format == Format.BZIP2;
    }

    /** Resolves a case-insensitive output suffix, or returns {@code null} when not writable. */
    public static Format outputFormat(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".tar.xz") || lower.endsWith(".txz")) return Format.TAR_XZ;
        if (lower.endsWith(".tar.bz2")
                || lower.endsWith(".tar.bzip2")
                || lower.endsWith(".tbz2")
                || lower.endsWith(".tbz")) return Format.TAR_BZIP2;
        if (lower.endsWith(".xz")) return Format.XZ;
        if (lower.endsWith(".bz2") || lower.endsWith(".bzip2")) return Format.BZIP2;
        if (lower.endsWith(".tar.lz4")) return Format.TAR_LZ4;
        if (lower.endsWith(".lz4")) return Format.LZ4;
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tar.gzip") || lower.endsWith(".tgz"))
            return Format.TAR_GZIP;
        if (lower.endsWith(".tar")) return Format.TAR;
        if (lower.endsWith(".gz") || lower.endsWith(".gzip")) return Format.GZIP;
        if (lower.endsWith(".zip")) return Format.ZIP;
        if (lower.endsWith(".7z")) return Format.SEVEN_Z;
        return null;
    }

    /** Tests supported input suffixes; this does not authenticate or validate the file contents. */
    public static boolean readableName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return outputFormat(n) != null || n.endsWith(".rar") || n.endsWith(".rar5");
    }

    /**
     * An archive-relative entry without resolving it against the host filesystem.
     *
     * @param name original path stored in the container
     * @param directory whether this entry represents a directory
     * @param size uncompressed size in bytes
     */
    public record ArchiveItem(String name, boolean directory, long size) {}

    /** Optional container metadata; unavailable text is empty and unavailable packed size is -1. */
    public record EntryDetails(
            ArchiveItem item,
            long packedSize,
            java.time.LocalDateTime modified,
            String method,
            String encryption,
            String crc,
            String comment) {}

    /** Receives raw-byte progress and the original file-name plan; callbacks can be concurrent. */
    @FunctionalInterface
    public interface Progress {
        /** Reports progress; compression may include input buffered but not yet published. */
        void update(long completed, long total, String file);

        /** Supplies the immutable planned file names before processing begins. */
        default void files(List<String> names) {}
    }

    private record Item(
            Path source, String name, boolean directory, long size, ArchiveTimes times) {}

    private static final Progress SILENT = (a, b, c) -> {};

    /**
     * Creates an archive using automatic bounded workers and no global description.
     *
     * @see #compress(List, Path, Format, int, char[], BooleanSupplier, Progress, int, String)
     */
    public Path compress(
            List<Path> sources,
            Path output,
            Format format,
            int level,
            char[] password,
            BooleanSupplier cancelled,
            Progress progress)
            throws IOException {
        return compress(sources, output, format, level, password, cancelled, progress, 0);
    }

    /**
     * Creates an archive using the requested worker upper bound and no global description.
     *
     * @see #compress(List, Path, Format, int, char[], BooleanSupplier, Progress, int, String)
     */
    public Path compress(
            List<Path> sources,
            Path output,
            Format format,
            int level,
            char[] password,
            BooleanSupplier cancelled,
            Progress progress,
            int threads)
            throws IOException {
        return compress(sources, output, format, level, password, cancelled, progress, threads, "");
    }

    /**
     * Plans inputs, encodes to a private sibling staging file and publishes on success.
     *
     * @param sources input files/directories; archived names are derived without reading GUI
     *     settings
     * @param output new archive path, which must not already exist
     * @param format writable container/codec combination
     * @param level -1 for the codec default, or 0 through 9; support varies by format
     * @param password caller-owned password, null/empty for no encryption; encrypted writing is ZIP
     *     only
     * @param cancelled optional cooperative cancellation predicate
     * @param progress optional thread-safe progress receiver
     * @param threads 0 for an automatic upper bound, or 1 through 64
     * @param comment UTF-8 global ZIP description; must be empty for other formats
     * @return normalized absolute path of the published archive
     * @throws IOException when validation, encoding or publication fails
     * @throws IllegalArgumentException for an invalid level or worker count
     */
    public Path compress(
            List<Path> sources,
            Path output,
            Format format,
            int level,
            char[] password,
            BooleanSupplier cancelled,
            Progress progress,
            int threads,
            String comment)
            throws IOException {
        Objects.requireNonNull(sources);
        Objects.requireNonNull(format);
        Objects.requireNonNull(comment);
        if (!comment.isEmpty() && format != Format.ZIP) throw new IOException("目前仅支持 ZIP 压缩包注释");
        if (comment.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65535)
            throw new IOException("ZIP 注释超过 65535 UTF-8 字节");
        if (threads < 0 || threads > 64) throw new IllegalArgumentException("執行緒數應為0–64");
        if (level < -1 || level > 9) throw new IllegalArgumentException("壓縮等級應為 -1 或 0–9");
        if (format != Format.ZIP && password != null && password.length != 0)
            throw new IOException("此格式不提供密碼寫入，請使用 ZIP AES-256 或取消密碼。");
        output = output.toAbsolutePath().normalize();
        check(cancelled);
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS))
            throw new FileAlreadyExistsException(output.toString());
        List<Item> items = plan(sources, output, cancelled);
        if (singleStreamFormat(format) && (items.size() != 1 || items.getFirst().directory))
            throw new IOException("單檔串流格式僅接受一個一般檔案；多檔案或資料夾請使用相應 TAR 格式。");
        long total = items.stream().mapToLong(Item::size).reduce(0, Math::addExact);
        Files.createDirectories(output.getParent());
        Path staging = Files.createTempFile(output.getParent(), ".onezip-", ".part");
        Progress observer = progress == null ? SILENT : progress;
        long[] completed = {0};
        boolean published = false;
        try {
            observer.files(items.stream().filter(i -> !i.directory).map(Item::name).toList());
            if (tarFormat(format)) {
                long tarSize = 1024;
                for (Item item : items)
                    tarSize = ReadSupport.add(tarSize, TarWriter.entryBytes(item.name, item.size));
                try (OutputStream raw = ArchiveIo.output(staging, true);
                        OutputStream framed =
                                format == Format.TAR
                                        ? raw
                                        : frameOutput(raw, format, level, threads, tarSize, null);
                        TarWriter writer = new TarWriter(framed)) {
                    for (Item item : items) {
                        check(cancelled);
                        observer.update(completed[0], total, item.name);
                        try (OutputStream entry =
                                writer.begin(
                                        item.name,
                                        item.directory,
                                        item.size,
                                        Files.getLastModifiedTime(
                                                                item.source,
                                                                LinkOption.NOFOLLOW_LINKS)
                                                        .toMillis()
                                                / 1000)) {
                            copySource(item, entry, completed, total, cancelled, observer);
                        }
                    }
                    check(cancelled);
                    writer.finish();
                }
            } else if (singleStreamFormat(format)) {
                Item item = items.getFirst();
                try (OutputStream raw = ArchiveIo.output(staging, true);
                        OutputStream writer =
                                frameOutput(raw, format, level, threads, item.size, item.name)) {
                    copySource(item, writer, completed, total, cancelled, observer);
                    check(cancelled);
                }
            } else if (format == Format.SEVEN_Z) {
                try (SevenZWriter writer = new SevenZWriter(staging, level, threads)) {
                    for (Item item : items) {
                        check(cancelled);
                        observer.update(completed[0], total, item.name);
                        try (OutputStream entry =
                                writer.begin(item.name, item.directory, item.size)) {
                            copySource(item, entry, completed, total, cancelled, observer);
                        }
                    }
                    check(cancelled);
                    writer.finish();
                }
            } else {
                int jobs = 0;
                for (Item item : items)
                    jobs =
                            (int)
                                    Math.min(
                                            64L,
                                            jobs
                                                    + (item.size
                                                                    >= ParallelDeflateOutputStream
                                                                            .LARGE
                                                            ? Math.ceilDiv(
                                                                    item.size,
                                                                    ParallelDeflateOutputStream
                                                                            .SEGMENT)
                                                            : 1));
                int workers = ArchiveWorkers.count(threads, 48L * 1024 * 1024, jobs);
                try (ZipCompressionPool pool =
                                workers > 1 ? new ZipCompressionPool(workers) : null;
                        ZipWriter writer = new ZipWriter(staging, level, password, pool)) {
                    if (workers == 1)
                        for (Item item : items) {
                            check(cancelled);
                            observer.update(completed[0], total, item.name);
                            try (OutputStream entry =
                                    writer.begin(
                                            item.name, item.directory, item.size, item.times)) {
                                copySource(item, entry, completed, total, cancelled, observer);
                            }
                        }
                    else
                        compressZip(
                                items, writer, pool, level, password, cancelled, observer,
                                completed, total);
                    check(cancelled);
                    writer.finish();
                }
            }
            check(cancelled);
            if (!comment.isEmpty()) ZipComments.write(staging, comment);
            check(cancelled);
            Files.move(staging, output);
            published = true;
            observer.update(total, total, "完成");
            return output;
        } finally {
            if (!published) Files.deleteIfExists(staging);
        }
    }

    private static OutputStream frameOutput(
            OutputStream out, Format format, int level, int threads, long size, String name)
            throws IOException {
        return switch (format) {
            case GZIP, TAR_GZIP -> new GzipOutputStream(out, level, threads, size, name);
            case LZ4, TAR_LZ4 -> new Lz4OutputStream(out, level, size);
            case XZ, TAR_XZ -> new XzOutputStream(out, level, size);
            case BZIP2, TAR_BZIP2 -> new Bzip2OutputStream(out, level, size);
            default -> throw new IllegalArgumentException("Not a framed stream format");
        };
    }

    private static void compressZip(
            List<Item> items,
            ZipWriter writer,
            ZipCompressionPool pool,
            int level,
            char[] password,
            BooleanSupplier cancel,
            Progress observer,
            long[] completed,
            long total)
            throws IOException {
        int workers = pool.workers();
        Progress locked =
                (done, size, name) -> {
                    synchronized (completed) {
                        observer.update(done, size, name);
                    }
                };
        ArrayDeque<Future<ZipWriter.Fragment>> pending = new ArrayDeque<>();
        try {
            for (Item item : items) {
                check(cancel);
                if (!item.directory && item.size >= ParallelDeflateOutputStream.LARGE) {
                    // The large-file stream uses this same worker pool: drain file jobs first.
                    while (!pending.isEmpty()) {
                        check(cancel);
                        writer.append(ZipCompressionPool.await(pending.removeFirst()));
                    }
                    observer.update(completed[0], total, item.name);
                    try (OutputStream entry =
                            writer.begin(item.name, false, item.size, item.times)) {
                        copySource(item, entry, completed, total, cancel, observer);
                    }
                    continue;
                }
                while (!pending.isEmpty() && pending.getFirst().isDone()) {
                    check(cancel);
                    writer.append(ZipCompressionPool.await(pending.removeFirst()));
                }
                if (item.directory && pending.isEmpty()) {
                    writer.begin(item.name, true, 0, item.times).close();
                    continue;
                }
                if (pending.size() >= workers) {
                    check(cancel);
                    writer.append(ZipCompressionPool.await(pending.removeFirst()));
                }
                // Files below the large-file cutoff have a strict per-job memory bound.
                // Chunk replay avoids both disk spooling and ByteArrayOutputStream.toByteArray().
                pending.addLast(
                        pool.submit(
                                () -> {
                                    check(cancel);
                                    ChunkedOutputStream bytes =
                                            new ChunkedOutputStream(
                                                    Math.toIntExact(item.size + 131072),
                                                    (int)
                                                            Math.min(
                                                                    65536,
                                                                    Math.max(
                                                                            1024,
                                                                            item.size + 256)));
                                    try (ZipWriter local =
                                            new ZipWriter(
                                                    bytes, level, password, pool.workspace())) {
                                        try (OutputStream entry =
                                                local.begin(
                                                        item.name,
                                                        item.directory,
                                                        item.size,
                                                        item.times)) {
                                            copySource(
                                                    item, entry, completed, total, cancel, locked);
                                        }
                                        return local.fragment(bytes);
                                    }
                                }));
            }
            while (!pending.isEmpty()) {
                check(cancel);
                writer.append(ZipCompressionPool.await(pending.removeFirst()));
            }
        } catch (IOException | RuntimeException | Error ex) {
            pool.close();
            throw ex;
        }
    }

    private static List<Item> plan(List<Path> sources, Path output, BooleanSupplier cancel)
            throws IOException {
        if (sources.isEmpty()) throw new IOException("請先選擇檔案或資料夾");
        List<Item> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Path selected : sources) {
            Path root = selected.toAbsolutePath().normalize();
            if (root.getFileName() == null) throw new IOException("請選擇具名的資料夾");
            if (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) && output.startsWith(root))
                throw new IOException("壓縮檔不可存放在來源資料夾內");
            if (output.equals(root)) throw new IOException("輸出不可覆蓋來源檔案");
            Files.walkFileTree(
                    root,
                    new SimpleFileVisitor<>() {
                        private void add(Path path, BasicFileAttributes attrs) throws IOException {
                            check(cancel);
                            if (attrs.isSymbolicLink()
                                    || attrs.isOther()
                                    || (!attrs.isDirectory() && !attrs.isRegularFile()))
                                throw new IOException("不支援符號連結或特殊檔案: " + path);
                            String name =
                                    root.getFileName()
                                            + (root.equals(path)
                                                    ? ""
                                                    : "/"
                                                            + root.relativize(path)
                                                                    .toString()
                                                                    .replace('\\', '/'));
                            if (name.indexOf('\0') >= 0 || name.indexOf('\\') >= 0)
                                throw new IOException("不支援的檔名: " + name);
                            if (!names.add(name.toLowerCase(Locale.ROOT)))
                                throw new IOException("壓縮包內有重複名稱: " + name);
                            result.add(
                                    new Item(
                                            path,
                                            name + (attrs.isDirectory() ? "/" : ""),
                                            attrs.isDirectory(),
                                            attrs.isDirectory() ? 0 : attrs.size(),
                                            ArchiveTimes.of(attrs)));
                        }

                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path path, BasicFileAttributes attrs) throws IOException {
                            add(path, attrs);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs)
                                throws IOException {
                            add(path, attrs);
                            return FileVisitResult.CONTINUE;
                        }
                    });
        }
        return result;
    }

    private static void copySource(
            Item item,
            OutputStream out,
            long[] done,
            long total,
            BooleanSupplier cancel,
            Progress observer)
            throws IOException {
        if (item.directory) return;
        if (Files.isSymbolicLink(item.source)) throw new IOException("來源已變成符號連結: " + item.source);
        try (InputStream in = ArchiveIo.input(item.source, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) {
                check(cancel);
                out.write(buffer, 0, n);
                synchronized (done) {
                    done[0] += n;
                    observer.update(done[0], total, item.name);
                }
            }
        }
    }

    /**
     * Extracts with automatic bounded workers and the default legacy ZIP filename charset.
     *
     * @see #extract(Path, Path, char[], BooleanSupplier, Progress, int, java.nio.charset.Charset)
     */
    public Path extract(
            Path archive,
            Path destination,
            char[] password,
            BooleanSupplier cancelled,
            Progress progress)
            throws IOException {
        return extract(archive, destination, password, cancelled, progress, 0);
    }

    /**
     * Extracts with an explicit worker upper bound and default legacy ZIP filename charset.
     *
     * @see #extract(Path, Path, char[], BooleanSupplier, Progress, int, java.nio.charset.Charset)
     */
    public Path extract(
            Path archive,
            Path destination,
            char[] password,
            BooleanSupplier cancelled,
            Progress progress,
            int threads)
            throws IOException {
        return extract(
                archive,
                destination,
                password,
                cancelled,
                progress,
                threads,
                java.nio.charset.Charset.forName("IBM437"));
    }

    /**
     * Extracts into an explicit destination without overwriting existing files.
     *
     * <p>Archive-relative paths and existing parents are validated before writing. On failure or
     * cancellation only files/directories created by this operation are removed. Existing content
     * is not restored or modified because replacement is never permitted.
     *
     * @param archive input container
     * @param destination explicit output root; smart-layout selection is a separate operation
     * @param password caller-owned password, null/empty when unencrypted
     * @param cancelled optional cooperative cancellation predicate
     * @param progress optional thread-safe progress receiver
     * @param threads 0 for automatic bounded workers, or 1 through 64
     * @param charset legacy ZIP filename charset; marked UTF-8/Unicode metadata takes precedence
     * @return normalized absolute output root
     * @throws IOException on invalid paths, collisions, password/checksum errors or I/O failure
     */
    public Path extract(
            Path archive,
            Path destination,
            char[] password,
            BooleanSupplier cancelled,
            Progress progress,
            int threads,
            java.nio.charset.Charset charset)
            throws IOException {
        if (threads < 0 || threads > 64) throw new IllegalArgumentException("執行緒數應為0–64");
        Path root = destination.toAbsolutePath().normalize();
        Progress observer = progress == null ? SILENT : progress;
        List<Path> created = new ArrayList<>();
        try {
            check(cancelled);
            verifyParents(root);
            createDirectories(root, created);
            extractArchive(archive, root, password, cancelled, observer, created, threads, charset);
            return root;
        } catch (IOException | RuntimeException ex) {
            Collections.reverse(created);
            for (Path path : created)
                try {
                    Files.deleteIfExists(path);
                } catch (IOException cleanup) {
                    ex.addSuppressed(cleanup);
                }
            throw ex;
        }
    }

    /** Container metadata; GZIP streams must be decoded to index their contents. */
    public List<ArchiveItem> list(Path archive, char[] password, BooleanSupplier cancelled)
            throws IOException {
        try (ArchiveReader reader = openReader(archive, password, cancelled)) {
            List<ArchiveItem> result = new ArrayList<>();
            for (ArchiveReader.Entry entry : reader.entries()) {
                check(cancelled);
                result.add(new ArchiveItem(entry.name(), entry.directory(), entry.size()));
            }
            return List.copyOf(result);
        }
    }

    /**
     * Listing with optional metadata, without decoding ZIP payloads. Unknown values stay unknown.
     */
    public List<EntryDetails> listDetails(Path archive, char[] password, BooleanSupplier cancelled)
            throws IOException {
        return listDetails(
                archive, password, cancelled, java.nio.charset.Charset.forName("IBM437"));
    }

    /**
     * Parsed entries and the container description; this is metadata, not a payload integrity test.
     */
    public record ArchiveListing(List<EntryDetails> entries, String comment) {}

    public List<EntryDetails> listDetails(
            Path archive,
            char[] password,
            BooleanSupplier cancelled,
            java.nio.charset.Charset charset)
            throws IOException {
        return listArchive(archive, password, cancelled, charset).entries();
    }

    public ArchiveListing listArchive(
            Path archive,
            char[] password,
            BooleanSupplier cancelled,
            java.nio.charset.Charset charset)
            throws IOException {
        try (ArchiveReader reader = openReader(archive, password, cancelled, charset)) {
            List<EntryDetails> result = new ArrayList<>();
            for (ArchiveReader.Entry entry : reader.entries()) {
                check(cancelled);
                ArchiveItem item = new ArchiveItem(entry.name(), entry.directory(), entry.size());
                result.add(
                        reader instanceof ZipArchiveReader zip
                                ? zip.details(entry, item)
                                : new EntryDetails(item, -1, null, "", "", "", ""));
            }
            return new ArchiveListing(
                    List.copyOf(result),
                    reader instanceof ZipArchiveReader zip ? zip.comment() : "");
        }
    }

    private static ArchiveReader openReader(
            Path source,
            char[] password,
            BooleanSupplier cancelled,
            java.nio.charset.Charset charset)
            throws IOException {
        if (source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
            return new ZipArchiveReader(source, password, cancelled, charset);
        return openReader(source, password, cancelled);
    }

    /** Fully decodes and validates every stream without creating any output files. */
    public void test(Path archive, char[] password, BooleanSupplier cancelled, Progress progress)
            throws IOException {
        Progress observer = progress == null ? SILENT : progress;
        try (ArchiveReader reader = openReader(archive, password, cancelled)) {
            observer.files(
                    reader.entries().stream()
                            .filter(e -> !e.directory())
                            .map(ArchiveReader.Entry::name)
                            .toList());
            long total = 0;
            for (ArchiveReader.Entry entry : reader.entries()) {
                check(cancelled);
                total = ReadSupport.add(total, entry.size());
            }
            long[] done = {0};
            for (ArchiveReader.Entry entry : reader.entries()) {
                check(cancelled);
                observer.update(done[0], total, entry.name());
                try (InputStream in = reader.open(entry)) {
                    transfer(
                            in,
                            OutputStream.nullOutputStream(),
                            entry.name(),
                            done,
                            total,
                            cancelled,
                            observer);
                }
            }
            observer.update(total, total, "完成");
        }
    }

    private static ArchiveReader openReader(Path source, char[] password, BooleanSupplier cancel)
            throws IOException {
        check(cancel);
        if (source.getFileName() == null) throw new IOException("請指定 ZIP、7z 或 RAR 檔案。");
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) return new ZipArchiveReader(source, password, cancel);
        if (name.endsWith(".7z")) return new SevenZArchiveReader(source, password, cancel);
        if (name.endsWith(".rar") || name.endsWith(".rar5"))
            return new RarArchiveReader(source, password, cancel);
        Format format = outputFormat(name);
        if (format == Format.TAR) return new TarArchiveReader(source, cancel);
        if (singleStreamFormat(format) || tarFormat(format))
            return new FramedArchiveReader(source, tarFormat(format), format, cancel);
        throw new IOException("純 Java 模式支援 ZIP、7z、RAR／RAR5、TAR、GZIP、LZ4、XZ、BZIP2 及其 TAR 格式。");
    }

    private static void extractArchive(
            Path source,
            Path root,
            char[] password,
            BooleanSupplier cancel,
            Progress observer,
            List<Path> created,
            int threads,
            java.nio.charset.Charset charset)
            throws IOException {
        try (ArchiveReader reader = openReader(source, password, cancel, charset)) {
            observer.files(
                    reader.entries().stream()
                            .filter(e -> !e.directory())
                            .map(ArchiveReader.Entry::name)
                            .toList());
            Set<Path> seen = new HashSet<>(),
                    preflightParents = new HashSet<>(),
                    absentParents = new HashSet<>();
            long total = 0;
            for (ArchiveReader.Entry e : reader.entries()) {
                check(cancel);
                preflight(root, e.name(), e.directory(), seen, preflightParents, absentParents);
                if (!e.directory()) total = ReadSupport.add(total, e.size());
            }
            if (reader instanceof ZipArchiveReader
                    || reader instanceof TarArchiveReader
                    || reader instanceof FramedArchiveReader) {
                int zipWorkers =
                        ArchiveWorkers.count(
                                threads, reader.workerMemory(), reader.entries().size());
                if (zipWorkers > 1) {
                    extractParallelZip(reader, root, cancel, observer, created, total, zipWorkers);
                    restoreTimes(reader, root, created, cancel);
                    return;
                }
            }
            List<List<ArchiveReader.Entry>> groups = reader.groups();
            int smallFiles =
                    (int)
                            reader.entries().stream()
                                    .filter(e -> !e.directory() && e.size() <= 65536)
                                    .count();
            int pipeline = ArchiveWorkers.count(threads, reader.workerMemory(), smallFiles + 1);
            if (reader instanceof SevenZArchiveReader
                    && pipeline > 1
                    && smallFiles >= 8
                    && groups.stream().filter(g -> g.stream().anyMatch(e -> e.size() > 0)).count()
                            == 1) {
                extractPipelined(reader, root, cancel, observer, created, total, pipeline - 1);
                return;
            }
            int workers = ArchiveWorkers.count(threads, reader.workerMemory(), groups.size());
            if (workers > 1) {
                long size = total;
                long[] done = {0};
                ArchiveWorkers.run(
                        workers,
                        groups.size(),
                        "onezip-extract-",
                        index -> {
                            try (ArchiveReader cursor = reader.fork()) {
                                for (ArchiveReader.Entry entry : groups.get(index)) {
                                    synchronized (done) {
                                        observer.update(done[0], size, entry.name());
                                    }
                                    check(cancel);
                                    Path target = safePath(root, entry.name());
                                    OutputStream output;
                                    synchronized (created) {
                                        createDirectories(
                                                entry.directory() ? target : target.getParent(),
                                                created);
                                        output =
                                                entry.directory()
                                                        ? OutputStream.nullOutputStream()
                                                        : createFile(target, created);
                                    }
                                    try (OutputStream out = output;
                                            InputStream in = cursor.open(entry)) {
                                        if (entry.directory()) {
                                            if (in.read() != -1) throw new IOException("資料夾項目含資料");
                                        } else
                                            transfer(
                                                    in,
                                                    out,
                                                    entry.name(),
                                                    done,
                                                    size,
                                                    cancel,
                                                    observer);
                                    }
                                }
                            }
                        });
                observer.update(total, total, "完成");
                return;
            }
            long[] done = {0};
            byte[] transferBuffer = new byte[65536];
            for (ArchiveReader.Entry e : reader.entries()) {
                check(cancel);
                Path target = safePath(root, e.name());
                observer.update(done[0], total, e.name());
                if (e.directory()) {
                    try (InputStream in = reader.open(e)) {
                        if (in.read() != -1) throw new IOException("資料夾項目含資料");
                    }
                    createDirectories(target, created);
                    continue;
                }
                createDirectories(target.getParent(), created);
                try (InputStream in = reader.open(e);
                        OutputStream out = createFile(target, created)) {
                    transfer(in, out, e.name(), done, total, cancel, observer, transferBuffer);
                }
                restoreFileTime(reader, e, target, cancel);
            }
            restoreTimes(reader, root, created, cancel);
            observer.update(total, total, "完成");
        }
    }

    private static void restoreFileTime(
            ArchiveReader reader, ArchiveReader.Entry entry, Path target, BooleanSupplier cancel)
            throws IOException {
        var modified = reader.modifiedTime(entry);
        if (modified != null) {
            check(cancel);
            Files.setAttribute(target, "lastModifiedTime", modified, LinkOption.NOFOLLOW_LINKS);
        }
    }

    /** Files are restored by their own worker; only new directories wait for all children. */
    private static void restoreTimes(
            ArchiveReader reader, Path root, List<Path> created, BooleanSupplier cancel)
            throws IOException {
        if (!(reader instanceof ZipArchiveReader)) return;
        var directories = new HashMap<Path, java.nio.file.attribute.FileTime>();
        for (var entry : reader.entries()) {
            check(cancel);
            if (!entry.directory()) continue;
            var modified = reader.modifiedTime(entry);
            if (modified == null) continue;
            Path target = safePath(root, entry.name());
            directories.put(target, modified);
        }
        for (int i = created.size() - 1; i >= 0; i--) {
            check(cancel);
            Path path = created.get(i);
            var modified = directories.get(path);
            if (modified != null)
                Files.setAttribute(path, "lastModifiedTime", modified, LinkOption.NOFOLLOW_LINKS);
        }
    }

    private static final class ReaderCopies implements AutoCloseable {
        final ArchiveReader source;
        final ThreadLocal<ArchiveReader> local = new ThreadLocal<>();
        final Queue<ArchiveReader> opened = new ConcurrentLinkedQueue<>();

        ReaderCopies(ArchiveReader source) {
            this.source = source;
        }

        ArchiveReader get() throws IOException {
            ArchiveReader reader = local.get();
            if (reader == null) {
                reader = source.fork();
                opened.add(reader);
                local.set(reader);
            }
            return reader;
        }

        public void close() throws IOException {
            IOException failure = null;
            for (ArchiveReader reader : opened)
                try {
                    reader.close();
                } catch (IOException ex) {
                    if (failure == null) failure = ex;
                    else failure.addSuppressed(ex);
                }
            if (failure != null) throw failure;
        }
    }

    private static void extractParallelZip(
            ArchiveReader reader,
            Path root,
            BooleanSupplier cancel,
            Progress observer,
            List<Path> created,
            long total,
            int workers)
            throws IOException {
        Set<Path> directories = new LinkedHashSet<>();
        for (ArchiveReader.Entry entry : reader.entries()) {
            check(cancel);
            Path target = safePath(root, entry.name());
            directories.add(entry.directory() ? target : target.getParent());
        }
        for (Path directory : directories) {
            check(cancel);
            createDirectories(directory, created);
        }
        long[] done = {0};
        try (ReaderCopies copies = new ReaderCopies(reader)) {
            if (!reader.entries().isEmpty())
                observer.update(0, total, reader.entries().getFirst().name());
            check(cancel);
            ArchiveWorkers.run(
                    workers,
                    reader.entries().size(),
                    "onezip-extract-",
                    index -> {
                        check(cancel);
                        ArchiveReader.Entry entry = reader.entries().get(index);
                        ArchiveReader cursor = copies.get();
                        Path target = safePath(root, entry.name());
                        synchronized (done) {
                            observer.update(done[0], total, entry.name());
                        }
                        if (entry.directory()) {
                            verifyParents(target);
                            try (InputStream in = cursor.open(entry)) {
                                if (in.read() != -1) throw new IOException("資料夾項目含資料");
                            }
                        } else {
                            verifyParents(target.getParent());
                            try (OutputStream out = createFile(target, created);
                                    InputStream in = cursor.open(entry)) {
                                transfer(in, out, entry.name(), done, total, cancel, observer);
                            }
                            restoreFileTime(cursor, entry, target, cancel);
                        }
                    });
        }
        check(cancel);
        observer.update(total, total, "完成");
    }

    /** Decode one solid stream in order; overlap only independent small-file writes. */
    private static void extractPipelined(
            ArchiveReader reader,
            Path root,
            BooleanSupplier cancel,
            Progress observer,
            List<Path> created,
            long total,
            int writers)
            throws IOException {
        Set<Path> directories = new LinkedHashSet<>();
        for (ArchiveReader.Entry entry : reader.entries()) {
            check(cancel);
            Path target = safePath(root, entry.name());
            directories.add(entry.directory() ? target : target.getParent());
        }
        for (Path directory : directories) {
            check(cancel);
            createDirectories(directory, created);
        }
        long[] done = {0};
        try (ExtractionOutputPool outputs = new ExtractionOutputPool(writers)) {
            for (ArchiveReader.Entry entry : reader.entries()) {
                check(cancel);
                Path target = safePath(root, entry.name());
                synchronized (done) {
                    observer.update(done[0], total, entry.name());
                }
                if (entry.directory()) {
                    try (InputStream in = reader.open(entry)) {
                        if (in.read() != -1) throw new IOException("資料夾項目含資料");
                    }
                } else if (entry.size() <= 65536) {
                    byte[] bytes = new byte[(int) entry.size()];
                    try (InputStream in = reader.open(entry)) {
                        if (in.readNBytes(bytes, 0, bytes.length) != bytes.length
                                || in.read() != -1) throw new IOException("7z項目大小不符");
                    }
                    outputs.submit(
                            () -> {
                                check(cancel);
                                verifyParents(target.getParent());
                                try (OutputStream out = createFile(target, created)) {
                                    out.write(bytes);
                                    check(cancel);
                                }
                                synchronized (done) {
                                    done[0] += bytes.length;
                                    observer.update(done[0], total, entry.name());
                                }
                            });
                } else {
                    outputs.finish();
                    check(cancel);
                    verifyParents(target.getParent());
                    try (InputStream in = reader.open(entry);
                            OutputStream out = createFile(target, created)) {
                        transfer(in, out, entry.name(), done, total, cancel, observer);
                    }
                }
            }
            outputs.finish();
            check(cancel);
        }
        observer.update(total, total, "完成");
    }

    private static void transfer(
            InputStream in,
            OutputStream out,
            String name,
            long[] done,
            long total,
            BooleanSupplier cancel,
            Progress observer)
            throws IOException {
        transfer(in, out, name, done, total, cancel, observer, new byte[65536]);
    }

    private static void transfer(
            InputStream in,
            OutputStream out,
            String name,
            long[] done,
            long total,
            BooleanSupplier cancel,
            Progress observer,
            byte[] buffer)
            throws IOException {
        int n;
        while ((n = in.read(buffer)) != -1) {
            check(cancel);
            out.write(buffer, 0, n);
            synchronized (done) {
                done[0] += n;
                observer.update(done[0], total, name);
            }
        }
    }

    private static void preflight(
            Path root,
            String name,
            boolean directory,
            Set<Path> seen,
            Set<Path> checkedParents,
            Set<Path> absentParents)
            throws IOException {
        Path target = safePath(root, name);
        // Share only read-only preflight work. createDirectories still rechecks the
        // complete parent chain immediately before every extraction write.
        for (Path p = directory ? target : target.getParent();
                p != null && checkedParents.add(p);
                p = p.getParent()) if (!verifyDirectory(p)) absentParents.add(p);
        if (!seen.add(target)) throw new IOException("壓縮檔含重複路徑: " + name);
        // A missing parent implies a missing child in this read-only preflight snapshot.
        // Every actual write still rechecks all parents and uses CREATE_NEW.
        if (!absentParents.contains(target.getParent())
                && Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && (!directory || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)))
            throw new FileAlreadyExistsException(target.toString());
    }

    private static Path safePath(Path root, String name) throws IOException {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0)
            throw new IOException("無效的壓縮項目名稱");
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*"))
            throw new IOException("拒絕壓縮檔絕對路徑: " + name);
        Path target = root.resolve(normalized).normalize();
        if (!target.startsWith(root) || target.equals(root) || normalized.contains(":"))
            throw new IOException("拒絕壓縮檔越界路徑: " + name);
        return target;
    }

    private static void verifyParents(Path path) throws IOException {
        for (Path p = path; p != null; p = p.getParent()) verifyDirectory(p);
    }

    private static boolean verifyDirectory(Path path) throws IOException {
        BasicFileAttributes attrs;
        try {
            attrs =
                    Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException absent) {
            return false;
        }
        if (!attrs.isDirectory() || attrs.isSymbolicLink() || attrs.isOther())
            throw new IOException("解壓路徑含連結或非資料夾: " + path);
        return true;
    }

    private static void createDirectories(Path path, List<Path> created) throws IOException {
        if (verifyDirectory(path)) {
            verifyParents(path.getParent());
            return;
        }
        if (path.getParent() != null) createDirectories(path.getParent(), created);
        Files.createDirectory(path);
        created.add(path);
    }

    private static OutputStream createFile(Path path, List<Path> created) throws IOException {
        OutputStream out =
                ArchiveIo.output(
                        path, false, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        synchronized (created) {
            created.add(path);
        }
        return out;
    }

    private static void check(BooleanSupplier cancelled) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()
                || (cancelled != null && cancelled.getAsBoolean()))
            throw new InterruptedIOException("操作已取消");
    }
}
