package cn.shed4329.onezip.engine;

import cn.shed4329.onezip.engine.lzma.ArkLzma2OutputStream;
import cn.shed4329.onezip.engine.lzma.LzmaInputStream;
import org.junit.jupiter.api.Test;
import org.tukaani.xz.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent checks around optimized bulk paths, bit lookahead and ring boundaries. */
class OptimizedCodecTest {
    private static byte[] read(InputStream in,int size)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[size];int n;
        while((n=in.read(b))!=-1)out.write(b,0,n);assertEquals(0,in.read(b,0,0));return out.toByteArray();
    }
    @Test void slicingCrcMatchesIndependentJdkAtOffsetsTailsAndSingleByteUpdates(){
        byte[] b=new byte[70039];new Random(190).nextBytes(b);
        for(int offset=0;offset<17;offset++)for(int size:new int[]{0,1,2,7,8,9,15,16,17,31,32,33,255,4096,65535}){
            CRC32 expected=new CRC32();expected.update(b,offset,size);Crc32 actual=new Crc32();actual.update(b,offset,size);assertEquals(expected.getValue(),actual.value());
            actual=new Crc32();int pos=0;for(;pos+13<size;pos+=13)actual.update(b,offset+pos,13);for(;pos<size;pos++)actual.update(b[offset+pos]);assertEquals(expected.getValue(),actual.value());
        }
    }
    @Test void dynamicOneBitEndCodeCanBeFollowedByStoredBlock()throws Exception{
        byte[] a=new byte[19003],noise=new byte[33013];Arrays.fill(a,(byte)'A');new Random(723).nextBytes(noise);ByteArrayOutputStream encoded=new ByteArrayOutputStream(),source=new ByteArrayOutputStream();
        Deflater deflater=new Deflater(6,true);deflater.setStrategy(Deflater.HUFFMAN_ONLY);
        try(DeflaterOutputStream out=new DeflaterOutputStream(encoded,deflater,8192,true)){
            out.write(a);source.write(a);out.flush();out.write(noise);source.write(noise);out.flush();out.write(a);source.write(a);
        }finally{deflater.end();}
        byte[] bytes=encoded.toByteArray();assertEquals(2,(bytes[0]>>>1)&3);
        Inflater reference=new Inflater(true);try(InflaterInputStream in=new InflaterInputStream(new ByteArrayInputStream(bytes),reference)){assertArrayEquals(source.toByteArray(),in.readAllBytes());}finally{reference.end();}
        for(int size:new int[]{1,2,7,127,8191,65536})assertArrayEquals(source.toByteArray(),read(new DeflateInputStream(new ByteArrayInputStream(bytes)),size),"read buffer "+size);
    }
    @Test void storedFallbackDoesNotExpandRandomInputAndPreservesFollowingBlockBits()throws Exception{
        byte[] noise=new byte[65535*3+17];new Random(492).nextBytes(noise);ByteArrayOutputStream encoded=new ByteArrayOutputStream();DeflateEncoder encoder=new DeflateEncoder(encoded,5);encoder.write(noise);encoder.finish();
        assertTrue(encoded.size()<noise.length+40,"Incompressible input must use stored blocks");
        Inflater reference=new Inflater(true);try(InflaterInputStream in=new InflaterInputStream(new ByteArrayInputStream(encoded.toByteArray()),reference)){assertArrayEquals(noise,in.readAllBytes());}finally{reference.end();}
        ByteArrayOutputStream source=new ByteArrayOutputStream();byte[] repeats=new byte[65535];Arrays.fill(repeats,(byte)'Z');source.write(repeats);source.write(noise);source.write(repeats);encoded.reset();encoder=new DeflateEncoder(encoded,5);encoder.write(source.toByteArray());encoder.finish();
        for(int size:new int[]{1,3,259,32767,65536})assertArrayEquals(source.toByteArray(),read(new DeflateInputStream(new ByteArrayInputStream(encoded.toByteArray())),size));
    }
    @Test void lzmaBulkCopyHandlesShortDistancesRingWrapAndSmallConsumerReads()throws Exception{
        byte[] data=new byte[260003];Random random=new Random(5741);random.nextBytes(data);
        for(int i=4000;i<data.length;i++){int distance=i<50000?1:i<100000?2:i<150000?3:i<200000?4095:4096;data[i]=data[i-distance];}
        LZMA2Options options=new LZMA2Options(5);options.setDictSize(4096);ByteArrayOutputStream packed=new ByteArrayOutputStream();
        try(FinishableOutputStream out=options.getOutputStream(new FinishableWrapperOutputStream(packed))){out.write(data);}
        for(int size:new int[]{1,2,3,17,257,4095,65536})assertArrayEquals(data,read(new LzmaInputStream(new ByteArrayInputStream(packed.toByteArray()),0),size),"read buffer "+size);
    }
    @Test void reusableLzmaWorkspaceIsSequentialAndResetsBetweenEntries()throws Exception{
        ArkLzma2OutputStream.Workspace workspace=new ArkLzma2OutputStream.Workspace();byte[] data="shared workspace must not share dictionary history across entries\n".repeat(2000).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for(int iteration=0;iteration<4;iteration++){
            ByteArrayOutputStream packed=new ByteArrayOutputStream();ArkLzma2OutputStream encoder=new ArkLzma2OutputStream(packed,iteration==0?0:5,workspace);
            assertThrows(IllegalStateException.class,()->new ArkLzma2OutputStream(new ByteArrayOutputStream(),5,workspace));encoder.write(data);encoder.finish();encoder.close();
            assertArrayEquals(data,new LZMA2InputStream(new ByteArrayInputStream(packed.toByteArray()),1<<21).readAllBytes());
        }
    }
    @Test void incompressibilityProbeRecognizesHighEntropyDictionaryRepeats()throws Exception{
        byte[] random=new byte[65536];new Random(77341).nextBytes(random);ByteArrayOutputStream source=new ByteArrayOutputStream();for(int i=0;i<12;i++)source.write(random);
        ByteArrayOutputStream packed=new ByteArrayOutputStream();try(ArkLzma2OutputStream encoder=new ArkLzma2OutputStream(packed,5)){encoder.write(source.toByteArray());}
        assertTrue(packed.size()<source.size()/5,"A random-looking block repeated from the dictionary is compressible");
        assertArrayEquals(source.toByteArray(),new LZMA2InputStream(new ByteArrayInputStream(packed.toByteArray()),1<<21).readAllBytes());
        assertArrayEquals(source.toByteArray(),new LzmaInputStream(new ByteArrayInputStream(packed.toByteArray()),18).readAllBytes());
    }
}
