package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.*;

/** Independent BZIP2 decoder: checked Huffman/MTF, inverse BWT and streaming inverse RLE1. */
public final class Bzip2InputStream extends InputStream {
    private final InputStream in;
    private final Bzip2Support.BitsIn bits;
    private byte[] last;
    private int[] next;
    private int maximum, position, remaining, runByte = -1, run, repeats, storedCrc, combined;
    private Bzip2Support.Crc crc;
    private boolean active, ended, closed;
    private final byte[] one = new byte[1];

    public Bzip2InputStream(InputStream source) throws IOException {
        in = new BufferedInputStream(Objects.requireNonNull(source), 65536);
        bits = new Bzip2Support.BitsIn(in);
        try {
            header(ReadSupport.u8(in));
        } catch (IOException | RuntimeException ex) {
            try {
                in.close();
            } catch (IOException close) {
                ex.addSuppressed(close);
            }
            throw ex;
        }
    }

    private void header(int first) throws IOException {
        if (first != 'B' || ReadSupport.u8(in) != 'Z' || ReadSupport.u8(in) != 'h')
            throw new IOException("Invalid BZIP2 signature");
        int level = ReadSupport.u8(in) - '0';
        if (level < 1 || level > 9) throw new IOException("Invalid BZIP2 block size");
        maximum = level * 100000;
        combined = 0;
    }

    private static final class Symbols {
        final Bzip2Support.BitsIn bits;
        final byte[] selectors;
        final Bzip2Support.Tree[] trees;
        int group = -1, left;

        Symbols(Bzip2Support.BitsIn b, byte[] s, Bzip2Support.Tree[] t) {
            bits = b;
            selectors = s;
            trees = t;
        }

        int read() throws IOException {
            if (left == 0) {
                if (++group >= selectors.length) throw new IOException("BZIP2 selectors exhausted");
                left = 50;
            }
            left--;
            return trees[selectors[group] & 255].read(bits);
        }
    }

    private boolean block() throws IOException {
        while (!ended) {
            ReadSupport.check(null);
            long magic = bits.magic();
            if (magic == Bzip2Support.END) {
                if (bits.read(32) != combined) throw new IOException("BZIP2 combined CRC mismatch");
                bits.align();
                int b = in.read();
                if (b < 0) {
                    ended = true;
                    return false;
                }
                header(b);
                continue;
            }
            if (magic != Bzip2Support.BLOCK) throw new IOException("BZIP2 invalid block marker");
            storedCrc = bits.read(32);
            if (bits.read(1) != 0)
                throw new IOException("Legacy randomized BZIP2 blocks are unsupported");
            int original = bits.read(24);
            boolean[] used = new boolean[256], groups = new boolean[16];
            for (int i = 0; i < 16; i++) groups[i] = bits.read(1) != 0;
            int nUsed = 0;
            for (int i = 0; i < 16; i++)
                if (groups[i])
                    for (int k = 0; k < 16; k++)
                        if (bits.read(1) != 0) {
                            used[i * 16 + k] = true;
                            nUsed++;
                        }
            if (nUsed == 0) throw new IOException("BZIP2 empty alphabet");
            int nGroups = bits.read(3), nSelectors = bits.read(15);
            if (nGroups < 2 || nGroups > 6 || nSelectors < 1)
                throw new IOException("BZIP2 Huffman group count invalid");
            byte[] selectors = new byte[nSelectors], order = new byte[nGroups];
            for (int i = 0; i < nGroups; i++) order[i] = (byte) i;
            for (int i = 0; i < nSelectors; i++) {
                int index = 0;
                while (bits.read(1) != 0)
                    if (++index >= nGroups) throw new IOException("BZIP2 selector MTF invalid");
                byte value = order[index];
                System.arraycopy(order, 0, order, 1, index);
                order[0] = value;
                selectors[i] = value;
            }
            int alphabet = nUsed + 2;
            Bzip2Support.Tree[] trees = new Bzip2Support.Tree[nGroups];
            for (int i = 0; i < nGroups; i++) {
                int current = bits.read(5);
                int[] lengths = new int[alphabet];
                for (int k = 0; k < alphabet; k++) {
                    if (current < 1 || current > 20)
                        throw new IOException("BZIP2 Huffman length invalid");
                    while (bits.read(1) != 0) {
                        current += bits.read(1) == 0 ? 1 : -1;
                        if (current < 1 || current > 20)
                            throw new IOException("BZIP2 Huffman length invalid");
                    }
                    lengths[k] = current;
                }
                trees[i] = new Bzip2Support.Tree(lengths);
            }
            int[] mtf = new int[nUsed];
            for (int i = 0, j = 0; i < 256; i++) if (used[i]) mtf[j++] = i;
            last = new byte[maximum];
            Symbols symbols = new Symbols(bits, selectors, trees);
            int length = 0, symbol = symbols.read(), eob = nUsed + 1;
            while (symbol != eob) {
                ReadSupport.check(null);
                if (symbol <= 1) {
                    long count = 0, weight = 1;
                    do {
                        count += (symbol + 1) * weight;
                        if (count > maximum - length)
                            throw new IOException("BZIP2 RUNA/B range invalid");
                        weight *= 2;
                        symbol = symbols.read();
                    } while (symbol <= 1);
                    Arrays.fill(last, length, length + (int) count, (byte) mtf[0]);
                    length += (int) count;
                } else {
                    int index = symbol - 1;
                    if (index < 1 || index >= nUsed || length == maximum)
                        throw new IOException("BZIP2 MTF range invalid");
                    int value = mtf[index];
                    System.arraycopy(mtf, 0, mtf, 1, index);
                    mtf[0] = value;
                    last[length++] = (byte) value;
                    symbol = symbols.read();
                }
            }
            if (length == 0 || original >= length)
                throw new IOException("BZIP2 original BWT pointer invalid");
            int[] start = new int[257];
            for (int i = 0; i < length; i++) start[(last[i] & 255) + 1]++;
            for (int i = 1; i < start.length; i++) start[i] += start[i - 1];
            next = new int[length];
            for (int i = 0; i < length; i++) next[start[last[i] & 255]++] = i;
            position = next[original];
            remaining = length;
            runByte = -1;
            run = repeats = 0;
            crc = new Bzip2Support.Crc();
            active = true;
            return true;
        }
        return false;
    }

    private int inverse() {
        int b = last[position] & 255;
        position = next[position];
        remaining--;
        return b;
    }

    private int value() throws IOException {
        while (!ended) {
            if (!active && !block()) return -1;
            int b;
            if (repeats > 0) {
                repeats--;
                b = runByte;
            } else if (remaining > 0) {
                b = inverse();
                if (b == runByte) run++;
                else {
                    runByte = b;
                    run = 1;
                }
                if (run == 4) {
                    if (remaining == 0) throw new IOException("BZIP2 RLE count missing");
                    repeats = inverse();
                    run = 0;
                }
            } else {
                int actual = crc.finish();
                if (actual != storedCrc) throw new IOException("BZIP2 block CRC mismatch");
                combined = Integer.rotateLeft(combined, 1) ^ actual;
                active = false;
                continue;
            }
            crc.update(b);
            return b;
        }
        return -1;
    }

    @Override
    public int read() throws IOException {
        if (closed) throw new IOException("Stream closed");
        ReadSupport.check(null);
        return value();
    }

    @Override
    public int read(byte[] b, int o, int n) throws IOException {
        Objects.checkFromIndexSize(o, n, b.length);
        if (closed) throw new IOException("Stream closed");
        if (n == 0) return 0;
        int done = 0;
        while (done < n) {
            if ((done & 4095) == 0) ReadSupport.check(null);
            int v = value();
            if (v < 0) break;
            b[o + done++] = (byte) v;
        }
        return done == 0 ? -1 : done;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            in.close();
        }
    }
}
