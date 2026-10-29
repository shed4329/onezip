package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.*;
import java.util.concurrent.Future;

/** Byte-aligned nonfinal RFC1951 fragments retain the preceding 32KiB history. */
final class ParallelDeflateOutputStream extends OutputStream {
    static final int SEGMENT = DeflateEncoder.BLOCK * 16, LARGE = 4 * 1024 * 1024;
    private final OutputStream out;
    private final ZipCompressionPool pool;
    private final int level;
    private final long expected;
    private final ArrayDeque<Future<ChunkedOutputStream>> pending = new ArrayDeque<>();
    private byte[] buffer, history = new byte[0];
    private int used;
    private long submitted;
    private boolean finished;
    private IOException failure;

    ParallelDeflateOutputStream(
            OutputStream out, int level, ZipCompressionPool pool, long expected) {
        this.out = out;
        this.level = level;
        this.pool = pool;
        this.expected = expected;
        buffer = new byte[target()];
    }

    private int target() {
        long remaining = expected - submitted;
        return (int) Math.min(remaining, remaining <= SEGMENT + 65536 ? SEGMENT + 65536 : SEGMENT);
    }

    @Override
    public void write(int b) throws IOException {
        check();
        if (submitted + used == expected) throw new IOException("ZIP input exceeds declared size");
        try {
            buffer[used++] = (byte) b;
            if (used == buffer.length) submit();
        } catch (IOException ex) {
            failure = ex;
            throw ex;
        }
    }

    @Override
    public void write(byte[] b, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, b.length);
        check();
        if (length > expected - submitted - used)
            throw new IOException("ZIP input exceeds declared size");
        try {
            while (length != 0) {
                ReadSupport.check(null);
                int n = Math.min(length, buffer.length - used);
                System.arraycopy(b, offset, buffer, used, n);
                used += n;
                offset += n;
                length -= n;
                if (used == buffer.length) submit();
            }
        } catch (IOException ex) {
            failure = ex;
            throw ex;
        }
    }

    private void submit() throws IOException {
        if (used == 0) return;
        if (pending.size() >= pool.workers()) drain();
        byte[] data = used == buffer.length ? buffer : Arrays.copyOf(buffer, used),
                prefix = history;
        if (data.length >= DeflateEncoder.WINDOW)
            history = Arrays.copyOfRange(data, data.length - DeflateEncoder.WINDOW, data.length);
        else {
            int keep = Math.min(DeflateEncoder.WINDOW, prefix.length + data.length),
                    old = keep - data.length;
            history = new byte[keep];
            System.arraycopy(prefix, prefix.length - old, history, 0, old);
            System.arraycopy(data, 0, history, old, data.length);
        }
        pending.addLast(
                pool.submit(() -> DeflateEncoder.fragment(data, prefix, level, pool.workspace())));
        submitted += used;
        used = 0;
        buffer = new byte[target()];
    }

    private void drain() throws IOException {
        ZipCompressionPool.await(pending.removeFirst()).writeTo(out);
    }

    private void check() throws IOException {
        if (failure != null) throw failure;
        if (finished) throw new IOException("DEFLATE stream finished");
    }

    void finish() throws IOException {
        if (finished) return;
        check();
        try {
            if (submitted + used != expected)
                throw new IOException("ZIP input shorter than declared size");
            submit();
            while (!pending.isEmpty()) drain();
            out.write(new byte[] {3, 0});
            finished = true;
        } catch (IOException ex) {
            failure = ex;
            throw ex;
        }
    }

    @Override
    public void flush() throws IOException {
        check();
        try {
            submit();
            while (!pending.isEmpty()) drain();
            out.flush();
        } catch (IOException ex) {
            failure = ex;
            throw ex;
        }
    }
}
