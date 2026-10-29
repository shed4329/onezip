package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Objects;

/**
 * Buffered blocking-channel output, including partial writes. flush() does not request disk fsync.
 */
public final class NioOutputStream extends OutputStream {
    private final WritableByteChannel channel;
    private final ByteBuffer buffer;
    private boolean closed;
    private IOException failure;

    /**
     * Creates an adapter that owns the supplied blocking channel.
     *
     * @param channel channel to write and close with this stream
     * @param bufferSize positive byte capacity of the write buffer
     * @param direct whether to allocate the buffer outside the Java heap
     */
    public NioOutputStream(WritableByteChannel channel, int bufferSize, boolean direct) {
        if (bufferSize < 1) throw new IllegalArgumentException("Invalid buffer size");
        this.channel = Objects.requireNonNull(channel);
        buffer = direct ? ByteBuffer.allocateDirect(bufferSize) : ByteBuffer.allocate(bufferSize);
    }

    private void check() throws IOException {
        if (closed) throw new IOException("Stream closed");
        if (failure != null) throw failure;
        ReadSupport.check(null);
    }

    private void writeFully(ByteBuffer bytes) throws IOException {
        try {
            while (bytes.hasRemaining()) {
                ReadSupport.check(null);
                if (channel.write(bytes) == 0) Thread.onSpinWait();
            }
        } catch (IOException ex) {
            failure = ex;
            throw ex;
        }
    }

    private void drain() throws IOException {
        buffer.flip();
        writeFully(buffer);
        buffer.clear();
    }

    @Override
    public void write(int value) throws IOException {
        check();
        if (!buffer.hasRemaining()) drain();
        buffer.put((byte) value);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        check();
        if (length == 0) return;
        if (!buffer.isDirect() && length >= buffer.capacity()) {
            drain();
            writeFully(ByteBuffer.wrap(bytes, offset, length));
            return;
        }
        while (length > 0) {
            int n = Math.min(length, buffer.remaining());
            buffer.put(bytes, offset, n);
            offset += n;
            length -= n;
            if (!buffer.hasRemaining()) drain();
        }
    }

    @Override
    public void flush() throws IOException {
        check();
        drain();
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        IOException problem = null;
        try {
            if (failure == null) flush();
        } catch (IOException ex) {
            problem = ex;
        } finally {
            closed = true;
            try {
                channel.close();
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
