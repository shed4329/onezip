package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Objects;

/**
 * Buffered adapter for a blocking channel. Heap bulk reads can fill the caller's array directly.
 */
public final class NioInputStream extends InputStream {
    private final ReadableByteChannel channel;
    private final ByteBuffer buffer;
    private boolean closed, eof;
    private IOException failure;

    /**
     * Creates an adapter that owns the supplied blocking channel.
     *
     * @param channel channel to read and close with this stream
     * @param bufferSize positive byte capacity of the read buffer
     * @param direct whether to allocate the buffer outside the Java heap
     */
    public NioInputStream(ReadableByteChannel channel, int bufferSize, boolean direct) {
        if (bufferSize < 1) throw new IllegalArgumentException("Invalid buffer size");
        this.channel = Objects.requireNonNull(channel);
        buffer = direct ? ByteBuffer.allocateDirect(bufferSize) : ByteBuffer.allocate(bufferSize);
        buffer.limit(0);
    }

    private void check() throws IOException {
        if (closed) throw new IOException("Stream closed");
        if (failure != null) throw failure;
        ReadSupport.check(null);
    }

    private int readChannel(ByteBuffer target) throws IOException {
        try {
            int n;
            do {
                check();
                n = channel.read(target);
                if (n == 0) Thread.onSpinWait();
            } while (n == 0);
            if (n < 0) eof = true;
            return n;
        } catch (IOException ex) {
            failure = ex;
            throw ex;
        }
    }

    private boolean refill() throws IOException {
        if (buffer.hasRemaining()) return true;
        if (eof) return false;
        buffer.clear();
        int n = readChannel(buffer);
        buffer.flip();
        return n >= 0;
    }

    @Override
    public int read() throws IOException {
        check();
        return refill() ? buffer.get() & 255 : -1;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        check();
        if (length == 0) return 0;
        if (!buffer.hasRemaining() && !buffer.isDirect() && length >= buffer.capacity())
            return eof ? -1 : readChannel(ByteBuffer.wrap(bytes, offset, length));
        if (!refill()) return -1;
        int n = Math.min(length, buffer.remaining());
        buffer.get(bytes, offset, n);
        return n;
    }

    @Override
    public int available() throws IOException {
        check();
        return buffer.remaining();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            channel.close();
        }
    }
}
