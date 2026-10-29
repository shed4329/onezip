package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;

/** RAR3/4 and RAR5 read-only containers. Decoder attribution: RAR-NOTICE.txt. */
final class RarArchiveReader implements ArchiveReader {
    record File(
            Entry entry,
            long offset,
            long packed,
            Long crc,
            long dictionary,
            int method,
            int version,
            boolean solid,
            byte[] hash,
            RarCrypto.Info crypto) {}

    private final char[] password;
    private final IdentityHashMap<RarCrypto.Info, RarCrypto.Keys> keyCache =
            new IdentityHashMap<>();
    private final Path path;
    private final RandomAccessFile file;
    private final BooleanSupplier cancel;
    private final List<File> files;
    private final List<Entry> entries;
    private final boolean five;
    private final List<List<Entry>> groups;
    private final IdentityHashMap<Entry, Integer> indices = new IdentityHashMap<>();
    private int completed = -1;
    private RarCodec.State state;
    private InputStream active;
    private int activeIndex = -1;

    RarArchiveReader(Path path, char[] password, BooleanSupplier cancel) throws IOException {
        this.path = path;
        this.cancel = cancel;
        file = new RandomAccessFile(path.toFile(), "r");
        this.password = password == null ? null : password.clone();
        try {
            byte[] magic = new byte[7];
            file.readFully(magic);
            if (!Arrays.equals(Arrays.copyOf(magic, 6), new byte[] {82, 97, 114, 33, 26, 7}))
                throw RarCodec.bad("檔案簽名無效");
            five = magic[6] == 1;
            if (five) {
                if (file.read() != 0) throw RarCodec.bad("RAR5 簽名無效");
            } else if (magic[6] != 0) throw RarCodec.bad("不支援的簽名版本");
            files = List.copyOf(five ? parse5() : parse4());
            entries = files.stream().map(File::entry).toList();
            groups = makeGroups();
            index();
        } catch (IOException | RuntimeException e) {
            try {
                close();
            } catch (IOException close) {
                e.addSuppressed(close);
            }
            throw e;
        }
    }

    private RarArchiveReader(RarArchiveReader other) throws IOException {
        path = other.path;
        cancel = other.cancel;
        password = other.password == null ? null : other.password.clone();
        five = other.five;
        files = other.files;
        entries = other.entries;
        groups = other.groups;
        file = new RandomAccessFile(path.toFile(), "r");
        index();
    }

    private RarCrypto.Keys keys(RarCrypto.Info info) throws IOException {
        RarCrypto.Keys key = keyCache.get(info);
        if (key == null) {
            key = RarCrypto.derive(info, password, cancel);
            if (keyCache.size() >= 16) {
                for (var old : keyCache.values()) wipe(old);
                keyCache.clear();
            }
            keyCache.put(info, key);
        }
        return key;
    }

    private static void wipe(RarCrypto.Keys key) {
        Arrays.fill(key.aes(), (byte) 0);
        if (key.hash() != null) Arrays.fill(key.hash(), (byte) 0);
    }

    private InputStream headerStream(RarCrypto.Info info, boolean encrypted4) throws IOException {
        if (info == null && !encrypted4)
            return ReadSupport.region(
                    file, file.getFilePointer(), file.length() - file.getFilePointer());
        byte[] iv;
        if (encrypted4) {
            byte[] salt = new byte[8];
            file.readFully(salt);
            info = new RarCrypto.Info(false, salt, null, 0, null, false);
            iv = keys(info).iv();
        } else {
            iv = new byte[16];
            file.readFully(iv);
        }
        return new RarCrypto.Cbc(
                ReadSupport.region(
                        file, file.getFilePointer(), file.length() - file.getFilePointer()),
                keys(info),
                iv,
                true);
    }

    private void index() {
        for (int i = 0; i < entries.size(); i++) indices.put(entries.get(i), i);
    }

    private List<List<Entry>> makeGroups() {
        List<List<Entry>> result = new ArrayList<>();
        List<Entry> group = null;
        for (File f : files) {
            if (group == null || (!f.solid && !f.entry.directory())) {
                group = new ArrayList<>();
                result.add(group);
            }
            group.add(f.entry);
        }
        return result.stream().map(List::copyOf).toList();
    }

    private static long vint(InputStream in) throws IOException {
        long v = 0;
        for (int i = 0; i < 9; i++) {
            int b = ReadSupport.u8(in);
            if (i == 8 && (b & 128) != 0) throw RarCodec.bad("整數溢位");
            v |= (long) (b & 127) << (i * 7);
            if ((b & 128) == 0) return v;
        }
        throw RarCodec.bad("整數溢位");
    }

    private long vintFile(InputStream in, ByteArrayOutputStream encoded) throws IOException {
        for (int i = 0; i < 3; i++) {
            int b = ReadSupport.u8(in);
            encoded.write(b);
            if ((b & 128) == 0) return vint(new ByteArrayInputStream(encoded.toByteArray()));
        }
        throw RarCodec.bad("標頭過大");
    }

    private static int bounded(long v, int max) throws IOException {
        return ReadSupport.count(v, max);
    }

    private static String utf8(byte[] name) throws IOException {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(name))
                    .toString();
        } catch (CharacterCodingException e) {
            throw RarCodec.bad("檔名不是有效 UTF-8");
        }
    }

    private void add(List<File> result, File item) throws IOException {
        if (result.size() >= ReadSupport.MAX_ENTRIES) throw RarCodec.bad("項目過多");
        if (item.entry.name().isEmpty() || item.entry.name().indexOf('\0') >= 0)
            throw RarCodec.bad("檔名無效");
        result.add(item);
    }

    private List<File> parse5() throws IOException {
        List<File> result = new ArrayList<>();
        boolean main = false, ended = false;
        RarCrypto.Info headers = null;
        while (file.getFilePointer() < file.length()) {
            ReadSupport.check(cancel);
            InputStream input = headerStream(headers, false);
            long expected = ReadSupport.le(input, 4);
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            int length = bounded(vintFile(input, encoded), 2 * 1024 * 1024);
            byte[] header = ReadSupport.bytes(input, length);
            Crc32 crc = new Crc32();
            byte[] sizeBytes = encoded.toByteArray();
            crc.update(sizeBytes, 0, sizeBytes.length);
            crc.update(header, 0, header.length);
            if (crc.value() != expected) throw RarCodec.bad("標頭 CRC 不符（密碼錯誤或資料損壞）");
            ByteArrayInputStream h = new ByteArrayInputStream(header);
            long type = vint(h),
                    flags = vint(h),
                    extra = (flags & 1) != 0 ? vint(h) : 0,
                    packed = (flags & 2) != 0 ? vint(h) : 0;
            long data = file.getFilePointer(), next = ReadSupport.add(data, packed);
            if (next > file.length() || extra > h.available()) throw RarCodec.bad("標頭／資料超出檔案範圍");
            if ((flags & 24) != 0) throw RarCodec.bad("尚不支援分卷壓縮檔");
            if (type == 1) {
                if (main) throw RarCodec.bad("重複主標頭");
                main = true;
                long archiveFlags = vint(h);
                if ((archiveFlags & 1) != 0) throw RarCodec.bad("尚不支援分卷壓縮檔");
            } else if (type == 2) {
                if (!main) throw RarCodec.bad("缺少主標頭");
                long ff = vint(h), unpacked = vint(h), attrs = vint(h);
                if ((ff & 8) != 0) throw RarCodec.bad("不支援未知解壓大小");
                if ((ff & 2) != 0) ReadSupport.le(h, 4);
                Long dataCrc = (ff & 4) != 0 ? ReadSupport.le(h, 4) : null;
                long compression = vint(h), host = vint(h);
                int nameLength = bounded(vint(h), 65535);
                if (nameLength > h.available() - extra) throw RarCodec.bad("檔名超出標頭");
                String name = utf8(ReadSupport.bytes(h, nameLength));
                boolean dir = (ff & 1) != 0;
                if (host == 1 && !dir && (attrs & 0xf000) != 0 && (attrs & 0xf000) != 0x8000)
                    throw RarCodec.bad("不支援符號連結或特殊檔案");
                int version = (int) compression & 63, method = (int) (compression >>> 7) & 7;
                if (version > 1 || method > 5) throw RarCodec.bad("不支援的壓縮版本／方法");
                long dictionary = 0x20000L << ((compression >>> 10) & (version == 0 ? 15 : 31));
                if (version == 1) dictionary += dictionary / 32 * ((compression >>> 15) & 31);
                if (version == 1 && (compression & 0x100000) != 0) version = 0;
                byte[] hash = null;
                RarCrypto.Info encryption = null;
                int skip = h.available() - (int) extra;
                if (h.skip(skip) != skip) throw new EOFException();
                while (h.available() > 0) {
                    int recordLength = bounded(vint(h), h.available());
                    ByteArrayInputStream record =
                            new ByteArrayInputStream(ReadSupport.bytes(h, recordLength));
                    long kind = vint(record);
                    if (kind == 1) {
                        if (encryption != null || vint(record) != 0)
                            throw RarCodec.bad("不支援或重複的加密版本");
                        long cf = vint(record);
                        if ((cf & ~3L) != 0) throw RarCodec.bad("未知加密旗標");
                        int exponent = ReadSupport.u8(record);
                        byte[] salt = ReadSupport.bytes(record, 16),
                                iv = ReadSupport.bytes(record, 16),
                                check = (cf & 1) != 0 ? RarCrypto.checkBytes(record) : null;
                        encryption =
                                new RarCrypto.Info(true, salt, iv, exponent, check, (cf & 2) != 0);
                        if (exponent > 24) throw RarCodec.bad("KDF 次數過大");
                    }
                    if (kind == 5) throw RarCodec.bad("不支援連結、重新導向或檔案複製項目");
                    if (kind == 2) {
                        if (vint(record) != 0) throw RarCodec.bad("不支援的雜湊算法");
                        hash = ReadSupport.bytes(record, 32);
                        if (record.available() != 0) throw RarCodec.bad("雜湊欄位長度無效");
                    }
                }
                if (dir && (unpacked != 0 || packed != 0)) throw RarCodec.bad("目錄含資料");
                checkPacked(method, packed, unpacked, encryption);
                add(
                        result,
                        new File(
                                new Entry(name, dir, unpacked),
                                data,
                                packed,
                                dataCrc,
                                dictionary,
                                method,
                                version,
                                (compression & 64) != 0,
                                hash,
                                encryption));
            } else if (type == 4) {
                if (headers != null || main || vint(h) != 0) throw RarCodec.bad("加密標頭版本／位置無效");
                long cf = vint(h);
                if ((cf & ~1L) != 0) throw RarCodec.bad("未知標頭加密旗標");
                int exponent = ReadSupport.u8(h);
                byte[] salt = ReadSupport.bytes(h, 16),
                        check = (cf & 1) != 0 ? RarCrypto.checkBytes(h) : null;
                headers = new RarCrypto.Info(true, salt, null, exponent, check, false);
                keys(headers);
            } else if (type == 5) {
                if ((vint(h) & 1) != 0) throw RarCodec.bad("壓縮檔需要下一分卷");
                ended = true;
                break;
            } else if (type != 3 && (flags & 4) == 0) throw RarCodec.bad("未知必要標頭類型 " + type);
            file.seek(next);
        }
        if (!main || !ended) throw RarCodec.bad("主標頭或結束標頭缺失");
        return result;
    }

    private List<File> parse4() throws IOException {
        List<File> result = new ArrayList<>();
        boolean main = false, encryptedHeaders = false;
        while (file.getFilePointer() < file.length()) {
            ReadSupport.check(cancel);
            InputStream input = headerStream(null, encryptedHeaders);
            int expected = (int) ReadSupport.le(input, 2),
                    type = ReadSupport.u8(input),
                    flags = (int) ReadSupport.le(input, 2),
                    length = (int) ReadSupport.le(input, 2);
            if (length < 7) throw RarCodec.bad("標頭長度無效（密碼錯誤或資料損壞）");
            byte[] header = new byte[length - 2];
            header[0] = (byte) type;
            header[1] = (byte) flags;
            header[2] = (byte) (flags >>> 8);
            header[3] = (byte) length;
            header[4] = (byte) (length >>> 8);
            byte[] rest = ReadSupport.bytes(input, length - 7);
            System.arraycopy(rest, 0, header, 5, rest.length);
            if ((Crc32.of(header) & 65535) != expected) throw RarCodec.bad("標頭 CRC 不符");
            ByteArrayInputStream h = new ByteArrayInputStream(header, 5, header.length - 5);
            long packed = (flags & 0x8000) != 0 ? ReadSupport.le(h, 4) : 0;
            if (type == 0x73) {
                if (main) throw RarCodec.bad("重複主標頭");
                main = true;
                if ((flags & 1) != 0) throw RarCodec.bad("尚不支援分卷壓縮檔");
                encryptedHeaders = (flags & 128) != 0;
            } else if (type == 0x74) {
                if (!main) throw RarCodec.bad("缺少主標頭");
                if ((flags & 3) != 0) throw RarCodec.bad("尚不支援分卷壓縮檔");
                long size = ReadSupport.le(h, 4);
                int host = ReadSupport.u8(h);
                long crc = ReadSupport.le(h, 4);
                ReadSupport.le(h, 4);
                int version = ReadSupport.u8(h),
                        method = ReadSupport.u8(h) - 0x30,
                        nameLength = (int) ReadSupport.le(h, 2);
                long attrs = ReadSupport.le(h, 4);
                if ((flags & 256) != 0) {
                    packed |= ReadSupport.le(h, 4) << 32;
                    size |= ReadSupport.le(h, 4) << 32;
                    if (packed < 0 || size < 0) throw RarCodec.bad("檔案過大");
                }
                byte[] nameBytes = ReadSupport.bytes(h, nameLength);
                String name = name4(nameBytes, (flags & 512) != 0);
                boolean dir = (flags & 224) == 224;
                byte[] salt = (flags & 1024) != 0 ? ReadSupport.bytes(h, 8) : null;
                RarCrypto.Info encryption =
                        (flags & 4) != 0
                                ? new RarCrypto.Info(false, salt, null, 0, null, false)
                                : null;
                if (encryption != null && version < 29) throw RarCodec.bad("尚不支援 RAR2 以前的加密");
                if (host == 3 && !dir && (attrs & 0xf000) != 0 && (attrs & 0xf000) != 0x8000)
                    throw RarCodec.bad("不支援符號連結或特殊檔案");
                if (method < 0 || method > 5) throw RarCodec.bad("不支援的壓縮方法");
                if (method != 0 && version != 29 && version != 36)
                    throw RarCodec.bad("尚不支援 RAR 解碼版本 " + version);
                if (!dir) checkPacked(method, packed, size, encryption);
                add(
                        result,
                        new File(
                                new Entry(name, dir, dir ? 0 : size),
                                file.getFilePointer(),
                                packed,
                                dir ? null : crc,
                                0x10000L << ((flags & 224) >>> 5),
                                method,
                                version,
                                (flags & 16) != 0,
                                null,
                                encryption));
            } else if (type == 0x7b) {
                if ((flags & 1) != 0) throw RarCodec.bad("壓縮檔需要下一分卷");
                break;
            } else if (type < 0x72 || type > 0x7a) throw RarCodec.bad("未知標頭類型");
            long next = ReadSupport.add(file.getFilePointer(), packed);
            if (next > file.length()) throw RarCodec.bad("資料超出檔案範圍");
            file.seek(next);
        }
        if (!main) throw RarCodec.bad("缺少主標頭");
        return result;
    }

    private static void checkPacked(int method, long packed, long size, RarCrypto.Info crypto)
            throws IOException {
        if (crypto != null && (packed & 15) != 0) throw RarCodec.bad("加密資料長度未對齊");
        if (method == 0
                && (crypto == null
                        ? packed != size
                        : packed != ReadSupport.add(size, 15) / 16 * 16))
            throw RarCodec.bad("儲存檔案長度不符");
    }

    private static String name4(byte[] bytes, boolean unicode) throws IOException {
        if (!unicode) {
            try {
                return utf8(bytes);
            } catch (IOException malformed) {
                try {
                    return new String(
                            bytes,
                            Charset.forName(System.getProperty("onezip.rar.charset", "IBM437")));
                } catch (IllegalArgumentException e) {
                    throw RarCodec.bad("舊式檔名編碼設定無效");
                }
            }
        }
        int split = 0;
        while (split < bytes.length && bytes[split] != 0) split++;
        if (split == bytes.length) return utf8(bytes);
        ByteArrayInputStream in =
                new ByteArrayInputStream(bytes, split + 1, bytes.length - split - 1);
        int high = ReadSupport.u8(in), flags = 0, count = 0;
        StringBuilder name = new StringBuilder();
        while (in.available() > 0) {
            if (count == 0) {
                flags = ReadSupport.u8(in);
                count = 4;
            }
            int mode = flags >>> 6;
            flags = (flags << 2) & 255;
            count--;
            switch (mode) {
                case 0 -> name.append((char) ReadSupport.u8(in));
                case 1 -> name.append((char) (ReadSupport.u8(in) | (high << 8)));
                case 2 -> name.append((char) ReadSupport.le(in, 2));
                case 3 -> {
                    int n = ReadSupport.u8(in),
                            correction = (n & 128) != 0 ? ReadSupport.u8(in) : 0;
                    boolean corrected = (n & 128) != 0;
                    n = (n & 127) + 2;
                    if (name.length() + n > split) throw RarCodec.bad("Unicode 檔名超出範圍");
                    for (int j = 0; j < n; j++) {
                        int b = bytes[name.length()] & 255;
                        name.append(
                                (char) (corrected ? ((b + correction) & 255) | (high << 8) : b));
                    }
                }
            }
            if (name.length() > 65535) throw RarCodec.bad("檔名過長");
        }
        return name.toString();
    }

    public List<Entry> entries() {
        return entries;
    }

    public List<List<Entry>> groups() {
        return groups;
    }

    public ArchiveReader fork() throws IOException {
        return new RarArchiveReader(this);
    }

    public long workerMemory() {
        long max = 1;
        for (File f : files) max = Math.max(max, Math.min(f.dictionary, 256L * 1024 * 1024));
        return max
                + 20L * 1024 * 1024
                + (five ? 0 : Math.min(256L << 20, Runtime.getRuntime().maxMemory() / 4));
    }

    private int dictionary(int start) throws IOException {
        long needed = 0, dict = 1;
        for (int i = start; i < files.size(); i++) {
            File f = files.get(i);
            if (i > start && !f.solid && !f.entry.directory()) break;
            needed = ReadSupport.add(needed, f.entry.size());
            dict = Math.max(dict, f.dictionary);
        }
        long size = Math.min(dict, Math.max(needed, 1));
        long budget = Math.min(256L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 4);
        if (size > budget) throw RarCodec.bad("字典超出此 JVM 的記憶體預算: " + size);
        return (int) size;
    }

    public InputStream open(Entry entry) throws IOException {
        Integer target = indices.get(entry);
        if (target == null) throw RarCodec.bad("項目不屬於此壓縮檔");
        finishActive();
        int start = target;
        while (start > 0 && (files.get(start).solid || files.get(start).entry.directory())) start--;
        if (completed < start - 1 || completed >= target) {
            completed = start - 1;
            state = null;
        }
        while (completed + 1 < target) {
            InputStream previous = openOne(completed + 1);
            previous.transferTo(OutputStream.nullOutputStream());
            finishActive();
        }
        return openOne(target);
    }

    private InputStream openOne(int index) throws IOException {
        File f = files.get(index);
        ReadSupport.check(cancel);
        InputStream data;
        if (f.entry.directory())
            data = ReadSupport.verified(InputStream.nullInputStream(), 0, null, true, cancel);
        else {
            InputStream packed = ReadSupport.region(file, f.offset, f.packed);
            RarCrypto.Keys key = f.crypto == null ? null : keys(f.crypto);
            if (key != null)
                packed =
                        new RarCrypto.Cbc(
                                packed, key, f.crypto.five() ? f.crypto.iv() : key.iv(), false);
            if (f.method == 0 || (f.packed == 0 && f.entry.size() == 0)) {
                data =
                        f.crypto == null
                                ? packed
                                : ReadSupport.verified(packed, f.entry.size(), null, false, cancel);
                if (!f.solid) state = null;
            } else {
                if (state == null || !f.solid) state = new RarCodec.State(dictionary(index));
                data =
                        new RarCodec.Decoder(
                                state,
                                packed,
                                f.entry.size(),
                                five,
                                f.version == 1,
                                f.solid,
                                cancel);
            }
            RarCrypto.Keys hashKey = key != null && f.crypto.mac() ? key : null;
            data = RarCrypto.verified(data, f.entry.size(), f.crc, f.hash, hashKey, cancel);
        }
        active = data;
        activeIndex = index;
        return data;
    }

    private void finishActive() throws IOException {
        if (active != null) {
            active.transferTo(OutputStream.nullOutputStream());
            completed = activeIndex;
            active = null;
        }
    }

    public void close() throws IOException {
        if (password != null) Arrays.fill(password, '\0');
        for (var key : keyCache.values()) wipe(key);
        keyCache.clear();
        file.close();
    }
}
