package cn.shed4329.onezip.engine;

import cn.shed4329.onezip.engine.lzma.LzmaInputStream;
import org.junit.jupiter.api.Test;
import org.tukaani.xz.*;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent coder inputs exercise range overflow, matched literals and short reads. */
class LzmaDecoderBoundaryTest {
    private static byte[] data(){
        byte[] bytes=new byte[180137];new Random(83019).nextBytes(bytes);
        for(int i=1024;i<bytes.length;i++)if(i%257<239)bytes[i]=(byte)(bytes[i-997]^(i%113==0?1:0));
        return bytes;
    }
    private static byte[] decode(InputStream in,int stride)throws Exception{
        try(in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[stride];for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);return out.toByteArray();
        }
    }
    @Test void independentlyEncodedPositionContextsAndSmallDictionaryWrap()throws Exception{
        byte[] original=data();
        for(int lc=0;lc<=4;lc++)for(int pb:new int[]{0,4}){
            LZMA2Options options=new LZMA2Options(5);options.setDictSize(4096);options.setLcLp(lc,4-lc);options.setPb(pb);
            ByteArrayOutputStream packed=new ByteArrayOutputStream();
            try(FinishableOutputStream out=options.getOutputStream(new FinishableWrapperOutputStream(packed))){out.write(original);}
            assertArrayEquals(original,decode(new LzmaInputStream(new ByteArrayInputStream(packed.toByteArray()),0),lc%2==0?7:65536),"lc="+lc+" pb="+pb);
        }
    }
    @Test void lzma1EndMarkerUnsignedRangeAndOneByteReads()throws Exception{
        byte[] original=data();LZMA2Options options=new LZMA2Options(5);options.setDictSize(4096);options.setLcLp(0,4);options.setPb(4);
        ByteArrayOutputStream packed=new ByteArrayOutputStream();int property;
        try(LZMAOutputStream out=new LZMAOutputStream(packed,options,true)){property=out.getProps();out.write(original);}
        byte[] props={(byte)property,0,16,0,0};
        assertArrayEquals(original,decode(new LzmaInputStream(new ByteArrayInputStream(packed.toByteArray()),props,original.length),1));
    }
    @Test void truncatedPackedChunksFailWithIOException()throws Exception{
        byte[] original=data();LZMA2Options options=new LZMA2Options(5);options.setDictSize(4096);
        ByteArrayOutputStream packed=new ByteArrayOutputStream();try(FinishableOutputStream out=options.getOutputStream(new FinishableWrapperOutputStream(packed))){out.write(original);}
        byte[] bytes=packed.toByteArray();
        for(int cut=1;cut<=48;cut++){
            byte[] truncated=Arrays.copyOf(bytes,bytes.length-cut);
            assertThrows(IOException.class,()->decode(new LzmaInputStream(new ByteArrayInputStream(truncated),0),273),"cut="+cut);
        }
        // Keep the chunk's advertised size but remove range bytes, without allowing
        // a short physical input to be mistaken for zero padding.
        assertThrows(IOException.class,()->decode(new LzmaInputStream(new ByteArrayInputStream(new byte[]{(byte)0xe0,0,0,0,4,93,0,0,0,0}),0),17));
    }
}
