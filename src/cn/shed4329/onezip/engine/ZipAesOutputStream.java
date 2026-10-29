package cn.shed4329.onezip.engine;

import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** WinZip AE-2 framing using standard JCE primitives, not a native archive API. */
final class ZipAesOutputStream extends OutputStream {
    private final OutputStream out;
    private final Cipher aes;
    private final Mac mac;
    private final byte[] counter = new byte[16];
    private byte[] keyStream;
    private int index = 16;
    private long nonce = 1;

    ZipAesOutputStream(OutputStream out, char[] password) throws IOException {
        this.out = out;
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, 1000, 66 * 8);
            byte[] keys;
            try {
                keys =
                        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                                .generateSecret(spec)
                                .getEncoded();
            } finally {
                spec.clearPassword();
            }
            try {
                aes = Cipher.getInstance("AES/ECB/NoPadding");
                aes.init(
                        Cipher.ENCRYPT_MODE,
                        new SecretKeySpec(Arrays.copyOfRange(keys, 0, 32), "AES"));
                mac = Mac.getInstance("HmacSHA1");
                mac.init(new SecretKeySpec(Arrays.copyOfRange(keys, 32, 64), "HmacSHA1"));
                out.write(salt);
                out.write(keys, 64, 2);
            } finally {
                Arrays.fill(keys, (byte) 0);
            }
        } catch (GeneralSecurityException ex) {
            throw new IOException("無法初始化 ZIP AES 加密", ex);
        }
    }

    @Override
    public void write(int value) throws IOException {
        if (index == 16) {
            if (nonce > 0xffffffffL) throw new IOException("ZIP AES entry too large");
            for (int i = 0; i < 4; ++i) counter[i] = (byte) (nonce >>> (8 * i));
            ++nonce;
            try {
                keyStream = aes.doFinal(counter);
            } catch (GeneralSecurityException ex) {
                throw new IOException(ex);
            }
            index = 0;
        }
        int encoded = (value ^ keyStream[index++]) & 255;
        out.write(encoded);
        mac.update((byte) encoded);
    }

    @Override
    public void write(byte[] b, int o, int n) throws IOException {
        java.util.Objects.checkFromIndexSize(o, n, b.length);
        for (int i = o; i < o + n; ++i) write(b[i]);
    }

    void finish() throws IOException {
        out.write(mac.doFinal(), 0, 10);
    }
}
