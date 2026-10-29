package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.*;

/** Own RLE1 -> cyclic BWT -> MTF/RUNA/B -> canonical Huffman BZIP2 encoder. */
public final class Bzip2OutputStream extends OutputStream {
    private final OutputStream out;
    private final Bzip2Support.BitsOut bits;
    private final byte[] block;
    private final long expected;
    private Bzip2Support.Crc crc = new Bzip2Support.Crc();
    private int used, runByte = -1, run, combined;
    private long written;
    private boolean finished, closed;

    public Bzip2OutputStream(OutputStream out, int level, long size) throws IOException {
        if (level < -1 || level > 9 || size < -1)
            throw new IllegalArgumentException("Invalid BZIP2 options");
        this.out = Objects.requireNonNull(out);
        expected = size;
        int blocks = level < 0 ? 5 : Math.max(1, level);
        block = new byte[blocks * 100000];
        bits = new Bzip2Support.BitsOut(out);
        out.write(new byte[] {'B', 'Z', 'h', (byte) ('0' + blocks)});
    }

    private void writable(int n) throws IOException {
        if (closed || finished) throw new IOException("BZIP2 stream finished");
        if (expected >= 0 && n > expected - written)
            throw new IOException("BZIP2 source exceeds declared size");
    }

    @Override
    public void write(int b) throws IOException {
        byte[] p = {(byte) b};
        write(p, 0, 1);
    }

    @Override
    public void write(byte[] b, int o, int n) throws IOException {
        Objects.checkFromIndexSize(o, n, b.length);
        writable(n);
        for (int i = o, e = o + n; i < e; i++) {
            if ((i & 4095) == 0) ReadSupport.check(null);
            int v = b[i] & 255;
            if (v == runByte && run < 259) run++;
            else {
                emitRun();
                runByte = v;
                run = 1;
            }
        }
        written = ReadSupport.add(written, n);
    }

    private void emitRun() throws IOException {
        if (run == 0) return;
        int encoded = run < 4 ? run : 5;
        if (used + encoded > block.length) encodeBlock();
        for (int i = 0; i < Math.min(run, 4); i++) block[used++] = (byte) runByte;
        if (run >= 4) block[used++] = (byte) (run - 4);
        for (int i = 0; i < run; i++) crc.update(runByte);
        run = 0;
    }

    private void encodeBlock() throws IOException {
        if (used == 0) return;
        ReadSupport.check(null);
        int blockCrc = crc.finish();
        Bzip2Support.Bwt bwt = Bzip2Support.transform(block, used);
        byte[] last = bwt.last();
        boolean[] inUse = new boolean[256];
        for (byte b : last) inUse[b & 255] = true;
        int nUsed = 0;
        for (boolean v : inUse) if (v) nUsed++;
        int alpha = nUsed + 2;
        int[] list = new int[nUsed];
        for (int i = 0, j = 0; i < 256; i++) if (inUse[i]) list[j++] = i;
        int[] symbols = new int[used + 1], frequency = new int[alpha];
        int count = 0, zeros = 0;
        for (int i = 0; i < last.length; i++) {
            if ((i & 4095) == 0) ReadSupport.check(null);
            int v = last[i] & 255, index = 0;
            while (list[index] != v) index++;
            if (index == 0) {
                zeros++;
                continue;
            }
            while (zeros > 0) {
                zeros--;
                int s = zeros & 1;
                symbols[count++] = s;
                frequency[s]++;
                zeros >>>= 1;
            }
            System.arraycopy(list, 0, list, 1, index);
            list[0] = v;
            symbols[count++] = index + 1;
            frequency[index + 1]++;
        }
        while (zeros > 0) {
            zeros--;
            int s = zeros & 1;
            symbols[count++] = s;
            frequency[s]++;
            zeros >>>= 1;
        }
        symbols[count++] = nUsed + 1;
        frequency[nUsed + 1]++;
        // Two identical adaptive trees are valid BZIP2; table selection remains bounded and simple.
        for (int i = 0; i < alpha; i++) frequency[i] = Math.max(1, frequency[i]);
        DeflateHuffman tree = new DeflateHuffman(alpha);
        tree.build(frequency, 15);
        int[] lengths = tree.lengths, codes = Bzip2Support.codes(lengths);
        bits.magic(Bzip2Support.BLOCK);
        bits.write(32, Integer.toUnsignedLong(blockCrc));
        bits.write(1, 0);
        bits.write(24, bwt.original());
        for (int group = 0; group < 16; group++) {
            boolean any = false;
            for (int i = 0; i < 16; i++) any |= inUse[group * 16 + i];
            bits.write(1, any ? 1 : 0);
        }
        for (int group = 0; group < 16; group++) {
            boolean any = false;
            for (int i = 0; i < 16; i++) any |= inUse[group * 16 + i];
            if (any) for (int i = 0; i < 16; i++) bits.write(1, inUse[group * 16 + i] ? 1 : 0);
        }
        int selectors = (count + 49) / 50;
        bits.write(3, 2);
        bits.write(15, selectors);
        for (int i = 0; i < selectors; i++) bits.write(1, 0);
        for (int table = 0; table < 2; table++) {
            int current = lengths[0];
            bits.write(5, current);
            for (int length : lengths) {
                while (current < length) {
                    bits.write(2, 2);
                    current++;
                }
                while (current > length) {
                    bits.write(2, 3);
                    current--;
                }
                bits.write(1, 0);
            }
        }
        for (int i = 0; i < count; i++) {
            if ((i & 4095) == 0) ReadSupport.check(null);
            int s = symbols[i];
            bits.write(lengths[s], codes[s]);
        }
        combined = Integer.rotateLeft(combined, 1) ^ blockCrc;
        used = 0;
        crc = new Bzip2Support.Crc();
    }

    public void finish() throws IOException {
        if (closed) throw new IOException("Stream closed");
        if (finished) return;
        if (expected >= 0 && written != expected)
            throw new EOFException("BZIP2 source shorter than declared size");
        emitRun();
        encodeBlock();
        bits.magic(Bzip2Support.END);
        bits.write(32, Integer.toUnsignedLong(combined));
        bits.align();
        finished = true;
    }

    @Override
    public void flush() throws IOException {
        if (closed) throw new IOException("Stream closed");
        if (!finished) {
            emitRun();
            encodeBlock();
        }
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (!closed)
            try {
                finish();
            } finally {
                closed = true;
                out.close();
            }
    }
}
