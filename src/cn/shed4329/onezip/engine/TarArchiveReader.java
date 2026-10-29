package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Indexed V7/USTAR/PAX/GNU long-name reader. Links, sparse and special files are refused. */
final class TarArchiveReader implements ArchiveReader {
    private final Path path;
    private final RandomAccessFile file;
    private final BooleanSupplier cancel;
    private final List<Entry> entries;
    private final Map<Entry, Long> offsets;
    private byte[] headerCache;
    private long cacheStart = -1;
    private int cacheLength;

    TarArchiveReader(Path path, BooleanSupplier cancel) throws IOException {
        this.path = path;
        this.cancel = cancel;
        file = new RandomAccessFile(path.toFile(), "r");
        entries = new ArrayList<>();
        offsets = new IdentityHashMap<>();
        try {
            parse();
        } catch (IOException | RuntimeException ex) {
            try {
                file.close();
            } catch (IOException close) {
                ex.addSuppressed(close);
            }
            throw ex;
        }
    }

    private TarArchiveReader(TarArchiveReader source) throws IOException {
        path = source.path;
        cancel = source.cancel;
        entries = source.entries;
        offsets = source.offsets;
        file = new RandomAccessFile(path.toFile(), "r");
    }

    public ArchiveReader fork() throws IOException {
        return new TarArchiveReader(this);
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public InputStream open(Entry e) throws IOException {
        ReadSupport.check(cancel);
        Long offset = offsets.get(e);
        if (offset == null) throw new IOException("Unknown TAR entry");
        return ReadSupport.verified(
                ReadSupport.region(file, offset, e.size()), e.size(), null, false, cancel);
    }

    public void close() throws IOException {
        file.close();
    }

    private byte[] header(long pos) throws IOException {
        if (headerCache == null) headerCache = new byte[65536];
        if (cacheStart < 0 || pos < cacheStart || pos - cacheStart > cacheLength - 512) {
            file.seek(pos);
            cacheStart = pos;
            cacheLength = (int) Math.min(headerCache.length, file.length() - pos);
            file.readFully(headerCache, 0, cacheLength);
        }
        return Arrays.copyOfRange(
                headerCache, (int) (pos - cacheStart), (int) (pos - cacheStart) + 512);
    }

    private void parse() throws IOException {
        long pos = 0, metadata = 0;
        Map<String, String> global = new HashMap<>(), local = new HashMap<>();
        boolean pending = false;
        while (true) {
            ReadSupport.check(cancel);
            if (ReadSupport.add(pos, 512) > file.length()) throw new EOFException("TAR 結尾或標頭截斷");
            byte[] h = header(pos);
            pos += 512;
            if (zero(h)) {
                if (pending) throw new IOException("TAR 擴充標頭沒有對應項目");
                if (ReadSupport.add(pos, 512) > file.length())
                    throw new EOFException("TAR 缺少第二個結尾區塊");
                h = header(pos);
                pos += 512;
                if (!zero(h)) throw new IOException("TAR 結尾無效");
                file.seek(pos);
                byte[] tail = new byte[65536];
                while (pos < file.length()) {
                    ReadSupport.check(cancel);
                    int n = file.read(tail);
                    if (n < 0) throw new EOFException();
                    for (int i = 0; i < n; i++)
                        if (tail[i] != 0) throw new IOException("TAR 結尾含多餘資料");
                    pos += n;
                }
                headerCache = null;
                return;
            }
            long checksum = number(h, 148, 8), sum = 0, signed = 0;
            for (int i = 0; i < 512; i++) {
                sum += i >= 148 && i < 156 ? 32 : h[i] & 255;
                signed += i >= 148 && i < 156 ? 32 : h[i];
            }
            if (checksum != sum && checksum != signed) throw new IOException("TAR 標頭 checksum 不符");
            String magic = text(h, 257, 6);
            if (!magic.isEmpty() && !magic.equals("ustar") && !magic.equals("ustar "))
                throw new IOException("未知 TAR 格式");
            long size = number(h, 124, 12);
            int type = h[156] & 255;
            if (type == 'x' || type == 'g' || type == 'L' || type == 'K') {
                metadata = ReadSupport.add(metadata, ReadSupport.add(size, 512));
                if (metadata > ReadSupport.MAX_HEADER || size > 1048576)
                    throw new IOException("TAR 擴充標頭過大");
                if (ReadSupport.add(pos, size) > file.length()) throw new EOFException();
                byte[] data = new byte[(int) size];
                file.seek(pos);
                file.readFully(data);
                if (type == 'K') throw new IOException("不支援 TAR 連結");
                if (type == 'L') {
                    local.put("path", text(data, 0, data.length));
                    pending = true;
                } else {
                    Map<String, String> p = properties(data);
                    if (type == 'g') {
                        for (var e : p.entrySet()) {
                            if (e.getValue().isEmpty()) global.remove(e.getKey());
                            else global.put(e.getKey(), e.getValue());
                        }
                    } else {
                        local.putAll(p);
                        pending = true;
                    }
                }
            } else {
                if (type != 0 && type != '0' && type != '5')
                    throw new IOException("不支援 TAR 連結、sparse 或特殊檔案: " + type);
                Map<String, String> props = new HashMap<>(global);
                for (var e : local.entrySet()) {
                    if (e.getValue().isEmpty()) props.remove(e.getKey());
                    else props.put(e.getKey(), e.getValue());
                }
                String name = text(h, 0, 100);
                if (!magic.isEmpty()) {
                    String prefix = text(h, 345, 155);
                    if (!prefix.isEmpty()) name = prefix + "/" + name;
                }
                name = props.getOrDefault("path", name);
                if (props.containsKey("size"))
                    try {
                        size = Long.parseLong(props.get("size"));
                        if (size < 0) throw new NumberFormatException();
                    } catch (NumberFormatException ex) {
                        throw new IOException("PAX size 無效", ex);
                    }
                if (type == '5' && size != 0) throw new IOException("TAR 資料夾包含資料");
                boolean directory = type == '5' || name.endsWith("/");
                if (directory && size != 0) throw new IOException("TAR 目錄大小無效");
                if (name.isEmpty()) throw new IOException("TAR 檔名為空");
                // Conventional archive root './' has no extractable payload.
                if (!(directory && (name.equals("./") || name.equals(".")))) {
                    Entry entry = new Entry(name, directory, size);
                    entries.add(entry);
                    offsets.put(entry, pos);
                    if (entries.size() > ReadSupport.MAX_ENTRIES) throw new IOException("TAR 項目過多");
                }
                local.clear();
                pending = false;
            }
            pos = ReadSupport.add(pos, ReadSupport.add(size, (512 - size % 512) % 512));
            if (pos > file.length()) throw new EOFException("TAR 資料或 padding 截斷");
        }
    }

    private static Map<String, String> properties(byte[] b) throws IOException {
        Map<String, String> map = new HashMap<>();
        int pos = 0;
        while (pos < b.length) {
            int start = pos;
            long len = 0;
            int digits = 0;
            while (pos < b.length && b[pos] != ' ') {
                int digit = b[pos++] - '0';
                if (digit < 0 || digit > 9 || ++digits > 8) throw new IOException("PAX length 無效");
                len = len * 10 + digit;
            }
            if (pos == b.length || len <= pos - start + 2 || len > b.length - start)
                throw new IOException("PAX record 越界");
            int end = start + (int) len;
            if (b[end - 1] != '\n') throw new IOException("PAX record 缺少換行");
            String body = utf8(b, ++pos, end - 1 - pos);
            int eq = body.indexOf('=');
            if (eq < 1) throw new IOException("PAX record 無效");
            String key = body.substring(0, eq), value = body.substring(eq + 1);
            if (key.startsWith("GNU.sparse")
                    || key.equals("SCHILY.realsize")
                    || key.equals("SCHILY.filetype") && !value.equals("file"))
                throw new IOException("不支援 sparse TAR");
            if (key.equals("path") || key.equals("size")) map.put(key, value);
            pos = end;
        }
        return map;
    }

    private static boolean zero(byte[] b) {
        for (byte v : b) if (v != 0) return false;
        return true;
    }

    private static String text(byte[] b, int o, int n) throws IOException {
        int e = o;
        while (e < o + n && b[e] != 0) e++;
        return utf8(b, o, e - o);
    }

    private static String utf8(byte[] b, int o, int n) throws IOException {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b, o, n))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new IOException("TAR 檔名不是 UTF-8", ex);
        }
    }

    private static long number(byte[] b, int o, int n) throws IOException {
        long v = 0;
        if ((b[o] & 128) != 0) {
            if ((b[o] & 64) != 0) throw new IOException("負數 TAR 大小");
            v = b[o] & 127;
            for (int i = o + 1; i < o + n; i++) {
                if (v > Long.MAX_VALUE >>> 8) throw new IOException("TAR 數字溢位");
                v = (v << 8) | (b[i] & 255);
            }
            return v;
        }
        boolean ended = false;
        for (int i = o; i < o + n; i++) {
            int c = b[i] & 255;
            if (c == 0 || c == ' ') {
                if (v != 0) ended = true;
                continue;
            }
            if (ended || c < '0' || c > '7' || v > (Long.MAX_VALUE - (c - '0')) / 8)
                throw new IOException("TAR 八進位數字無效");
            v = v * 8 + c - '0';
        }
        return v;
    }
}
