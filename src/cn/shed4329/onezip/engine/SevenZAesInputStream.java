package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.BooleanSupplier;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

/** 7z AES-256: SHA-256 iterative KDF, UTF-16LE password, CBC and zero padding. */
final class SevenZAesInputStream extends InputStream {
    private final InputStream in;
    private final BooleanSupplier cancel;
    private final Cipher aes;
    private byte[] previous, plain;
    private int index = 16;
    private long remaining;
    private boolean verified;

    SevenZAesInputStream(
            InputStream in, byte[] properties, char[] password, long size, BooleanSupplier cancel)
            throws IOException {
        this.in = in;
        this.cancel = cancel;
        remaining = size;
        int[] shape = shape(properties);
        int cycles = shape[0], saltSize = shape[1], ivSize = shape[2], offset = shape[3];
        byte[] salt = Arrays.copyOfRange(properties, offset, offset + saltSize);
        previous = new byte[16];
        System.arraycopy(properties, offset + saltSize, previous, 0, ivSize);
        ByteBuffer encoded = StandardCharsets.UTF_16LE.encode(CharBuffer.wrap(password));
        byte[] passwordBytes = new byte[encoded.remaining()];
        encoded.get(passwordBytes);
        if (encoded.hasArray()) Arrays.fill(encoded.array(), (byte) 0);
        byte[] key = null;
        try {
            if (cycles == 63) {
                key = new byte[32];
                int n = Math.min(salt.length, 32);
                System.arraycopy(salt, 0, key, 0, n);
                System.arraycopy(passwordBytes, 0, key, n, Math.min(passwordBytes.length, 32 - n));
            } else {
                MessageDigest hash = MessageDigest.getInstance("SHA-256");
                byte[] counter = new byte[8];
                for (long i = 0, n = 1L << cycles; i < n; i++) {
                    if ((i & 4095) == 0) ReadSupport.check(cancel);
                    hash.update(salt);
                    hash.update(passwordBytes);
                    hash.update(counter);
                    for (int j = 0; j < 8; j++) {
                        if (++counter[j] != 0) break;
                    }
                }
                key = hash.digest();
            }
            aes = Cipher.getInstance("AES/ECB/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
        } catch (GeneralSecurityException e) {
            throw new IOException("無法初始化 7z AES", e);
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
            if (key != null) Arrays.fill(key, (byte) 0);
        }
    }

    static int[] shape(byte[] p) throws IOException {
        if (p.length == 0) throw new IOException("7z AES 屬性遺失");
        int first = p[0] & 255, cycles = first & 63, salt = 0, iv = 0, offset = 1;
        if ((first & 192) != 0) {
            if (p.length < 2) throw new IOException("7z AES 屬性截斷");
            salt = ((first >>> 7) & 1) + ((p[1] & 255) >>> 4);
            iv = ((first >>> 6) & 1) + (p[1] & 15);
            offset = 2;
        }
        if (p.length != offset + salt + iv) throw new IOException("7z AES salt/IV 長度不符");
        if (cycles != 63 && cycles > 24) throw new IOException("7z AES KDF 超過支援的 2^24 迭代上限");
        return new int[] {cycles, salt, iv, offset};
    }

    private void block() throws IOException {
        byte[] cipher = ReadSupport.bytes(in, 16);
        try {
            plain = aes.doFinal(cipher);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
        for (int i = 0; i < 16; i++) plain[i] ^= previous[i];
        previous = cipher;
        index = 0;
    }

    @Override
    public int read() throws IOException {
        if (remaining == 0) {
            if (!verified) {
                if (plain != null)
                    for (int i = index; i < 16; i++)
                        if (plain[i] != 0) throw new IOException("7z AES padding 不符，密碼錯誤或資料損壞");
                if (in.read() != -1) throw new IOException("7z AES 密文長度不符");
                verified = true;
            }
            return -1;
        }
        if (index == 16) block();
        remaining--;
        return plain[index++] & 255;
    }

    @Override
    public int read(byte[] b, int o, int n) throws IOException {
        Objects.checkFromIndexSize(o, n, b.length);
        if (n == 0) return 0;
        int i = 0;
        for (; i < n; i++) {
            if ((i & 4095) == 0) ReadSupport.check(cancel);
            int value = read();
            if (value < 0) break;
            b[o + i] = (byte) value;
        }
        return i == 0 ? -1 : i;
    }
}
