package cn.shed4329.onezip.engine;

import cn.shed4329.onezip.engine.lzma.ArkLzma2OutputStream;
import cn.shed4329.onezip.engine.lzma.Lzma2EncoderPool;
import cn.shed4329.onezip.engine.lzma.ParallelLzma2OutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Bounded solid 7z writer. Consecutive nonempty files share one LZMA2 stream up to 2 MiB of input;
 * a larger file gets its own streaming folder. This grouping is our explicit policy, not a claim
 * about Ark's default block size.
 */
final class SevenZWriter implements AutoCloseable {
    static final long SOLID_BLOCK_SIZE = ArkLzma2OutputStream.BLOCK_SIZE;

    private record Entry(String name, boolean directory, long size, long crc) {}

    private record Folder(List<Entry> streams, long unpacked, long packed, long crc) {}

    private record PendingFolder(
            List<Entry> streams,
            long unpacked,
            long crc,
            List<Future<byte[]>> packed,
            boolean fragments) {}

    private final class Group {
        final List<Entry> streams = new ArrayList<>();
        final Crc32 crc = new Crc32();
        final long start;
        final OutputStream encoder;
        final java.io.ByteArrayOutputStream buffered;
        long unpacked;

        Group(long firstSize) throws IOException {
            if (level != 0 && workers.workers() > 1 && firstSize <= SOLID_BLOCK_SIZE) {
                buffered = new java.io.ByteArrayOutputStream(65536);
                encoder = buffered;
                start = 0;
            } else {
                buffered = null;
                drainFolders();
                payload.flush();
                start = position();
                encoder =
                        level != 0 && workers.workers() > 1 && firstSize >= 2 * SOLID_BLOCK_SIZE
                                ? new ParallelLzma2OutputStream(payload, level, workers, firstSize)
                                : new ArkLzma2OutputStream(payload, level, workspace);
            }
        }
    }

    private final RandomAccessFile file;
    private final java.io.BufferedOutputStream payload;
    private final int level;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Folder> folders = new ArrayList<>();
    private final ArkLzma2OutputStream.Workspace workspace = new ArkLzma2OutputStream.Workspace();
    private final Lzma2EncoderPool workers;
    private final ArrayDeque<PendingFolder> pending = new ArrayDeque<>();
    private int pendingWork;
    private Group group;
    private boolean finished, closed, entryActive;
    private IOException failure;

    SevenZWriter(Path path, int level) throws IOException {
        this(path, level, 0);
    }

    SevenZWriter(Path path, int level, int threads) throws IOException {
        workers = new Lzma2EncoderPool(threads);
        RandomAccessFile opened = null;
        try {
            opened = new RandomAccessFile(path.toFile(), "rw");
            opened.setLength(32);
            opened.seek(32);
        } catch (IOException e) {
            if (opened != null) opened.close();
            workers.close();
            throw e;
        }
        file = opened;
        this.level = level < 0 ? 5 : level;
        payload =
                new java.io.BufferedOutputStream(
                        new OutputStream() {
                            public void write(int b) throws IOException {
                                file.write(b);
                            }

                            public void write(byte[] b, int o, int n) throws IOException {
                                file.write(b, o, n);
                            }
                        },
                        65536);
    }

    OutputStream begin(String name, boolean directory, long size) throws IOException {
        ensureWritable();
        if (entryActive) throw new IOException("上一個 7z 項目尚未關閉");
        if (size < 0 || (directory && size != 0)) throw new IOException("無效的 7z 項目大小");
        boolean hasData = !directory && size != 0;
        if (hasData) {
            // Do not split an entry between folders: 7z substreams belong to
            // exactly one folder. Oversized entries stream through the encoder.
            if (group != null && size > SOLID_BLOCK_SIZE - group.unpacked) finishGroup();
            if (group == null)
                try {
                    group = new Group(size);
                } catch (IOException e) {
                    throw fail(e);
                }
        }
        Group target = hasData ? group : null;
        entryActive = true;
        return new OutputStream() {
            final Crc32 crc = new Crc32();
            long written;
            boolean entryClosed;

            public void write(int v) throws IOException {
                byte[] b = {(byte) v};
                write(b, 0, 1);
            }

            public void write(byte[] b, int o, int n) throws IOException {
                java.util.Objects.checkFromIndexSize(o, n, b.length);
                ensureWritable();
                if (entryClosed) throw new IOException("7z 項目已關閉");
                if (n == 0) return;
                if (target == null || n > size - written)
                    throw fail(new IOException("來源檔案在壓縮期間改變: " + name));
                try {
                    target.encoder.write(b, o, n);
                    crc.update(b, o, n);
                    target.crc.update(b, o, n);
                    written += n;
                    target.unpacked += n;
                } catch (IOException ex) {
                    throw fail(ex);
                }
            }

            public void close() throws IOException {
                if (entryClosed) return;
                entryClosed = true;
                entryActive = false;
                // Preserve the primary write exception in try-with-resources;
                // returning here also avoids suppressing an exception onto itself.
                if (failure != null) return;
                ensureWritable();
                if (written != size) throw fail(new IOException("來源檔案在壓縮期間改變: " + name));
                Entry entry = new Entry(name, directory, size, crc.value());
                entries.add(entry);
                if (target != null) {
                    target.streams.add(entry);
                    if (target.unpacked >= SOLID_BLOCK_SIZE) finishGroup();
                }
            }
        };
    }

    private IOException fail(IOException ex) {
        if (failure == null) failure = ex;
        return ex;
    }

    private void ensureWritable() throws IOException {
        if (failure != null) throw failure;
        if (closed || finished) throw new IOException("7z writer 已關閉");
    }

    private void finishGroup() throws IOException {
        if (group == null) return;
        try {
            if (group.buffered != null) {
                byte[] raw = group.buffered.toByteArray();
                int parts = workers.folderParts(raw);
                while (pendingWork + parts > workers.workers()) drainFolder();
                List<Future<byte[]>> tasks = new ArrayList<>();
                if (parts == 1) tasks.add(workers.whole(raw, level));
                else
                    for (int part = 0; part < parts; part++) {
                        int start = raw.length * part / parts,
                                end = raw.length * (part + 1) / parts;
                        int prefix = Math.max(0, start - ArkLzma2OutputStream.BLOCK_SIZE);
                        tasks.add(
                                workers.fragment(
                                        java.util.Arrays.copyOfRange(raw, prefix, end),
                                        start - prefix,
                                        start,
                                        level));
                    }
                pendingWork += parts;
                pending.addLast(
                        new PendingFolder(
                                List.copyOf(group.streams),
                                group.unpacked,
                                group.crc.value(),
                                List.copyOf(tasks),
                                parts > 1));
            } else {
                if (group.encoder instanceof ArkLzma2OutputStream encoder) encoder.finish();
                else ((ParallelLzma2OutputStream) group.encoder).finish();
                payload.flush();
                folders.add(
                        new Folder(
                                List.copyOf(group.streams),
                                group.unpacked,
                                position() - group.start,
                                group.crc.value()));
            }
            group = null;
        } catch (IOException ex) {
            throw fail(ex);
        }
    }

    private void drainFolder() throws IOException {
        PendingFolder folder = pending.removeFirst();
        long packedSize = 0;
        for (Future<byte[]> task : folder.packed) {
            byte[] packed = Lzma2EncoderPool.await(task);
            payload.write(packed);
            packedSize += packed.length;
        }
        if (folder.fragments) {
            payload.write(0);
            packedSize++;
        }
        pendingWork -= folder.packed.size();
        folders.add(new Folder(folder.streams, folder.unpacked, packedSize, folder.crc));
    }

    private void drainFolders() throws IOException {
        while (!pending.isEmpty()) drainFolder();
    }

    private long position() {
        try {
            return file.getFilePointer();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    void finish() throws IOException {
        if (finished) return;
        ensureWritable();
        if (entryActive) throw new IOException("上一個 7z 項目尚未關閉");
        try {
            finishGroup();
            drainFolders();
            payload.flush();
            workers.close();
            BinaryWriter h = new BinaryWriter();
            h.write(1); // Header
            if (!folders.isEmpty()) {
                h.write(4); // MainStreamsInfo
                h.write(6);
                h.uint64(0);
                h.uint64(folders.size()); // PackInfo
                h.write(9);
                for (Folder f : folders) h.uint64(f.packed);
                h.write(0);
                h.write(7);
                h.write(11);
                h.uint64(folders.size());
                h.write(0); // UnpackInfo, folders inline
                for (Folder ignored : folders) {
                    h.uint64(1);
                    h.write(0x21);
                    h.write(0x21);
                    h.uint64(1);
                    h.write(18); // LZMA2, 2 MiB dictionary
                }
                h.write(12);
                for (Folder f : folders) h.uint64(f.unpacked);
                h.write(10);
                h.write(1);
                for (Folder f : folders) h.le(f.crc, 4);
                h.write(0);
                h.write(8); // End UnpackInfo; SubStreamsInfo
                if (folders.stream().anyMatch(f -> f.streams.size() > 1)) {
                    h.write(13);
                    for (Folder f : folders) h.uint64(f.streams.size()); // NumUnpackStream
                    h.write(9);
                    for (Folder f : folders)
                        for (int i = 0; i < f.streams.size() - 1; i++)
                            h.uint64(f.streams.get(i).size);
                    // A single-substream folder inherits its already defined folder
                    // CRC; only multi-substream folders have digests in this list.
                    h.write(10);
                    h.write(1);
                    for (Folder f : folders)
                        if (f.streams.size() > 1) for (Entry e : f.streams) h.le(e.crc, 4);
                }
                h.write(0);
                h.write(0); // End SubStreamsInfo; End Streams
            }
            h.write(5);
            h.uint64(entries.size()); // FilesInfo
            List<Entry> empty = entries.stream().filter(e -> e.directory || e.size == 0).toList();
            if (!empty.isEmpty()) {
                property(
                        h,
                        14,
                        bits(entries.stream().map(e -> e.directory || e.size == 0).toList()));
                property(h, 15, bits(empty.stream().map(e -> !e.directory).toList()));
            }
            BinaryWriter names = new BinaryWriter();
            names.write(0);
            for (Entry e : entries) {
                names.append(e.name.getBytes(StandardCharsets.UTF_16LE));
                names.le(0, 2);
            }
            property(h, 17, names.toByteArray());
            h.write(0);
            h.write(0);
            long dataSize = file.getFilePointer() - 32;
            byte[] header = h.toByteArray();
            long nextOffset = dataSize;
            if (header.length > ReadSupport.MAX_HEADER) throw new IOException("7z 目錄標頭超過大小上限");
            // Many small entries otherwise spend more bytes on UTF-16 names and
            // substream metadata than on file contents. Compress that actual header
            // with our own LZMA2 encoder, in a separate, independently reset stream.
            // Tiny/stored headers retain their old representation; only select the
            // encoded form when its payload plus descriptor is genuinely smaller.
            if (level != 0 && header.length >= 1024) {
                java.io.ByteArrayOutputStream encoded = new java.io.ByteArrayOutputStream();
                try (ArkLzma2OutputStream encoder =
                        new ArkLzma2OutputStream(encoded, level, workspace)) {
                    encoder.write(header);
                }
                byte[] packed = encoded.toByteArray();
                BinaryWriter descriptor = new BinaryWriter();
                descriptor.write(
                        23); // EncodedHeader; the following StreamsInfo describes metadata only.
                descriptor.write(6);
                descriptor.uint64(dataSize);
                descriptor.uint64(1);
                descriptor.write(9);
                descriptor.uint64(packed.length);
                descriptor.write(0); // PackInfo
                descriptor.write(7);
                descriptor.write(11);
                descriptor.uint64(1);
                descriptor.write(0);
                descriptor.uint64(1);
                descriptor.write(0x21);
                descriptor.write(0x21);
                descriptor.uint64(1);
                descriptor.write(18);
                descriptor.write(12);
                descriptor.uint64(header.length);
                descriptor.write(10);
                descriptor.write(1);
                descriptor.le(Crc32.of(header), 4);
                descriptor.write(0);
                descriptor.write(0); // End UnpackInfo; End StreamsInfo
                byte[] compact = descriptor.toByteArray();
                if ((long) packed.length + compact.length < header.length) {
                    file.write(packed);
                    nextOffset += packed.length;
                    header = compact;
                }
            }
            file.write(header);
            BinaryWriter startHeader = new BinaryWriter();
            startHeader.le(nextOffset, 8);
            startHeader.le(header.length, 8);
            startHeader.le(Crc32.of(header), 4);
            file.seek(0);
            file.write(new byte[] {0x37, 0x7a, (byte) 0xbc, (byte) 0xaf, 0x27, 0x1c, 0, 4});
            byte[] start = startHeader.toByteArray();
            BinaryWriter signature = new BinaryWriter();
            signature.le(Crc32.of(start), 4);
            signature.append(start);
            file.write(signature.toByteArray());
            finished = true;
        } catch (IOException e) {
            throw fail(e);
        }
    }

    private static void property(BinaryWriter h, int id, byte[] data) {
        h.write(id);
        h.uint64(data.length);
        h.append(data);
    }

    private static byte[] bits(List<Boolean> flags) {
        byte[] result = new byte[(flags.size() + 7) / 8];
        for (int i = 0; i < flags.size(); ++i)
            if (flags.get(i)) result[i / 8] |= (byte) (0x80 >>> (i % 8));
        return result;
    }

    public void close() throws IOException {
        if (closed) return;
        closed = true;
        // Abort does not finish a pending solid encoder or manufacture a valid
        // header; ArchiveEngine removes its unpublished staging file on failure.
        try {
            workers.close();
        } finally {
            try {
                payload.flush();
            } finally {
                file.close();
            }
        }
    }
}
