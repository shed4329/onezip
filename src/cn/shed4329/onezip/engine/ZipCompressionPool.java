package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Per-archive reusable workers and matcher tables; callers bound outstanding work to workers(). */
final class ZipCompressionPool implements AutoCloseable {
    private final ExecutorService executor;
    private final int workers;
    private final ThreadLocal<DeflateEncoder.Workspace> workspace =
            ThreadLocal.withInitial(DeflateEncoder.Workspace::new);
    private boolean closed;

    ZipCompressionPool(int workers) {
        this.workers = workers;
        AtomicInteger sequence = new AtomicInteger();
        executor =
                new ThreadPoolExecutor(
                        workers,
                        workers,
                        0,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(workers),
                        r -> {
                            Thread t = new Thread(r, "onezip-zip-" + sequence.incrementAndGet());
                            t.setDaemon(true);
                            return t;
                        });
    }

    int workers() {
        return workers;
    }

    DeflateEncoder.Workspace workspace() {
        return workspace.get();
    }

    <T> Future<T> submit(Callable<T> task) throws IOException {
        if (closed) throw new IOException("ZIP workers closed");
        try {
            return executor.submit(task);
        } catch (RejectedExecutionException ex) {
            throw new IOException("ZIP queue unavailable", ex);
        }
    }

    static <T> T await(Future<T> task) throws IOException {
        try {
            return task.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("操作已取消");
        } catch (CancellationException ex) {
            throw new IOException("ZIP worker cancelled", ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof IOException io) throw io;
            if (ex.getCause() instanceof RuntimeException re) throw re;
            throw new IOException("ZIP worker failed", ex.getCause());
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (Runnable queued : executor.shutdownNow())
            if (queued instanceof Future<?> future) future.cancel(true);
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
