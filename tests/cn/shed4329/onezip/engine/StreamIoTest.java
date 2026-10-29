package cn.shed4329.onezip.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class StreamIoTest {
    @TempDir Path temp;
    @Test void chunkViewsHaveIndependentCursorsAndReplayTheOriginalArrays()throws Exception{
        byte[] expected=new byte[180005];new Random(73).nextBytes(expected);
        ChunkedOutputStream chunks=new ChunkedOutputStream(expected.length,777);
        for(int i=0;i<9;i++)chunks.write(expected[i]);chunks.write(expected,9,expected.length-9);
        assertEquals(expected.length,chunks.size());assertThrows(IOException.class,()->chunks.write(1));assertThrows(IOException.class,chunks::openInputStream);chunks.close();
        assertThrows(IOException.class,()->chunks.write(new byte[0]));
        List<byte[]> arrays=new ArrayList<>();OutputStream observe=new OutputStream(){public void write(int b){fail("Must replay in bulk");}public void write(byte[] b,int o,int n){arrays.add(b);}};
        chunks.writeTo(observe);int count=arrays.size();chunks.writeTo(observe);for(int i=0;i<count;i++)assertSame(arrays.get(i),arrays.get(i+count));
        try(InputStream first=chunks.openInputStream();InputStream second=chunks.openInputStream()){
            assertEquals(0,first.skip(-1));assertEquals(778,first.skip(778));assertEquals(expected[778]&255,first.read());
            assertArrayEquals(expected,second.readAllBytes());assertEquals(0,second.read(new byte[0]));assertEquals(-1,second.read());
            ByteArrayOutputStream tail=new ByteArrayOutputStream();assertEquals(expected.length-779,first.transferTo(tail));assertArrayEquals(Arrays.copyOfRange(expected,779,expected.length),tail.toByteArray());
        }
        ChunkedOutputStream empty=new ChunkedOutputStream(0);empty.write(new byte[0]);empty.close();assertEquals(-1,empty.openInputStream().read());
    }
    @Test void rawStreamSyncFlushRetainsHistoryAfterOneByteAndShortWrites()throws Exception{
        byte[] input=new byte[220019];new Random(893).nextBytes(input);for(int i=4000;i<input.length;i++)if(i%17!=0)input[i]=input[i-3999];
        for(int level:new int[]{0,1,3,5,9}){
            ByteArrayOutputStream packed=new ByteArrayOutputStream();Inflater decoder=new Inflater(true);ByteArrayOutputStream restored=new ByteArrayOutputStream();int supplied=0,pos=0;
            try(DeflateOutputStream out=new DeflateOutputStream(packed,level)){
                for(int n:new int[]{1,1,1,19000,5000,65535,input.length-89538}){
                    if(n==1)out.write(input[pos]);else out.write(input,pos,n);pos+=n;out.flush();
                    byte[] bytes=packed.toByteArray();decoder.setInput(bytes,supplied,bytes.length-supplied);supplied=bytes.length;byte[] buffer=new byte[7777];int k;while((k=decoder.inflate(buffer))>0)restored.write(buffer,0,k);
                    assertArrayEquals(Arrays.copyOf(input,pos),restored.toByteArray());
                }
                out.finish();out.finish();assertThrows(IOException.class,()->out.write(1));byte[] bytes=packed.toByteArray();decoder.setInput(bytes,supplied,bytes.length-supplied);byte[] buffer=new byte[7777];while(decoder.inflate(buffer)>0){}assertTrue(decoder.finished());
                assertArrayEquals(input,new DeflateInputStream(new ByteArrayInputStream(bytes)).readAllBytes());
            }finally{decoder.end();}
        }
    }
    @Test void publicParallelStreamFinishesWithoutClosingDestinationAndHonorsExactSize()throws Exception{
        byte[] input=new byte[5*1024*1024+3];new Random(71).nextBytes(input);for(int i=8192;i<input.length;i++)if(i%97<80)input[i]=input[i-8191];
        AtomicBoolean closed=new AtomicBoolean();ByteArrayOutputStream packed=new ByteArrayOutputStream(){public void close(){closed.set(true);}};
        DeflateOutputStream out=new DeflateOutputStream(packed,5,4,input.length);out.write(input,0,5000);out.flush();out.write(input,5000,input.length-5000);out.finish();assertFalse(closed.get());
        Inflater inflater=new Inflater(true);try(InputStream decoded=new InflaterInputStream(new ByteArrayInputStream(packed.toByteArray()),inflater)){assertArrayEquals(input,decoded.readAllBytes());}finally{inflater.end();}
        out.close();out.close();assertTrue(closed.get());assertThrows(IOException.class,out::flush);
        DeflateOutputStream shortStream=new DeflateOutputStream(new ByteArrayOutputStream(),5,4,input.length);shortStream.write(7);assertThrows(IOException.class,shortStream::finish);shortStream.close();
        ParallelArchiveTest.noWorkers();
        assertThrows(IllegalArgumentException.class,()->new DeflateOutputStream(packed,5,65,1));assertThrows(IllegalArgumentException.class,()->new DeflateOutputStream(packed,10));
    }
    @Test void channelStreamsHandleZeroAndPartialTransfersAndCloseOwnership()throws Exception{
        byte[] expected=new byte[200003];new Random(293).nextBytes(expected);
        for(boolean direct:new boolean[]{false,true}){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();AtomicBoolean closed=new AtomicBoolean();
            WritableByteChannel channel=new WritableByteChannel(){int calls;boolean open=true;public boolean isOpen(){return open;}public void close(){open=false;closed.set(true);}public int write(ByteBuffer b){if(calls++%11==0)return 0;int n=Math.min(17,b.remaining());for(int i=0;i<n;i++)bytes.write(b.get());return n;}};
            NioOutputStream out=new NioOutputStream(channel,127,direct);for(int i=0;i<11;i++)out.write(expected[i]);out.write(expected,11,expected.length-11);out.flush();assertFalse(closed.get());out.close();assertTrue(closed.get());assertArrayEquals(expected,bytes.toByteArray());assertThrows(IOException.class,()->out.write(1));
            ReadableByteChannel source=new ReadableByteChannel(){int pos,calls;boolean open=true;public boolean isOpen(){return open;}public void close(){open=false;}public int read(ByteBuffer b){if(pos==expected.length)return -1;if(calls++%7==0)return 0;int n=Math.min(Math.min(13,b.remaining()),expected.length-pos);b.put(expected,pos,n);pos+=n;return n;}};
            NioInputStream in=new NioInputStream(source,89,direct);ByteArrayOutputStream restored=new ByteArrayOutputStream();restored.write(in.read());restored.write(in.read());in.transferTo(restored);assertArrayEquals(expected,restored.toByteArray());assertEquals(0,in.read(new byte[0]));assertEquals(-1,in.read());in.close();assertFalse(source.isOpen());assertThrows(IOException.class,in::read);
        }
    }
    @Test void failuresDoNotReplayBufferedGarbageOrSelfSuppressTheOriginalException()throws Exception{
        IOException fault=new IOException("injected");AtomicBoolean closed=new AtomicBoolean();
        ReadableByteChannel source=new ReadableByteChannel(){public boolean isOpen(){return !closed.get();}public void close(){closed.set(true);}public int read(ByteBuffer b)throws IOException{b.put((byte)99);throw fault;}};
        try(NioInputStream in=new NioInputStream(source,128,false)){assertSame(fault,assertThrows(IOException.class,in::read));assertSame(fault,assertThrows(IOException.class,in::read));}assertTrue(closed.get());
        WritableByteChannel channel=new WritableByteChannel(){public boolean isOpen(){return true;}public void close(){closed.set(true);}public int write(ByteBuffer b)throws IOException{throw fault;}};
        IOException actual=assertThrows(IOException.class,()->{try(NioOutputStream out=new NioOutputStream(channel,128,false)){out.write(new byte[1000]);}});assertSame(fault,actual);
        OutputStream sink=new OutputStream(){public void write(int b)throws IOException{throw fault;}public void write(byte[] b,int o,int n)throws IOException{throw fault;}};
        assertSame(fault,assertThrows(IOException.class,()->{try(DeflateOutputStream out=new DeflateOutputStream(sink,5)){out.write(new byte[1000]);out.flush();}}));
    }
    @Test void mediumFilesStayStandardWithoutSpoolingAndAllWorkersStopAfterCancellation()throws Exception{
        Path source=Files.createDirectory(temp.resolve("source"));Files.createDirectories(source.resolve("nested/empty"));Random random=new Random(322);
        for(int i=0;i<10;i++){byte[] bytes=new byte[1100000+i*100003];random.nextBytes(bytes);for(int p=4096;p<bytes.length;p++)if(p%13!=0)bytes[p]=bytes[p-4093];Files.write(source.resolve("f"+i),bytes);}
        ArchiveEngine engine=new ArchiveEngine();Path single=temp.resolve("single.zip"),multi=temp.resolve("multi.zip");
        ParallelArchiveTest.stableModifiedTimes(source);
        engine.compress(List.of(source),single,ArchiveEngine.Format.ZIP,5,null,null,null,1);
        engine.compress(List.of(source),multi,ArchiveEngine.Format.ZIP,5,null,null,(d,t,n)->{try(var paths=Files.list(temp)){assertFalse(paths.anyMatch(p->p.getFileName().toString().startsWith(".onezip-zip-")));}catch(IOException ex){throw new UncheckedIOException(ex);}},8);
        ParallelArchiveTest.sameZipPayload(single,multi);try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(multi.toFile())){var entries=zip.entries();while(entries.hasMoreElements()){var e=entries.nextElement();if(!e.isDirectory())try(InputStream in=zip.getInputStream(e)){assertArrayEquals(Files.readAllBytes(temp.resolve(e.getName())),in.readAllBytes());}}}
        AtomicBoolean cancel=new AtomicBoolean();Path cancelled=temp.resolve("cancelled.zip");assertThrows(IOException.class,()->engine.compress(List.of(source),cancelled,ArchiveEngine.Format.ZIP,5,null,cancel::get,(d,t,n)->{if(d>500000)cancel.set(true);},8));assertFalse(Files.exists(cancelled));ParallelArchiveTest.noWorkers();
    }
}
