package cn.shed4329.onezip.engine;

import cn.shed4329.onezip.engine.lzma.*;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class ParallelSevenZTest {
    @TempDir Path temp;
    private static byte[] encode(byte[] bytes,int workers,boolean flush)throws Exception{
        return encodeLevel(bytes,workers,flush,5);
    }
    private static byte[] encodeLevel(byte[] bytes,int workers,boolean flush,int level)throws Exception{
        ByteArrayOutputStream packed=new ByteArrayOutputStream();
        try(var pool=new Lzma2EncoderPool(workers);var out=new ParallelLzma2OutputStream(packed,level,pool,bytes.length)){
            for(int offset=0;offset<bytes.length;){int n=Math.min(77777,bytes.length-offset);out.write(bytes,offset,n);offset+=n;
                if(flush&&offset==77777*5)out.flush();}
        }
        return packed.toByteArray();
    }
    @Test void positionContextsChangeWithoutDiscardingTheDecoderDictionary()throws Exception{
        int mib=1024*1024;StringBuilder text=new StringBuilder();
        for(int i=0;text.length()<mib;i++)text.append("record ").append(i).append(": readable archive metadata and common English file contents, completed successfully\n");
        byte[] english=text.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] bytes=new byte[3*mib+17];System.arraycopy(english,0,bytes,0,mib);
        byte[] binary=new byte[131072];new Random(917).nextBytes(binary);
        for(int offset=mib;offset<2*mib;offset+=binary.length)System.arraycopy(binary,0,bytes,offset,binary.length);
        System.arraycopy(bytes,0,bytes,2*mib,mib+17);
        for(int level:new int[]{1,5,9}){
            byte[] packed=encodeLevel(bytes,3,false,level);Set<Integer> properties=new HashSet<>();
            for(int p=0;p<packed.length;){int control=packed[p++]&255;if(control==0)break;
                if(control<0x80){int length=((packed[p]&255)<<8)+(packed[p+1]&255)+1;p+=2+length;}
                else{int length=((packed[p+2]&255)<<8)+(packed[p+3]&255)+1;p+=4;if(control>=0xc0)properties.add(packed[p++]&255);p+=length;}
            }
            assertTrue(properties.contains(3)&&properties.contains(0x5d),"Fixture must switch pb0/pb2 inside one dictionary stream");
            independent(bytes,packed);
        }
    }
    private static void independent(byte[] bytes,byte[] packed)throws Exception{
        try(var in=new org.tukaani.xz.LZMA2InputStream(new ByteArrayInputStream(packed),1<<21)){assertArrayEquals(bytes,in.readAllBytes());}
        try(var in=new LzmaInputStream(new ByteArrayInputStream(packed),18)){assertArrayEquals(bytes,in.readAllBytes());}
    }
    @Test void fragmentsKeepHistoryAcrossModelResetsAndPartialFlush()throws Exception{
        byte[] bytes=new byte[5*1024*1024+231];new Random(7713).nextBytes(bytes);
        // The first two MiB must be raw; subsequent fragments need that prefix
        // to avoid treating the repeated random bytes as incompressible again.
        for(int i=1<<21;i<bytes.length;i++)bytes[i]=bytes[i-(1<<21)];
        byte[] packed=encode(bytes,3,true);independent(bytes,packed);
        assertTrue(packed.length<2_300_000,"Historical dictionary bytes must remain usable across fragments");
        assertArrayEquals(packed,encode(bytes,2,true),"Scheduling must not change stream order or decisions");
    }
    @Test void tinyFinalFragmentIsCoalescedAndBothLzma2LimitsRemainValid()throws Exception{
        byte[] bytes=new byte[4*1024*1024+17];new Random(909).nextBytes(bytes);
        for(int i=11003;i<bytes.length;i++)if(i%101!=0)bytes[i]=bytes[i-11003];
        independent(bytes,encode(bytes,4,false));
    }
    @Test void shortPeriodFallbackPreservesProbabilityHistoryExactly()throws Exception{
        byte[] bytes=new byte[5*1024*1024+71];byte[] pattern="short period archive data 0123456789\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for(int i=0;i<bytes.length;i++)bytes[i]=pattern[i%pattern.length];
        ByteArrayOutputStream expected=new ByteArrayOutputStream();
        try(var out=new ArkLzma2OutputStream(expected,5)){out.write(bytes);}
        byte[] actual=encode(bytes,4,false);assertArrayEquals(expected.toByteArray(),actual);independent(bytes,actual);
    }
    @Test void defaultLevelRemainsValidForLargeParallelEntries()throws Exception{
        byte[] bytes=new byte[4*1024*1024+19];new Random(7417).nextBytes(bytes);
        Path input=Files.write(temp.resolve("large-default"),bytes),archive=temp.resolve("default.7z");
        ArchiveEngine engine=new ArchiveEngine();engine.compress(List.of(input),archive,ArchiveEngine.Format.SEVEN_Z,-1,null,null,null,2);
        engine.test(archive,null,null,null);
        try(var reader=SevenZFile.builder().setPath(archive).get()){
            assertNotNull(reader.getNextEntry());ByteArrayOutputStream raw=new ByteArrayOutputStream();byte[] buffer=new byte[65536];int n;
            while((n=reader.read(buffer))!=-1)raw.write(buffer,0,n);assertArrayEquals(bytes,raw.toByteArray());
        }
    }
    @Test void multiFolderCompletionOrderPreservesEmptyEntriesAndSubstreamCrcs()throws Exception{
        Path archive=temp.resolve("folders.7z");Map<String,byte[]> expected=new LinkedHashMap<>();
        expected.put("empty/",null);
        for(int i=0;i<9;i++){
            byte[] bytes=new byte[400000];if(i%3==0)new Random(i).nextBytes(bytes);else Arrays.fill(bytes,(byte)i);
            expected.put("entry-"+i,bytes);
        }
        expected.put("zero",new byte[0]);
        try(var writer=new SevenZWriter(archive,5,3)){
            for(var e:expected.entrySet())try(var out=writer.begin(e.getKey(),e.getValue()==null,e.getValue()==null?0:e.getValue().length)){
                if(e.getValue()!=null)out.write(e.getValue());
            }
            writer.finish();
        }
        try(var reader=SevenZFile.builder().setPath(archive).get()){
            int count=0;for(var entry=reader.getNextEntry();entry!=null;entry=reader.getNextEntry()){
                ByteArrayOutputStream raw=new ByteArrayOutputStream();byte[] buffer=new byte[65536];int n;
                while((n=reader.read(buffer))!=-1)raw.write(buffer,0,n);
                assertArrayEquals(expected.get(entry.getName())==null?new byte[0]:expected.get(entry.getName()),raw.toByteArray());count++;
            }assertEquals(expected.size(),count);
        }
        new ArchiveEngine().test(archive,null,null,null);
    }
    @Test void oneWorkerPreservesTheSequentialBitstream()throws Exception{
        Path input=Files.writeString(temp.resolve("text"),"some common text and repeated archive metadata\n".repeat(3000));
        Path single=temp.resolve("one.7z"),multi=temp.resolve("three.7z");ArchiveEngine engine=new ArchiveEngine();
        engine.compress(List.of(input),single,ArchiveEngine.Format.SEVEN_Z,5,null,null,null,1);
        engine.compress(List.of(input),multi,ArchiveEngine.Format.SEVEN_Z,5,null,null,null,3);
        assertArrayEquals(Files.readAllBytes(single),Files.readAllBytes(multi),"An intact small solid folder must keep its decisions");
    }
    @Test void splitSolidFolderKeepsFileBoundariesAndReasonableQuality()throws Exception{
        StringBuilder text=new StringBuilder();Random random=new Random(43977);
        for(int i=0;i<11000;i++)text.append("entry index=").append(i).append(" value=").append(random.nextLong()).append(" sample=").append(random.nextInt(20000)).append(";\n");
        byte[] raw=text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try(var pool=new Lzma2EncoderPool(3)){assertTrue(pool.folderParts(raw)>1,"Fixture must exercise intra-folder parallel fragments");}
        Path dir=Files.createDirectory(temp.resolve("parts"));Map<String,byte[]> expected=new HashMap<>();
        for(int i=0;i<3;i++){byte[] part=Arrays.copyOfRange(raw,raw.length*i/3,raw.length*(i+1)/3);String name="p"+i;Files.write(dir.resolve(name),part);expected.put("parts/"+name,part);}
        ArchiveEngine engine=new ArchiveEngine();Path single=temp.resolve("sequential.7z"),parallel=temp.resolve("parallel.7z");
        engine.compress(List.of(dir),single,ArchiveEngine.Format.SEVEN_Z,5,null,null,null,1);
        engine.compress(List.of(dir),parallel,ArchiveEngine.Format.SEVEN_Z,5,null,null,null,3);
        assertTrue(Files.size(parallel)<=Files.size(single)*1.05,"Parallel model resets must keep most compression quality");
        try(var reader=SevenZFile.builder().setPath(parallel).get()){
            for(var entry=reader.getNextEntry();entry!=null;entry=reader.getNextEntry())if(!entry.isDirectory()){
                ByteArrayOutputStream decoded=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
                while((n=reader.read(buffer))!=-1)decoded.write(buffer,0,n);
                assertArrayEquals(expected.get(entry.getName()),decoded.toByteArray());
            }
        }
        engine.test(parallel,null,null,null);
    }
    @Test void cancellationStopsActiveWorkersAndRemovesStagingFiles()throws Exception{
        byte[] bytes=new byte[8*1024*1024];new Random(643).nextBytes(bytes);
        Path input=Files.write(temp.resolve("large"),bytes),output=temp.resolve("cancelled.7z");AtomicBoolean cancel=new AtomicBoolean();
        assertThrows(InterruptedIOException.class,()->new ArchiveEngine().compress(List.of(input),output,ArchiveEngine.Format.SEVEN_Z,5,null,cancel::get,
            (done,total,name)->{if(done>2*1024*1024)cancel.set(true);},4));
        assertFalse(Files.exists(output));
        try(var files=Files.list(temp)){assertFalse(files.anyMatch(p->p.getFileName().toString().endsWith(".part")));}
        for(Thread thread:Thread.getAllStackTraces().keySet())if(thread.getName().startsWith("onezip-7z-")){thread.join(1000);assertFalse(thread.isAlive());}
    }
    @Test void workerFailuresAndShortInputCannotProduceSuccessfulStreams()throws Exception{
        IOException marker=new IOException("worker marker");
        var exceptional=new java.util.concurrent.CompletableFuture<byte[]>();exceptional.completeExceptionally(marker);
        assertSame(marker,assertThrows(IOException.class,()->Lzma2EncoderPool.await(exceptional)));
        var cancelled=new java.util.concurrent.CompletableFuture<byte[]>();cancelled.cancel(true);
        assertThrows(IOException.class,()->Lzma2EncoderPool.await(cancelled));
        try(var pool=new Lzma2EncoderPool(2)){
            var failed=pool.whole(new byte[]{1,2,3},5);pool.close();
            // Completion may win the race with close; either a complete result
            // or an IOException is valid, but never a hung future.
            try{assertTrue(Lzma2EncoderPool.await(failed).length>0);}catch(IOException expected){assertNotNull(expected.getMessage());}
        }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(var pool=new Lzma2EncoderPool(2)){
            var out=new ParallelLzma2OutputStream(bytes,5,pool,100);out.write(new byte[99]);
            assertThrows(IOException.class,out::finish);out.close();
        }
    }
}
