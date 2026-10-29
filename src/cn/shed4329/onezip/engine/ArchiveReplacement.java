package cn.shed4329.onezip.engine;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/** Optimistic, same-directory archive replacement; does not lock out other processes. */
final class ArchiveReplacement {
    private final Path archive;
    private final BasicFileAttributes original;

    ArchiveReplacement(Path archive) throws IOException {
        this.archive = archive;
        original =
                Files.readAttributes(archive, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!original.isRegularFile() || original.isSymbolicLink())
            throw new IOException("Cannot modify a link or special archive file: " + archive);
    }

    void publish(Path staging) throws IOException {
        var current =
                Files.readAttributes(archive, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!current.isRegularFile()
                || current.isSymbolicLink()
                || original.size() != current.size()
                || !original.lastModifiedTime().equals(current.lastModifiedTime())
                || !Objects.equals(original.fileKey(), current.fileKey()))
            throw new IOException("Archive changed during editing; reopen it: " + archive);
        try {
            Files.move(
                    staging,
                    archive,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(staging, archive, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
