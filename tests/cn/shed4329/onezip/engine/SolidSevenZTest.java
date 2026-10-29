package cn.shed4329.onezip.engine;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import static org.junit.jupiter.api.Assertions.*;

class SolidSevenZTest {
    @TempDir Path temp;

    @Test void crossFileMatchesAndInterleavedEmptyEntriesDecodeIndependentlyAtEveryLevel() throws Exception {
        byte[] repeated=new byte[4096];new Random(519).nextBytes(repeated);
        LinkedHashMap<String,byte[]> expected=new LinkedHashMap<>();
        expected.put("資料/",null);
        for(int i=0;i<16;i++) {
            byte[] contents=repeated.clone();contents[contents.length-1]=(byte)i;
            expected.put("資料/part-"+i+".bin",contents);
            if(i==7){expected.put("資料/empty.txt",new byte[0]);expected.put("資料/空目錄/",null);}
        }
        for(int level=0;level<=9;level++) {
            Path archive=write("solid-"+level,level,expected);
            assertEquals(1,packedStreams(archive),"Cross-file history must share one folder");
            verifyIndependent(archive,expected);verifyOwn(archive,expected);
            if(level!=0)assertTrue(Files.size(archive)<16384,"Must exploit cross-file repetition, not just shrink headers");
        }
    }

    @Test void exactGroupBoundaryAndOversizedSingleEntryKeepSubstreamMapping() throws Exception {
        int limit=(int)SevenZWriter.SOLID_BLOCK_SIZE;
        LinkedHashMap<String,byte[]> expected=new LinkedHashMap<>();
        expected.put("almost.bin",pattern(limit-4096));
        expected.put("empty/",null);expected.put("empty.txt",new byte[0]);
        expected.put("boundary.bin",pattern(4096)); // Completes the first group exactly.
        expected.put("before-large.bin",pattern(4096));
        expected.put("large.bin",pattern(limit+17)); // Cannot share or split into folders.
        expected.put("after-large.bin",pattern(8192));
        expected.put("tail.bin",pattern(1024));
        for(int level:new int[]{0,5}) {
            Path archive=write("boundaries-"+level,level,expected);
            assertEquals(4,packedStreams(archive));
            verifyIndependent(archive,expected);verifyOwn(archive,expected);
        }
    }

    @Test void singletonAndEmptyOnlyArchivesRemainValid() throws Exception {
        LinkedHashMap<String,byte[]> singleton=new LinkedHashMap<>();
        singleton.put("empty/",null);singleton.put("only.bin",pattern(17321));singleton.put("empty.txt",new byte[0]);
        Path archive=write("singleton",5,singleton);assertEquals(1,packedStreams(archive));
        verifyIndependent(archive,singleton);verifyOwn(archive,singleton);
        LinkedHashMap<String,byte[]> empty=new LinkedHashMap<>();empty.put("empty/",null);empty.put("empty.txt",new byte[0]);
        archive=write("empties",5,empty);verifyIndependent(archive,empty);verifyOwn(archive,empty);
    }

    @Test void solidSubstreamCrcIsCheckedSeparatelyFromFolderCrc() throws Exception {
        LinkedHashMap<String,byte[]> expected=new LinkedHashMap<>();expected.put("first",pattern(9031));expected.put("second",pattern(1037));
        Path archive=write("substream-crc",5,expected);byte[] bytes=Files.readAllBytes(archive);
        int header=32+Math.toIntExact(ByteBuffer.wrap(bytes,12,8).order(ByteOrder.LITTLE_ENDIAN).getLong());
        CRC32 crc=new CRC32();crc.update(expected.get("first"));
        byte[] digest=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int)crc.getValue()).array();
        int found=-1;
        for(int i=header;i<=bytes.length-4;i++) if(Arrays.equals(digest,Arrays.copyOfRange(bytes,i,i+4))) {
            assertEquals(-1,found,"Fixture must identify only the first substream CRC");found=i;
        }
        assertTrue(found>=header);bytes[found]^=1;
        crc.reset();crc.update(bytes,header,bytes.length-header);
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(28,(int)crc.getValue());
        crc.reset();crc.update(bytes,12,20);
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(8,(int)crc.getValue());
        Files.write(archive,bytes);
        try(SevenZArchiveReader reader=new SevenZArchiveReader(archive,null,null)) {
            assertThrows(IOException.class,()->reader.open(reader.entries().getFirst()).readAllBytes());
        }
        try(SevenZFile reader=SevenZFile.builder().setPath(archive).get()) {
            assertEquals("first",reader.getNextEntry().getName());
            assertThrows(IOException.class,()->read(reader,expected.get("first").length));
        }
    }

    @Test void failedOrOpenEntriesCannotBecomeSuccessfulArchives() throws Exception {
        try(SevenZWriter writer=new SevenZWriter(temp.resolve("short.7z"),5)) {
            OutputStream entry=writer.begin("short",false,10);entry.write(new byte[9]);
            assertThrows(IOException.class,entry::close);assertThrows(IOException.class,writer::finish);
        }
        try(SevenZWriter writer=new SevenZWriter(temp.resolve("long.7z"),5)) {
            IOException failure=assertThrows(IOException.class,()->{
                try(OutputStream entry=writer.begin("long",false,1)){entry.write(new byte[2]);}
            });
            assertTrue(failure.getMessage().contains("來源檔案"));assertThrows(IOException.class,writer::finish);
        }
        try(SevenZWriter writer=new SevenZWriter(temp.resolve("active.7z"),5)) {
            OutputStream entry=writer.begin("active",false,1);
            assertThrows(IOException.class,()->writer.begin("overlap",false,1));
            assertThrows(IOException.class,writer::finish);
            entry.write(42);entry.close();writer.finish();writer.finish();
            assertThrows(IOException.class,()->writer.begin("after-finish",false,1));
        }
    }

    @Test void cancellationWithUnfinishedSolidGroupLeavesNoPublishedOrStagingArchive() throws Exception {
        Path first=Files.write(temp.resolve("first"),pattern(4096)),second=Files.write(temp.resolve("second"),pattern(4096));
        AtomicBoolean cancelled=new AtomicBoolean();Path archive=temp.resolve("cancelled.7z");
        assertThrows(InterruptedIOException.class,()->new ArchiveEngine().compress(List.of(first,second),archive,
                ArchiveEngine.Format.SEVEN_Z,5,null,cancelled::get,(done,total,name)->{if(done>=4096)cancelled.set(true);}));
        assertFalse(Files.exists(archive));
        try(var files=Files.list(temp)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".onezip-")));}
    }

    private Path write(String name,int level,LinkedHashMap<String,byte[]> expected) throws Exception {
        Path archive=temp.resolve(name+".7z");
        try(SevenZWriter writer=new SevenZWriter(archive,level)) {
            for(var e:expected.entrySet())try(OutputStream entry=writer.begin(e.getKey(),e.getValue()==null,e.getValue()==null?0:e.getValue().length)) {
                if(e.getValue()!=null)for(int i=0;i<e.getValue().length;i+=777)entry.write(e.getValue(),i,Math.min(777,e.getValue().length-i));
            }
            writer.finish();
        }
        return archive;
    }
    private static void verifyOwn(Path archive,LinkedHashMap<String,byte[]> expected) throws Exception {
        try(SevenZArchiveReader reader=new SevenZArchiveReader(archive,null,null)) {
            assertEquals(new ArrayList<>(expected.keySet()),reader.entries().stream().map(ArchiveReader.Entry::name).toList());
            for(var e:reader.entries()) {
                byte[] contents=expected.get(e.name());assertEquals(contents==null,e.directory());
                assertArrayEquals(contents==null?new byte[0]:contents,reader.open(e).readAllBytes(),e.name());
            }
        }
    }
    private static void verifyIndependent(Path archive,LinkedHashMap<String,byte[]> expected) throws Exception {
        try(SevenZFile reader=SevenZFile.builder().setPath(archive).get()) {
            int count=0;SevenZArchiveEntry e;
            while((e=reader.getNextEntry())!=null) {
                assertTrue(expected.containsKey(e.getName()),e.getName());byte[] contents=expected.get(e.getName());
                assertEquals(contents==null,e.isDirectory());assertEquals(contents==null?0:contents.length,e.getSize());
                assertArrayEquals(contents==null?new byte[0]:contents,read(reader,Math.toIntExact(e.getSize())),e.getName());count++;
            }
            assertEquals(expected.size(),count);
        }
    }
    private static byte[] read(SevenZFile reader,int size) throws IOException {
        byte[] result=new byte[size];int offset=0;
        while(offset<size){int n=reader.read(result,offset,size-offset);if(n<0)throw new EOFException();offset+=n;}
        assertEquals(-1,reader.read());return result;
    }
    private static byte[] pattern(int size) {
        byte[] bytes=new byte[size];for(int i=0;i<size;i++)bytes[i]=(byte)(i%251);return bytes;
    }
    private static long packedStreams(Path archive) throws Exception {
        byte[] bytes=Files.readAllBytes(archive);
        long offset=ByteBuffer.wrap(bytes,12,8).order(ByteOrder.LITTLE_ENDIAN).getLong();
        ReadSupport.Cursor h=new ReadSupport.Cursor(Arrays.copyOfRange(bytes,32+Math.toIntExact(offset),bytes.length));
        h.expect(1);h.expect(4);h.expect(6);assertEquals(0,h.number());return h.number();
    }
}
