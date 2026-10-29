package cn.shed4329.onezip.engine;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.*;

/** Source filesystem times captured once; ZIP stores UTC NTFS times and a DOS local fallback. */
record ArchiveTimes(FileTime modified, FileTime accessed, FileTime created) {
    private static final long EPOCH_SECONDS = 11_644_473_600L;

    static ArchiveTimes of(BasicFileAttributes attrs) {
        return new ArchiveTimes(
                attrs.lastModifiedTime(), attrs.lastAccessTime(), attrs.creationTime());
    }

    static long ntfs(FileTime time) throws IOException {
        Instant instant = time.toInstant();
        try {
            long ticks =
                    Math.addExact(
                            Math.multiplyExact(
                                    Math.addExact(instant.getEpochSecond(), EPOCH_SECONDS),
                                    10_000_000L),
                            instant.getNano() / 100);
            if (ticks < 0) throw new ArithmeticException("Before NTFS epoch");
            return ticks;
        } catch (ArithmeticException ex) {
            throw new IOException("File timestamp is outside the supported NTFS range", ex);
        }
    }

    static FileTime fromNtfs(long ticks) {
        if (ticks <= 0) return null;
        return FileTime.from(
                Instant.ofEpochSecond(
                        ticks / 10_000_000L - EPOCH_SECONDS, (ticks % 10_000_000L) * 100));
    }

    LocalDateTime dosLocal() {
        var local = modified.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        if (local.getYear() < 1980) return LocalDateTime.of(1980, 1, 1, 0, 0);
        if (local.getYear() > 2107) return LocalDateTime.of(2107, 12, 31, 23, 59, 58);
        return local;
    }

    void appendZipExtra(BinaryWriter extra) throws IOException {
        extra.le(0x000a, 2);
        extra.le(32, 2);
        extra.le(0, 4);
        extra.le(1, 2);
        extra.le(24, 2);
        extra.le(ntfs(modified), 8);
        extra.le(ntfs(accessed), 8);
        extra.le(ntfs(created), 8);
    }
}
