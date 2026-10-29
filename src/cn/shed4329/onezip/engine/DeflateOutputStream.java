package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.Objects;

/** Self-written raw RFC1951 stream. finish() leaves the destination open; close() owns it. */
public final class DeflateOutputStream extends OutputStream {
    private final OutputStream out;
    private final DeflateEncoder encoder;
    private final ParallelDeflateOutputStream parallel;
    private final ZipCompressionPool ownedPool;
    private final long expected;
    private long written;
    private boolean finished, closed;
    private IOException failure;

    /** Sequential stream; the input size need not be known in advance. */
    public DeflateOutputStream(OutputStream out, int level) {
        this(out, level, null, -1, new DeflateEncoder.Workspace(), false);
    }

    /**
     * threads: 0=auto, 1=sequential, 2..64=requested limit. Parallel mode requires the exact input
     * size.
     */
    public DeflateOutputStream(OutputStream out, int level, int threads, long size) {
        this(out, level, createPool(threads, size), size, new DeflateEncoder.Workspace(), true);
    }

    private static ZipCompressionPool createPool(int threads, long size) {
        if (threads < 0 || threads > 64 || size < 0)
            throw new IllegalArgumentException("Invalid threads or input size");
        int jobs = (int) Math.min(64, Math.ceilDiv(size, ParallelDeflateOutputStream.SEGMENT));
        int workers = ArchiveWorkers.count(threads, 48L * 1024 * 1024, jobs);
        return size >= ParallelDeflateOutputStream.LARGE && workers > 1
                ? new ZipCompressionPool(workers)
                : null;
    }

    DeflateOutputStream(
            OutputStream out,
            int level,
            ZipCompressionPool pool,
            long size,
            DeflateEncoder.Workspace workspace) {
        this(out, level, pool, size, workspace, false);
    }

    private DeflateOutputStream(
            OutputStream out,
            int level,
            ZipCompressionPool pool,
            long size,
            DeflateEncoder.Workspace workspace,
            boolean own) {
        if (level < -1 || level > 9) {
            if (own && pool != null) pool.close();
            throw new IllegalArgumentException("Invalid compression level");
        }
        if (out == null) {
            if (own && pool != null) pool.close();
            throw new NullPointerException("out");
        }
        this.out = out;
        expected = size;
        ownedPool = own ? pool : null;
        parallel =
                pool != null && size >= ParallelDeflateOutputStream.LARGE
                        ? new ParallelDeflateOutputStream(out, level, pool, size)
                        : null;
        encoder = parallel == null ? new DeflateEncoder(out, level, workspace) : null;
    }

    private void checkOpen() throws IOException {
        if (closed) throw new IOException("Stream closed");
        if (failure != null) throw failure;
    }

    private void checkWrite(int length) throws IOException {
        checkOpen();
        if (finished) throw new IOException("DEFLATE stream finished");
        if (expected >= 0 && length > expected - written)
            throw new IOException("Input exceeds declared size");
    }

    @Override
    public void write(int value) throws IOException {
        checkWrite(1);
        try {
            if (parallel != null) parallel.write(value);
            else encoder.write(value);
            written++;
        } catch (IOException ex) {
            fail(ex);
            throw ex;
        }
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        checkWrite(length);
        try {
            if (parallel != null) parallel.write(bytes, offset, length);
            else encoder.write(bytes, offset, length);
            written += length;
        } catch (IOException ex) {
            fail(ex);
            throw ex;
        }
    }

    private void fail(IOException ex) {
        failure = ex;
        if (ownedPool != null) ownedPool.close();
    }

    /** Sync-flushes pending input as nonfinal blocks; subsequent writes retain the dictionary. */
    @Override
    public void flush() throws IOException {
        checkOpen();
        try {
            if (finished) out.flush();
            else if (parallel != null) parallel.flush();
            else encoder.flush();
        } catch (IOException ex) {
            fail(ex);
            throw ex;
        }
    }

    public void finish() throws IOException {
        checkOpen();
        if (finished) return;
        try {
            if (expected >= 0 && written != expected)
                throw new IOException("Input shorter than declared size");
            if (parallel != null) parallel.finish();
            else encoder.finish();
            finished = true;
        } catch (IOException ex) {
            fail(ex);
            throw ex;
        } finally {
            if (ownedPool != null) ownedPool.close();
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        IOException problem = null;
        try {
            if (failure == null) finish();
        } catch (IOException ex) {
            problem = ex;
        } finally {
            closed = true;
            if (ownedPool != null) ownedPool.close();
            try {
                out.close();
            } catch (IOException ex) {
                if (ex != failure) {
                    if (problem == null) problem = ex;
                    else if (problem != ex) problem.addSuppressed(ex);
                }
            }
        }
        if (problem != null) throw problem;
    }
}
