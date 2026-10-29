package cn.shed4329.onezip.application;

import static cn.shed4329.onezip.i18n.I18n.t;

import cn.shed4329.onezip.engine.ArchiveEngine;

import javafx.concurrent.Task;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** One operation per task; observable state is marshalled by JavaFX Task. */
public final class ArchiveTask extends Task<Path> {
    private final List<Path> sources;
    private final Path archive, destination;
    private final ArchiveEngine.Format format;
    private final int level, threads;
    private final char[] password;
    private final boolean testing;
    private final java.nio.charset.Charset filenameCharset;
    private volatile long processedBytes;
    private volatile long totalBytes;
    private volatile int fileIndex, fileCount;
    private String compressionComment = "";
    private boolean smartExtraction;
    private volatile Path resolvedDestination;

    public int fileIndex() {
        return fileIndex;
    }

    public int fileCount() {
        return fileCount;
    }

    /** Original bytes reported by the engine, including buffered input during compression. */
    public long processedBytes() {
        return processedBytes;
    }

    public long totalBytes() {
        return totalBytes;
    }

    public Path archivePath() {
        return archive;
    }

    public Path targetDirectory() {
        return testing
                ? null
                : destination != null
                        ? resolvedDestination != null ? resolvedDestination : destination
                        : archive.toAbsolutePath().getParent();
    }

    public Path revealTarget() {
        return testing
                ? null
                : destination != null
                        ? resolvedDestination != null ? resolvedDestination : destination
                        : archive;
    }

    public String operationLabel() {
        return testing
                ? t("task.testing")
                : t(format == null ? "task.extracting" : "task.compressing");
    }

    private ArchiveTask(
            List<Path> sources,
            Path archive,
            Path destination,
            ArchiveEngine.Format format,
            int level,
            char[] password,
            int threads) {
        this(sources, archive, destination, format, level, password, threads, false);
    }

    private ArchiveTask(
            List<Path> sources,
            Path archive,
            Path destination,
            ArchiveEngine.Format format,
            int level,
            char[] password,
            int threads,
            boolean testing) {
        this(
                sources,
                archive,
                destination,
                format,
                level,
                password,
                threads,
                testing,
                java.nio.charset.Charset.forName("IBM437"));
    }

    private ArchiveTask(
            List<Path> sources,
            Path archive,
            Path destination,
            ArchiveEngine.Format format,
            int level,
            char[] password,
            int threads,
            boolean testing,
            java.nio.charset.Charset charset) {
        filenameCharset = charset;
        this.testing = testing;
        if (threads < 0 || threads > 64)
            throw new IllegalArgumentException(t("validation.threads"));
        this.threads = threads;
        this.sources = List.copyOf(sources);
        this.archive = archive;
        this.destination = destination;
        this.format = format;
        this.level = level;
        this.password = password == null ? new char[0] : password.clone();
    }

    public static ArchiveTask compress(
            List<Path> sources,
            Path output,
            ArchiveEngine.Format format,
            int level,
            char[] password) {
        return compress(sources, output, format, level, password, 0);
    }

    public static ArchiveTask compress(
            List<Path> sources,
            Path output,
            ArchiveEngine.Format format,
            int level,
            char[] password,
            int threads) {
        return new ArchiveTask(sources, output, null, format, level, password, threads);
    }

    public static ArchiveTask compress(
            List<Path> sources,
            Path output,
            ArchiveEngine.Format format,
            int level,
            char[] password,
            int threads,
            String comment) {
        var task = compress(sources, output, format, level, password, threads);
        task.compressionComment = java.util.Objects.requireNonNull(comment);
        return task;
    }

    public static ArchiveTask extract(Path archive, Path destination, char[] password) {
        return extract(archive, destination, password, 0);
    }

    public static ArchiveTask extract(
            Path archive, Path destination, char[] password, int threads) {
        return new ArchiveTask(List.of(), archive, destination, null, 0, password, threads);
    }

    public static ArchiveTask extract(
            Path archive,
            Path destination,
            char[] password,
            int threads,
            java.nio.charset.Charset charset) {
        return new ArchiveTask(
                List.of(), archive, destination, null, 0, password, threads, false, charset);
    }

    public static ArchiveTask extractSmart(
            Path archive,
            Path destination,
            char[] password,
            int threads,
            java.nio.charset.Charset charset) {
        var task = extract(archive, destination, password, threads, charset);
        task.smartExtraction = true;
        return task;
    }

    public static ArchiveTask test(Path archive, char[] password) {
        return new ArchiveTask(List.of(), archive, null, null, 0, password, 1, true);
    }

    @Override
    protected Path call() throws Exception {
        char[] secret;
        synchronized (password) {
            secret = password.clone();
            Arrays.fill(password, '\0');
        }
        ArchiveEngine engine = new ArchiveEngine();
        ArchiveEngine.Progress observer =
                new ArchiveEngine.Progress() {
                    private final java.util.Map<String, Integer> indices =
                            new java.util.HashMap<>();

                    @Override
                    public void files(List<String> names) {
                        for (int i = 0; i < names.size(); i++)
                            indices.putIfAbsent(names.get(i), i + 1);
                        fileCount = names.size();
                    }

                    @Override
                    public void update(long done, long total, String name) {
                        processedBytes = done;
                        totalBytes = total;
                        fileIndex = indices.getOrDefault(name, fileIndex);
                        updateMessage(name);
                        updateProgress(Math.min(.99, total == 0 ? 0 : done / (double) total), 1);
                    }
                };
        try {
            if (isCancelled()) throw new java.util.concurrent.CancellationException();
            updateTitle(format == null ? t("task.extractLegacy") : t("task.compressLegacy"));
            updateMessage(t("task.scanningLegacy"));
            updateProgress(-1, 1);
            Path result;
            if (smartExtraction) {
                updateMessage(t("extraction.checking"));
                if (ArchiveEngine.singleStreamFormat(
                        ArchiveEngine.outputFormat(archive.getFileName().toString())))
                    resolvedDestination = destination.toAbsolutePath().normalize();
                else
                    resolvedDestination =
                            cn.shed4329.onezip.engine.ExtractionLayout.choose(
                                    archive,
                                    destination,
                                    engine
                                            .listArchive(
                                                    archive,
                                                    secret,
                                                    this::isCancelled,
                                                    filenameCharset)
                                            .entries()
                                            .stream()
                                            .map(ArchiveEngine.EntryDetails::item)
                                            .toList(),
                                    this::isCancelled);
                if (isCancelled()) throw new java.util.concurrent.CancellationException();
            }
            if (testing) {
                updateTitle(t("test.title"));
                engine.test(archive, secret, this::isCancelled, observer);
                result = archive;
            } else
                result =
                        format == null
                                ? engine.extract(
                                        archive,
                                        resolvedDestination != null
                                                ? resolvedDestination
                                                : destination,
                                        secret,
                                        this::isCancelled,
                                        observer,
                                        threads,
                                        filenameCharset)
                                : engine.compress(
                                        sources,
                                        archive,
                                        format,
                                        level,
                                        secret,
                                        this::isCancelled,
                                        observer,
                                        threads,
                                        compressionComment);
            fileIndex = fileCount;
            updateProgress(1, 1);
            return result;
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    @Override
    protected void cancelled() {
        synchronized (password) {
            Arrays.fill(password, '\0');
        }
    }
}
