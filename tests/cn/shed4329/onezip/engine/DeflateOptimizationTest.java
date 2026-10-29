package cn.shed4329.onezip.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class DeflateOptimizationTest {
    @TempDir Path temp;
    static byte[] inflate(byte[] bytes)throws Exception{
        Inflater inflater=new Inflater(true);try(InputStream in=new InflaterInputStream(new ByteArrayInputStream(bytes),inflater)){return in.readAllBytes();}finally{inflater.end();}
    }
    static byte[] encode(byte[] bytes,int level)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();DeflateEncoder encoder=new DeflateEncoder(out,level);encoder.write(bytes);encoder.finish();return out.toByteArray();
    }
    @Test void canonicalTreesAreCompleteAndLengthBoundedForSkewedAndSparseHistograms(){
        Random random=new Random(5221);
        for(int size:new int[]{19,30,286})for(int iteration=0;iteration<80;iteration++){
            int[] frequency=new int[size];for(int i=0;i<size;i++)frequency[i]=iteration<20?(i<22?(i<2?1:frequency[i-1]+frequency[i-2]):0):random.nextInt(5)==0?0:random.nextInt(65536);
            if(iteration==0)Arrays.fill(frequency,0);if(iteration==1){Arrays.fill(frequency,0);frequency[size-1]=1;}
            int limit=size==19?7:15;DeflateHuffman tree=new DeflateHuffman(size);tree.build(frequency,limit);int kraft=0;
            for(int i=0;i<size;i++){assertTrue(tree.lengths[i]<=limit);if(frequency[i]>0)assertTrue(tree.lengths[i]>0);if(tree.lengths[i]>0)kraft+=1<<(limit-tree.lengths[i]);}
            assertEquals(1<<limit,kraft);
        }
    }
    @Test void dynamicTreesAndHistoryAcrossStoredBlocksImproveSizeAndDecodeIndependently()throws Exception{
        byte[] noise=new byte[65535];new Random(75).nextBytes(noise);ByteArrayOutputStream input=new ByteArrayOutputStream();input.write(noise);
        for(int i=0;i<8;i++)input.write(noise,noise.length-32768,32768);
        byte[] bytes=input.toByteArray();
        for(int level:new int[]{1,3,5,7,9}){
            byte[] packed=encode(bytes,level);assertTrue(packed.length<noise.length+12000,"Cross-block history must survive a stored block");assertArrayEquals(bytes,inflate(packed));
            assertArrayEquals(bytes,new DeflateInputStream(new ByteArrayInputStream(packed)).readAllBytes());
        }
        byte[] text="dynamic Huffman symbols, short matches, filenames and repeated English phrases\n".repeat(2000).getBytes();byte[] packed=encode(text,5);
        assertEquals(2,(packed[0]>>>1)&3);assertArrayEquals(text,inflate(packed));
    }
    @Test void parallelFragmentsKeepHistoryAcrossShortFlushesAndAreDeterministic()throws Exception{
        byte[] bytes=new byte[ParallelDeflateOutputStream.SEGMENT*4+17];new Random(971).nextBytes(bytes);
        for(int i=32768;i<bytes.length;i++)if(i%65521<60000)bytes[i]=bytes[i-32768];byte[] previous=null;
        for(int workers:new int[]{2,4}){
            ByteArrayOutputStream packed=new ByteArrayOutputStream();
            try(ZipCompressionPool pool=new ZipCompressionPool(workers)){
                ParallelDeflateOutputStream out=new ParallelDeflateOutputStream(packed,5,pool,bytes.length);
                out.write(bytes,0,19000);out.flush();out.write(bytes,19000,5000);out.flush();
                for(int offset=24000;offset<bytes.length;){int n=Math.min(7777,bytes.length-offset);out.write(bytes,offset,n);offset+=n;}out.finish();out.finish();
            }
            assertArrayEquals(bytes,inflate(packed.toByteArray()));assertArrayEquals(bytes,new DeflateInputStream(new ByteArrayInputStream(packed.toByteArray())).readAllBytes());
            if(previous!=null)assertArrayEquals(previous,packed.toByteArray());previous=packed.toByteArray();
        }
    }
    @Test void bufferedDecoderKeepsStoredAlignmentAndRejectsPrefetchedTrailingBytes()throws Exception{
        byte[] input=new byte[180000];Random random=new Random(421);random.nextBytes(input);
        for(int i=50000;i<140000;i++)if(i%19!=0)input[i]=(byte)('a'+random.nextInt(4));
        for(int level:new int[]{0,1,6,9}){
            ByteArrayOutputStream packed=new ByteArrayOutputStream();Deflater reference=new Deflater(level,true);
            try(DeflaterOutputStream encoder=new DeflaterOutputStream(packed,reference)){encoder.write(input);}finally{reference.end();}
            byte[] bytes=packed.toByteArray();
            assertArrayEquals(input,new DeflateInputStream(new ByteArrayInputStream(bytes)).readAllBytes());
            // Refill boundaries must preserve EOF, and may not hide bytes from the ZIP wrapper.
            assertThrows(IOException.class,()->new DeflateInputStream(new ByteArrayInputStream(Arrays.copyOf(bytes,bytes.length-1))).readAllBytes());
            for(int extra:new int[]{1,16384,18000})assertThrows(IOException.class,()->new DeflateInputStream(new ByteArrayInputStream(Arrays.copyOf(bytes,bytes.length+extra))).readAllBytes());
        }
    }
    @Test void largeParallelZipAndAesUseStandardDeflateWithoutNestedWorkerDeadlocks()throws Exception{
        byte[] bytes=new byte[5*1024*1024+17];new Random(771).nextBytes(bytes);for(int i=4096;i<bytes.length;i++)if(i%137<120)bytes[i]=bytes[i-4093];
        Path input=Files.write(temp.resolve("large.bin"),bytes);ArchiveEngine engine=new ArchiveEngine();
        for(boolean aes:new boolean[]{false,true}){
            Path archive=temp.resolve(aes?"aes.zip":"plain.zip");char[] password=aes?"secret".toCharArray():null;
            engine.compress(List.of(input),archive,ArchiveEngine.Format.ZIP,5,password,null,null,4);engine.test(archive,password,null,null);
            if(aes)try(net.lingala.zip4j.ZipFile zip=new net.lingala.zip4j.ZipFile(archive.toFile(),password);InputStream in=zip.getInputStream(zip.getFileHeader("large.bin"))){assertArrayEquals(bytes,in.readAllBytes());}
            else try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive.toFile());InputStream in=zip.getInputStream(zip.getEntry("large.bin"))){assertArrayEquals(bytes,in.readAllBytes());}
        }
    }
    @Test void workspaceResetsBetweenFilesAndCancellationStopsThePersistentPool()throws Exception{
        DeflateEncoder.Workspace workspace=new DeflateEncoder.Workspace();
        for(int size:new int[]{100000,0,1,65535,200000}){byte[] bytes=new byte[size];new Random(size).nextBytes(bytes);ByteArrayOutputStream out=new ByteArrayOutputStream();DeflateEncoder encoder=new DeflateEncoder(out,5,workspace);encoder.write(bytes);encoder.finish();assertArrayEquals(bytes,inflate(out.toByteArray()));}
        byte[] bytes=new byte[8*1024*1024];new Random(533).nextBytes(bytes);Path input=Files.write(temp.resolve("cancel.bin"),bytes),archive=temp.resolve("cancel.zip");AtomicBoolean cancel=new AtomicBoolean();
        assertThrows(InterruptedIOException.class,()->new ArchiveEngine().compress(List.of(input),archive,ArchiveEngine.Format.ZIP,5,null,cancel::get,(d,t,n)->{if(d>2*1024*1024)cancel.set(true);},4));
        assertFalse(Files.exists(archive));try(var files=Files.list(temp)){assertTrue(files.noneMatch(p->p.getFileName().toString().startsWith(".onezip-")));}
        assertTrue(Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&t.getName().startsWith("onezip-zip-")));
    }
}
