package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/**
 * Backend switch for reproducible I/O experiments; codec, queueing and archive bytes stay
 * identical.
 */
final class ArchiveIo {
    private static final String BACKEND = System.getProperty("onezip.io", "stream");

    static {
        if (!Set.of("stream", "buffered", "nio-heap", "nio-direct").contains(BACKEND))
            throw new IllegalArgumentException("Unknown onezip.io backend: " + BACKEND);
    }

    private ArchiveIo() {}

    static InputStream input(Path path, OpenOption... options) throws IOException {
        if (BACKEND.equals("stream")) return Files.newInputStream(path, options);
        if (BACKEND.equals("buffered"))
            return new BufferedInputStream(Files.newInputStream(path, options), 65536);
        Set<OpenOption> flags = new HashSet<>(Arrays.asList(options));
        flags.add(StandardOpenOption.READ);
        FileChannel channel = FileChannel.open(path, flags);
        try {
            return new NioInputStream(channel, 65536, BACKEND.equals("nio-direct"));
        } catch (RuntimeException | Error ex) {
            try {
                channel.close();
            } catch (IOException close) {
                ex.addSuppressed(close);
            }
            throw ex;
        }
    }

    static OutputStream output(Path path, boolean buffered, OpenOption... options)
            throws IOException {
        if (BACKEND.equals("stream")) {
            OutputStream out = Files.newOutputStream(path, options);
            return buffered ? new BufferedOutputStream(out) : out;
        }
        if (BACKEND.equals("buffered"))
            return new BufferedOutputStream(Files.newOutputStream(path, options), 65536);
        Set<OpenOption> flags = new HashSet<>(Arrays.asList(options));
        flags.add(StandardOpenOption.WRITE);
        if (options.length == 0) {
            flags.add(StandardOpenOption.CREATE);
            flags.add(StandardOpenOption.TRUNCATE_EXISTING);
        }
        FileChannel channel = FileChannel.open(path, flags);
        try {
            return new NioOutputStream(channel, 65536, BACKEND.equals("nio-direct"));
        } catch (RuntimeException | Error ex) {
            try {
                channel.close();
            } catch (IOException close) {
                ex.addSuppressed(close);
            }
            throw ex;
        }
    }
}
