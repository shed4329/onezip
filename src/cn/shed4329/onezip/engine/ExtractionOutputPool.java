package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Ordered backpressure for independently written small files; the caller owns decoding. */
final class ExtractionOutputPool implements AutoCloseable {
    @FunctionalInterface
    interface Write {
        void run() throws IOException;
    }

    private final ExecutorService executor;
    private final ArrayDeque<Future<Void>> pending = new ArrayDeque<>();
    private final int limit;

    ExtractionOutputPool(int workers) {
        limit = workers;
        AtomicInteger sequence = new AtomicInteger();
        executor =
                new ThreadPoolExecutor(
                        workers,
                        workers,
                        0,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(workers),
                        task -> {
                            Thread thread =
                                    new Thread(
                                            task,
                                            "onezip-extract-write-" + sequence.incrementAndGet());
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    void submit(Write write) throws IOException {
        if (pending.size() >= limit) awaitOne();
        try {
            pending.addLast(
                    executor.submit(
                            () -> {
                                write.run();
                                return null;
                            }));
        } catch (RejectedExecutionException ex) {
            throw new IOException("Extraction output queue unavailable", ex);
        }
    }

    private void awaitOne() throws IOException {
        try {
            pending.removeFirst().get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("操作已取消");
        } catch (CancellationException ex) {
            throw new IOException("Extraction writer cancelled", ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof IOException io) throw io;
            if (ex.getCause() instanceof RuntimeException re) throw re;
            throw new IOException("Extraction writer failed", ex.getCause());
        }
    }

    void finish() throws IOException {
        while (!pending.isEmpty()) awaitOne();
    }

    @Override
    public void close() {
        for (Future<?> task : pending) task.cancel(true);
        for (Runnable task : executor.shutdownNow())
            if (task instanceof Future<?> future) future.cancel(true);
        boolean interrupted = Thread.interrupted();
        try {
            while (!executor.isTerminated())
                try {
                    executor.awaitTermination(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                    interrupted = true;
                }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
