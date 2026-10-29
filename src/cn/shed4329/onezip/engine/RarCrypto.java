package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.BooleanSupplier;

import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * RAR KDFs, CBC framing and checksum MACs; standard JDK cryptographic primitives. Algorithm
 * attribution and UnRAR handling-only license: RAR-NOTICE.txt.
 */
final class RarCrypto {
    record Info(boolean five, byte[] salt, byte[] iv, int exponent, byte[] check, boolean mac) {}

    record Keys(byte[] aes, byte[] iv, byte[] hash, byte[] check) {}

    static byte[] digest(String name, byte[] data) throws IOException {
        try {
            return MessageDigest.getInstance(name).digest(data);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    static Mac hmac(byte[] key) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.length == 0 ? new byte[1] : key, "HmacSHA256"));
            return mac;
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    static Keys derive(Info info, char[] password, BooleanSupplier cancel) throws IOException {
        if (password == null) throw RarCodec.bad("此壓縮檔需要密碼");
        if (password.length > 127) throw RarCodec.bad("RAR 密碼超過 127 個 UTF-16 字元");
        if (!info.five) return derive4(info, password, cancel);
        if (info.exponent < 0 || info.exponent > 24) throw RarCodec.bad("RAR5 KDF 次數超出支援範圍");
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        byte[] pwd = new byte[encoded.remaining()];
        encoded.get(pwd);
        Arrays.fill(encoded.array(), (byte) 0);
        try {
            Mac mac = hmac(pwd);
            byte[] first = Arrays.copyOf(info.salt, info.salt.length + 4);
            first[first.length - 1] = 1;
            byte[] u = mac.doFinal(first), f = u.clone(), key = null, hash = null;
            int rounds = 1 << info.exponent;
            for (int i = 1; i < rounds + 32; i++) {
                if ((i & 4095) == 0) ReadSupport.check(cancel);
                if (i == rounds) key = f.clone();
                if (i == rounds + 16) hash = f.clone();
                byte[] next = mac.doFinal(u);
                Arrays.fill(u, (byte) 0);
                u = next;
                for (int j = 0; j < 32; j++) f[j] ^= u[j];
            }
            byte[] check = new byte[8];
            for (int j = 0; j < 32; j++) check[j & 7] ^= f[j];
            Arrays.fill(u, (byte) 0);
            Arrays.fill(f, (byte) 0);
            if (info.check != null && !MessageDigest.isEqual(check, info.check)) {
                Arrays.fill(key, (byte) 0);
                Arrays.fill(hash, (byte) 0);
                throw RarCodec.bad("密碼錯誤");
            }
            return new Keys(key, info.iv, hash, check);
        } finally {
            Arrays.fill(pwd, (byte) 0);
        }
    }

    private static Keys derive4(Info info, char[] password, BooleanSupplier cancel)
            throws IOException {
        byte[] raw = new byte[password.length * 2 + (info.salt == null ? 0 : 8)];
        for (int i = 0; i < password.length; i++) {
            raw[i * 2] = (byte) password[i];
            raw[i * 2 + 1] = (byte) (password[i] >>> 8);
        }
        if (info.salt != null) System.arraycopy(info.salt, 0, raw, password.length * 2, 8);
        // RAR29 mutates only direct full blocks after the first copied block.
        // At <=64 bytes the legacy operation is exactly ordinary SHA-1.
        try {
            Keys fast = raw.length <= 64 ? derive4Short(raw, cancel) : derive4Long(raw, cancel);
            if (fast != null) {
                Arrays.fill(raw, (byte) 0);
                return fast;
            }
        } catch (IOException | RuntimeException e) {
            Arrays.fill(raw, (byte) 0);
            throw e;
        }
        Sha1 sha = new Sha1();
        byte[] iv = new byte[16], counter = new byte[3];
        try {
            for (int i = 0; i < 0x40000; i++) {
                if ((i & 4095) == 0) ReadSupport.check(cancel);
                sha.update(raw, true);
                counter[0] = (byte) i;
                counter[1] = (byte) (i >>> 8);
                counter[2] = (byte) (i >>> 16);
                sha.update(counter, false);
                if ((i & 16383) == 0) {
                    byte[] d = sha.copy().finish();
                    iv[i >>> 14] = d[19];
                    Arrays.fill(d, (byte) 0);
                }
            }
            byte[] full = sha.finish(), key = new byte[16];
            for (int i = 0; i < 16; i++) key[i] = full[(i & ~3) + 3 - (i & 3)];
            Arrays.fill(full, (byte) 0);
            return new Keys(key, iv, null, null);
        } finally {
            Arrays.fill(raw, (byte) 0);
            sha.clear();
        }
    }

    private static Keys derive4Short(byte[] raw, BooleanSupplier cancel) throws IOException {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-1");
            sha.clone();
        } catch (NoSuchAlgorithmException | CloneNotSupportedException unavailable) {
            return null;
        }
        int stride = raw.length + 3;
        byte[] batch = new byte[4096 * stride], iv = new byte[16];
        try {
            for (int p = 0; p < batch.length; p += stride)
                System.arraycopy(raw, 0, batch, p, raw.length);
            ReadSupport.check(cancel);
            sha.update(raw);
            sha.update(new byte[3]);
            byte[] first = shaSnapshot(sha);
            iv[0] = first[19];
            Arrays.fill(first, (byte) 0);
            for (int i = 1; i < 0x40000; ) {
                ReadSupport.check(cancel);
                int border = Math.min(0x3ffff, ((i + 16383) / 16384) * 16384),
                        count = Math.min(4096, border - i + 1);
                for (int j = 0, p = raw.length; j < count; j++, p += stride) {
                    int counter = i + j;
                    batch[p] = (byte) counter;
                    batch[p + 1] = (byte) (counter >>> 8);
                    batch[p + 2] = (byte) (counter >>> 16);
                }
                sha.update(batch, 0, count * stride);
                i += count;
                if (((i - 1) & 16383) == 0) {
                    byte[] snapshot = shaSnapshot(sha);
                    iv[(i - 1) >>> 14] = snapshot[19];
                    Arrays.fill(snapshot, (byte) 0);
                }
            }
            byte[] full = sha.digest(), key = new byte[16];
            for (int i = 0; i < 16; i++) key[i] = full[(i & ~3) + 3 - (i & 3)];
            Arrays.fill(full, (byte) 0);
            return new Keys(key, iv, null, null);
        } finally {
            Arrays.fill(batch, (byte) 0);
            sha.reset();
        }
    }

    private static byte[] shaSnapshot(MessageDigest sha) throws IOException {
        try {
            return ((MessageDigest) sha.clone()).digest();
        } catch (CloneNotSupportedException e) {
            throw new IOException("SHA-1 provider 不支援狀態快照", e);
        }
    }

    private static Keys derive4Long(byte[] raw, BooleanSupplier cancel) throws IOException {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-1");
            sha.clone();
        } catch (NoSuchAlgorithmException | CloneNotSupportedException unavailable) {
            return null;
        }
        int stride = raw.length + 3;
        byte[] batch = new byte[4096 * stride], iv = new byte[16];
        int[] schedule = new int[16];
        try {
            ReadSupport.check(cancel);
            System.arraycopy(raw, 0, batch, 0, raw.length);
            sha.update(batch, 0, stride);
            legacyWriteback(raw, 64, schedule);
            byte[] first = shaSnapshot(sha);
            iv[0] = first[19];
            Arrays.fill(first, (byte) 0);
            for (int i = 1; i < 0x40000; ) {
                ReadSupport.check(cancel);
                int border = Math.min(0x3ffff, ((i + 16383) / 16384) * 16384),
                        count = Math.min(4096, border - i + 1);
                for (int j = 0, p = 0; j < count; j++, p += stride) {
                    int counter = i + j;
                    System.arraycopy(raw, 0, batch, p, raw.length);
                    // The message schedule does not depend on the running digest.
                    // Copy this iteration's original bytes before mutating next iteration's input.
                    legacyWriteback(raw, 64 - ((counter * stride) & 63), schedule);
                    int q = p + raw.length;
                    batch[q] = (byte) counter;
                    batch[q + 1] = (byte) (counter >>> 8);
                    batch[q + 2] = (byte) (counter >>> 16);
                }
                sha.update(batch, 0, count * stride);
                i += count;
                if (((i - 1) & 16383) == 0) {
                    byte[] snapshot = shaSnapshot(sha);
                    iv[(i - 1) >>> 14] = snapshot[19];
                    Arrays.fill(snapshot, (byte) 0);
                }
            }
            byte[] full = sha.digest(), key = new byte[16];
            for (int i = 0; i < 16; i++) key[i] = full[(i & ~3) + 3 - (i & 3)];
            Arrays.fill(full, (byte) 0);
            return new Keys(key, iv, null, null);
        } finally {
            Arrays.fill(batch, (byte) 0);
            Arrays.fill(schedule, 0);
            sha.reset();
        }
    }

    /** RAR29 modifies only direct blocks after its first copied block, using W[64..79]. */
    private static void legacyWriteback(byte[] raw, int first, int[] w) {
        for (int off = first; off + 64 <= raw.length; off += 64) {
            for (int i = 0; i < 16; i++) {
                int p = off + i * 4;
                w[i] =
                        ((raw[p] & 255) << 24)
                                | ((raw[p + 1] & 255) << 16)
                                | ((raw[p + 2] & 255) << 8)
                                | (raw[p + 3] & 255);
            }
            for (int i = 16; i < 80; i++)
                w[i & 15] =
                        Integer.rotateLeft(
                                w[(i + 13) & 15] ^ w[(i + 8) & 15] ^ w[(i + 2) & 15] ^ w[i & 15],
                                1);
            for (int i = 0; i < 64; i++) raw[off + i] = (byte) (w[i / 4] >>> (8 * (i & 3)));
        }
    }

    static final class Cbc extends InputStream {
        final InputStream in;
        final Cipher cipher;
        final byte[] block, plain;
        int pos, end;
        boolean eof;

        Cbc(InputStream in, Keys keys, byte[] iv, boolean header) throws IOException {
            this.in = in;
            block = new byte[header ? 16 : 65536];
            plain = new byte[block.length];
            try {
                cipher = Cipher.getInstance("AES/CBC/NoPadding");
                cipher.init(
                        Cipher.DECRYPT_MODE,
                        new SecretKeySpec(keys.aes, "AES"),
                        new IvParameterSpec(iv));
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }
        }

        void fill() throws IOException {
            if (eof) return;
            int n = in.readNBytes(block, 0, block.length);
            if (n == 0) {
                eof = true;
                pos = end = 0;
                return;
            }
            if ((n & 15) != 0) throw RarCodec.bad("加密資料未對齊或已截斷");
            try {
                end = cipher.update(block, 0, n, plain, 0);
                pos = 0;
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }
        }

        public int read() throws IOException {
            if (pos == end) fill();
            return pos == end ? -1 : plain[pos++] & 255;
        }

        public int read(byte[] b, int o, int n) throws IOException {
            Objects.checkFromIndexSize(o, n, b.length);
            if (n == 0) return 0;
            if (pos == end) fill();
            if (pos == end) return -1;
            int k = Math.min(n, end - pos);
            System.arraycopy(plain, pos, b, o, k);
            pos += k;
            return k;
        }

        public void close() {
            Arrays.fill(block, (byte) 0);
            Arrays.fill(plain, (byte) 0);
        }
    }

    static byte[] checkBytes(InputStream in) throws IOException {
        byte[] check = ReadSupport.bytes(in, 8), crc = ReadSupport.bytes(in, 4);
        if (!MessageDigest.isEqual(crc, Arrays.copyOf(digest("SHA-256", check), 4)))
            throw RarCodec.bad("密碼校驗欄位損壞");
        return check;
    }

    static InputStream verified(
            InputStream in, long size, Long crc, byte[] hash, Keys keys, BooleanSupplier cancel) {
        return new InputStream() {
            long remaining = size;
            final Crc32 sum = crc == null ? null : new Crc32();
            final RarBlake2sp blake = hash == null ? null : new RarBlake2sp();
            boolean done;
            IOException failure;
            final byte[] one = new byte[1];

            void finish() throws IOException {
                if (failure != null) throw failure;
                if (done) return;
                try {
                    if (in.read() != -1) throw RarCodec.bad("解壓大小超過宣告");
                    if (sum != null) {
                        long value = sum.value();
                        if (keys != null && keys.hash != null) {
                            byte[] raw = new byte[4];
                            for (int i = 0; i < 4; i++) raw[i] = (byte) (value >>> (8 * i));
                            byte[] mac = hmac(keys.hash).doFinal(raw);
                            value = 0;
                            for (int i = 0; i < 32; i++)
                                value ^= (long) (mac[i] & 255) << ((i & 3) * 8);
                        }
                        if (value != crc) throw RarCodec.bad("資料 CRC／MAC 不符（密碼錯誤或資料損壞）");
                    }
                    if (blake != null) {
                        byte[] actual = blake.finish();
                        if (keys != null && keys.hash != null)
                            actual = hmac(keys.hash).doFinal(actual);
                        if (!MessageDigest.isEqual(actual, hash))
                            throw RarCodec.bad("BLAKE2sp／MAC 不符（密碼錯誤或資料損壞）");
                    }
                    done = true;
                } catch (IOException e) {
                    failure = e;
                    throw e;
                }
            }

            public int read() throws IOException {
                int n = read(one, 0, 1);
                return n < 0 ? -1 : one[0] & 255;
            }

            public int read(byte[] b, int o, int n) throws IOException {
                Objects.checkFromIndexSize(o, n, b.length);
                ReadSupport.check(cancel);
                if (n == 0) return 0;
                if (remaining == 0) {
                    finish();
                    return -1;
                }
                int k = in.read(b, o, (int) Math.min(n, remaining));
                if (k < 0) throw RarCodec.bad("解壓大小小於宣告");
                if (sum != null) sum.update(b, o, k);
                if (blake != null) blake.update(b, o, k);
                remaining -= k;
                return k;
            }
        };
    }

    /** SHA-1 with the legacy RAR29 in-place schedule side effect for long passwords. */
    private static final class Sha1 {
        int[] h = {0x67452301, 0xefcdab89, 0x98badcfe, 0x10325476, 0xc3d2e1f0};
        byte[] buffer = new byte[64];
        int used;
        long count;

        Sha1 copy() {
            Sha1 s = new Sha1();
            s.h = h.clone();
            s.buffer = buffer.clone();
            s.used = used;
            s.count = count;
            return s;
        }

        void update(byte[] data, boolean rar) {
            int off = 0;
            count += data.length;
            if (used + data.length >= 64) {
                int n = 64 - used;
                System.arraycopy(data, 0, buffer, used, n);
                compress(buffer, 0, false);
                off = n;
                used = 0;
                while (off + 64 <= data.length) {
                    compress(data, off, rar);
                    off += 64;
                }
            }
            int n = data.length - off;
            System.arraycopy(data, off, buffer, used, n);
            used += n;
        }

        // Two bounded methods keep each unrolled core below HotSpot's huge-method limit.
        int a, b, c, d, e;
        final int[] workspace = new int[16];

        void compress(byte[] bytes, int off, boolean mutate) {
            int[] w = workspace;
            w[0] =
                    ((bytes[off + 0] & 255) << 24)
                            | ((bytes[off + 1] & 255) << 16)
                            | ((bytes[off + 2] & 255) << 8)
                            | (bytes[off + 3] & 255);
            w[1] =
                    ((bytes[off + 4] & 255) << 24)
                            | ((bytes[off + 5] & 255) << 16)
                            | ((bytes[off + 6] & 255) << 8)
                            | (bytes[off + 7] & 255);
            w[2] =
                    ((bytes[off + 8] & 255) << 24)
                            | ((bytes[off + 9] & 255) << 16)
                            | ((bytes[off + 10] & 255) << 8)
                            | (bytes[off + 11] & 255);
            w[3] =
                    ((bytes[off + 12] & 255) << 24)
                            | ((bytes[off + 13] & 255) << 16)
                            | ((bytes[off + 14] & 255) << 8)
                            | (bytes[off + 15] & 255);
            w[4] =
                    ((bytes[off + 16] & 255) << 24)
                            | ((bytes[off + 17] & 255) << 16)
                            | ((bytes[off + 18] & 255) << 8)
                            | (bytes[off + 19] & 255);
            w[5] =
                    ((bytes[off + 20] & 255) << 24)
                            | ((bytes[off + 21] & 255) << 16)
                            | ((bytes[off + 22] & 255) << 8)
                            | (bytes[off + 23] & 255);
            w[6] =
                    ((bytes[off + 24] & 255) << 24)
                            | ((bytes[off + 25] & 255) << 16)
                            | ((bytes[off + 26] & 255) << 8)
                            | (bytes[off + 27] & 255);
            w[7] =
                    ((bytes[off + 28] & 255) << 24)
                            | ((bytes[off + 29] & 255) << 16)
                            | ((bytes[off + 30] & 255) << 8)
                            | (bytes[off + 31] & 255);
            w[8] =
                    ((bytes[off + 32] & 255) << 24)
                            | ((bytes[off + 33] & 255) << 16)
                            | ((bytes[off + 34] & 255) << 8)
                            | (bytes[off + 35] & 255);
            w[9] =
                    ((bytes[off + 36] & 255) << 24)
                            | ((bytes[off + 37] & 255) << 16)
                            | ((bytes[off + 38] & 255) << 8)
                            | (bytes[off + 39] & 255);
            w[10] =
                    ((bytes[off + 40] & 255) << 24)
                            | ((bytes[off + 41] & 255) << 16)
                            | ((bytes[off + 42] & 255) << 8)
                            | (bytes[off + 43] & 255);
            w[11] =
                    ((bytes[off + 44] & 255) << 24)
                            | ((bytes[off + 45] & 255) << 16)
                            | ((bytes[off + 46] & 255) << 8)
                            | (bytes[off + 47] & 255);
            w[12] =
                    ((bytes[off + 48] & 255) << 24)
                            | ((bytes[off + 49] & 255) << 16)
                            | ((bytes[off + 50] & 255) << 8)
                            | (bytes[off + 51] & 255);
            w[13] =
                    ((bytes[off + 52] & 255) << 24)
                            | ((bytes[off + 53] & 255) << 16)
                            | ((bytes[off + 54] & 255) << 8)
                            | (bytes[off + 55] & 255);
            w[14] =
                    ((bytes[off + 56] & 255) << 24)
                            | ((bytes[off + 57] & 255) << 16)
                            | ((bytes[off + 58] & 255) << 8)
                            | (bytes[off + 59] & 255);
            w[15] =
                    ((bytes[off + 60] & 255) << 24)
                            | ((bytes[off + 61] & 255) << 16)
                            | ((bytes[off + 62] & 255) << 8)
                            | (bytes[off + 63] & 255);
            a = h[0];
            b = h[1];
            c = h[2];
            d = h[3];
            e = h[4];
            rounds0to39();
            rounds40to79();
            h[0] += a;
            h[1] += b;
            h[2] += c;
            h[3] += d;
            h[4] += e;
            if (mutate)
                for (int i = 0; i < 64; i++) bytes[off + i] = (byte) (w[i / 4] >>> (8 * (i & 3)));
        }

        private void rounds0to39() {
            int[] w = workspace;
            int a = this.a, b = this.b, c = this.c, d = this.d, e = this.e, t;
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[0];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[1];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[2];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[3];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[4];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[5];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[6];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[7];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[8];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[9];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[10];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[11];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[12];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[13];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[14];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[15];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[0] = Integer.rotateLeft(w[13] ^ w[8] ^ w[2] ^ w[0], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[0];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[1] = Integer.rotateLeft(w[14] ^ w[9] ^ w[3] ^ w[1], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[1];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[2] = Integer.rotateLeft(w[15] ^ w[10] ^ w[4] ^ w[2], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[2];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[3] = Integer.rotateLeft(w[0] ^ w[11] ^ w[5] ^ w[3], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (~b & d)) + e + 0x5a827999 + w[3];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[4] = Integer.rotateLeft(w[1] ^ w[12] ^ w[6] ^ w[4], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[4];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[5] = Integer.rotateLeft(w[2] ^ w[13] ^ w[7] ^ w[5], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[5];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[6] = Integer.rotateLeft(w[3] ^ w[14] ^ w[8] ^ w[6], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[6];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[7] = Integer.rotateLeft(w[4] ^ w[15] ^ w[9] ^ w[7], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[7];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[8] = Integer.rotateLeft(w[5] ^ w[0] ^ w[10] ^ w[8], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[8];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[9] = Integer.rotateLeft(w[6] ^ w[1] ^ w[11] ^ w[9], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[9];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[10] = Integer.rotateLeft(w[7] ^ w[2] ^ w[12] ^ w[10], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[10];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[11] = Integer.rotateLeft(w[8] ^ w[3] ^ w[13] ^ w[11], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[11];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[12] = Integer.rotateLeft(w[9] ^ w[4] ^ w[14] ^ w[12], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[12];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[13] = Integer.rotateLeft(w[10] ^ w[5] ^ w[15] ^ w[13], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[13];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[14] = Integer.rotateLeft(w[11] ^ w[6] ^ w[0] ^ w[14], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[14];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[15] = Integer.rotateLeft(w[12] ^ w[7] ^ w[1] ^ w[15], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[15];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[0] = Integer.rotateLeft(w[13] ^ w[8] ^ w[2] ^ w[0], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[0];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[1] = Integer.rotateLeft(w[14] ^ w[9] ^ w[3] ^ w[1], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[1];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[2] = Integer.rotateLeft(w[15] ^ w[10] ^ w[4] ^ w[2], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[2];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[3] = Integer.rotateLeft(w[0] ^ w[11] ^ w[5] ^ w[3], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[3];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[4] = Integer.rotateLeft(w[1] ^ w[12] ^ w[6] ^ w[4], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[4];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[5] = Integer.rotateLeft(w[2] ^ w[13] ^ w[7] ^ w[5], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[5];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[6] = Integer.rotateLeft(w[3] ^ w[14] ^ w[8] ^ w[6], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[6];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[7] = Integer.rotateLeft(w[4] ^ w[15] ^ w[9] ^ w[7], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + w[7];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
        }

        private void rounds40to79() {
            int[] w = workspace;
            int a = this.a, b = this.b, c = this.c, d = this.d, e = this.e, t;
            w[8] = Integer.rotateLeft(w[5] ^ w[0] ^ w[10] ^ w[8], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[8];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[9] = Integer.rotateLeft(w[6] ^ w[1] ^ w[11] ^ w[9], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[9];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[10] = Integer.rotateLeft(w[7] ^ w[2] ^ w[12] ^ w[10], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[10];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[11] = Integer.rotateLeft(w[8] ^ w[3] ^ w[13] ^ w[11], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[11];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[12] = Integer.rotateLeft(w[9] ^ w[4] ^ w[14] ^ w[12], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[12];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[13] = Integer.rotateLeft(w[10] ^ w[5] ^ w[15] ^ w[13], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[13];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[14] = Integer.rotateLeft(w[11] ^ w[6] ^ w[0] ^ w[14], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[14];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[15] = Integer.rotateLeft(w[12] ^ w[7] ^ w[1] ^ w[15], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[15];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[0] = Integer.rotateLeft(w[13] ^ w[8] ^ w[2] ^ w[0], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[0];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[1] = Integer.rotateLeft(w[14] ^ w[9] ^ w[3] ^ w[1], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[1];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[2] = Integer.rotateLeft(w[15] ^ w[10] ^ w[4] ^ w[2], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[2];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[3] = Integer.rotateLeft(w[0] ^ w[11] ^ w[5] ^ w[3], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[3];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[4] = Integer.rotateLeft(w[1] ^ w[12] ^ w[6] ^ w[4], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[4];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[5] = Integer.rotateLeft(w[2] ^ w[13] ^ w[7] ^ w[5], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[5];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[6] = Integer.rotateLeft(w[3] ^ w[14] ^ w[8] ^ w[6], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[6];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[7] = Integer.rotateLeft(w[4] ^ w[15] ^ w[9] ^ w[7], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[7];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[8] = Integer.rotateLeft(w[5] ^ w[0] ^ w[10] ^ w[8], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[8];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[9] = Integer.rotateLeft(w[6] ^ w[1] ^ w[11] ^ w[9], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[9];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[10] = Integer.rotateLeft(w[7] ^ w[2] ^ w[12] ^ w[10], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[10];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[11] = Integer.rotateLeft(w[8] ^ w[3] ^ w[13] ^ w[11], 1);
            t = Integer.rotateLeft(a, 5) + ((b & c) | (b & d) | (c & d)) + e + 0x8f1bbcdc + w[11];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[12] = Integer.rotateLeft(w[9] ^ w[4] ^ w[14] ^ w[12], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[12];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[13] = Integer.rotateLeft(w[10] ^ w[5] ^ w[15] ^ w[13], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[13];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[14] = Integer.rotateLeft(w[11] ^ w[6] ^ w[0] ^ w[14], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[14];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[15] = Integer.rotateLeft(w[12] ^ w[7] ^ w[1] ^ w[15], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[15];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[0] = Integer.rotateLeft(w[13] ^ w[8] ^ w[2] ^ w[0], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[0];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[1] = Integer.rotateLeft(w[14] ^ w[9] ^ w[3] ^ w[1], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[1];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[2] = Integer.rotateLeft(w[15] ^ w[10] ^ w[4] ^ w[2], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[2];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[3] = Integer.rotateLeft(w[0] ^ w[11] ^ w[5] ^ w[3], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[3];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[4] = Integer.rotateLeft(w[1] ^ w[12] ^ w[6] ^ w[4], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[4];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[5] = Integer.rotateLeft(w[2] ^ w[13] ^ w[7] ^ w[5], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[5];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[6] = Integer.rotateLeft(w[3] ^ w[14] ^ w[8] ^ w[6], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[6];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[7] = Integer.rotateLeft(w[4] ^ w[15] ^ w[9] ^ w[7], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[7];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[8] = Integer.rotateLeft(w[5] ^ w[0] ^ w[10] ^ w[8], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[8];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[9] = Integer.rotateLeft(w[6] ^ w[1] ^ w[11] ^ w[9], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[9];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[10] = Integer.rotateLeft(w[7] ^ w[2] ^ w[12] ^ w[10], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[10];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[11] = Integer.rotateLeft(w[8] ^ w[3] ^ w[13] ^ w[11], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[11];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[12] = Integer.rotateLeft(w[9] ^ w[4] ^ w[14] ^ w[12], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[12];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[13] = Integer.rotateLeft(w[10] ^ w[5] ^ w[15] ^ w[13], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[13];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[14] = Integer.rotateLeft(w[11] ^ w[6] ^ w[0] ^ w[14], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[14];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            w[15] = Integer.rotateLeft(w[12] ^ w[7] ^ w[1] ^ w[15], 1);
            t = Integer.rotateLeft(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + w[15];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
        }

        byte[] finish() {
            long bits = count * 8;
            int pad = used < 56 ? 56 - used : 120 - used;
            byte[] last = new byte[pad + 8];
            last[0] = (byte) 128;
            for (int i = 0; i < 8; i++) last[pad + i] = (byte) (bits >>> (56 - i * 8));
            update(last, false);
            byte[] result = new byte[20];
            for (int i = 0; i < 20; i++) result[i] = (byte) (h[i / 4] >>> (24 - 8 * (i & 3)));
            return result;
        }

        void clear() {
            Arrays.fill(h, 0);
            Arrays.fill(buffer, (byte) 0);
            Arrays.fill(workspace, 0);
            a = b = c = d = e = 0;
        }
    }
}
