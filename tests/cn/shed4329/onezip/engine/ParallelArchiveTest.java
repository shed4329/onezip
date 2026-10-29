package cn.shed4329.onezip.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class ParallelArchiveTest {
    @TempDir Path temp;
    final ArchiveEngine engine=new ArchiveEngine();
    Path corpus(int count,int size)throws Exception{
        Path root=Files.createDirectory(temp.resolve("input"));Files.createDirectories(root.resolve("nested/empty"));Files.write(root.resolve("zero"),new byte[0]);
        Random random=new Random(71);byte[] bytes=new byte[size];
        for(int i=0;i<count;i++){random.nextBytes(bytes);for(int p=32;p<bytes.length;p++)if((p&127)<100)bytes[p]=bytes[p&31];Files.write(root.resolve("nested/file"+i),bytes);}
        return root;
    }
    void contents(Path source,Path output)throws Exception{
        try(var paths=Files.walk(source)){for(Path p:paths.toList()){
            Path target=output.resolve(source.getFileName()).resolve(source.relativize(p));
            if(Files.isDirectory(p))assertTrue(Files.isDirectory(target));else assertArrayEquals(Files.readAllBytes(p),Files.readAllBytes(target));
        }}
    }
    static void noWorkers(){assertTrue(Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&(t.getName().startsWith("onezip-extract-")||t.getName().startsWith("onezip-zip-"))));}
    static void stableModifiedTimes(Path root)throws Exception{
        var fixed=java.nio.file.attribute.FileTime.from(java.time.Instant.parse("2020-02-03T04:05:07Z"));
        try(var walk=Files.walk(root)){
            for(Path path:walk.sorted(Comparator.reverseOrder()).toList())Files.setLastModifiedTime(path,fixed);
        }
    }
    /** Reading a source can change its access time; compressed bytes and mtime must still agree. */
    static void sameZipPayload(Path first,Path second)throws Exception{
        try(var a=org.apache.commons.compress.archivers.zip.ZipFile.builder().setPath(first).get();
            var b=org.apache.commons.compress.archivers.zip.ZipFile.builder().setPath(second).get()){
            var left=Collections.list(a.getEntries());var right=Collections.list(b.getEntries());
            assertEquals(left.size(),right.size());
            for(int i=0;i<left.size();i++){
                var x=left.get(i);var y=right.get(i);
                assertEquals(x.getName(),y.getName());assertEquals(x.getMethod(),y.getMethod());
                assertEquals(x.getSize(),y.getSize());assertEquals(x.getCompressedSize(),y.getCompressedSize());
                assertEquals(x.getCrc(),y.getCrc());assertEquals(x.getLastModifiedTime(),y.getLastModifiedTime(),x.getName());
                try(var rawA=a.getRawInputStream(x);var rawB=b.getRawInputStream(y)){
                    assertArrayEquals(rawA.readAllBytes(),rawB.readAllBytes(),x.getName());
                }
            }
        }
    }
    @Test void zipParallelEncodingPreservesPayloadBytesAndIndependentDecodersAgree()throws Exception{
        Path input=corpus(10,180000),single=temp.resolve("single.zip"),parallel=temp.resolve("parallel.zip");
        stableModifiedTimes(input);
        engine.compress(List.of(input),single,ArchiveEngine.Format.ZIP,5,null,null,null,1);
        Set<String> threads=ConcurrentHashMap.newKeySet();AtomicLong progress=new AtomicLong();
        engine.compress(List.of(input),parallel,ArchiveEngine.Format.ZIP,5,null,null,(done,total,name)->{assertTrue(done>=progress.getAndSet(done));if(done>0)threads.add(Thread.currentThread().getName());},4);
        sameZipPayload(single,parallel);assertTrue(threads.stream().filter(s->s.startsWith("onezip-zip-")).count()>1);
        try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(parallel.toFile())){
            var entries=zip.entries();while(entries.hasMoreElements()){var e=entries.nextElement();if(!e.isDirectory())try(InputStream in=zip.getInputStream(e)){assertArrayEquals(Files.readAllBytes(input.getParent().resolve(e.getName())),in.readAllBytes());}}
        }
        Path out=temp.resolve("decoded");threads.clear();progress.set(0);
        engine.extract(parallel,out,null,null,(done,total,name)->{assertTrue(done>=progress.getAndSet(done));threads.add(Thread.currentThread().getName());},4);
        contents(input,out);assertTrue(threads.stream().filter(s->s.startsWith("onezip-extract-")).count()>1);noWorkers();
    }
    @Test void independentSevenZFoldersRunConcurrentlyAndSolidSubstreamsRemainOrdered()throws Exception{
        Path input=corpus(12,300000),archive=temp.resolve("solid.7z");
        engine.compress(List.of(input),archive,ArchiveEngine.Format.SEVEN_Z,1,null,null,null,3);
        try(var reader=new SevenZArchiveReader(archive,null,null)){
            assertTrue(reader.groups().stream().filter(g->g.stream().anyMatch(e->e.size()>0)).count()>=2);
            assertTrue(reader.groups().stream().anyMatch(g->g.stream().filter(e->e.size()>0).count()>1));
        }
        Set<String> threads=ConcurrentHashMap.newKeySet();Path out=temp.resolve("decoded");
        engine.extract(archive,out,null,null,(d,t,n)->threads.add(Thread.currentThread().getName()),4);contents(input,out);
        assertTrue(threads.stream().filter(s->s.startsWith("onezip-extract-")).count()>1);noWorkers();
        Path sequential=temp.resolve("seq");engine.extract(archive,sequential,null,null,null,1);contents(input,sequential);
    }
    @Test void oneSolidFolderFallsBackToOrderedDecoding()throws Exception{
        Path input=corpus(5,30000),archive=temp.resolve("small.7z");engine.compress(List.of(input),archive,ArchiveEngine.Format.SEVEN_Z,1,null,null,null,1);
        Path out=temp.resolve("out");engine.extract(archive,out,null,null,null,64);contents(input,out);noWorkers();
    }
    @Test void aesEntriesHaveIndependentEncryptionAndPasswordFailuresRollBack()throws Exception{
        Path input=corpus(8,40000),archive=temp.resolve("aes.zip");char[] password="secret".toCharArray();
        engine.compress(List.of(input),archive,ArchiveEngine.Format.ZIP,3,password,null,null,4);
        try(var zip=new net.lingala.zip4j.ZipFile(archive.toFile(),password)){
            for(var entry:zip.getFileHeaders())if(!entry.isDirectory())try(InputStream in=zip.getInputStream(entry)){assertArrayEquals(Files.readAllBytes(temp.resolve(entry.getFileName())),in.readAllBytes());}
        }
        Path out=temp.resolve("aes-out");engine.extract(archive,out,password,null,null,4);contents(input,out);
        Path wrong=temp.resolve("wrong");assertThrows(IOException.class,()->engine.extract(archive,wrong,"wrong".toCharArray(),null,null,4));assertFalse(Files.exists(wrong));noWorkers();
    }
    @Test void cancelledCompressionAndExtractionJoinWorkersBeforeCleanup()throws Exception{
        Path input=corpus(8,200000),archive=temp.resolve("cancel.zip");AtomicBoolean cancelled=new AtomicBoolean();
        assertThrows(InterruptedIOException.class,()->engine.compress(List.of(input),archive,ArchiveEngine.Format.ZIP,5,null,cancelled::get,(d,t,n)->{if(d>0)cancelled.set(true);},4));
        assertFalse(Files.exists(archive));noWorkers();cancelled.set(false);
        engine.compress(List.of(input),archive,ArchiveEngine.Format.ZIP,1,null,null,null,4);
        Path out=Files.createDirectory(temp.resolve("existing")),keep=Files.writeString(out.resolve("keep"),"preserved");
        assertThrows(InterruptedIOException.class,()->engine.extract(archive,out,null,cancelled::get,(d,t,n)->{if(d>0)cancelled.set(true);},4));
        assertEquals("preserved",Files.readString(keep));try(var paths=Files.list(out)){assertEquals(1,paths.count());}noWorkers();
        try(var paths=Files.list(temp)){assertFalse(paths.anyMatch(p->p.getFileName().toString().startsWith(".onezip-")));}
    }
    @Test void malformedPayloadAndUnsafePathsFailWithoutPartialOutput()throws Exception{
        Path archive=temp.resolve("broken.zip");
        try(ZipWriter zip=new ZipWriter(archive,0,null)){
            for(int i=0;i<6;i++)try(OutputStream entry=zip.begin("file"+i,false,60000)){entry.write(new byte[60000]);}zip.finish();
        }
        try(RandomAccessFile file=new RandomAccessFile(archive.toFile(),"rw")){file.seek(30+5+123);file.write(1);} // Corrupt STORE payload, retaining the original central CRC.
        Path out=temp.resolve("broken-out");assertThrows(IOException.class,()->engine.extract(archive,out,null,null,null,4));assertFalse(Files.exists(out));noWorkers();
        Path unsafe=temp.resolve("unsafe.zip");try(ZipWriter zip=new ZipWriter(unsafe,0,null)){try(OutputStream entry=zip.begin("../escape",false,1)){entry.write(1);}zip.finish();}
        assertThrows(IOException.class,()->engine.extract(unsafe,temp.resolve("unsafe-out"),null,null,null,4));assertFalse(Files.exists(temp.resolve("escape")));
        assertThrows(IllegalArgumentException.class,()->engine.extract(archive,out,null,null,null,65));
    }
}
