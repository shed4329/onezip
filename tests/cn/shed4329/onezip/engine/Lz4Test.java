package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class Lz4Test {
    @TempDir Path temp;
    private static byte[] data(int n){byte[] b=new byte[n];new Random(239).nextBytes(b);for(int i=1024;i<n;i++)if((i/65536)%3!=0)b[i]=b[i%997];return b;}
    private static byte[] own(byte[] data,int level,long expected)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(var stream=new Lz4OutputStream(out,level,expected)){for(int i=0;i<data.length;){int n=Math.min(7777,data.length-i);stream.write(data,i,n);i+=n;}}return out.toByteArray();}
    private static byte[] decode(byte[] encoded)throws Exception{try(var in=new Lz4InputStream(new ByteArrayInputStream(encoded))){return in.readAllBytes();}}
    @Test void independentReferenceDecodesOurBlocksChecksumsAndAllBoundarySizes()throws Exception{
        for(int n:new int[]{0,1,4,5,12,13,255,256,65535,65536,262143,262144,262145,600000})for(int level:new int[]{0,1,9}){
            byte[] data=data(n),encoded=own(data,level,n);
            try(var in=new FramedLZ4CompressorInputStream(new ByteArrayInputStream(encoded))){assertArrayEquals(data,in.readAllBytes());}
            assertArrayEquals(data,decode(encoded));
        }
        byte[] repetitive=new byte[500000];Arrays.fill(repetitive,(byte)65);byte[] encoded=own(repetitive,5,-1);assertTrue(encoded.length<5000);assertArrayEquals(repetitive,decode(encoded));
        try(var in=new FramedLZ4CompressorInputStream(new ByteArrayInputStream(encoded))){assertArrayEquals(repetitive,in.readAllBytes());}
    }
    @Test void readsReferenceIndependentLinkedBlocksAllBlockSizesAndChecksumOptions()throws Exception{
        for(var size:FramedLZ4CompressorOutputStream.BlockSize.values())for(int flags=0;flags<8;flags++){
            // Commons 1.28's frame writer retains the first, rather than last, 64KiB
            // for linked blocks larger than 64KiB. Use valid smaller reference input;
            // the explicit multi-block fixture below covers large linked histories.
            byte[] plain=data((flags&4)!=0?100000:350000);
            ByteArrayOutputStream b=new ByteArrayOutputStream();var params=new FramedLZ4CompressorOutputStream.Parameters(size,(flags&1)!=0,(flags&2)!=0,(flags&4)!=0);
            try(var out=new FramedLZ4CompressorOutputStream(b,params)){out.write(plain);}assertArrayEquals(plain,decode(b.toByteArray()),size+" / "+flags);
        }
        ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(new byte[]{0x50,0x2a,0x4d,0x18,3,0,0,0,1,2,3});b.write(own(new byte[]{1,2},1,2));b.write(own(new byte[0],0,0));b.write(own(new byte[]{3,4},1,-1));assertArrayEquals(new byte[]{1,2,3,4},decode(b.toByteArray()));
    }
    @Test void linkedFramesUseTheLast64KiBOfLargePreviousBlocks()throws Exception{
        for(int code=4;code<=7;code++){
            byte[] first=data(1<<(8+2*code));System.arraycopy(new byte[]{98,76,54,32},0,first,first.length-4,4);
            byte[] second={98,76,54,32,1,2,3,4,5,6,7,8},packed={0,4,0,(byte)0x80,1,2,3,4,5,6,7,8};
            ByteArrayOutputStream frame=new ByteArrayOutputStream();word(frame,0x184d2204L);byte[] descriptor={0x54,(byte)(code<<4)};frame.write(descriptor);frame.write((int)(independentHash(descriptor)>>>8)&255);
            word(frame,0x80000000L|first.length);frame.write(first);word(frame,independentHash(first));word(frame,packed.length);frame.write(packed);word(frame,independentHash(packed));word(frame,0);
            ByteArrayOutputStream plain=new ByteArrayOutputStream();plain.write(first);plain.write(second);word(frame,independentHash(plain.toByteArray()));
            byte[] encoded=frame.toByteArray();assertArrayEquals(plain.toByteArray(),decode(encoded));
            try(var reference=new FramedLZ4CompressorInputStream(new ByteArrayInputStream(encoded))){assertArrayEquals(plain.toByteArray(),reference.readAllBytes());}
        }
    }
    private static long independentHash(byte[] b){var hash=new org.apache.commons.codec.digest.XXHash32();hash.update(b,0,b.length);return hash.getValue();}
    private static void word(OutputStream out,long n)throws IOException{out.write(java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt((int)n).array());}
    @Test void xxHashVectorsAndIncrementalStripeBoundariesAreStable()throws Exception{
        assertEquals(0x02cc5d05L,XxHash32.hash(new byte[0],0,0));
        assertEquals(0x32d153ffL,XxHash32.hash("abc".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,3));
        byte[] data=data(100000);long expected=XxHash32.hash(data,0,data.length);
        for(int chunk:new int[]{1,3,15,16,17,255,4096}){XxHash32 h=new XxHash32();for(int p=0;p<data.length;){int n=Math.min(chunk,data.length-p);h.update(data,p,n);p+=n;long ignored=h.value();}assertEquals(expected,h.value());assertEquals(expected,h.value());}
    }
    @Test void detectsMalformedHeaderBlockContentSizesAndTruncation()throws Exception{
        byte[] encoded=own(data(300000),5,300000);
        for(int n:new int[]{0,1,4,6,14,15,encoded.length-1,encoded.length-4})assertThrows(IOException.class,()->decode(Arrays.copyOf(encoded,n)));
        for(int offset:new int[]{0,4,5,6,14,19,encoded.length-1}){byte[] bad=encoded.clone();bad[offset]^=1;assertThrows(IOException.class,()->decode(bad),"offset "+offset);}
        byte[] overflow=encoded.clone();Arrays.fill(overflow,15,19,(byte)127);assertThrows(IOException.class,()->decode(overflow));
        byte[] invalidSize=encoded.clone();invalidSize[6]++;invalidSize[14]=(byte)(XxHash32.hash(invalidSize,4,10)>>>8);assertThrows(IOException.class,()->decode(invalidSize));
        byte[] reserved=encoded.clone();reserved[4]|=2;reserved[14]=(byte)(XxHash32.hash(reserved,4,10)>>>8);assertThrows(IOException.class,()->decode(reserved));
        ByteArrayOutputStream dict=new ByteArrayOutputStream();dict.write(new byte[]{4,0x22,0x4d,0x18});byte[] desc={0x61,0x40,0,0,0,0};dict.write(desc);dict.write((int)(XxHash32.hash(desc,0,desc.length)>>>8)&255);assertTrue(assertThrows(IOException.class,()->decode(dict.toByteArray())).getMessage().contains("dictionaries"));
        assertThrows(IOException.class,()->decode(new byte[]{2,0x21,0x4c,0x18}));
        byte[] trailing=Arrays.copyOf(encoded,encoded.length+1);assertThrows(IOException.class,()->decode(trailing));
        byte[] out=new byte[100];for(byte[] invalid:List.of(new byte[]{0,0,0,0},new byte[]{0,1,0,0},new byte[]{(byte)0xf0,(byte)255},new byte[]{0,1,0,(byte)255}))assertThrows(IOException.class,()->Lz4Block.decode(invalid,invalid.length,out,0,100,0));
    }
    @Test void engineCreatesExtractsTestsAndListsBothFormatsWithAtomicConstraints()throws Exception{
        ArchiveEngine engine=new ArchiveEngine();byte[] data=data(400000);Path input=Files.write(temp.resolve("input.bin"),data);
        Path folder=Files.createDirectory(temp.resolve("資料"));Files.write(folder.resolve("hello.bin"),data);Files.createDirectory(folder.resolve("empty"));
        for(var format:List.of(ArchiveEngine.Format.LZ4,ArchiveEngine.Format.TAR_LZ4)){
            Path archive=temp.resolve(format==ArchiveEngine.Format.LZ4?"input.bin.lz4":"bundle.tar.lz4");Path source=format==ArchiveEngine.Format.LZ4?input:folder;
            engine.compress(List.of(source),archive,format,5,null,null,null,8);engine.test(archive,null,null,null);assertFalse(engine.list(archive,null,null).isEmpty());Path output=temp.resolve(format+"-out");engine.extract(archive,output,null,null,null,1);assertArrayEquals(data,Files.readAllBytes(output.resolve(format==ArchiveEngine.Format.LZ4?"input.bin":"資料/hello.bin")));
            assertThrows(IOException.class,()->engine.extract(archive,output,null,null,null));assertArrayEquals(data,Files.readAllBytes(output.resolve(format==ArchiveEngine.Format.LZ4?"input.bin":"資料/hello.bin")));
            Path bad=temp.resolve(format+"-secret.lz4");assertThrows(IOException.class,()->engine.compress(List.of(source),bad,format,5,"secret".toCharArray(),null,null));assertFalse(Files.exists(bad));
            java.util.concurrent.atomic.AtomicBoolean stop=new java.util.concurrent.atomic.AtomicBoolean();Path cancelled=temp.resolve(format+"-cancelled.lz4");assertThrows(InterruptedIOException.class,()->engine.compress(List.of(source),cancelled,format,5,null,stop::get,(n,t,s)->stop.set(n>0),1));assertFalse(Files.exists(cancelled));
        }
        assertThrows(IOException.class,()->engine.compress(List.of(folder),temp.resolve("directory.lz4"),ArchiveEngine.Format.LZ4,5,null,null,null));
        ByteArrayOutputStream b=new ByteArrayOutputStream();Lz4OutputStream stream=new Lz4OutputStream(b,5,2);stream.write(1);assertThrows(IOException.class,stream::finish);assertThrows(IOException.class,stream::close);
    }
}
