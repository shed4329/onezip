package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.function.BooleanSupplier;

import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * ZIP/ZIP64 central directory, STORE/DEFLATE, ZipCrypto and WinZip AES, all archive logic in Java.
 */
final class ZipArchiveReader implements ArchiveReader {
    private record Spec(
            Entry entry,
            int flags,
            int method,
            int time,
            long crc,
            long packed,
            long offset,
            int aesVersion,
            int strength,
            byte[] rawName,
            int date,
            long commentOffset,
            int commentLength,
            java.nio.file.attribute.FileTime modified) {}

    private final RandomAccessFile file;
    private final Path path;
    private final List<Entry> entries;
    private final Map<Entry, Spec> specs;
    private final char[] password;
    private final BooleanSupplier cancel;
    private long centralOffset;
    private String comment;
    private final Charset legacyCharset;

    ZipArchiveReader(Path path, char[] password, BooleanSupplier cancel) throws IOException {
        this(path, password, cancel, Charset.forName("IBM437"));
    }

    ZipArchiveReader(Path path, char[] password, BooleanSupplier cancel, Charset legacyCharset)
            throws IOException {
        this.legacyCharset = Objects.requireNonNull(legacyCharset);
        this.path = path;
        entries = new ArrayList<>();
        specs = new IdentityHashMap<>();
        file = new RandomAccessFile(path.toFile(), "r");
        this.password = password == null ? new char[0] : password.clone();
        this.cancel = cancel;
        try {
            parse();
        } catch (IOException | RuntimeException e) {
            try {
                close();
            } catch (IOException close) {
                e.addSuppressed(close);
            }
            throw e;
        }
    }

    private ZipArchiveReader(ZipArchiveReader source) throws IOException {
        legacyCharset = source.legacyCharset;
        path = source.path;
        cancel = source.cancel;
        file = new RandomAccessFile(path.toFile(), "r");
        password = source.password.clone();
        entries = source.entries;
        specs = source.specs;
        centralOffset = source.centralOffset;
        comment = source.comment;
    }

    public ArchiveReader fork() throws IOException {
        return new ZipArchiveReader(this);
    }

    static long endRecord(RandomAccessFile file) throws IOException {
        int n = (int) Math.min(file.length(), 65557);
        if (n < 22) throw new IOException("不是 ZIP 檔案");
        byte[] tail = new byte[n];
        file.seek(file.length() - n);
        file.readFully(tail);
        for (int i = n - 22; i >= 0; i--)
            if (tail[i] == 0x50
                    && tail[i + 1] == 0x4b
                    && tail[i + 2] == 5
                    && tail[i + 3] == 6
                    && i + 22 + ((tail[i + 20] & 255) | ((tail[i + 21] & 255) << 8)) == n)
                return file.length() - n + i;
        throw new IOException("ZIP 缺少完整的結尾目錄");
    }

    private void parse() throws IOException {
        long end = endRecord(file);
        ReadSupport.Cursor e = new ReadSupport.Cursor(readAt(end, 22));
        e.little(4);
        long disk = e.little(2),
                cdDisk = e.little(2),
                onDisk = e.little(2),
                count = e.little(2),
                cdSize = e.little(4);
        centralOffset = e.little(4);
        int commentLength = (int) e.little(2);
        comment = new String(readAt(end + 22, commentLength), StandardCharsets.UTF_8);
        if (disk != 0 || cdDisk != 0 || onDisk != count) throw new IOException("不支援分卷 ZIP");
        if (count == 65535 || cdSize == 0xffffffffL || centralOffset == 0xffffffffL) {
            if (end < 20) throw new IOException("ZIP64 locator 遺失");
            ReadSupport.Cursor loc = new ReadSupport.Cursor(readAt(end - 20, 20));
            if (loc.little(4) != 0x07064b50L || loc.little(4) != 0)
                throw new IOException("無效的 ZIP64 locator");
            long off = loc.little(8);
            if (loc.little(4) != 1) throw new IOException("不支援分卷 ZIP64");
            ReadSupport.Cursor z = new ReadSupport.Cursor(readAt(off, 56));
            if (z.little(4) != 0x06064b50L || z.little(8) < 44)
                throw new IOException("無效的 ZIP64 結尾");
            z.little(4);
            if (z.little(4) != 0 || z.little(4) != 0) throw new IOException("不支援分卷 ZIP64");
            long n = z.little(8);
            count = z.little(8);
            if (n != count) throw new IOException("ZIP64 項目數不符");
            cdSize = z.little(8);
            centralOffset = z.little(8);
        }
        int number = ReadSupport.count(count, ReadSupport.MAX_ENTRIES);
        long cdEnd = ReadSupport.add(centralOffset, cdSize);
        if (cdEnd > end) throw new IOException("ZIP 目錄範圍無效");
        long offset = centralOffset;
        for (int i = 0; i < number; i++) {
            ReadSupport.check(cancel);
            if (ReadSupport.add(offset, 46) > cdEnd) throw new EOFException("ZIP 目錄截斷");
            ReadSupport.Cursor h = new ReadSupport.Cursor(readAt(offset, 46));
            if (h.little(4) != 0x02014b50L) throw new IOException("ZIP 目錄簽名無效");
            int creator = (int) h.little(2);
            h.little(2);
            int flags = (int) h.little(2),
                    method = (int) h.little(2),
                    time = (int) h.little(2),
                    date = (int) h.little(2);
            long crc = h.little(4), packed = h.little(4), size = h.little(4);
            int nl = (int) h.little(2), xl = (int) h.little(2), cl = (int) h.little(2);
            long startDisk = h.little(2);
            h.little(2);
            long attrs = h.little(4), local = h.little(4);
            long next = ReadSupport.add(offset, 46L + nl + xl + cl);
            if (next > cdEnd) throw new IOException("ZIP 目錄欄位越界");
            byte[] rawName = readAt(offset + 46, nl), extra = readAt(offset + 46 + nl, xl);
            String name =
                    decode(rawName, (flags & 2048) != 0 ? StandardCharsets.UTF_8 : legacyCharset);
            int aesVersion = 0, strength = 0;
            boolean got64 = false, gotAes = false;
            java.nio.file.attribute.FileTime modified = null;
            boolean gotNtfsTime = false;
            ReadSupport.Cursor fields = new ReadSupport.Cursor(extra);
            while (fields.available() > 0) {
                int id = (int) fields.little(2), len = (int) fields.little(2);
                ReadSupport.Cursor p = new ReadSupport.Cursor(ReadSupport.bytes(fields, len));
                if (id == 1) {
                    if (got64) throw new IOException("重複的 ZIP64 屬性");
                    got64 = true;
                    if (size == 0xffffffffL) size = p.little(8);
                    if (packed == 0xffffffffL) packed = p.little(8);
                    if (local == 0xffffffffL) local = p.little(8);
                    if (startDisk == 65535) startDisk = p.little(4);
                } else if (id == 0x9901) {
                    if (gotAes || len != 7) throw new IOException("無效的 ZIP AES 屬性");
                    gotAes = true;
                    aesVersion = (int) p.little(2);
                    if (p.little(2) != 0x4541) throw new IOException("未知的 ZIP AES vendor");
                    strength = p.byteValue();
                    int actual = (int) p.little(2);
                    if (method != 99) throw new IOException("ZIP AES 方法不符");
                    method = actual;
                } else if (id == 0x000a && len >= 4) {
                    p.little(4);
                    while (p.available() >= 4) {
                        int tag = (int) p.little(2), length = (int) p.little(2);
                        byte[] attribute = ReadSupport.bytes(p, length);
                        if (tag == 1 && length == 24) {
                            var precise =
                                    ArchiveTimes.fromNtfs(
                                            ByteBuffer.wrap(attribute)
                                                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                                                    .getLong());
                            if (precise != null) {
                                modified = precise;
                                gotNtfsTime = true;
                            }
                        }
                    }
                } else if (id == 0x5455 && len >= 5 && !gotNtfsTime) {
                    int bits = p.byteValue();
                    if ((bits & 1) != 0)
                        modified =
                                java.nio.file.attribute.FileTime.from(
                                        java.time.Instant.ofEpochSecond((int) p.little(4)));
                } else if (id == 0x7075
                        && len >= 5
                        && p.byteValue() == 1
                        && p.little(4) == Crc32.of(rawName))
                    name = decode(p.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!got64
                    && (size == 0xffffffffL
                            || packed == 0xffffffffL
                            || local == 0xffffffffL
                            || startDisk == 65535)) throw new IOException("ZIP64 屬性遺失");
            if (startDisk != 0) throw new IOException("不支援分卷 ZIP");
            if ((flags & ~(1 | 2 | 4 | 8 | 2048)) != 0) throw new IOException("ZIP 使用不支援的功能旗標");
            if (method != 0 && method != 8) throw new IOException("尚未實作 ZIP 壓縮方法: " + method);
            if (gotAes
                    && ((flags & 1) == 0
                            || aesVersion < 1
                            || aesVersion > 2
                            || strength < 1
                            || strength > 3)) throw new IOException("ZIP AES 屬性無效");
            int unix = (int) (attrs >>> 16);
            if ((creator >>> 8) == 3
                    && (unix & 0170000) != 0
                    && (unix & 0170000) != 0100000
                    && (unix & 0170000) != 0040000)
                throw new IOException("拒絕 ZIP 連結或特殊檔案: " + name);
            boolean directory =
                    name.endsWith("/")
                            || (attrs & 16) != 0
                            || ((creator >>> 8) == 3 && (unix & 0170000) == 0040000);
            if (directory && size != 0) throw new IOException("ZIP 資料夾含資料");
            Entry entry = new Entry(name, directory, size);
            entries.add(entry);
            specs.put(
                    entry,
                    new Spec(
                            entry,
                            flags,
                            method,
                            time,
                            crc,
                            packed,
                            local,
                            aesVersion,
                            strength,
                            rawName,
                            date,
                            offset + 46L + nl + xl,
                            cl,
                            modified));
            offset = next;
        }
        if (offset != cdEnd) throw new IOException("ZIP 目錄大小不符");
    }

    private static String decode(byte[] b, Charset charset) throws IOException {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IOException("ZIP 檔名編碼無效", e);
        }
    }

    private byte[] readAt(long offset, int length) throws IOException {
        return ReadSupport.bytes(ReadSupport.region(file, offset, length), length);
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public java.nio.file.attribute.FileTime modifiedTime(Entry entry) {
        Spec s = specs.get(entry);
        if (s.modified != null) return s.modified;
        try {
            return java.nio.file.attribute.FileTime.from(
                    java.time.LocalDateTime.of(
                                    1980 + (s.date >>> 9),
                                    (s.date >>> 5) & 15,
                                    s.date & 31,
                                    s.time >>> 11,
                                    (s.time >>> 5) & 63,
                                    (s.time & 31) * 2)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant());
        } catch (java.time.DateTimeException invalid) {
            return null;
        }
    }

    ArchiveEngine.EntryDetails details(Entry entry, ArchiveEngine.ArchiveItem item)
            throws IOException {
        Spec s = specs.get(entry);
        var timestamp = modifiedTime(entry);
        java.time.LocalDateTime modified =
                timestamp == null
                        ? null
                        : timestamp
                                .toInstant()
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDateTime();
        String encryption =
                (s.flags & 1) == 0
                        ? "无"
                        : s.aesVersion == 0 ? "ZipCrypto" : "AES-" + (64 * (s.strength + 1));
        return new ArchiveEngine.EntryDetails(
                item,
                s.packed,
                modified,
                s.method == 8 ? "Deflate" : "Store",
                encryption,
                s.aesVersion == 2 ? "" : String.format(Locale.ROOT, "%08X", s.crc),
                new String(
                        readAt(s.commentOffset, s.commentLength),
                        (s.flags & 2048) != 0 ? StandardCharsets.UTF_8 : legacyCharset));
    }

    @Override
    public InputStream open(Entry entry) throws IOException {
        Spec s = specs.get(entry);
        if (s == null) throw new IOException("未知的 ZIP 項目");
        ReadSupport.Cursor h = new ReadSupport.Cursor(readAt(s.offset, 30));
        if (h.little(4) != 0x04034b50L) throw new IOException("ZIP 本地標頭無效");
        h.little(2);
        int flags = (int) h.little(2), method = (int) h.little(2);
        h.little(2);
        h.little(2);
        h.little(4);
        h.little(4);
        h.little(4);
        int nl = (int) h.little(2), xl = (int) h.little(2);
        if (flags != s.flags
                || method != (s.aesVersion == 0 ? s.method : 99)
                || !Arrays.equals(readAt(s.offset + 30, nl), s.rawName))
            throw new IOException("ZIP 本地與中央標頭不符");
        long start = ReadSupport.add(s.offset, 30L + nl + xl);
        if (ReadSupport.add(start, s.packed) > centralOffset) throw new IOException("ZIP 項目資料越界");
        InputStream packed = new BufferedInputStream(ReadSupport.region(file, start, s.packed));
        if ((flags & 1) != 0) {
            if (password.length == 0) throw new IOException("此 ZIP 需要密碼");
            packed =
                    s.aesVersion == 0
                            ? new Crypto(packed, password, s)
                            : new Aes(packed, password, s.packed, s.strength);
        }
        final InputStream data = packed,
                decoded = s.method == 8 ? new DeflateInputStream(data) : data;
        InputStream complete =
                new FilterInputStream(decoded) {
                    boolean ended;

                    private int finish(int result) throws IOException {
                        if (result < 0 && !ended) {
                            if (data.read() != -1) throw new IOException("ZIP 壓縮資料含多餘位元組");
                            ended = true;
                        }
                        return result;
                    }

                    public int read() throws IOException {
                        return finish(in.read());
                    }

                    public int read(byte[] b, int o, int n) throws IOException {
                        return finish(in.read(b, o, n));
                    }

                    public void close() {
                        /* The archive owns its file; entry close cannot bypass checks. */
                    }
                };
        return ReadSupport.verified(
                complete, entry.size(), s.aesVersion == 2 ? null : s.crc, true, cancel);
    }

    String comment() {
        return comment;
    }

    @Override
    public void close() throws IOException {
        Arrays.fill(password, '\0');
        file.close();
    }

    private static final class Aes extends InputStream {
        final InputStream in;
        final Cipher aes;
        final Mac mac;
        final byte[] counter = new byte[16];
        byte[] keyStream;
        int index = 16;
        long nonce = 1, remaining;
        boolean verified;

        Aes(InputStream in, char[] password, long size, int strength) throws IOException {
            this.in = in;
            int keySize = 8 * (strength + 1), saltSize = keySize / 2;
            remaining = size - saltSize - 12;
            if (remaining < 0) throw new IOException("ZIP AES 資料過短");
            byte[] salt = ReadSupport.bytes(in, saltSize), verifier = ReadSupport.bytes(in, 2);
            byte[] keys = null;
            try {
                PBEKeySpec spec = new PBEKeySpec(password, salt, 1000, (keySize * 2 + 2) * 8);
                try {
                    keys =
                            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                                    .generateSecret(spec)
                                    .getEncoded();
                } finally {
                    spec.clearPassword();
                }
                if (!MessageDigest.isEqual(
                        verifier, Arrays.copyOfRange(keys, keySize * 2, keySize * 2 + 2)))
                    throw new IOException("ZIP 密碼錯誤");
                aes = Cipher.getInstance("AES/ECB/NoPadding");
                aes.init(
                        Cipher.ENCRYPT_MODE,
                        new SecretKeySpec(Arrays.copyOfRange(keys, 0, keySize), "AES"));
                mac = Mac.getInstance("HmacSHA1");
                mac.init(
                        new SecretKeySpec(
                                Arrays.copyOfRange(keys, keySize, keySize * 2), "HmacSHA1"));
            } catch (GeneralSecurityException e) {
                throw new IOException("無法初始化 ZIP AES", e);
            } finally {
                if (keys != null) Arrays.fill(keys, (byte) 0);
            }
        }

        public int read() throws IOException {
            if (remaining == 0) {
                if (!verified) {
                    byte[] expected = ReadSupport.bytes(in, 10),
                            actual = Arrays.copyOf(mac.doFinal(), 10);
                    if (!MessageDigest.isEqual(expected, actual))
                        throw new IOException("ZIP AES 驗證碼不符");
                    if (in.read() != -1) throw new IOException("ZIP AES 尾端資料無效");
                    verified = true;
                }
                return -1;
            }
            if (index == 16) {
                if (nonce > 0xffffffffL) throw new IOException("ZIP AES counter 溢位");
                for (int i = 0; i < 4; i++) counter[i] = (byte) (nonce >>> (8 * i));
                nonce++;
                try {
                    keyStream = aes.doFinal(counter);
                } catch (GeneralSecurityException e) {
                    throw new IOException(e);
                }
                index = 0;
            }
            int b = ReadSupport.u8(in);
            mac.update((byte) b);
            remaining--;
            return (b ^ keyStream[index++]) & 255;
        }

        public int read(byte[] b, int o, int n) throws IOException {
            Objects.checkFromIndexSize(o, n, b.length);
            if (n == 0) return 0;
            int i = 0;
            for (; i < n; i++) {
                int v = read();
                if (v < 0) break;
                b[o + i] = (byte) v;
            }
            return i == 0 ? -1 : i;
        }
    }

    private static final class Crypto extends InputStream {
        final InputStream in;
        int k0 = 0x12345678, k1 = 0x23456789, k2 = 0x34567890;
        private static final int[] TABLE = new int[256];

        static {
            for (int i = 0; i < 256; i++) {
                int c = i;
                for (int j = 0; j < 8; j++) c = (c >>> 1) ^ ((c & 1) != 0 ? 0xedb88320 : 0);
                TABLE[i] = c;
            }
        }

        Crypto(InputStream in, char[] password, Spec spec) throws IOException {
            this.in = in;
            byte[] encoded = new String(password).getBytes(StandardCharsets.UTF_8);
            for (byte b : encoded) update(b & 255);
            Arrays.fill(encoded, (byte) 0);
            int check = 0;
            for (int i = 0; i < 12; i++) check = decrypt(ReadSupport.u8(in));
            if (check != ((spec.flags & 8) != 0 ? spec.time >>> 8 : (int) (spec.crc >>> 24)))
                throw new IOException("ZIP 密碼錯誤");
        }

        private void update(int b) {
            k0 = (k0 >>> 8) ^ TABLE[(k0 ^ b) & 255];
            k1 = (k1 + (k0 & 255)) * 134775813 + 1;
            k2 = (k2 >>> 8) ^ TABLE[(k2 ^ (k1 >>> 24)) & 255];
        }

        private int decrypt(int b) {
            int t = k2 | 2, value = (b ^ ((t * (t ^ 1)) >>> 8)) & 255;
            update(value);
            return value;
        }

        public int read() throws IOException {
            int b = in.read();
            return b < 0 ? -1 : decrypt(b);
        }

        public int read(byte[] b, int o, int n) throws IOException {
            int count = in.read(b, o, n);
            for (int i = 0; i < count; i++) b[o + i] = (byte) decrypt(b[o + i] & 255);
            return count;
        }
    }
}
