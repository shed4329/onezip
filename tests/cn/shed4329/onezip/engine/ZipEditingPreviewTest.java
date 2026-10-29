package cn.shed4329.onezip.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ZipEditingPreviewTest {
    @TempDir Path root;
    private static final Charset CP437=Charset.forName("IBM437");
    @Test void failedProgressPlanLeavesNoPartialOutput()throws Exception{
        Path input=Files.writeString(root.resolve("input.txt"),"payload"),output=root.resolve("failed.zip");
        ArchiveEngine.Progress broken=new ArchiveEngine.Progress(){public void files(List<String> names){throw new IllegalStateException("observer failure");}public void update(long done,long total,String name){}};
        assertThrows(IllegalStateException.class,()->new ArchiveEngine().compress(List.of(input),output,ArchiveEngine.Format.ZIP,5,null,null,broken,1));
        assertFalse(Files.exists(output));try(var files=Files.list(root)){assertEquals(List.of(input),files.toList());}
    }
    @Test void createsZipWithUtf8CommentAndPreservesPayload()throws Exception{
        Path input=Files.writeString(root.resolve("source.txt"),"payload 内容\n".repeat(100)),zip=root.resolve("comment.zip");
        String comment="创建时的注释\n第二行 🚀";
        new ArchiveEngine().compress(List.of(input),zip,ArchiveEngine.Format.ZIP,5,null,null,null,1,comment);
        try(var reader=new ZipFile(zip.toFile())){assertEquals(comment,reader.getComment());assertEquals(Files.readString(input),new String(reader.getInputStream(reader.getEntry("source.txt")).readAllBytes(),StandardCharsets.UTF_8));}
        assertEquals(comment,ZipComments.read(zip));
        Path boundary=root.resolve("boundary.zip");String maximum="界".repeat(21845);
        new ArchiveEngine().compress(List.of(input),boundary,ArchiveEngine.Format.ZIP,5,null,null,null,1,maximum);
        try(var reader=new ZipFile(boundary.toFile())){assertEquals(maximum,reader.getComment());}
    }
    @Test void rejectsInvalidCreationCommentsBeforeWritingOutput()throws Exception{
        Path input=Files.writeString(root.resolve("source.txt"),"payload"),output=root.resolve("invalid.zip");var engine=new ArchiveEngine();
        assertThrows(java.io.IOException.class,()->engine.compress(List.of(input),output,ArchiveEngine.Format.ZIP,5,null,null,null,1,"界".repeat(21846)));
        assertFalse(Files.exists(output));
        assertThrows(java.io.IOException.class,()->engine.compress(List.of(input),output,ArchiveEngine.Format.SEVEN_Z,5,null,null,null,1,"unsupported"));
        assertFalse(Files.exists(output));try(var files=Files.list(root)){assertEquals(List.of(input),files.toList());}
    }
    private Path sample()throws Exception{
        Path zip=root.resolve("original.zip");try(var out=new ZipOutputStream(Files.newOutputStream(zip))){out.setComment("全局注释");
            for(String name:List.of("keep.txt","folder/child.txt","old.txt")){var entry=new ZipEntry(name);entry.setComment("项目注释 "+name);entry.setTimeLocal(LocalDateTime.of(2024,2,28,14,47,36));out.putNextEntry(entry);out.write((name+" content\n").repeat(100).getBytes(StandardCharsets.UTF_8));out.closeEntry();}}
        return zip;
    }
    @Test void addsReplacesAndDeletesWhilePreservingUnchangedMetadata()throws Exception{
        Path zip=sample(),added=Files.writeString(root.resolve("old.txt"),"replacement");
        long packed;try(var before=new ZipFile(zip.toFile())){packed=before.getEntry("keep.txt").getCompressedSize();}
        ZipEditor.update(zip,List.of(added),"",List.of(new ArchiveEngine.ArchiveItem("folder/",true,0)),CP437,()->false);
        try(var result=new ZipFile(zip.toFile())){
            assertEquals("全局注释",result.getComment());assertEquals(2,result.size());assertNull(result.getEntry("folder/child.txt"));
            assertEquals("replacement",new String(result.getInputStream(result.getEntry("old.txt")).readAllBytes(),StandardCharsets.UTF_8));
            var keep=result.getEntry("keep.txt");assertEquals(packed,keep.getCompressedSize());assertEquals("项目注释 keep.txt",keep.getComment());assertEquals(LocalDateTime.of(2024,2,28,14,47,36),keep.getTimeLocal());
            assertEquals("keep.txt content\n".repeat(100),new String(result.getInputStream(keep).readAllBytes(),StandardCharsets.UTF_8));
        }
        new ArchiveEngine().test(zip,null,null,null);
        try(var files=Files.list(root)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".onezip-")));}
    }
    @Test void unchangedAesPayloadRemainsDecryptableByIndependentReader()throws Exception{
        Path input=Files.writeString(root.resolve("secret.txt"),"secret ".repeat(1000)),zip=root.resolve("secret.zip"),added=Files.writeString(root.resolve("added.txt"),"plain addition");
        new ArchiveEngine().compress(List.of(input),zip,ArchiveEngine.Format.ZIP,5,"password".toCharArray(),null,null,1);
        ZipEditor.update(zip,List.of(added),"",List.of(),CP437,()->false);
        try(var oracle=new net.lingala.zip4j.ZipFile(zip.toFile(),"password".toCharArray())){
            assertTrue(oracle.getFileHeader("secret.txt").isEncrypted());assertFalse(oracle.getFileHeader("added.txt").isEncrypted());
            assertEquals(Files.readString(input),new String(oracle.getInputStream(oracle.getFileHeader("secret.txt")).readAllBytes(),StandardCharsets.UTF_8));
        }
        new ArchiveEngine().test(zip,"password".toCharArray(),null,null);
    }
    @Test void cancellationMissingSourcesAndSelfAdditionPreserveOriginal()throws Exception{
        Path zip=sample();byte[] before=Files.readAllBytes(zip);
        assertThrows(java.io.IOException.class,()->ZipEditor.update(zip,List.of(),"",List.of(),CP437,()->true));assertArrayEquals(before,Files.readAllBytes(zip));
        assertThrows(java.io.IOException.class,()->ZipEditor.update(zip,List.of(root.resolve("missing.txt")),"",List.of(),CP437,()->false));
        assertThrows(java.io.IOException.class,()->ZipEditor.update(zip,List.of(zip),"",List.of(),CP437,()->false));assertArrayEquals(before,Files.readAllBytes(zip));
        try(var files=Files.list(root)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".onezip-")));}
    }
    @Test void folderPrefixAndRemovingAllEntriesProduceValidArchives()throws Exception{
        Path zip=sample(),file=Files.writeString(root.resolve("新文件.txt"),"test");
        ZipEditor.update(zip,List.of(file),"folder/",List.of(),CP437,()->false);
        var items=new ArchiveEngine().list(zip,null,null);assertTrue(items.stream().anyMatch(i->i.name().equals("folder/新文件.txt")));
        ZipEditor.update(zip,List.of(),"",items,CP437,()->false);try(var oracle=new ZipFile(zip.toFile())){assertEquals(0,oracle.size());}
    }
    @Test void filenameEncodingOverrideHonorsUtf8Flags()throws Exception{
        Charset gb=Charset.forName("GB18030");Path zip=root.resolve("gb.zip");
        try(var out=new ZipOutputStream(Files.newOutputStream(zip),gb)){out.putNextEntry(new ZipEntry("中文.txt"));out.write("内容".repeat(100).getBytes(gb));out.closeEntry();}
        var engine=new ArchiveEngine();var item=engine.listDetails(zip,null,null,gb).getFirst().item();assertEquals("中文.txt",item.name());
        engine.extract(zip,root.resolve("gb-output"),null,null,null,2,gb);assertEquals("内容".repeat(100),Files.readString(root.resolve("gb-output/中文.txt"),gb));
        Path utf8=sample();assertEquals("keep.txt",engine.listDetails(utf8,null,null,gb).getFirst().item().name());
    }
    @Test void caseOnlyCollisionDoesNotModifyTheArchive()throws Exception{
        Path zip=sample(),file=Files.writeString(root.resolve("KEEP.TXT"),"collision");byte[] before=Files.readAllBytes(zip);
        assertThrows(java.io.IOException.class,()->ZipEditor.update(zip,List.of(file),"",List.of(),CP437,()->false));assertArrayEquals(before,Files.readAllBytes(zip));
    }
    @Test void noChangesPreserveFileIdentityAndTimestamp()throws Exception{
        Path zip=sample();Files.setLastModifiedTime(zip,java.nio.file.attribute.FileTime.from(java.time.Instant.parse("2020-01-02T00:00:00Z")));
        var before=Files.readAttributes(zip,java.nio.file.attribute.BasicFileAttributes.class);byte[] bytes=Files.readAllBytes(zip);
        ZipEditor.update(zip,List.of(),"",List.of(),CP437,()->false);
        var after=Files.readAttributes(zip,java.nio.file.attribute.BasicFileAttributes.class);
        assertEquals(before.fileKey(),after.fileKey());assertEquals(before.lastModifiedTime(),after.lastModifiedTime());assertArrayEquals(bytes,Files.readAllBytes(zip));
    }
    @Test void retainedRecordsWorkWhenDirectoryOrderDiffersFromPhysicalOrder()throws Exception{
        Path zip=sample();byte[] bytes=Files.readAllBytes(zip);int end;
        try(var file=new java.io.RandomAccessFile(zip.toFile(),"r")){end=(int)ZipArchiveReader.endRecord(file);}
        var buffer=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);int start=buffer.getInt(end+16),position=start;List<byte[]> records=new ArrayList<>();
        while(position<end){int length=46+Short.toUnsignedInt(buffer.getShort(position+28))+Short.toUnsignedInt(buffer.getShort(position+30))+Short.toUnsignedInt(buffer.getShort(position+32));records.add(Arrays.copyOfRange(bytes,position,position+length));position+=length;}
        Collections.reverse(records);position=start;for(byte[] record:records){System.arraycopy(record,0,bytes,position,record.length);position+=record.length;}Files.write(zip,bytes);
        try(var oracle=new ZipFile(zip.toFile())){assertEquals(List.of("old.txt","folder/child.txt","keep.txt"),oracle.stream().map(ZipEntry::getName).toList());}
        ZipEditor.update(zip,List.of(),"",List.of(new ArchiveEngine.ArchiveItem("folder/",true,0)),CP437,()->false);
        try(var oracle=new ZipFile(zip.toFile())){
            assertEquals(List.of("old.txt","keep.txt"),oracle.stream().map(ZipEntry::getName).toList());
            for(String name:List.of("old.txt","keep.txt"))assertEquals((name+" content\n").repeat(100),new String(oracle.getInputStream(oracle.getEntry(name)).readAllBytes(),StandardCharsets.UTF_8));
        }
        new ArchiveEngine().test(zip,null,null,null);
    }
    @Test void largeCoalescedRangesDeleteGapsAndRollBackCancellationDuringCopy()throws Exception{
        Path zip=root.resolve("large.zip");byte[] payload=new byte[5*1024*1024];new Random(1234).nextBytes(payload);
        try(var out=new ZipOutputStream(Files.newOutputStream(zip))){for(int i=0;i<5;i++){
            payload[0]=(byte)i;var crc=new CRC32();crc.update(payload);var entry=new ZipEntry(i+".bin");entry.setMethod(ZipEntry.STORED);entry.setSize(payload.length);entry.setCompressedSize(payload.length);entry.setCrc(crc.getValue());out.putNextEntry(entry);out.write(payload);out.closeEntry();}}
        ZipEditor.update(zip,List.of(),"",List.of(new ArchiveEngine.ArchiveItem("2.bin",false,payload.length)),CP437,()->false);
        try(var oracle=new ZipFile(zip.toFile())){assertEquals(4,oracle.size());for(int i:List.of(0,1,3,4)){payload[0]=(byte)i;assertArrayEquals(payload,oracle.getInputStream(oracle.getEntry(i+".bin")).readAllBytes());}}
        byte[] before=Files.readAllBytes(zip);var cancelledAfterCopy=new java.util.concurrent.atomic.AtomicBoolean();
        assertThrows(java.io.IOException.class,()->ZipEditor.update(zip,List.of(),"",List.of(new ArchiveEngine.ArchiveItem("4.bin",false,payload.length)),CP437,()->{
            try(var files=Files.list(root)){for(Path path:files.filter(p->p.getFileName().toString().startsWith(".onezip-edit-")).toList())if(Files.size(path)>=8*1024*1024){cancelledAfterCopy.set(true);return true;}}
            catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}return false;
        }));
        assertTrue(cancelledAfterCopy.get());assertArrayEquals(before,Files.readAllBytes(zip));
        try(var files=Files.list(root)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".onezip-")));}
    }
}
