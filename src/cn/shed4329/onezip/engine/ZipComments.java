package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** ZIP comment access without the legacy archive library. */
public final class ZipComments {
    private ZipComments() {}

    /** Reads the global ZIP description; decoding follows the reader's normal charset rules. */
    public static String read(Path path) throws IOException {
        try (ZipArchiveReader reader = new ZipArchiveReader(path, null, null)) {
            return reader.comment();
        }
    }

    /**
     * Replaces the UTF-8 global description without recompressing entries. Uses a private sibling
     * copy so failures before publication preserve the original. Requires temporary space for one
     * complete archive; rejects links and concurrent changes detected by size, modification time or
     * file identity. Callers must serialize editing.
     *
     * @param path existing ZIP file
     * @param comment description, at most 65535 UTF-8 bytes
     * @throws IOException invalid ZIP, oversized description or filesystem failure
     */
    public static void write(Path path, String comment) throws IOException {
        byte[] bytes = comment.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65535) throw new IOException("ZIP 註解超過 65535 bytes");
        path = path.toAbsolutePath().normalize();
        var replacement = new ArchiveReplacement(path);
        Path staging = Files.createTempFile(path.getParent(), ".onezip-comment-", ".zip");
        try {
            Files.copy(path, staging, StandardCopyOption.REPLACE_EXISTING);
            try (RandomAccessFile file = new RandomAccessFile(staging.toFile(), "rw")) {
                long end = ZipArchiveReader.endRecord(file);
                file.seek(end + 20);
                file.write(bytes.length);
                file.write(bytes.length >>> 8);
                file.write(bytes);
                file.setLength(end + 22 + bytes.length);
            }
            replacement.publish(staging);
        } finally {
            Files.deleteIfExists(staging);
        }
    }
}
