package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.List;

/** Internal contract shared by the independent container readers. */
interface ArchiveReader extends AutoCloseable {
    record Entry(String name, boolean directory, long size) {}

    List<Entry> entries();

    /** Optional modification time; absence leaves the destination filesystem's default. */
    default java.nio.file.attribute.FileTime modifiedTime(Entry entry) {
        return null;
    }

    InputStream open(Entry entry) throws IOException;

    /** Independent cursors, sharing only parsed read-only metadata. */
    ArchiveReader fork() throws IOException;

    default List<List<Entry>> groups() {
        return entries().stream().map(List::of).toList();
    }

    default long workerMemory() {
        return 16L * 1024 * 1024;
    }

    @Override
    void close() throws IOException;
}
