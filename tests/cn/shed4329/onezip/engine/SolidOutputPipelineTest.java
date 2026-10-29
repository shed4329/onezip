package cn.shed4329.onezip.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class SolidOutputPipelineTest {
    @TempDir Path temp;
    final ArchiveEngine engine=new ArchiveEngine();
    Map<String,byte[]> expected=new LinkedHashMap<>();
    Path archive()throws Exception{
        Path archive=temp.resolve("solid.7z");Random random=new Random(531);
        try(SevenZWriter writer=new SevenZWriter(archive,0,1)){
            try(OutputStream out=writer.begin("empty/",true,0)){}
            for(int i=0;i<24;i++){
                int size=i==6?128000:i==17?65536:i==0?0:8192;
                byte[] data=new byte[size];random.nextBytes(data);String name="nested/file"+i;expected.put(name,data);
                try(OutputStream out=writer.begin(name,false,size)){out.write(data);}
            }writer.finish();
        }
        return archive;
    }
    void matches(Path root)throws Exception{
        assertTrue(Files.isDirectory(root.resolve("empty")));
        for(var e:expected.entrySet())assertArrayEquals(e.getValue(),Files.readAllBytes(root.resolve(e.getKey())));
    }
    static void joined(){assertTrue(Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&t.getName().startsWith("onezip-extract-write-")));}
    @Test void oneSolidDecoderOverlapsSmallWritesAndFlushesBeforeLargeFiles()throws Exception{
        Path archive=archive(),output=temp.resolve("parallel");Set<String> threads=ConcurrentHashMap.newKeySet();AtomicLong done=new AtomicLong();
        engine.extract(archive,output,null,null,(d,t,n)->{assertTrue(d>=done.getAndSet(d));threads.add(Thread.currentThread().getName());},4);
        matches(output);assertTrue(threads.stream().filter(n->n.startsWith("onezip-extract-write-")).count()>1);joined();
        threads.clear();Path single=temp.resolve("single");engine.extract(archive,single,null,null,(d,t,n)->threads.add(Thread.currentThread().getName()),1);matches(single);
        assertTrue(threads.stream().noneMatch(n->n.startsWith("onezip-extract-write-")));
    }
    @Test void writerFailureAndCancellationJoinBeforeRollbackAndPreserveExistingFiles()throws Exception{
        Path archive=archive(),output=Files.createDirectory(temp.resolve("cancel"));Files.writeString(output.resolve("keep"),"preserved");AtomicBoolean cancel=new AtomicBoolean();
        assertThrows(InterruptedIOException.class,()->engine.extract(archive,output,null,cancel::get,(d,t,n)->{if(d>0)cancel.set(true);},4));
        assertEquals("preserved",Files.readString(output.resolve("keep")));try(var files=Files.list(output)){assertEquals(1,files.count());}joined();
        RuntimeException marker=new IllegalStateException("writer callback failure");Path failed=temp.resolve("failed");
        assertSame(marker,assertThrows(IllegalStateException.class,()->engine.extract(archive,failed,null,null,(d,t,n)->{if(Thread.currentThread().getName().startsWith("onezip-extract-write-"))throw marker;},4)));
        assertFalse(Files.exists(failed));joined();
    }
    @Test void damagedLaterSubstreamStopsPendingWritersAndCleansDirectories()throws Exception{
        Path archive=archive();try(RandomAccessFile file=new RandomAccessFile(archive.toFile(),"rw")){file.seek(200032);int b=file.read();file.seek(200032);file.write(b^1);}
        Path output=temp.resolve("damaged");assertThrows(IOException.class,()->engine.extract(archive,output,null,null,null,4));assertFalse(Files.exists(output));joined();
    }
}
