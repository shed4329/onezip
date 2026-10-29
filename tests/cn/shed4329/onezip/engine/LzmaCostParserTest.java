package cn.shed4329.onezip.engine;

import cn.shed4329.onezip.engine.lzma.ArkLzma2OutputStream;
import cn.shed4329.onezip.engine.lzma.LzmaInputStream;
import org.junit.jupiter.api.Test;
import org.tukaani.xz.LZMA2InputStream;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent decoders and workload properties, not regenerated encoder hashes. */
class LzmaCostParserTest {
    private static byte[] encode(byte[] source,int level,boolean flush)throws Exception{
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(var encoder=new ArkLzma2OutputStream(output,level)){
            for(int pos=0;pos<source.length;){int length=Math.min(777,source.length-pos);encoder.write(source,pos,length);pos+=length;
                if(flush&&pos%5439==0)encoder.flush();}
        }
        return output.toByteArray();
    }
    private static void independent(byte[] source,byte[] encoded)throws Exception{
        try(var decoder=new LZMA2InputStream(new ByteArrayInputStream(encoded),1<<21)){assertArrayEquals(source,decoder.readAllBytes());}
        try(var decoder=new LzmaInputStream(new ByteArrayInputStream(encoded),18)){assertArrayEquals(source,decoder.readAllBytes());}
    }
    private static byte[] records(int count){
        StringBuilder text=new StringBuilder();Random random=new Random(67119);
        String[] verbs={"read","write","open","close","move","delete","query","update"};
        for(int i=0;i<count;i++)text.append("{\"id\":").append(i).append(",\"user\":\"user-").append(random.nextInt(47))
                .append("\",\"operation\":\"").append(verbs[random.nextInt(verbs.length)]).append("\",\"result\":")
                .append(random.nextInt(5)).append(",\"elapsed\":").append(random.nextInt(997)).append("}\n");
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }
    @Test void normalCostParsingImprovesVariedShortTextDespiteReducedSearchBudget()throws Exception{
        byte[] source=records(1800),fast=encode(source,4,false),normal=encode(source,5,false);
        // Normal level5 caps ASCII-prefix chains at16, below fast level4's64:
        // the text improvement must survive the balanced default's effort cap.
        assertTrue(normal.length<fast.length,"Short text should benefit from price-based alternatives: "+normal.length+" vs "+fast.length);
        independent(source,normal);independent(source,fast);
    }
    @Test void twoByteContextsAndShortRepAlternativesRemainValidAtEveryLevel()throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();Random random=new Random(817);
        String[] pairs={"th","he","in","er","an","re","on","at","en","nd"};
        for(int i=0;i<1800;i++){bytes.write(pairs[random.nextInt(pairs.length)].getBytes(StandardCharsets.US_ASCII));bytes.write(random.nextInt(256));}
        byte[] source=bytes.toByteArray();
        for(int level=0;level<=9;level++)independent(source,encode(source,level,false));
    }
    @Test void plannedPathsNeverLeakAcrossRawFallbackAndNonalignedFlush()throws Exception{
        byte[] source=new byte[2*1024*1024+191003];new Random(9147).nextBytes(source);
        byte[] text=records(3000);
        for(int offset:new int[]{153,65003,1024*1024+7})System.arraycopy(text,0,source,offset,Math.min(text.length,source.length-offset));
        independent(source,encode(source,5,true));
    }
    @Test void priceQuantizationAgreesWithIndependentLogarithmicBounds()throws Exception{
        Class<?> encoder=Class.forName("cn.shed4329.onezip.engine.lzma.ArkLzma2OutputStream$Encoder");
        Method bit=encoder.getDeclaredMethod("bitPrice",int.class,int.class);bit.setAccessible(true);
        for(int probability=31;probability<=2017;probability+=17)for(int value=0;value<2;value++){
            int index=(probability^(value==0?0:0x7f0))>>>4;
            double midpoint=(index*16+8)/2048.0;
            double ideal=-StrictMath.log(midpoint)/StrictMath.log(2)*16;
            int actual=(Integer)bit.invoke(null,probability,value);
            assertTrue(Math.abs(actual-ideal)<2,"Quantized fixed-point price must track -log2 probability");
        }
        assertEquals(16,bit.invoke(null,1024,0));assertEquals(17,bit.invoke(null,1024,1));
    }
    @Test void normalModeIsDeterministicAcrossSequentialWorkspaceReuse()throws Exception{
        byte[] source=records(700);var workspace=new ArkLzma2OutputStream.Workspace();byte[] expected=encode(source,5,false);
        for(int repetition=0;repetition<3;repetition++){
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            try(var encoder=new ArkLzma2OutputStream(output,5,workspace)){encoder.write(source);}
            assertArrayEquals(expected,output.toByteArray());independent(source,output.toByteArray());
        }
    }
}
