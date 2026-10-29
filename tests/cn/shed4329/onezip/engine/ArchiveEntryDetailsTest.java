package cn.shed4329.onezip.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ArchiveEntryDetailsTest {
    @TempDir Path root;
    @Test void centralMetadataMatchesIndependentZipReader() throws Exception {
        Path zip=root.resolve("details.zip");
        try(var out=new ZipOutputStream(Files.newOutputStream(zip))){
            var folder=new ZipEntry("资料/");folder.setTimeLocal(LocalDateTime.of(2024,2,28,14,47,36));out.putNextEntry(folder);out.closeEntry();
            var file=new ZipEntry("资料/README.md");file.setComment("中文注释");file.setTimeLocal(LocalDateTime.of(2024,2,28,14,47,36));out.putNextEntry(file);out.write("hello metadata\n".repeat(100).getBytes(java.nio.charset.StandardCharsets.UTF_8));out.closeEntry();
        }
        var details=new ArchiveEngine().listDetails(zip,null,null);assertEquals(2,details.size());
        try(var oracle=new ZipFile(zip.toFile())){
            for(var detail:details){var expected=oracle.getEntry(detail.item().name());
                assertEquals(expected.getSize(),detail.item().size());assertEquals(expected.getCompressedSize(),detail.packedSize());
                assertEquals(expected.getTimeLocal(),detail.modified());assertEquals("Deflate",detail.method());assertEquals("无",detail.encryption());
                assertEquals(String.format(Locale.ROOT,"%08X",expected.getCrc()),detail.crc());assertEquals(Objects.requireNonNullElse(expected.getComment(),""),detail.comment());
            }
        }
    }
    @Test void encryptedZipListsWithoutPasswordAndAes2DoesNotInventCrc() throws Exception {
        Path input=Files.writeString(root.resolve("secret.txt"),"secret"),zip=root.resolve("secret.zip");
        var engine=new ArchiveEngine();engine.compress(List.of(input),zip,ArchiveEngine.Format.ZIP,1,"password".toCharArray(),null,null,1);
        var detail=engine.listDetails(zip,null,null).getFirst();assertEquals("AES-256",detail.encryption());assertEquals("",detail.crc());assertTrue(detail.packedSize()>0);
    }
    @Test void unknownMetadataRemainsUnknownAndListingDoesNotValidatePayload() throws Exception {
        Path input=Files.writeString(root.resolve("file.txt"),"generic metadata"),archive=root.resolve("file.tar");
        var engine=new ArchiveEngine();engine.compress(List.of(input),archive,ArchiveEngine.Format.TAR,0,null,null,null,1);
        var detail=engine.listDetails(archive,null,null).getFirst();assertEquals(-1,detail.packedSize());assertNull(detail.modified());assertEquals("",detail.method());
        Path zip=root.resolve("stored.zip");try(var out=new ZipOutputStream(Files.newOutputStream(zip))){
            var entry=new ZipEntry("data");entry.setMethod(ZipEntry.STORED);entry.setSize(3);entry.setCompressedSize(3);var crc=new CRC32();crc.update(new byte[]{1,2,3});entry.setCrc(crc.getValue());out.putNextEntry(entry);out.write(new byte[]{1,2,3});out.closeEntry();}
        try(var file=new java.io.RandomAccessFile(zip.toFile(),"rw")){file.seek(34);file.write(99);}
        assertEquals("Store",engine.listDetails(zip,null,null).getFirst().method());assertThrows(java.io.IOException.class,()->engine.test(zip,null,null,null));
    }
}
