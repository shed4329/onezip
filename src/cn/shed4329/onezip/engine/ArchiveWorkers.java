package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** At most N long-lived jobs; no per-file unbounded task queue. */
final class ArchiveWorkers {
    @FunctionalInterface
    interface Job {
        void run(int index) throws IOException;
    }

    static int count(int requested, long memory, int jobs) {
        if (requested < 0 || requested > 64) throw new IllegalArgumentException("執行緒數應為0–64");
        int available =
                requested == 0
                        ? Math.min(8, Runtime.getRuntime().availableProcessors())
                        : requested;
        long budget =
                Math.max(
                        1,
                        (Runtime.getRuntime().maxMemory() - 96L * 1024 * 1024)
                                / Math.max(1, memory));
        return Math.max(1, Math.min(jobs, (int) Math.min(available, budget)));
    }

    static void run(int workers, int jobs, String name, Job job) throws IOException {
        if (workers == 1) {
            for (int i = 0; i < jobs; i++) job.run(i);
            return;
        }
        AtomicInteger next = new AtomicInteger(), sequence = new AtomicInteger();
        ExecutorService pool =
                Executors.newFixedThreadPool(
                        workers,
                        r -> {
                            Thread t = new Thread(r, name + sequence.incrementAndGet());
                            t.setDaemon(true);
                            return t;
                        });
        CompletionService<Void> completed = new ExecutorCompletionService<>(pool);
        List<Future<Void>> tasks = new ArrayList<>();
        try {
            for (int w = 0; w < workers; w++)
                tasks.add(
                        completed.submit(
                                () -> {
                                    for (int i; (i = next.getAndIncrement()) < jobs; ) {
                                        ReadSupport.check(null);
                                        job.run(i);
                                    }
                                    return null;
                                }));
            for (int w = 0; w < workers; w++) completed.take().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("操作已取消");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException io) throw io;
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new IOException("Archive worker failed", e.getCause());
        } finally {
            for (Future<?> task : tasks) task.cancel(true);
            pool.shutdownNow();
            boolean interrupted = Thread.interrupted();
            try {
                while (!pool.isTerminated())
                    try {
                        pool.awaitTermination(100, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }
}
