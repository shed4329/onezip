package cn.shed4329.onezip.engine.lzma;

import java.io.*;
import java.util.*;
import java.util.concurrent.Future;

/** Ordered LZMA2 fragments with overlapping input history and independent models. */
public final class ParallelLzma2OutputStream extends OutputStream {
    private static final int BLOCK = ArkLzma2OutputStream.BLOCK_SIZE,
            SEGMENT = BLOCK / 2,
            MIN_TAIL = 65536;
    private final OutputStream out;
    private final Lzma2EncoderPool pool;
    private final int level;
    private final long expected;
    private final ArrayDeque<Future<byte[]>> pending = new ArrayDeque<>();
    private byte[] data = new byte[65536], history = new byte[0];
    private int used;
    private long submitted;
    private boolean finished;
    private IOException failure;
    private ArkLzma2OutputStream sequential;

    public ParallelLzma2OutputStream(
            OutputStream out, int level, Lzma2EncoderPool pool, long expected) {
        int selected = level < 0 ? 5 : Math.min(level, 9);
        if (selected == 0 || expected <= 0 || pool.workers() < 2)
            throw new IllegalArgumentException("Invalid parallel LZMA2 stream");
        this.out = out;
        this.level = selected;
        this.pool = pool;
        this.expected = expected;
    }

    @Override
    public void write(int value) throws IOException {
        write(new byte[] {(byte) value});
    }

    @Override
    public void write(byte[] input, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, input.length);
        ensureOpen();
        if (length > expected - submitted - used)
            throw new IOException("7z input exceeds declared size");
        try {
            while (length != 0) {
                if (sequential != null) {
                    sequential.write(input, offset, length);
                    submitted += length;
                    return;
                }
                checkInterrupted();
                long remaining = expected - submitted;
                int target =
                        (int)
                                Math.min(
                                        remaining,
                                        remaining <= SEGMENT + MIN_TAIL
                                                ? SEGMENT + MIN_TAIL
                                                : SEGMENT);
                if (used == data.length)
                    data = Arrays.copyOf(data, Math.min(target, data.length * 2));
                int n = Math.min(length, Math.min(target - used, data.length - used));
                System.arraycopy(input, offset, data, used, n);
                used += n;
                offset += n;
                length -= n;
                if (used == target) submit();
            }
        } catch (IOException e) {
            failure = e;
            throw e;
        }
    }

    private void submit() throws IOException {
        if (used == 0) return;
        // Very short-period input is already cheap to encode. Avoid spending
        // extra workers and resetting its near-perfect probability models.
        if (submitted == 0 && shortPeriod(data, used)) {
            sequential = new ArkLzma2OutputStream(out, level);
            sequential.write(data, 0, used);
            submitted += used;
            used = 0;
            data = history = new byte[0];
            return;
        }
        if (pending.size() >= pool.workers()) drainOne();
        byte[] input = new byte[history.length + used];
        System.arraycopy(history, 0, input, 0, history.length);
        System.arraycopy(data, 0, input, history.length, used);
        pending.addLast(pool.fragment(input, history.length, submitted, level));
        if (used == BLOCK) history = data;
        else {
            int keep = Math.min(BLOCK, history.length + used);
            byte[] next = new byte[keep];
            int fromOld = Math.max(0, keep - used);
            System.arraycopy(history, history.length - fromOld, next, 0, fromOld);
            System.arraycopy(data, Math.max(0, used - keep), next, fromOld, keep - fromOld);
            history = next;
        }
        submitted += used;
        used = 0;
        data = new byte[65536];
    }

    private static boolean shortPeriod(byte[] bytes, int length) {
        int probe = Math.min(length, 65536);
        if (probe < 8192) return false;
        for (int period = 1; period <= 1024; period++)
            if (bytes[0] == bytes[period]
                    && Arrays.mismatch(bytes, 0, probe - period, bytes, period, probe) < 0)
                return true;
        return false;
    }

    private void drainOne() throws IOException {
        out.write(Lzma2EncoderPool.await(pending.removeFirst()));
    }

    public void finish() throws IOException {
        if (finished) return;
        ensureOpen();
        try {
            if (submitted + used != expected)
                throw new IOException("7z input shorter than declared size");
            submit();
            if (sequential != null) sequential.finish();
            else {
                while (!pending.isEmpty()) drainOne();
                out.write(0);
            }
            finished = true;
        } catch (IOException e) {
            failure = e;
            throw e;
        }
    }

    @Override
    public void flush() throws IOException {
        ensureOpen();
        try {
            submit();
            if (sequential != null) sequential.flush();
            else {
                while (!pending.isEmpty()) drainOne();
                out.flush();
            }
        } catch (IOException e) {
            failure = e;
            throw e;
        }
    }

    private void ensureOpen() throws IOException {
        if (failure != null) throw failure;
        if (finished) throw new IOException("LZMA2 stream finished");
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedIOException("7z compression interrupted");
    }

    @Override
    public void close() throws IOException {
        if (failure == null) finish();
    }
}
