package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Transactional ZIP32 edits. Existing local records are copied verbatim, including encryption. */
public final class ZipEditor {
    private ZipEditor() {}

    private record Record(String name, boolean directory, long offset, byte[] central) {}

    private record Catalog(List<Record> entries, long central, byte[] comment) {}

    private record Source(
            Path path, String name, boolean directory, long size, ArchiveTimes times) {}

    public static void update(
            Path archive,
            List<Path> additions,
            String prefix,
            List<ArchiveEngine.ArchiveItem> removals,
            Charset charset,
            BooleanSupplier cancelled)
            throws IOException {
        ReadSupport.check(cancelled);
        archive = archive.toAbsolutePath().normalize();
        var replacement = new ArchiveReplacement(archive);
        if (!prefix.isEmpty()
                && (!prefix.endsWith("/")
                        || prefix.startsWith("/")
                        || Arrays.stream(prefix.split("/"))
                                .anyMatch(
                                        p -> p.equals("..") || p.equals(".") || p.contains("\\"))))
            throw new IOException("当前归档路径不适合添加文件");
        Catalog original = catalog(archive, charset);
        List<Source> sources = collect(additions, prefix, archive, cancelled);
        Set<String> names = new HashSet<>();
        for (Source source : sources)
            if (!names.add(source.name)) throw new IOException("添加文件名重复：" + source.name);
        Set<String> deleted = new HashSet<>(), deletedFolders = new HashSet<>();
        for (var item : removals) {
            deleted.add(item.name());
            if (item.directory())
                deletedFolders.add(item.name().endsWith("/") ? item.name() : item.name() + "/");
        }
        List<Record> keep =
                original.entries.stream()
                        .filter(
                                r -> {
                                    if (names.contains(r.name)
                                            || deleted.contains(r.name)
                                            || r.directory
                                                    && deletedFolders.contains(
                                                            r.name.endsWith("/")
                                                                    ? r.name
                                                                    : r.name + "/")) return false;
                                    for (int slash = r.name.indexOf('/');
                                            slash >= 0;
                                            slash = r.name.indexOf('/', slash + 1))
                                        if (deletedFolders.contains(r.name.substring(0, slash + 1)))
                                            return false;
                                    return true;
                                })
                        .toList();
        Set<String> retained = new HashSet<>();
        for (Record record : keep) retained.add(record.name);
        Map<String, String> cases = new HashMap<>();
        for (String name : retained) cases.put(name.toLowerCase(Locale.ROOT), name);
        for (Source source : sources) {
            String prior = cases.putIfAbsent(source.name.toLowerCase(Locale.ROOT), source.name);
            if (prior != null && !prior.equals(source.name))
                throw new IOException("存在仅大小写不同的项目：" + source.name);
        }
        for (Source source : sources) {
            String name = source.name;
            if (!source.directory && retained.contains(name + "/"))
                throw new IOException("同名文件夹已存在：" + name);
            String clean = source.directory ? name.substring(0, name.length() - 1) : name;
            if (source.directory && retained.contains(clean))
                throw new IOException("同名文件已存在：" + clean);
            for (int slash = clean.indexOf('/'); slash >= 0; slash = clean.indexOf('/', slash + 1))
                if (retained.contains(clean.substring(0, slash)))
                    throw new IOException("父路径是文件：" + clean);
        }
        if (sources.isEmpty() && keep.size() == original.entries.size()) {
            ReadSupport.check(cancelled);
            return;
        }
        Path added = null, staging = null;
        try {
            Catalog fresh = null;
            if (!sources.isEmpty()) {
                added = Files.createTempFile(archive.getParent(), ".onezip-added-", ".zip");
                byte[] buffer = new byte[65536];
                try (ZipWriter writer = new ZipWriter(added, 5, null)) {
                    for (Source source : sources) {
                        ReadSupport.check(cancelled);
                        try (OutputStream out =
                                writer.begin(
                                        source.name, source.directory, source.size, source.times)) {
                            if (!source.directory)
                                try (InputStream in = Files.newInputStream(source.path)) {
                                    int n;
                                    while ((n = in.read(buffer)) != -1) {
                                        ReadSupport.check(cancelled);
                                        out.write(buffer, 0, n);
                                    }
                                }
                        }
                    }
                    writer.finish();
                }
                fresh = catalog(added, Charset.forName("UTF-8"));
            }
            if (keep.size() + (fresh == null ? 0 : fresh.entries.size()) >= 65535)
                throw new IOException("编辑暂支持 ZIP32，项目数不能超过 65534");
            staging = Files.createTempFile(archive.getParent(), ".onezip-edit-", ".zip");
            try (RandomAccessFile out = new RandomAccessFile(staging.toFile(), "rw")) {
                List<byte[]> central = new ArrayList<>();
                copyRecords(archive, original, keep, out, central, cancelled);
                if (fresh != null)
                    copyRecords(added, fresh, fresh.entries, out, central, cancelled);
                long start = out.getFilePointer();
                for (byte[] record : central) {
                    ReadSupport.check(cancelled);
                    out.write(record);
                }
                long length = out.getFilePointer() - start;
                if (out.getFilePointer() >= 0xffffffffL)
                    throw new IOException("编辑暂支持小于 4 GiB 的 ZIP32");
                BinaryWriter end = new BinaryWriter();
                end.le(0x06054b50L, 4);
                end.le(0, 4);
                end.le(central.size(), 2);
                end.le(central.size(), 2);
                end.le(length, 4);
                end.le(start, 4);
                end.le(original.comment.length, 2);
                end.append(original.comment);
                out.write(end.toByteArray());
            }
            ReadSupport.check(cancelled);
            replacement.publish(staging);
            staging = null;
        } finally {
            if (added != null) Files.deleteIfExists(added);
            if (staging != null) Files.deleteIfExists(staging);
        }
    }

    private static List<Source> collect(
            List<Path> inputs, String prefix, Path archive, BooleanSupplier cancelled)
            throws IOException {
        List<Source> sources = new ArrayList<>();
        for (Path input : inputs) {
            Path absolute = input.toAbsolutePath().normalize();
            if (absolute.getFileName() == null) throw new IOException("不能直接添加磁盘根目录");
            try (var walk = Files.walk(absolute)) {
                var iterator = walk.iterator();
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    ReadSupport.check(cancelled);
                    var attrs =
                            Files.readAttributes(
                                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (attrs.isSymbolicLink()
                            || attrs.isOther()
                            || !attrs.isDirectory() && !attrs.isRegularFile())
                        throw new IOException("不能添加链接或特殊文件：" + path);
                    if (attrs.isRegularFile() && Files.isSameFile(path, archive))
                        throw new IOException("不能把压缩包添加到自身");
                    String relative =
                            absolute.getFileName()
                                    + (path.equals(absolute)
                                            ? ""
                                            : "/"
                                                    + absolute.relativize(path)
                                                            .toString()
                                                            .replace('\\', '/'));
                    sources.add(
                            new Source(
                                    path,
                                    prefix + relative + (attrs.isDirectory() ? "/" : ""),
                                    attrs.isDirectory(),
                                    attrs.isDirectory() ? 0 : attrs.size(),
                                    ArchiveTimes.of(attrs)));
                }
            }
        }
        return sources;
    }

    private static Catalog catalog(Path path, Charset charset) throws IOException {
        try (ZipArchiveReader reader = new ZipArchiveReader(path, null, null, charset);
                RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long end = ZipArchiveReader.endRecord(file);
            file.seek(end);
            byte[] header = new byte[22];
            file.readFully(header);
            int count = (int) le(header, 10, 2);
            long size = le(header, 12, 4), central = le(header, 16, 4);
            if (count == 65535 || size == 0xffffffffL || central == 0xffffffffL)
                throw new IOException("ZIP64 可以浏览和解压，编辑暂只支持 ZIP32");
            if (count != reader.entries().size() || central + size != end)
                throw new IOException("不支持此 ZIP 的附加目录记录");
            byte[] comment = new byte[(int) le(header, 20, 2)];
            file.readFully(comment);
            List<Record> records = new ArrayList<>();
            file.seek(central);
            for (int i = 0; i < count; i++) {
                byte[] fixed = new byte[46];
                file.readFully(fixed);
                int variable = (int) (le(fixed, 28, 2) + le(fixed, 30, 2) + le(fixed, 32, 2));
                byte[] record = Arrays.copyOf(fixed, 46 + variable);
                file.readFully(record, 46, variable);
                long offset = le(record, 42, 4);
                if (offset >= central
                        || le(record, 20, 4) == 0xffffffffL
                        || le(record, 24, 4) == 0xffffffffL) throw new IOException("编辑暂只支持 ZIP32");
                var entry = reader.entries().get(i);
                records.add(new Record(entry.name(), entry.directory(), offset, record));
            }
            return new Catalog(records, central, comment);
        }
    }

    private static void copyRecords(
            Path path,
            Catalog catalog,
            List<Record> keep,
            RandomAccessFile out,
            List<byte[]> central,
            BooleanSupplier cancelled)
            throws IOException {
        if (keep.isEmpty()) return;
        TreeSet<Long> offsets = new TreeSet<>();
        for (Record record : catalog.entries) offsets.add(record.offset);
        try (FileChannel in = FileChannel.open(path, StandardOpenOption.READ)) {
            FileChannel target = out.getChannel();
            ByteBuffer fallback = ByteBuffer.allocate(65536);
            for (int index = 0; index < keep.size(); ) {
                ReadSupport.check(cancelled);
                long start = keep.get(index).offset, position = out.getFilePointer();
                Long following = offsets.higher(start);
                long end = following == null ? catalog.central : following;
                int limit = index + 1;
                // Batch only physically adjacent records in the existing central-directory order.
                while (limit < keep.size() && keep.get(limit).offset == end) {
                    following = offsets.higher(end);
                    end = following == null ? catalog.central : following;
                    limit++;
                }
                if (end - start >= 0xffffffffL - position) throw new IOException("ZIP32 大小限制");
                for (int recordIndex = index; recordIndex < limit; recordIndex++) {
                    Record record = keep.get(recordIndex);
                    long updatedOffset = position + record.offset - start;
                    byte[] updated = record.central.clone();
                    for (int i = 0; i < 4; i++)
                        updated[42 + i] = (byte) (updatedOffset >>> (8 * i));
                    central.add(updated);
                }
                long cursor = start;
                while (cursor < end) {
                    ReadSupport.check(cancelled);
                    long copied =
                            in.transferTo(cursor, Math.min(end - cursor, 8L * 1024 * 1024), target);
                    if (copied == 0) {
                        fallback.clear();
                        fallback.limit((int) Math.min(fallback.capacity(), end - cursor));
                        int read = in.read(fallback, cursor);
                        if (read < 0) throw new EOFException();
                        if (read == 0) throw new IOException("无法继续复制 ZIP 数据");
                        fallback.flip();
                        while (fallback.hasRemaining()) {
                            ReadSupport.check(cancelled);
                            if (target.write(fallback) == 0) throw new IOException("无法继续写入 ZIP 数据");
                        }
                        copied = read;
                    }
                    cursor += copied;
                }
                index = limit;
            }
        }
    }

    private static long le(byte[] bytes, int start, int count) {
        long value = 0;
        for (int i = 0; i < count; i++) value |= (long) (bytes[start + i] & 255) << (8 * i);
        return value;
    }
}
