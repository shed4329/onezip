package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Independent MSB-first RAR LZ/Huffman decoder. See RAR-NOTICE.txt. UnRAR source code may be used
 * in any software to handle RAR archives without limitations free of charge, but cannot be used to
 * develop RAR (WinRAR) compatible archiver and to re-create RAR compression algorithm, which is
 * proprietary. Distribution of modified UnRAR source code in separate form or as a part of other
 * software is permitted, provided that full text of this paragraph, starting from "UnRAR source
 * code" words, is included in license, or in documentation if license is not available, and in
 * source code comments of resulting package.
 */
final class RarCodec {
    static IOException bad(String what) {
        return new IOException("RAR: " + what);
    }

    static final class Bits {
        final InputStream in;
        long cache, position, limit = Long.MAX_VALUE;
        int available;
        boolean eof;

        Bits(InputStream in) {
            this.in = new BufferedInputStream(in, 65536);
        }

        int peek(int n) throws IOException {
            while (available < n && !eof) {
                int b = in.read();
                if (b < 0) eof = true;
                else {
                    cache = (cache << 8) | b;
                    available += 8;
                }
            }
            return available >= n
                    ? (int) (cache >>> (available - n)) & ((1 << n) - 1)
                    : (int) (cache << (n - available)) & ((1 << n) - 1);
        }

        int take(int n) throws IOException {
            if (n == 0) return 0;
            int v = peek(n);
            if (available < n || position > limit - n) throw bad("壓縮位元流截斷");
            available -= n;
            position += n;
            cache &= (1L << available) - 1;
            return v;
        }

        long wide(int n) throws IOException {
            long v = 0;
            while (n > 0) {
                int k = Math.min(n, 16);
                v = (v << k) | take(k);
                n -= k;
            }
            return v;
        }

        void align() throws IOException {
            take((int) ((8 - position % 8) % 8));
        }
    }

    static final class Huff {
        final int[] lookup = new int[32768];

        Huff(int[] lengths, int start, int count) throws IOException {
            int[] counts = new int[16], codes = new int[16];
            for (int i = start; i < start + count; i++) {
                int n = lengths[i];
                if (n < 0 || n > 15) throw bad("Huffman 長度無效");
                counts[n]++;
            }
            int code = 0;
            for (int n = 1; n <= 15; n++) {
                code = (code + (n == 1 ? 0 : counts[n - 1])) << 1;
                if (code + counts[n] > (1 << n)) throw bad("Huffman 表過滿");
                codes[n] = code;
            }
            for (int i = 0; i < count; i++) {
                int n = lengths[start + i];
                if (n > 0) {
                    int from = codes[n]++ << (15 - n);
                    Arrays.fill(lookup, from, from + (1 << (15 - n)), (i << 4) | n);
                }
            }
        }

        int read(Bits bits) throws IOException {
            int e = lookup[bits.peek(15)];
            if (e == 0) throw bad("Huffman 碼無效");
            bits.take(e & 15);
            return e >>> 4;
        }
    }

    static final class State {
        final byte[] window;
        int wp;
        long history;
        final long[] old = new long[4];
        int last, low, repeats;
        int[] previous = new int[404];
        Huff literals, distances, lowDistances, lengths;
        boolean tables, ppmMode;
        RarPpm ppm;
        final ArrayList<int[]> vmFilters = new ArrayList<>();
        int lastFilter;

        State(int dictionary) {
            window = new byte[Math.max(1, dictionary)];
        }

        void reset() {
            wp = 0;
            history = 0;
            last = low = repeats = 0;
            tables = ppmMode = false;
            ppm = null;
            Arrays.fill(old, 0);
            Arrays.fill(previous, 0);
            vmFilters.clear();
            lastFilter = 0;
        }

        void put(int b) {
            window[wp++] = (byte) b;
            if (wp == window.length) wp = 0;
            history++;
        }

        void insert(long d) {
            System.arraycopy(old, 0, old, 1, 3);
            old[0] = d;
        }
    }

    private static final int[] LB = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 14, 16, 20, 24, 28, 32, 40, 48, 56, 64, 80, 96, 112, 128,
        160, 192, 224
    };
    private static final int[] LX = {
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5
    };
    private static final int[] DB = new int[60], DX = new int[60];

    static {
        int slot = 0, d = 0;
        int[] counts = {4, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 14, 0, 12};
        for (int b = 0; b < counts.length; b++)
            for (int j = 0; j < counts[b]; j++) {
                DB[slot] = d;
                DX[slot++] = b;
                d += 1 << b;
            }
    }

    record Filter(long start, int length, int type, int channels, int[] parameters) {
        Filter(long start, int length, int type, int channels) {
            this(start, length, type, channels, null);
        }
    }

    static final class Decoder extends InputStream {
        final State s;
        final Bits bits;
        final boolean rar5, extraDist;
        final long size;
        final BooleanSupplier cancel;
        final ArrayDeque<Filter> filters = new ArrayDeque<>();
        long produced, delivered, matchDistance;
        int match;
        boolean ended, lastBlock;
        long blockEnd;
        byte[] output = new byte[65536];
        int pos, end;

        Decoder(
                State state,
                InputStream in,
                long size,
                boolean five,
                boolean extended,
                boolean solid,
                BooleanSupplier cancel)
                throws IOException {
            s = state;
            bits = new Bits(in);
            this.size = size;
            rar5 = five;
            extraDist = extended;
            this.cancel = cancel;
            if (!solid) s.reset();
            if (five) block();
            else if (!s.tables) tables(false);
            else if (s.ppmMode) s.ppm.bits = bits;
        }

        void tables(boolean five) throws IOException {
            if (!five) {
                bits.align();
                if (bits.peek(1) != 0) {
                    s.ppmMode = true;
                    if (s.ppm == null) s.ppm = new RarPpm(cancel);
                    s.ppm.init(bits);
                    return;
                }
                s.ppmMode = false;
                bits.take(1);
                if (bits.take(1) == 0) Arrays.fill(s.previous, 0);
                s.low = s.repeats = 0;
            }
            int[] pre = new int[20];
            for (int i = 0; i < 20; i++) {
                int v = bits.take(4);
                if (v == 15) {
                    int z = bits.take(4);
                    if (z != 0) {
                        i += Math.min(z + 2, 20 - i) - 1;
                        continue;
                    }
                }
                pre[i] = v;
            }
            Huff h = new Huff(pre, 0, 20);
            int nc = five ? 306 : 299,
                    dc = five ? (extraDist ? 80 : 64) : 60,
                    ld = five ? 16 : 17,
                    rc = five ? 44 : 28;
            int[] t = new int[nc + dc + ld + rc];
            for (int i = 0; i < t.length; ) {
                int sym = h.read(bits);
                if (sym < 16) {
                    t[i] = five ? sym : (sym + s.previous[i]) & 15;
                    i++;
                } else {
                    int n = (sym == 16 || sym == 18) ? 3 + bits.take(3) : 11 + bits.take(7);
                    if (sym < 18 && i == 0) throw bad("Huffman 重複碼沒有前值");
                    // RAR permits the final run to extend past the table boundary.
                    int value = sym < 18 ? t[i - 1] : 0;
                    int count = Math.min(n, t.length - i);
                    Arrays.fill(t, i, i + count, value);
                    i += count;
                }
            }
            s.literals = new Huff(t, 0, nc);
            s.distances = new Huff(t, nc, dc);
            s.lowDistances = new Huff(t, nc + dc, ld);
            s.lengths = new Huff(t, nc + dc + ld, rc);
            if (!five) s.previous = t;
            s.tables = true;
        }

        void block() throws IOException {
            bits.limit = Long.MAX_VALUE;
            bits.align();
            int flags = bits.take(8), check = bits.take(8), n = ((flags >>> 3) & 3) + 1;
            if (n == 4) throw bad("區塊大小欄位無效");
            int bytes = 0;
            for (int i = 0; i < n; i++) bytes |= bits.take(8) << (8 * i);
            if (((0x5a ^ flags ^ bytes ^ (bytes >>> 8) ^ (bytes >>> 16)) & 255) != check)
                throw bad("壓縮區塊校驗失敗");
            blockEnd = bits.position + (bytes == 0 ? 0 : (bytes - 1L) * 8 + (flags & 7) + 1);
            bits.limit = blockEnd;
            lastBlock = (flags & 64) != 0;
            if ((flags & 128) != 0) tables(true);
            if (!s.tables) throw bad("缺少 Huffman 表");
        }

        int length5(int slot) throws IOException {
            if (slot < 8) return slot + 2;
            int b = slot / 4 - 1;
            return 2 + ((4 | (slot & 3)) << b) + bits.take(b);
        }

        long filterData() throws IOException {
            int n = bits.take(2) + 1;
            long v = 0;
            for (int i = 0; i < n; i++) v |= (long) bits.take(8) << (8 * i);
            return v;
        }

        void prepare() throws IOException {
            while (match == 0 && !ended) {
                if ((produced & 65535) == 0) ReadSupport.check(cancel);
                if (rar5) {
                    while (bits.position == blockEnd) {
                        if (lastBlock) {
                            ended = true;
                            break;
                        }
                        block();
                    }
                    if (ended) return;
                }
                if (!rar5 && s.ppmMode) {
                    int c = s.ppm.read();
                    if (c == s.ppm.escape) {
                        int next = s.ppm.read();
                        if (next == 0) {
                            tables(false);
                            continue;
                        }
                        if (next == 2) {
                            ended = true;
                            return;
                        }
                        if (next == 3) {
                            vmFilter();
                            continue;
                        }
                        if (next == 4) {
                            long d = 0;
                            for (int i = 0; i < 3; i++) d = (d << 8) | s.ppm.read();
                            int length = s.ppm.read() + 32, last = s.last;
                            startMatch(length, d + 2);
                            s.last = last;
                            return;
                        }
                        if (next == 5) {
                            int length = s.ppm.read() + 4, last = s.last;
                            startMatch(length, 1);
                            s.last = last;
                            return;
                        }
                        if (next != 1) throw bad("無效 PPMd escape 碼");
                    }
                    match = -c - 1;
                    return;
                }
                int c = s.literals.read(bits);
                if (c < 256) {
                    match = -c - 1;
                    return;
                }
                if (rar5) {
                    if (c >= 262) {
                        int len = length5(c - 262),
                                slot = s.distances.read(bits),
                                b = slot < 4 ? 0 : slot / 2 - 1;
                        long d = slot < 4 ? slot + 1 : 1 + ((long) (2 | (slot & 1)) << b);
                        if (b >= 4) {
                            d += (bits.wide(b - 4) << 4) + s.lowDistances.read(bits);
                        } else d += bits.take(b);
                        if (d > 256) len++;
                        if (d > 8192) len++;
                        if (d > 262144) len++;
                        s.insert(d);
                        startMatch(len, d);
                    } else if (c == 256) {
                        long offset = filterData(), length = filterData();
                        int type = bits.take(3), channels = type == 0 ? bits.take(5) + 1 : 0;
                        if (type > 3
                                || length == 0
                                || length > 4 * 1024 * 1024
                                || offset > s.window.length) throw bad("不支援或無效的 RAR5 篩選器");
                        if (filters.size() >= 8192) throw bad("篩選器數量過多");
                        filters.add(new Filter(produced + offset, (int) length, type, channels));
                    } else if (c == 257) {
                        if (s.last == 0) throw bad("重複匹配沒有前值");
                        startMatch(s.last, s.old[0]);
                    } else {
                        int index = c - 258;
                        long d = s.old[index];
                        for (int i = index; i > 0; i--) s.old[i] = s.old[i - 1];
                        s.old[0] = d;
                        startMatch(length5(s.lengths.read(bits)), d);
                    }
                } else {
                    if (c >= 271) {
                        int slot = c - 271, len = LB[slot] + 3 + bits.take(LX[slot]);
                        int ds = s.distances.read(bits), b = DX[ds];
                        long d = DB[ds] + 1L;
                        if (ds > 9) {
                            d += bits.wide(b - 4) << 4;
                            if (s.repeats > 0) {
                                s.repeats--;
                                d += s.low;
                            } else {
                                int low = s.lowDistances.read(bits);
                                if (low == 16) {
                                    s.repeats = 15;
                                    d += s.low;
                                } else {
                                    d += low;
                                    s.low = low;
                                }
                            }
                        } else d += bits.take(b);
                        if (d >= 8192) len++;
                        if (d >= 262144) len++;
                        s.insert(d);
                        startMatch(len, d);
                    } else if (c == 256) {
                        if (bits.take(1) == 1) {
                            s.tables = false;
                            tables(false);
                        } else {
                            s.tables = bits.take(1) == 0;
                            ended = true;
                        }
                    } else if (c == 257) vmFilter();
                    else if (c == 258) {
                        if (s.last == 0) throw bad("重複匹配沒有前值");
                        startMatch(s.last, s.old[0]);
                    } else if (c < 263) {
                        int index = c - 259;
                        long d = s.old[index];
                        for (int i = index; i > 0; i--) s.old[i] = s.old[i - 1];
                        s.old[0] = d;
                        int ls = s.lengths.read(bits);
                        startMatch(LB[ls] + 2 + bits.take(LX[ls]), d);
                    } else {
                        int[] base = {0, 4, 8, 16, 32, 64, 128, 192},
                                extra = {2, 2, 3, 4, 5, 6, 6, 6};
                        int slot = c - 263;
                        long d = base[slot] + 1 + bits.take(extra[slot]);
                        s.insert(d);
                        startMatch(2, d);
                    }
                }
            }
        }

        void vmFilter() throws IOException {
            int first = filterByte(), n = (first & 7) + 1;
            if (n == 7) n = filterByte() + 7;
            else if (n == 8) n = (filterByte() << 8) | filterByte();
            if (n == 0) throw bad("空 VM 描述符");
            byte[] descriptor = new byte[n];
            for (int i = 0; i < n; i++) descriptor[i] = (byte) filterByte();
            Bits vm = new Bits(new ByteArrayInputStream(descriptor));
            int index = s.lastFilter;
            if ((first & 128) != 0) {
                index = vmNumber(vm);
                if (index == 0) {
                    s.vmFilters.clear();
                    s.lastFilter = 0;
                } else index--;
            }
            if (index < 0 || index > s.vmFilters.size() || index > 1024) throw bad("VM 篩選器索引無效");
            s.lastFilter = index;
            boolean fresh = index == s.vmFilters.size();
            long offset = Integer.toUnsignedLong(vmNumber(vm));
            if ((first & 64) != 0) offset += 258;
            int[] definition = fresh ? new int[] {-1, 0} : s.vmFilters.get(index);
            int length = (first & 32) != 0 ? vmNumber(vm) : definition[1];
            definition[1] = length;
            int[] registers = new int[7];
            registers[4] = length;
            if ((first & 16) != 0) {
                int mask = vm.take(7);
                for (int i = 0; i < 7; i++) if ((mask & (1 << i)) != 0) registers[i] = vmNumber(vm);
            }
            if (fresh) {
                int codeSize = vmNumber(vm);
                if (codeSize <= 0 || codeSize >= 65536) throw bad("VM 程式長度無效");
                byte[] code = new byte[codeSize];
                for (int i = 0; i < codeSize; i++) code[i] = (byte) vm.take(8);
                int xor = 0;
                for (byte b : code) xor ^= b;
                if (xor != 0) throw bad("VM 程式校驗失敗");
                long crc = Crc32.of(code);
                int[] sizes = {29, 53, 57, 120, 149, 216};
                long[] hashes = {
                    0x0e06077dL, 0xad576887L, 0x3cd7e57eL, 0x3769893fL, 0x1c2c5dc8L, 0xbc85e701L
                };
                int[] types = {0, 1, 2, 4, 5, 6};
                for (int i = 0; i < sizes.length; i++)
                    if (codeSize == sizes[i] && crc == hashes[i]) definition[0] = types[i];
                if (definition[0] < 0) throw bad("不支援自訂 RAR4 VM 程式");
                s.vmFilters.add(definition);
            }
            if ((first & 8) != 0) throw bad("不支援 VM 全域資料");
            if (length <= 0
                    || length > 262144
                    || offset > s.window.length
                    || filters.size() >= 8192) throw bad("VM 篩選器範圍無效");
            if ((definition[0] == 0 || definition[0] == 6)
                    && (registers[0] < 1 || registers[0] > 128)) throw bad("VM 聲道數無效");
            filters.add(
                    new Filter(produced + offset, length, definition[0], registers[0], registers));
        }

        int filterByte() throws IOException {
            return s.ppmMode ? s.ppm.read() : bits.take(8);
        }

        int vmNumber(Bits in) throws IOException {
            int type = in.take(2);
            return switch (type) {
                case 0 -> in.take(4);
                case 1 -> {
                    int v = in.take(8);
                    if ((v & 0xf0) == 0) v = 0xffffff00 | (v << 4) | in.take(4);
                    yield v;
                }
                case 2 -> in.take(16);
                default -> (int) in.wide(32);
            };
        }

        void startMatch(int length, long distance) throws IOException {
            if (distance < 1
                    || distance > s.window.length
                    || distance > s.history
                    || length > size - produced) throw bad("匹配距離或長度超出字典／輸出範圍");
            match = length;
            matchDistance = distance;
            s.last = length;
        }

        int raw() throws IOException {
            prepare();
            if (ended) return -1;
            if (produced >= size) throw bad("解壓大小超過宣告");
            int b;
            if (match < 0) {
                b = -match - 1;
                match = 0;
            } else {
                int p = s.wp - (int) matchDistance;
                if (p < 0) p += s.window.length;
                b = s.window[p] & 255;
                match--;
            }
            s.put(b);
            produced++;
            return b;
        }

        int raw(byte[] out, int offset, int wanted) throws IOException {
            prepare();
            if (ended) return -1;
            if (match < 0) {
                out[offset] = (byte) raw();
                return 1;
            }
            int n = Math.min(match, wanted),
                    seed = (int) Math.min(n, matchDistance),
                    src = s.wp - (int) matchDistance;
            if (src < 0) src += s.window.length;
            int first = Math.min(seed, s.window.length - src);
            System.arraycopy(s.window, src, out, offset, first);
            System.arraycopy(s.window, 0, out, offset + first, seed - first);
            for (int copied = seed; copied < n; ) {
                int k = Math.min(copied, n - copied);
                System.arraycopy(out, offset, out, offset + copied, k);
                copied += k;
            }
            // A match can wrap a short dictionary more than once. Keep its final window.
            int tail = Math.min(n, s.window.length),
                    newWp = (int) (((long) s.wp + n) % s.window.length),
                    dest = newWp - tail;
            if (dest < 0) dest += s.window.length;
            int k = Math.min(tail, s.window.length - dest);
            System.arraycopy(out, offset + n - tail, s.window, dest, k);
            System.arraycopy(out, offset + n - tail + k, s.window, 0, tail - k);
            s.wp = newWp;
            s.history += n;
            produced += n;
            match -= n;
            return n;
        }

        void fill() throws IOException {
            pos = end = 0;
            output = output.length == 65536 ? output : new byte[65536];
            while (end < output.length) {
                prepare();
                Filter f = filters.peek();
                if (f != null && f.start < produced) throw bad("篩選器區塊重疊");
                if (f != null && f.start == produced) {
                    if (end > 0) break;
                    filters.remove();
                    if (f.length > size - produced) throw bad("篩選器超出檔案範圍");
                    byte[] data = new byte[f.length];
                    for (int i = 0; i < data.length; ) {
                        int n = raw(data, i, data.length - i);
                        if (n < 0) throw bad("篩選器資料截斷");
                        i += n;
                    }
                    output = apply(data, f, delivered);
                    while (!filters.isEmpty()
                            && filters.peek().start == f.start
                            && filters.peek().length == f.length)
                        output = apply(output, filters.remove(), delivered);
                    end = output.length;
                    break;
                }
                int wanted = output.length - end;
                if (f != null) wanted = (int) Math.min(wanted, f.start - produced);
                int n = raw(output, end, wanted);
                if (n < 0) {
                    if (produced != size || !filters.isEmpty()) throw bad("解壓長度或篩選器範圍不符");
                    break;
                }
                end += n;
            }
        }

        public int read() throws IOException {
            if (pos == end) fill();
            if (pos == end) return -1;
            delivered++;
            return output[pos++] & 255;
        }

        public int read(byte[] b, int o, int n) throws IOException {
            Objects.checkFromIndexSize(o, n, b.length);
            if (n == 0) return 0;
            if (pos == end) fill();
            if (pos == end) return -1;
            int k = Math.min(n, end - pos);
            System.arraycopy(output, pos, b, o, k);
            pos += k;
            delivered += k;
            return k;
        }
    }

    static byte[] apply(byte[] data, Filter f, long fileOffset) throws IOException {
        if (f.type >= 4) return applyLegacy(data, f, fileOffset);
        if (f.type == 0) {
            byte[] out = new byte[data.length];
            int src = 0;
            for (int c = 0; c < f.channels; c++) {
                int prev = 0;
                for (int p = c; p < out.length; p += f.channels)
                    out[p] = (byte) (prev -= data[src++] & 255);
            }
            return out;
        }
        if (f.type == 3) {
            for (int p = 0; p + 3 < data.length; p += 4)
                if ((data[p + 3] & 255) == 235) {
                    int addr =
                            (data[p] & 255)
                                    | ((data[p + 1] & 255) << 8)
                                    | ((data[p + 2] & 255) << 16);
                    addr -= (int) ((fileOffset + p) & 0xffffffffL) >>> 2;
                    data[p] = (byte) addr;
                    data[p + 1] = (byte) (addr >>> 8);
                    data[p + 2] = (byte) (addr >>> 16);
                }
            return data;
        }
        for (int p = 0; p + 4 < data.length; ) {
            int opcode = data[p++] & 255;
            if (opcode == 232 || (f.type == 2 && opcode == 233)) {
                int
                        addr =
                                (data[p] & 255)
                                        | ((data[p + 1] & 255) << 8)
                                        | ((data[p + 2] & 255) << 16)
                                        | (data[p + 3] << 24),
                        offset = (int) (fileOffset + p);
                if (f.parameters == null) offset &= 0xffffff;
                if (addr < 0) {
                    if (addr + offset >= 0) addr += 0x1000000;
                } else if (addr < 0x1000000) addr -= offset;
                for (int j = 0; j < 4; j++) data[p + j] = (byte) (addr >>> (j * 8));
                p += 4;
            }
        }
        return data;
    }

    static byte[] applyLegacy(byte[] data, Filter f, long offset) throws IOException {
        int length = data.length;
        int[] r = f.parameters;
        if (r == null) throw bad("缺少 VM 參數");
        if (f.type == 4) {
            int[] masks = {4, 4, 6, 6, 0, 0, 7, 7, 4, 4, 0, 0, 4, 4, 0, 0};
            for (int p = 0; p < length - 21; p += 16) {
                int cmd = (data[p] & 31) - 16;
                if (cmd >= 0)
                    for (int i = 0; i < 3; i++)
                        if ((masks[cmd] & (1 << i)) != 0) {
                            int start = i * 41 + 5;
                            if (getField(data, p, start + 37, 4) == 5) {
                                int addr =
                                        getField(data, p, start + 13, 20)
                                                - (int) ((offset + p) >>> 4);
                                putField(data, p, start + 13, 20, addr);
                            }
                        }
            }
            return data;
        }
        if (length > 131072) throw bad("VM 篩選器區塊過大");
        byte[] out = new byte[length];
        int src = 0;
        if (f.type == 5) {
            int width = r[0] - 3, posR = r[1];
            if (length < 3 || width < 0 || width > length || posR < 0 || posR > 2)
                throw bad("RGB 參數無效");
            for (int c = 0; c < 3; c++) {
                int prev = 0;
                for (int i = c; i < length; i += 3) {
                    int predicted = prev;
                    if (i >= width + 3) {
                        int upper = out[i - width] & 255, left = out[i - width - 3] & 255;
                        predicted = prev + upper - left;
                        int a = Math.abs(predicted - prev),
                                b = Math.abs(predicted - upper),
                                d = Math.abs(predicted - left);
                        predicted = a <= b && a <= d ? prev : b <= d ? upper : left;
                    }
                    prev = (predicted - (data[src++] & 255)) & 255;
                    out[i] = (byte) prev;
                }
            }
            for (int i = posR; i < length - 2; i += 3) {
                out[i] += out[i + 1];
                out[i + 2] += out[i + 1];
            }
            return out;
        }
        if (f.type == 6) {
            for (int c = 0; c < f.channels; c++) {
                int prev = 0, delta = 0, d1 = 0, d2 = 0, k1 = 0, k2 = 0, k3 = 0;
                int[] dif = new int[7];
                int count = 0;
                for (int i = c; i < length; i += f.channels, count++) {
                    int d3 = d2;
                    d2 = delta - d1;
                    d1 = delta;
                    int predicted = ((8 * prev + k1 * d1 + k2 * d2 + k3 * d3) >>> 3) & 255;
                    int cur = data[src++] & 255;
                    predicted -= cur;
                    out[i] = (byte) predicted;
                    delta = (byte) (predicted - prev);
                    prev = predicted;
                    int d = ((byte) cur) << 3;
                    dif[0] += Math.abs(d);
                    dif[1] += Math.abs(d - d1);
                    dif[2] += Math.abs(d + d1);
                    dif[3] += Math.abs(d - d2);
                    dif[4] += Math.abs(d + d2);
                    dif[5] += Math.abs(d - d3);
                    dif[6] += Math.abs(d + d3);
                    if ((count & 31) == 0) {
                        int min = 0;
                        for (int j = 1; j < 7; j++) if (dif[j] < dif[min]) min = j;
                        Arrays.fill(dif, 0);
                        switch (min) {
                            case 1 -> {
                                if (k1 >= -16) k1--;
                            }
                            case 2 -> {
                                if (k1 < 16) k1++;
                            }
                            case 3 -> {
                                if (k2 >= -16) k2--;
                            }
                            case 4 -> {
                                if (k2 < 16) k2++;
                            }
                            case 5 -> {
                                if (k3 >= -16) k3--;
                            }
                            case 6 -> {
                                if (k3 < 16) k3++;
                            }
                        }
                    }
                }
            }
            return out;
        }
        throw bad("未知 VM 篩選器");
    }

    static int getField(byte[] b, int p, int bit, int count) {
        int value = 0;
        for (int i = 0; i < count; i++)
            value |= ((b[p + (bit + i) / 8] >>> ((bit + i) % 8)) & 1) << i;
        return value;
    }

    static void putField(byte[] b, int p, int bit, int count, int value) {
        for (int i = 0; i < count; i++) {
            int pos = p + (bit + i) / 8, mask = 1 << ((bit + i) % 8);
            b[pos] = (byte) ((b[pos] & ~mask) | (((value >>> i) & 1) != 0 ? mask : 0));
        }
    }
}
