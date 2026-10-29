package cn.shed4329.onezip.engine;

import cn.shed4329.onezip.engine.lzma.LzmaInputStream;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Independent 7z reader, including encoded headers and solid substreams. */
final class SevenZArchiveReader implements ArchiveReader {
    private record Coder(long id, byte[] properties) {}

    private static final class Folder {
        final List<Coder> coders = new ArrayList<>();
        int[] next;
        int first, last;
        long[] output;
        long packedOffset, packedSize;
        Long packedCrc, crc;
        long[] sizes;
        Long[] checksums;

        long size() {
            return output[last];
        }
    }

    private static final class Streams {
        long packPosition;
        long[] packed = new long[0];
        Long[] crc = new Long[0];
        final List<Folder> folders = new ArrayList<>();
    }

    private record Spec(Entry entry, Folder folder, int index, Long crc) {}

    private final RandomAccessFile file;
    private final Path path;
    private final BooleanSupplier cancel;
    private final char[] password;
    private final List<Entry> entries;
    private final Map<Entry, Spec> specs;
    private Folder active;
    private InputStream folderStream;
    private int nextSubstream;
    private boolean entryComplete = true;

    SevenZArchiveReader(Path path, char[] password, BooleanSupplier cancel) throws IOException {
        this.path = path;
        entries = new ArrayList<>();
        specs = new IdentityHashMap<>();
        this.cancel = cancel;
        file = new RandomAccessFile(path.toFile(), "r");
        this.password = password == null ? new char[0] : password.clone();
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

    private SevenZArchiveReader(SevenZArchiveReader source) throws IOException {
        path = source.path;
        cancel = source.cancel;
        file = new RandomAccessFile(path.toFile(), "r");
        password = source.password.clone();
        entries = source.entries;
        specs = source.specs;
    }

    public ArchiveReader fork() throws IOException {
        return new SevenZArchiveReader(this);
    }

    public List<List<Entry>> groups() {
        Map<Folder, List<Entry>> groups = new LinkedHashMap<>();
        for (Entry entry : entries)
            groups.computeIfAbsent(specs.get(entry).folder, k -> new ArrayList<>()).add(entry);
        return new ArrayList<>(groups.values());
    }

    public long workerMemory() {
        long maximum = 16L * 1024 * 1024;
        for (Spec spec : specs.values())
            if (spec.folder != null) {
                long memory = 8L * 1024 * 1024;
                for (Coder coder : spec.folder.coders) {
                    if (coder.id == 0x21) {
                        int p = coder.properties[0] & 255;
                        memory += p == 40 ? 0xffffffffL : (2L | (p & 1)) << (p / 2 + 11);
                    } else if (coder.id == 0x030101) {
                        long size = 0;
                        for (int i = 1; i < 5; i++)
                            size |= (long) (coder.properties[i] & 255) << (8 * (i - 1));
                        memory += size;
                    }
                }
                maximum = Math.max(maximum, memory);
            }
        return maximum;
    }

    private void parse() throws IOException {
        ReadSupport.Cursor start =
                new ReadSupport.Cursor(ReadSupport.bytes(ReadSupport.region(file, 0, 32), 32));
        if (!Arrays.equals(
                ReadSupport.bytes(start, 6),
                new byte[] {0x37, 0x7a, (byte) 0xbc, (byte) 0xaf, 0x27, 0x1c}))
            throw new IOException("不是 7z 檔案");
        if (start.byteValue() != 0) throw new IOException("不支援的 7z major version");
        start.byteValue();
        long crc = start.little(4);
        byte[] data = start.readAllBytes();
        if (Crc32.of(data) != crc) throw new IOException("7z start header CRC 不符");
        ReadSupport.Cursor h = new ReadSupport.Cursor(data);
        long off = h.little(8), size = h.little(8), headerCrc = h.little(4);
        byte[] bytes =
                ReadSupport.bytes(
                        ReadSupport.region(file, ReadSupport.add(32, off), size),
                        ReadSupport.count(size, ReadSupport.MAX_HEADER));
        if (Crc32.of(bytes) != headerCrc) throw new IOException("7z next header CRC 不符");
        ReadSupport.Cursor header = new ReadSupport.Cursor(bytes);
        int type = header.byteValue();
        if (type == 23) {
            Streams encoded = streams(header);
            header.end();
            if (encoded.folders.size() != 1)
                throw new IOException("不支援多 folder 的 7z encoded header");
            Folder f = encoded.folders.getFirst();
            int n = ReadSupport.count(f.size(), ReadSupport.MAX_HEADER);
            try (InputStream in = openFolder(f)) {
                bytes = ReadSupport.bytes(in, n);
                if (in.read() != -1) throw new IOException("7z encoded header 大小不符");
            }
            header = new ReadSupport.Cursor(bytes);
            type = header.byteValue();
        }
        if (type != 1) throw new IOException("無效的 7z Header");
        Streams main = new Streams();
        boolean hasMain = false, hasFiles = false;
        while ((type = header.byteValue()) != 0) {
            ReadSupport.check(cancel);
            if (type == 2) {
                while (true) {
                    int property = header.byteValue();
                    if (property == 0) break;
                    header.property();
                }
            } else if (type == 3) throw new IOException("尚未實作 7z 外部屬性串流");
            else if (type == 4) {
                if (hasMain || hasFiles) throw new IOException("7z MainStreamsInfo 順序無效");
                main = streams(header);
                hasMain = true;
            } else if (type == 5) {
                if (hasFiles) throw new IOException("重複的 7z FilesInfo");
                files(header, main);
                hasFiles = true;
            } else throw new IOException("未知的 7z header id: " + type);
        }
        header.end();
        if (!hasFiles) throw new IOException("7z 缺少檔案目錄");
    }

    private Streams streams(ReadSupport.Cursor h) throws IOException {
        Streams s = new Streams();
        int id = h.byteValue();
        if (id == 6) {
            s.packPosition = h.number();
            int n = h.count();
            h.expect(9);
            s.packed = new long[n];
            for (int i = 0; i < n; i++) s.packed[i] = h.number();
            s.crc = new Long[n];
            id = h.byteValue();
            if (id == 10) {
                s.crc = digests(h, n);
                id = h.byteValue();
            }
            if (id != 0) throw new IOException("無效的 7z PackInfo");
            id = h.byteValue();
        }
        if (id == 7) {
            h.expect(11);
            int n = h.count();
            if (h.byteValue() != 0) throw new IOException("不支援外部 7z Folder");
            for (int i = 0; i < n; i++) {
                ReadSupport.check(cancel);
                s.folders.add(folder(h));
            }
            h.expect(12);
            for (Folder f : s.folders)
                for (int i = 0; i < f.output.length; i++) f.output[i] = h.number();
            id = h.byteValue();
            if (id == 10) {
                Long[] crc = digests(h, n);
                for (int i = 0; i < n; i++) s.folders.get(i).crc = crc[i];
                id = h.byteValue();
            }
            if (id != 0) throw new IOException("無效的 7z UnpackInfo");
            id = h.byteValue();
        }
        for (Folder f : s.folders) {
            f.sizes = new long[] {f.size()};
            f.checksums = new Long[] {f.crc};
        }
        if (id == 8) {
            substreams(h, s);
            id = h.byteValue();
        }
        if (id != 0) throw new IOException("無效的 7z StreamsInfo");
        if (s.folders.size() != s.packed.length) throw new IOException("7z packed stream 數量不符");
        long position = ReadSupport.add(32, s.packPosition);
        for (int i = 0; i < s.folders.size(); i++) {
            Folder f = s.folders.get(i);
            f.packedOffset = position;
            f.packedSize = s.packed[i];
            f.packedCrc = s.crc[i];
            position = ReadSupport.add(position, f.packedSize);
            if (position > file.length()) throw new IOException("7z packed stream 越界");
        }
        return s;
    }

    private Folder folder(ReadSupport.Cursor h) throws IOException {
        int n = ReadSupport.count(h.number(), 8);
        if (n == 0) throw new IOException("空的 7z coder graph");
        Folder f = new Folder();
        f.next = new int[n];
        Arrays.fill(f.next, -1);
        f.output = new long[n];
        for (int i = 0; i < n; i++) {
            int flags = h.byteValue(), len = flags & 15;
            if (len < 1 || len > 8 || (flags & 0xc0) != 0)
                throw new IOException("不支援的 7z coder flags");
            long id = 0;
            for (int j = 0; j < len; j++) id = (id << 8) | h.byteValue();
            if ((flags & 16) != 0 && (h.number() != 1 || h.number() != 1))
                throw new IOException("尚未實作多輸入／輸出的 7z coder");
            byte[] props = (flags & 32) != 0 ? h.property().readAllBytes() : new byte[0];
            validateCoder(id, props);
            f.coders.add(new Coder(id, props));
        }
        boolean[] boundIn = new boolean[n], boundOut = new boolean[n];
        for (int i = 0; i < n - 1; i++) {
            int input = ReadSupport.count(h.number(), n - 1),
                    output = ReadSupport.count(h.number(), n - 1);
            if (boundIn[input] || boundOut[output]) throw new IOException("7z coder graph 重複綁定");
            boundIn[input] = true;
            boundOut[output] = true;
            f.next[output] = input;
        }
        for (int i = 0; i < n; i++) {
            if (!boundIn[i]) f.first = i;
            if (!boundOut[i]) f.last = i;
        }
        boolean[] visited = new boolean[n];
        int seen = 0;
        for (int i = f.first; i != -1; i = f.next[i]) {
            if (visited[i]) throw new IOException("7z coder graph 含循環");
            visited[i] = true;
            seen++;
        }
        if (seen != n) throw new IOException("7z coder graph 不連通");
        return f;
    }

    private static void validateCoder(long id, byte[] props) throws IOException {
        if (id == 0 && props.length == 0) return;
        if (id == 0x21 && props.length == 1 && (props[0] & 255) <= 40) return;
        if (id == 0x030101 && props.length == 5 && (props[0] & 255) < 225) return;
        if (id == 0x040108 && props.length == 0) return;
        if (id == 3 && props.length == 1) return; // Delta filter.
        if (id == 0x03030103 && (props.length == 0 || props.length == 4)) return; // x86 BCJ.
        if (id == 0x06f10701) {
            SevenZAesInputStream.shape(props);
            return;
        }
        throw new IOException("尚未實作 7z coder / filter: 0x" + Long.toHexString(id));
    }

    private static boolean[] bits(ReadSupport.Cursor h, int n) throws IOException {
        boolean[] b = new boolean[n];
        int value = 0;
        for (int i = 0; i < n; i++) {
            if (i % 8 == 0) value = h.byteValue();
            b[i] = (value & (0x80 >>> (i % 8))) != 0;
        }
        return b;
    }

    private static Long[] digests(ReadSupport.Cursor h, int n) throws IOException {
        int all = h.byteValue();
        if (all > 1) throw new IOException("無效的 7z CRC flags");
        boolean[] defined = all == 0 ? bits(h, n) : null;
        Long[] result = new Long[n];
        for (int i = 0; i < n; i++) if (defined == null || defined[i]) result[i] = h.little(4);
        return result;
    }

    private void substreams(ReadSupport.Cursor h, Streams s) throws IOException {
        int id = h.byteValue();
        if (id == 13) {
            for (Folder f : s.folders) {
                int n = h.count();
                f.sizes = new long[n];
                f.checksums = new Long[n];
            }
            id = h.byteValue();
        }
        boolean hasSizes = id == 9;
        for (Folder f : s.folders) {
            long sum = 0;
            int n = f.sizes.length;
            if (n > 1 && !hasSizes) throw new IOException("7z solid substream 大小遺失");
            for (int i = 0; i < n - 1; i++) {
                long size = h.number();
                f.sizes[i] = size;
                sum = ReadSupport.add(sum, size);
            }
            if (n > 0) {
                if (sum > f.size()) throw new IOException("7z substreams 大小溢位");
                f.sizes[n - 1] = f.size() - sum;
            } else if (f.size() != 0) throw new IOException("7z 空 folder 含資料");
        }
        if (hasSizes) id = h.byteValue();
        int count = 0;
        for (Folder f : s.folders)
            if (f.sizes.length != 1 || f.crc == null)
                count =
                        ReadSupport.count(
                                ReadSupport.add(count, f.sizes.length), ReadSupport.MAX_ENTRIES);
        Long[] crc = new Long[count];
        if (id == 10) {
            crc = digests(h, count);
            id = h.byteValue();
        }
        int index = 0;
        for (Folder f : s.folders) {
            if (f.sizes.length == 1 && f.crc != null) f.checksums[0] = f.crc;
            else for (int i = 0; i < f.sizes.length; i++) f.checksums[i] = crc[index++];
        }
        if (id != 0) throw new IOException("無效的 7z SubStreamsInfo");
    }

    private void files(ReadSupport.Cursor h, Streams s) throws IOException {
        int count = h.count();
        String[] names = new String[count];
        boolean[] empty = new boolean[count], emptyFile = null;
        boolean gotNames = false, gotEmpty = false;
        long[] attributes = new long[count];
        int type;
        while ((type = h.byteValue()) != 0) {
            ReadSupport.Cursor p = h.property();
            if (type == 17) {
                if (gotNames) throw new IOException("重複的 7z 名稱屬性");
                gotNames = true;
                if (p.byteValue() != 0) throw new IOException("不支援外部 7z 名稱");
                for (int i = 0; i < count; i++) names[i] = p.utf16();
                p.end();
            } else if (type == 14) {
                if (gotEmpty) throw new IOException("重複的 7z EmptyStream");
                gotEmpty = true;
                empty = bits(p, count);
                p.end();
            } else if (type == 15 || type == 16) {
                if (!gotEmpty) throw new IOException("7z EmptyStream 順序無效");
                int n = 0;
                for (boolean b : empty) if (b) n++;
                boolean[] flags = bits(p, n);
                p.end();
                if (type == 15) emptyFile = flags;
                else
                    for (boolean anti : flags)
                        if (anti) throw new IOException("拒絕 7z anti-item 刪除項目");
            } else if (type == 21) {
                int all = p.byteValue();
                if (all > 1) throw new IOException("無效的 7z 屬性 flags");
                boolean[] defined = all == 0 ? bits(p, count) : null;
                if (p.byteValue() != 0) throw new IOException("不支援外部 7z 檔案屬性");
                for (int i = 0; i < count; i++)
                    if (defined == null || defined[i]) attributes[i] = p.little(4);
                p.end();
            }
            // Times, comments and padding are metadata and do not affect decoding.
        }
        if (count > 0 && !gotNames) throw new IOException("7z 名稱屬性遺失");
        List<Folder> folders = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        for (Folder f : s.folders)
            for (int i = 0; i < f.sizes.length; i++) {
                folders.add(f);
                indices.add(i);
            }
        int stream = 0, emptyIndex = 0;
        for (int i = 0; i < count; i++) {
            ReadSupport.check(cancel);
            long attrs = attributes[i];
            if ((attrs & 0x400) != 0
                    || ((attrs & 0x8000) != 0 && (attrs & 0xf0000000L) == 0xa0000000L))
                throw new IOException("拒絕 7z 連結: " + names[i]);
            Folder folder = null;
            int index = 0;
            boolean directory = false;
            long size = 0;
            Long crc = null;
            if (empty[i]) {
                directory = emptyFile == null || !emptyFile[emptyIndex];
                emptyIndex++;
            } else {
                if (stream >= folders.size()) throw new IOException("7z 項目與 substream 數量不符");
                folder = folders.get(stream);
                index = indices.get(stream++);
                size = folder.sizes[index];
                crc = folder.checksums[index];
            }
            Entry e = new Entry(names[i], directory, size);
            entries.add(e);
            specs.put(e, new Spec(e, folder, index, crc));
        }
        if (stream != folders.size()) throw new IOException("7z 含未對應的資料串流");
    }

    private InputStream openFolder(Folder f) throws IOException {
        InputStream
                packed =
                        ReadSupport.verified(
                                new BufferedInputStream(
                                        ReadSupport.region(file, f.packedOffset, f.packedSize)),
                                f.packedSize,
                                f.packedCrc,
                                true,
                                cancel),
                decoded = packed;
        for (int i = f.first; i != -1; i = f.next[i]) {
            Coder c = f.coders.get(i);
            if (c.id == 0x21) decoded = new LzmaInputStream(decoded, c.properties[0] & 255);
            else if (c.id == 0x030101)
                decoded = new LzmaInputStream(decoded, c.properties, f.output[i]);
            else if (c.id == 0x040108) decoded = new DeflateInputStream(decoded);
            else if (c.id == 3) decoded = delta(decoded, (c.properties[0] & 255) + 1);
            else if (c.id == 0x03030103) {
                long start = 0;
                for (int j = 0; j < c.properties.length; j++)
                    start |= (long) (c.properties[j] & 255) << (8 * j);
                decoded = new X86BcjInputStream(decoded, start);
            } else if (c.id == 0x06f10701)
                decoded =
                        new SevenZAesInputStream(
                                decoded, c.properties, password, f.output[i], cancel);
            decoded = ReadSupport.verified(decoded, f.output[i], null, true, cancel);
        }
        final InputStream result = decoded;
        InputStream complete =
                new FilterInputStream(result) {
                    boolean ended;

                    private int finish(int n) throws IOException {
                        if (n < 0 && !ended) {
                            if (packed.read() != -1)
                                throw new IOException("7z packed stream 含多餘資料");
                            ended = true;
                        }
                        return n;
                    }

                    public int read() throws IOException {
                        return finish(in.read());
                    }

                    public int read(byte[] b, int o, int n) throws IOException {
                        return finish(in.read(b, o, n));
                    }

                    public void close() {}
                };
        return ReadSupport.verified(complete, f.size(), f.crc, true, cancel);
    }

    private static InputStream delta(InputStream input, int distance) {
        return new FilterInputStream(input) {
            final byte[] history = new byte[256];
            int position;

            public int read() throws IOException {
                int b = in.read();
                if (b < 0) return -1;
                b = (b + history[(position - distance) & 255]) & 255;
                history[position++ & 255] = (byte) b;
                return b;
            }

            public int read(byte[] b, int o, int n) throws IOException {
                Objects.checkFromIndexSize(o, n, b.length);
                if (n == 0) return 0;
                int k = 0;
                for (; k < n; k++) {
                    int value = read();
                    if (value < 0) break;
                    b[o + k] = (byte) value;
                }
                return k == 0 ? -1 : k;
            }
        };
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    @Override
    public InputStream open(Entry entry) throws IOException {
        Spec s = specs.get(entry);
        if (s == null) throw new IOException("未知的 7z 項目");
        if (!entryComplete) throw new IOException("上一個 7z 項目尚未驗證");
        if (s.folder == null) return new ByteArrayInputStream(new byte[0]);
        if (active != s.folder) {
            if (active != null && nextSubstream != active.sizes.length)
                throw new IOException("7z solid substream 順序無效");
            active = s.folder;
            nextSubstream = 0;
            folderStream = openFolder(active);
        }
        if (s.index != nextSubstream) throw new IOException("7z 必須依目錄順序解壓");
        entryComplete = false;
        InputStream slice = ReadSupport.verified(folderStream, entry.size(), s.crc, false, cancel);
        return new FilterInputStream(slice) {
            boolean ended;

            private int finish(int n) throws IOException {
                if (n < 0 && !ended) {
                    nextSubstream++;
                    if (nextSubstream == active.sizes.length && folderStream.read() != -1)
                        throw new IOException("7z folder 大小不符");
                    entryComplete = true;
                    ended = true;
                }
                return n;
            }

            public int read() throws IOException {
                return finish(in.read());
            }

            public int read(byte[] b, int o, int n) throws IOException {
                return finish(in.read(b, o, n));
            }

            public void close() {}
        };
    }

    @Override
    public void close() throws IOException {
        Arrays.fill(password, '\0');
        file.close();
    }
}
