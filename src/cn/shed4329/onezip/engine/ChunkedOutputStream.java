package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.*;

/**
 * Bounded, thread-confined buffer. Closing seals immutable chunks for replay without flattening.
 */
public final class ChunkedOutputStream extends OutputStream {
    private final List<byte[]> chunks = new ArrayList<>();
    private final int maximum, chunkSize;
    private int size;
    private boolean sealed;

    public ChunkedOutputStream(int maximum) {
        this(maximum, 65536);
    }

    public ChunkedOutputStream(int maximum, int chunkSize) {
        if (maximum < 0 || chunkSize < 1 || chunkSize > 1048576)
            throw new IllegalArgumentException("Invalid buffer limit or chunk size");
        this.maximum = maximum;
        this.chunkSize = chunkSize;
    }

    public int size() {
        return size;
    }

    private void check(int length) throws IOException {
        if (sealed) throw new IOException("Buffer sealed");
        if (length > maximum - size) throw new IOException("Chunked buffer limit exceeded");
    }

    private byte[] tail() {
        int index = size / chunkSize;
        if (index == chunks.size()) chunks.add(new byte[Math.min(chunkSize, maximum - size)]);
        return chunks.get(index);
    }

    @Override
    public void write(int value) throws IOException {
        check(1);
        tail()[size % chunkSize] = (byte) value;
        size++;
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        check(length);
        while (length > 0) {
            byte[] chunk = tail();
            int start = size % chunkSize, n = Math.min(length, chunk.length - start);
            System.arraycopy(bytes, offset, chunk, start, n);
            size += n;
            offset += n;
            length -= n;
        }
    }

    /**
     * Seal before sharing the buffer with another thread; its publication still needs a
     * happens-before edge.
     */
    @Override
    public void close() {
        sealed = true;
    }

    public void writeTo(OutputStream destination) throws IOException {
        Objects.requireNonNull(destination);
        if (destination == this) throw new IllegalArgumentException("Cannot replay into itself");
        if (!sealed) throw new IOException("Seal buffer before replay");
        int left = size;
        for (byte[] chunk : chunks) {
            ReadSupport.check(null);
            int n = Math.min(left, chunk.length);
            destination.write(chunk, 0, n);
            left -= n;
        }
    }

    /**
     * Independent read cursor over sealed chunks. No payload copy; closing a view leaves other
     * views usable.
     */
    public InputStream openInputStream() throws IOException {
        if (!sealed) throw new IOException("Seal buffer before reading");
        return new InputStream() {
            int position;
            boolean closed;

            private void checkOpen() throws IOException {
                if (closed) throw new IOException("Stream closed");
            }

            @Override
            public int read() throws IOException {
                checkOpen();
                if (position == size) return -1;
                int value = chunks.get(position / chunkSize)[position % chunkSize] & 255;
                position++;
                return value;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                Objects.checkFromIndexSize(offset, length, bytes.length);
                checkOpen();
                if (length == 0) return 0;
                if (position == size) return -1;
                int total = Math.min(length, size - position), left = total;
                while (left > 0) {
                    byte[] chunk = chunks.get(position / chunkSize);
                    int start = position % chunkSize, n = Math.min(left, chunk.length - start);
                    System.arraycopy(chunk, start, bytes, offset, n);
                    position += n;
                    offset += n;
                    left -= n;
                }
                return total;
            }

            @Override
            public long skip(long n) throws IOException {
                checkOpen();
                int k = (int) Math.min(Math.max(0, n), size - position);
                position += k;
                return k;
            }

            @Override
            public int available() throws IOException {
                checkOpen();
                return size - position;
            }

            @Override
            public long transferTo(OutputStream out) throws IOException {
                Objects.requireNonNull(out);
                checkOpen();
                long total = size - position;
                while (position < size) {
                    ReadSupport.check(null);
                    byte[] chunk = chunks.get(position / chunkSize);
                    int start = position % chunkSize,
                            n = Math.min(size - position, chunk.length - start);
                    out.write(chunk, start, n);
                    position += n;
                }
                return total;
            }

            @Override
            public void close() {
                closed = true;
            }
        };
    }
}
