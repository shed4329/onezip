package cn.shed4329.onezip.engine;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * PPMd variant H used by RAR3/4. Java arena uses checked integer offsets, never native pointers.
 * Model reference: Dmitry Shkarin's public-domain PPMd and Alexander Roshal's UnRAR adaptation; see
 * RAR-NOTICE.txt and RarCodec.
 */
final class RarPpm {
    static IOException bad() {
        return RarCodec.bad("PPMd 模型／範圍編碼資料損壞");
    }

    static final int MAX_FREQ = 124;
    final BooleanSupplier cancel;
    RarCodec.Bits bits;
    Arena a;
    int min,
            max,
            found,
            masked,
            initEsc,
            fall,
            order,
            run,
            initRun,
            esc = 1,
            success,
            hi,
            escape = 2;
    long low, code, range;
    final int[] masks = new int[256],
            nsIndex = new int[256],
            bsIndex = new int[256],
            high = new int[256],
            stack = new int[256];
    final int[][] bin = new int[128][64];
    final See[][] see = new See[25][16];
    final See dummy = new See();

    RarPpm(BooleanSupplier cancel) {
        this.cancel = cancel;
        for (int i = 0; i < 25; i++) for (int j = 0; j < 16; j++) see[i][j] = new See();
    }

    static final class See {
        int sum, shift, count;

        void init(int v) {
            shift = 3;
            sum = v << 3;
            count = 4;
        }

        int mean() {
            int v = sum >>> shift;
            sum = (sum - v) & 65535;
            return v == 0 ? 1 : v;
        }

        void update() {
            if (shift < 7 && --count == 0) {
                sum = (sum * 2) & 65535;
                count = 3 << shift++;
            }
        }
    }

    void init(RarCodec.Bits bits) throws IOException {
        this.bits = bits;
        bits.align();
        int flags = bits.take(8), mb = (flags & 32) != 0 ? bits.take(8) + 1 : 0;
        if ((flags & 64) != 0) escape = bits.take(8);
        if (mb == 0 && a == null) throw bad();
        low = code = 0;
        range = 0xffffffffL;
        for (int i = 0; i < 4; i++) code = (code << 8) | bits.take(8);
        if (mb > 0) {
            int n = (flags & 31) + 1;
            if (n > 16) n = 16 + (n - 16) * 3;
            if (n < 2 || n > 64) throw bad();
            long bytes = (long) mb << 20;
            if (bytes > Math.min(256L << 20, Runtime.getRuntime().maxMemory() / 4))
                throw RarCodec.bad("PPMd 記憶體需求超出預算");
            if (a == null || a.size != bytes) a = new Arena((int) bytes);
            order = n;
            start();
        }
    }

    void start() throws IOException {
        esc = 1;
        restart();
        for (int i = 0; i < 256; i++) {
            bsIndex[i] = i == 0 ? 0 : i == 1 ? 2 : i < 11 ? 4 : 6;
            high[i] = i < 64 ? 0 : 8;
        }
        for (int i = 0; i < 3; i++) nsIndex[i] = i;
        int m = 3, k = 1, step = 1;
        for (int i = 3; i < 256; i++) {
            nsIndex[i] = m;
            if (--k == 0) {
                k = ++step;
                m++;
            }
        }
        dummy.shift = 7;
    }

    void restart() throws IOException {
        Arrays.fill(masks, 0);
        a.init();
        initRun = -Math.min(order, 12) - 1;
        min = max = a.context();
        fall = order;
        a.s16(min, 256);
        a.s16(min + 2, 257);
        int states = a.units(128);
        a.s32(min + 4, states);
        a.s32(min + 8, 0);
        found = states;
        run = initRun;
        success = 0;
        for (int i = 0; i < 256; i++) {
            a.s8(states + 6 * i, i);
            a.s8(states + 6 * i + 1, 1);
            a.s32(states + 6 * i + 2, 0);
        }
        int[] initial = {0x3cdd, 0x1f3f, 0x59bf, 0x48f3, 0x64a1, 0x5abc, 0x6632, 0x6051};
        for (int i = 0; i < 128; i++)
            for (int k = 0; k < 64; k++) bin[i][k] = 16384 - initial[k & 7] / (i + 2);
        for (int i = 0; i < 25; i++) for (int j = 0; j < 16; j++) see[i][j].init(i * 5 + 10);
    }

    void normalize() throws IOException {
        for (; ; ) {
            if ((low ^ ((low + range) & 0xffffffffL)) >= 0x1000000L) {
                if (range >= 0x8000) return;
                range = (-low) & 0x7fff;
            }
            code = ((code << 8) | bits.take(8)) & 0xffffffffL;
            range = (range << 8) & 0xffffffffL;
            low = (low << 8) & 0xffffffffL;
        }
    }

    int count(int scale) throws IOException {
        if (scale <= 0) throw bad();
        range /= scale;
        if (range == 0) throw bad();
        long n = ((code - low) & 0xffffffffL) / range;
        if (n >= scale) throw bad();
        return (int) n;
    }

    void decode(int lo, int hi) throws IOException {
        if (lo < 0 || hi <= lo) throw bad();
        low = (low + range * lo) & 0xffffffffL;
        range = range * (hi - lo) & 0xffffffffL;
        if (range == 0) throw bad();
    }

    int stats(int c) throws IOException {
        return a.u16(c);
    }

    int ptr(int c) throws IOException {
        return a.i32(c + 4);
    }

    int suffix(int c) throws IOException {
        return a.i32(c + 8);
    }

    int freq(int p) throws IOException {
        return a.u8(p + 1);
    }

    int sym(int p) throws IOException {
        return a.u8(p);
    }

    int succ(int p) throws IOException {
        return a.i32(p + 2);
    }

    void addFreq(int p, int n) throws IOException {
        a.s8(p + 1, freq(p) + n);
    }

    void addSum(int c, int n) throws IOException {
        a.s16(c + 2, a.u16(c + 2) + n);
    }

    void contextValid(int p) throws IOException {
        if (p <= a.text || p > a.memory.length - 12) throw bad();
        int n = stats(p);
        if (n < 1 || n > 256) throw bad();
    }

    int find(int c, int symbol) throws IOException {
        int n = stats(c), p = n == 1 ? c + 2 : ptr(c);
        for (int i = 0; i < n; i++, p += 6) if (sym(p) == symbol) return p;
        throw bad();
    }

    void swap(int p, int q) throws IOException {
        int s = sym(p), f = freq(p), t = succ(p);
        a.copy(q, p, 6);
        a.s8(q, s);
        a.s8(q + 1, f);
        a.s32(q + 2, t);
    }

    void rescale(int c) throws IOException {
        int old = stats(c), base = ptr(c);
        for (int p = found; p != base; p -= 6) swap(p, p - 6);
        addFreq(base, 4);
        addSum(c, 4);
        int escapeFreq = a.u16(c + 2) - freq(base), adder = fall != 0 ? 1 : 0;
        a.s8(base + 1, (freq(base) + adder) >>> 1);
        int total = freq(base);
        for (int i = 1; i < old; i++) {
            int p = base + i * 6;
            escapeFreq -= freq(p);
            a.s8(p + 1, (freq(p) + adder) >>> 1);
            total += freq(p);
            int q = p;
            while (q > base && freq(q) > freq(q - 6)) {
                swap(q, q - 6);
                q -= 6;
            }
        }
        int n = old;
        while (n > 1 && freq(base + (n - 1) * 6) == 0) n--;
        escapeFreq += old - n;
        a.s16(c, n);
        if (n == 1) {
            int f = freq(base);
            while (escapeFreq > 1) {
                f -= f >>> 1;
                escapeFreq >>>= 1;
            }
            int symbol = sym(base), successor = succ(base);
            a.free(base, (old + 1) >>> 1);
            a.s8(c + 2, symbol);
            a.s8(c + 3, f);
            a.s32(c + 4, successor);
            found = c + 2;
            return;
        }
        a.s16(c + 2, total + escapeFreq - (escapeFreq >>> 1));
        int oldUnits = (old + 1) >>> 1, newUnits = (n + 1) >>> 1;
        if (oldUnits != newUnits) {
            base = a.shrink(base, oldUnits, newUnits);
            a.s32(c + 4, base);
        }
        found = base;
    }

    int successors(boolean skip, int p1) throws IOException {
        int c = min, branch = succ(found), top = 0;
        if (!skip) {
            stack[top++] = found;
            if (suffix(c) == 0) return createChain(c, branch, top);
        }
        int p = p1;
        if (p != 0) c = suffix(c);
        else {
            c = suffix(c);
            if (c == 0) throw bad();
            p = find(c, sym(found));
        }
        for (; ; ) {
            if (succ(p) != branch) {
                c = succ(p);
                break;
            }
            if (top >= 64) throw bad();
            stack[top++] = p;
            if (suffix(c) == 0) break;
            c = suffix(c);
            p = find(c, sym(found));
        }
        return top == 0 ? c : createChain(c, branch, top);
    }

    int createChain(int c, int branch, int top) throws IOException {
        if (top == 0) return c;
        int symbol = a.u8(branch), successor = branch + 1, f;
        contextValid(c);
        if (stats(c) != 1) {
            int p = find(c, symbol), cf = freq(p) - 1, s0 = a.u16(c + 2) - stats(c) - cf;
            if (s0 <= 0) throw bad();
            f = 1 + (2 * cf <= s0 ? (5 * cf > s0 ? 1 : 0) : (2 * cf + 3 * s0 - 1) / (2 * s0));
        } else f = freq(c + 2);
        while (top > 0) {
            int child = a.context();
            if (child == 0) return 0;
            a.s16(child, 1);
            a.s8(child + 2, symbol);
            a.s8(child + 3, f);
            a.s32(child + 4, successor);
            a.s32(child + 8, c);
            a.s32(stack[--top] + 2, child);
            c = child;
        }
        return c;
    }

    void updateModel() throws IOException {
        int symbol = sym(found), frequency = freq(found), fsSuccessor = succ(found), p = 0, c;
        if (frequency < 31 && (c = suffix(min)) != 0) {
            p = find(c, symbol);
            if (stats(c) != 1) {
                int base = ptr(c);
                if (p > base && freq(p) >= freq(p - 6)) {
                    swap(p, p - 6);
                    p -= 6;
                }
                if (freq(p) < 115) {
                    addFreq(p, 2);
                    addSum(c, 2);
                }
            } else if (freq(p) < 32) addFreq(p, 1);
        }
        if (fall == 0) {
            int next = successors(true, p);
            if (next == 0) {
                restart();
                esc = 0;
            } else {
                a.s32(found + 2, next);
                min = max = next;
            }
            return;
        }
        a.s8(a.text++, symbol);
        int successor = a.text;
        if (a.text >= a.fake) {
            restart();
            esc = 0;
            return;
        }
        if (fsSuccessor != 0) {
            if (fsSuccessor <= a.text) {
                fsSuccessor = successors(false, p);
                if (fsSuccessor == 0) {
                    restart();
                    esc = 0;
                    return;
                }
            }
            if (--fall == 0) {
                successor = fsSuccessor;
                if (max != min) a.text--;
            }
        } else {
            a.s32(found + 2, successor);
            fsSuccessor = min;
        }
        int ns = stats(min), s0 = a.u16(min + 2) - ns - (frequency - 1);
        c = max;
        for (int depth = 0; c != min; c = suffix(c)) {
            if (++depth > 64) throw bad();
            int ns1 = stats(c), base;
            if (ns1 != 1) {
                base = ptr(c);
                if ((ns1 & 1) == 0) {
                    base = a.expand(base, ns1 >>> 1);
                    if (base == 0) {
                        restart();
                        esc = 0;
                        return;
                    }
                    a.s32(c + 4, base);
                }
                addSum(
                        c,
                        (2 * ns1 < ns ? 1 : 0)
                                + 2 * ((4 * ns1 <= ns && a.u16(c + 2) <= 8 * ns1) ? 1 : 0));
            } else {
                base = a.units(1);
                if (base == 0) {
                    restart();
                    esc = 0;
                    return;
                }
                a.copy(c + 2, base, 6);
                a.s32(c + 4, base);
                a.s8(base + 1, freq(base) < 30 ? freq(base) * 2 : 120);
                a.s16(c + 2, freq(base) + initEsc + (ns > 3 ? 1 : 0));
            }
            int cf = 2 * frequency * (a.u16(c + 2) + 6), sf = s0 + a.u16(c + 2);
            if (cf < 6 * sf) {
                cf = 1 + (cf > sf ? 1 : 0) + (cf >= 4 * sf ? 1 : 0);
                addSum(c, 3);
            } else {
                cf = 4 + (cf >= 9 * sf ? 1 : 0) + (cf >= 12 * sf ? 1 : 0) + (cf >= 15 * sf ? 1 : 0);
                addSum(c, cf);
            }
            p = base + ns1 * 6;
            a.s32(p + 2, successor);
            a.s8(p, symbol);
            a.s8(p + 1, cf);
            a.s16(c, ns1 + 1);
        }
        min = max = fsSuccessor;
    }

    void binary() throws IOException {
        int p = min + 2;
        hi = high[sym(found)];
        int row = freq(p) - 1,
                col =
                        success
                                + bsIndex[stats(suffix(min)) - 1]
                                + hi
                                + 2 * high[sym(p)]
                                + ((run >> 26) & 32);
        if (row < 0 || row >= 128) throw bad();
        int bs = bin[row][col];
        range >>>= 14;
        if (range == 0) throw bad();
        long n = ((code - low) & 0xffffffffL) / range;
        if (n < bs) {
            found = p;
            if (freq(p) < 128) addFreq(p, 1);
            decode(0, bs);
            bin[row][col] = (bs + 128 - ((bs + 32) >>> 7)) & 65535;
            success = 1;
            run++;
        } else {
            if (n >= 16384) throw bad();
            decode(bs, 16384);
            bs = (bs - ((bs + 32) >>> 7)) & 65535;
            bin[row][col] = bs;
            int[] ex = {25, 14, 9, 7, 5, 5, 4, 4, 4, 3, 3, 3, 2, 2, 2, 2};
            initEsc = ex[bs >>> 10];
            masked = 1;
            masks[sym(p)] = esc;
            success = 0;
            found = 0;
        }
    }

    void first() throws IOException {
        int base = ptr(min),
                n = stats(min),
                scale = a.u16(min + 2),
                count = count(scale),
                p = base,
                total = freq(p);
        if (count < total) {
            success = 2 * total > scale ? 1 : 0;
            run += success;
            found = p;
            addFreq(p, 4);
            addSum(min, 4);
            decode(0, total);
            if (freq(p) > 124) rescale(min);
            return;
        }
        if (found == 0) throw bad();
        success = 0;
        for (int i = 1; i < n; i++) {
            p += 6;
            int f = freq(p);
            total += f;
            if (count < total) {
                decode(total - f, total);
                found = p;
                addFreq(p, 4);
                addSum(min, 4);
                if (freq(p) > freq(p - 6)) {
                    swap(p, p - 6);
                    found = p - 6;
                    if (freq(found) > 124) rescale(min);
                }
                return;
            }
        }
        hi = high[sym(found)];
        decode(total, scale);
        for (int i = 0; i < n; i++) masks[sym(base + i * 6)] = esc;
        masked = n;
        found = 0;
    }

    void second() throws IOException {
        int n = stats(min), diff = n - masked;
        if (diff < 1 || diff > 256) throw bad();
        See ctx;
        if (n == 256) ctx = dummy;
        else {
            int index =
                    (diff < stats(suffix(min)) - n ? 1 : 0)
                            + 2 * (a.u16(min + 2) < 11 * n ? 1 : 0)
                            + 4 * (masked > diff ? 1 : 0)
                            + hi;
            ctx = see[nsIndex[diff - 1]][index];
        }
        int scale = n == 256 ? 1 : ctx.mean(), base = ptr(min), total = 0, num = 0;
        for (int i = 0; i < n; i++) {
            int p = base + i * 6;
            if (masks[sym(p)] != esc) {
                stack[num++] = p;
                total += freq(p);
            }
        }
        if (num != diff) throw bad();
        scale += total;
        int count = count(scale);
        if (count < total) {
            int running = 0;
            for (int i = 0; i < num; i++) {
                int p = stack[i], f = freq(p);
                running += f;
                if (count < running) {
                    decode(running - f, running);
                    ctx.update();
                    found = p;
                    addFreq(p, 4);
                    addSum(min, 4);
                    if (freq(p) > 124) rescale(min);
                    esc = (esc + 1) & 255;
                    run = initRun;
                    return;
                }
            }
            throw bad();
        }
        decode(total, scale);
        for (int i = 0; i < num; i++) masks[sym(stack[i])] = esc;
        ctx.sum = (ctx.sum + scale) & 65535;
        masked = n;
        found = 0;
    }

    int read() throws IOException {
        try {
            return readSymbol();
        } catch (IndexOutOfBoundsException | ArithmeticException malformed) {
            throw bad();
        }
    }

    private int readSymbol() throws IOException {
        contextValid(min);
        if (stats(min) == 1) binary();
        else {
            if (ptr(min) <= a.text) throw bad();
            first();
        }
        while (found == 0) {
            normalize();
            int depth = 0;
            do {
                if (++depth > 64) throw bad();
                fall++;
                min = suffix(min);
                contextValid(min);
            } while (stats(min) == masked);
            second();
        }
        int symbol = sym(found);
        if (fall == 0 && succ(found) > a.text) min = max = succ(found);
        else {
            updateModel();
            if (esc == 0) {
                esc = 1;
                Arrays.fill(masks, 0);
            }
        }
        normalize();
        return symbol;
    }

    /** Exact 12-byte logical allocator: allocation failure/restart is part of the codec. */
    static final class Arena {
        final byte[] memory;
        final int size;
        final int[] units = new int[38], index = new int[128], free = new int[38];
        int text, lo, hi, start, fake, glue;

        Arena(int size) {
            this.size = size;
            memory = new byte[size + 40];
            int n = 0, k = 1;
            for (; n < 4; n++, k++) units[n] = k;
            for (k++; n < 8; n++, k += 2) units[n] = k;
            for (k++; n < 12; n++, k += 3) units[n] = k;
            for (k++; n < 38; n++, k += 4) units[n] = k;
            for (int i = 0, j = 0; i < 128; i++) {
                if (units[j] < i + 1) j++;
                index[i] = j;
            }
        }

        void bounds(int p, int n) throws IOException {
            if (p < 1 || p > memory.length - n) throw bad();
        }

        int u8(int p) throws IOException {
            bounds(p, 1);
            return memory[p] & 255;
        }

        void s8(int p, int v) throws IOException {
            bounds(p, 1);
            memory[p] = (byte) v;
        }

        int u16(int p) throws IOException {
            return u8(p) | (u8(p + 1) << 8);
        }

        void s16(int p, int v) throws IOException {
            s8(p, v);
            s8(p + 1, v >>> 8);
        }

        int i32(int p) throws IOException {
            return u16(p) | (u16(p + 2) << 16);
        }

        void s32(int p, int v) throws IOException {
            s16(p, v);
            s16(p + 2, v >>> 16);
        }

        void copy(int from, int to, int n) throws IOException {
            bounds(from, n);
            bounds(to, n);
            System.arraycopy(memory, from, memory, to, n);
        }

        void init() {
            Arrays.fill(free, 0);
            text = 1;
            int size2 = 12 * (size / 8 / 12 * 7), size1 = size - size2;
            lo = start = 1 + size1 / 12 * 12 + 12;
            fake = 1 + size1;
            hi = lo + size2;
            glue = 0;
        }

        void insert(int p, int i) throws IOException {
            s32(p, free[i]);
            free[i] = p;
        }

        int remove(int i) throws IOException {
            int p = free[i];
            free[i] = i32(p);
            return p;
        }

        void split(int p, int old, int next) throws IOException {
            int diff = units[old] - units[next], addr = p + 12 * units[next], i = index[diff - 1];
            if (units[i] != diff) {
                i--;
                insert(addr, i);
                addr += 12 * units[i];
                diff -= units[i];
            }
            insert(addr, index[diff - 1]);
        }

        void glue() throws IOException {
            ArrayList<Integer> list = new ArrayList<>();
            HashMap<Integer, Integer> blocks = new HashMap<>();
            for (int i = 0; i < 38; i++)
                while (free[i] != 0) {
                    int p = remove(i);
                    list.add(p);
                    blocks.put(p, units[i]);
                }
            Collections.reverse(list);
            for (int p : list) {
                Integer n = blocks.get(p);
                if (n == null) continue;
                Integer other;
                while ((other = blocks.get(p + 12 * n)) != null && n + other < 65536) {
                    blocks.remove(p + 12 * n);
                    n += other;
                }
                blocks.put(p, n);
            }
            for (int p : list) {
                Integer value = blocks.remove(p);
                if (value == null) continue;
                int n = value;
                while (n > 128) {
                    insert(p, 37);
                    p += 1536;
                    n -= 128;
                }
                int i = index[n - 1];
                if (units[i] != n) {
                    i--;
                    int k = n - units[i];
                    insert(p + 12 * (n - k), k - 1);
                }
                insert(p, i);
            }
        }

        int rare(int i) throws IOException {
            if (glue == 0) {
                glue = 255;
                glue();
                if (free[i] != 0) return remove(i);
            }
            for (int j = i + 1; j < 38; j++)
                if (free[j] != 0) {
                    int p = remove(j);
                    split(p, j, i);
                    return p;
                }
            glue--;
            int bytes = 12 * units[i];
            if (fake - text > bytes) {
                fake -= bytes;
                start -= bytes;
                return start;
            }
            return 0;
        }

        int units(int n) throws IOException {
            if (n < 1 || n > 128) throw bad();
            int i = index[n - 1];
            if (free[i] != 0) return remove(i);
            int p = lo;
            lo += 12 * units[i];
            if (lo <= hi) return p;
            lo = p;
            return rare(i);
        }

        int context() throws IOException {
            if (hi != lo) {
                hi -= 12;
                return hi;
            }
            return free[0] != 0 ? remove(0) : rare(0);
        }

        int expand(int p, int n) throws IOException {
            if (index[n - 1] == index[n]) return p;
            int next = units(n + 1);
            if (next != 0) {
                copy(p, next, n * 12);
                insert(p, index[n - 1]);
            }
            return next;
        }

        int shrink(int p, int old, int n) throws IOException {
            int i = index[old - 1], j = index[n - 1];
            if (i == j) return p;
            if (free[j] != 0) {
                int next = remove(j);
                copy(p, next, n * 12);
                insert(p, i);
                return next;
            }
            split(p, i, j);
            return p;
        }

        void free(int p, int n) throws IOException {
            insert(p, index[n - 1]);
        }
    }
}
