package cn.shed4329.onezip.engine.lzma;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded per-archive workers; no worker writes directly to the archive file. */
public final class Lzma2EncoderPool implements AutoCloseable {
    private final int workers;
    private final ExecutorService executor;
    private final ThreadLocal<ArkLzma2OutputStream.Workspace> workspace =
            ThreadLocal.withInitial(ArkLzma2OutputStream.Workspace::new);
    private boolean closed;

    public Lzma2EncoderPool(int requested) {
        if (requested < 0 || requested > 64) throw new IllegalArgumentException("執行緒數應為0–64");
        int memory =
                (int)
                        Math.max(
                                1,
                                Math.min(
                                        64,
                                        (Runtime.getRuntime().maxMemory() - 96L * 1024 * 1024)
                                                / (48L * 1024 * 1024)));
        int available = Math.max(1, Runtime.getRuntime().availableProcessors());
        workers =
                Math.max(1, Math.min(requested == 0 ? Math.min(8, available) : requested, memory));
        AtomicInteger sequence = new AtomicInteger();
        executor =
                workers == 1
                        ? null
                        : new ThreadPoolExecutor(
                                workers,
                                workers,
                                0L,
                                TimeUnit.MILLISECONDS,
                                new ArrayBlockingQueue<>(workers),
                                r -> {
                                    Thread t =
                                            new Thread(
                                                    r, "onezip-7z-" + sequence.incrementAndGet());
                                    t.setDaemon(true);
                                    return t;
                                });
    }

    public int workers() {
        return workers;
    }

    private Future<byte[]> submit(Callable<byte[]> job) throws IOException {
        if (closed || executor == null) throw new IOException("7z workers unavailable");
        try {
            return executor.submit(job);
        } catch (RejectedExecutionException e) {
            throw new IOException("7z worker queue is full or closed", e);
        }
    }

    public Future<byte[]> whole(byte[] input, int level) throws IOException {
        return submit(
                () -> {
                    ByteArrayOutputStream packed = new ByteArrayOutputStream();
                    try (var encoder = new ArkLzma2OutputStream(packed, level, workspace.get())) {
                        encoder.write(input);
                    }
                    return packed.toByteArray();
                });
    }

    public Future<byte[]> fragment(byte[] input, int history, long offset, int level)
            throws IOException {
        return submit(
                () ->
                        ArkLzma2OutputStream.fragment(
                                input, history, offset, level, workspace.get()));
    }

    /** Avoid model splits on trivial/raw data; bound small-folder parallelism. */
    public int folderParts(byte[] input) {
        if (workers < 2 || input.length < 128 * 1024) return 1;
        int[] heads = new int[4096];
        Arrays.fill(heads, -1);
        int hits = 0, longHits = 0, samples = 0, probe = Math.min(65536, input.length);
        for (int pos = 0; pos + 8 <= probe; pos++) {
            int word =
                    (input[pos] & 255)
                            | ((input[pos + 1] & 255) << 8)
                            | ((input[pos + 2] & 255) << 16)
                            | (input[pos + 3] << 24);
            int tail =
                    (input[pos + 4] & 255)
                            | ((input[pos + 5] & 255) << 8)
                            | ((input[pos + 6] & 255) << 16)
                            | (input[pos + 7] << 24);
            int hash = ((word * 0x9e3779b1 ^ Integer.rotateLeft(tail, 13)) * 0x85ebca6b) >>> 20,
                    candidate = heads[hash];
            heads[hash] = pos;
            if ((pos & 7) != 0) continue;
            samples++;
            if (candidate >= 0) {
                int n = 0;
                while (n < 8 && input[pos + n] == input[candidate + n]) n++;
                if (n == 8) {
                    hits++;
                    if (pos + 256 <= probe
                            && Arrays.mismatch(
                                            input,
                                            pos,
                                            pos + 256,
                                            input,
                                            candidate,
                                            candidate + 256)
                                    < 0) longHits++;
                }
            }
        }
        if (hits * 100 < samples || hits * 5 > samples * 4 || longHits * 10 > samples) return 1;
        return Math.min(Math.min(workers, 4), (input.length + 96 * 1024 - 1) / (96 * 1024));
    }

    public static byte[] await(Future<byte[]> task) throws IOException {
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            InterruptedIOException failure =
                    new InterruptedIOException("7z compression interrupted");
            failure.initCause(e);
            throw failure;
        } catch (CancellationException e) {
            throw new IOException("7z worker cancelled", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw new IOException("7z worker failed", e.getCause());
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        if (executor == null) return;
        for (Runnable queued : executor.shutdownNow())
            if (queued instanceof Future<?> f) f.cancel(true);
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        try {
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new IOException("7z workers did not stop");
                try {
                    if (executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
