package cn.shed4329.onezip.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Chooses a destination only; normal engine path validation and no-overwrite rules still apply. */
public final class ExtractionLayout {
    private ExtractionLayout() {}

    public static String folderName(Path archive) {
        String name =
                archive.getFileName()
                        .toString()
                        .replaceFirst(
                                "(?i)(\\.tar\\.(gz|gzip|xz|bz2|bzip2|lz4)|\\.(zip|7z|rar5?|tar|gz|gzip|tgz|xz|txz|bz2|bzip2|tbz2?|lz4))$",
                                "");
        return name.isBlank() || name.equals(".") || name.equals("..") ? "Archive" : name;
    }

    public static Path choose(
            Path archive,
            Path parent,
            List<ArchiveEngine.ArchiveItem> entries,
            BooleanSupplier cancelled)
            throws IOException {
        Set<String> roots = new HashSet<>();
        for (var entry : entries) {
            if (cancelled != null && cancelled.getAsBoolean())
                throw new java.util.concurrent.CancellationException();
            String name = entry.name().replace('\\', '/');
            if (name.startsWith("/") || name.matches("^[A-Za-z]:.*"))
                throw new IOException("Unsafe archive path: " + entry.name());
            String root = null;
            for (String part : name.split("/")) {
                if (part.equals(".."))
                    throw new IOException("Unsafe archive path: " + entry.name());
                if (!part.isEmpty() && !part.equals(".") && root == null) root = part;
            }
            if (root != null) roots.add(root);
        }
        Path base = parent.toAbsolutePath().normalize();
        return roots.size() == 1 ? base : base.resolve(folderName(archive));
    }
}
