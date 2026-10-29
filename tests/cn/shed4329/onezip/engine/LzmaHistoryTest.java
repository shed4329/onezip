package cn.shed4329.onezip.engine;

import cn.shed4329.onezip.engine.lzma.ArkLzma2OutputStream;
import org.junit.jupiter.api.Test;
import org.tukaani.xz.LZMA2InputStream;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LzmaHistoryTest {
    private static final int DICTIONARY=ArkLzma2OutputStream.BLOCK_SIZE;

    @Test void repeatedBinaryTemplateSurvivesMultipleInputStagingBoundaries()throws Exception{
        byte[] template=new byte[131072];new Random(771).nextBytes(template);
        byte[] input=new byte[8*1024*1024];
        for(int p=0;p<input.length;p+=template.length)System.arraycopy(template,0,input,p,template.length);
        byte[] packed=encode(input,5,null,null);
        assertTrue(packed.length<180000,"A staging boundary must not store the random template again");
        assertEquals(1,dictionaryResets(packed));
        independentlyDecode(input,packed);
    }

    @Test void arbitraryFlushBoundariesPreservePositionContextAndDictionary()throws Exception{
        byte[] input=new byte[3*DICTIONARY+27031];new Random(91).nextBytes(input);
        for(int i=5000;i<input.length;i++)if(i%83!=0)input[i]=input[i-4095];
        int[] cuts={1,2,3,65537,DICTIONARY-65543,7,DICTIONARY+19};
        for(int level:new int[]{1,5,9}){
            byte[] packed=encode(input,level,cuts,null);
            assertEquals(1,dictionaryResets(packed));
            independentlyDecode(input,packed);
        }
    }

    @Test void rawThenCompressedAndCompressedThenRawCanResumeAcrossStaging()throws Exception{
        byte[] input=new byte[3*DICTIONARY+65539];new Random(419).nextBytes(input);
        Arrays.fill(input,0,131077,(byte)'X');
        for(int p=DICTIONARY-9000;p<2*DICTIONARY+33000;p++)input[p]=(byte)(p%251);
        for(int p=2*DICTIONARY+100003;p<input.length;p++)input[p]=input[p-65537];
        byte[] packed=encode(input,5,new int[]{131077,DICTIONARY-131076,DICTIONARY+33003},null);
        assertEquals(1,dictionaryResets(packed));
        independentlyDecode(input,packed);
    }

    @Test void referencesNeverExceedAdvertisedDictionaryDespiteLargerPhysicalWindow()throws Exception{
        byte[] input=new byte[2*DICTIONARY];new Random(819).nextBytes(input);
        // This old data still exists in the physical staging array, but its
        // distance is outside the 2 MiB dictionary advertised to the decoder.
        System.arraycopy(input,0,input,DICTIONARY+65536,131072);
        byte[] packed=encode(input,5,null,null);
        independentlyDecode(input,packed);
    }

    @Test void reusedWorkspaceStartsACompletelyNewStream()throws Exception{
        var workspace=new ArkLzma2OutputStream.Workspace();
        byte[] first=new byte[DICTIONARY+1301];Arrays.fill(first,(byte)'A');
        independentlyDecode(first,encode(first,5,null,workspace));
        byte[] next=new byte[170003];new Random(615).nextBytes(next);
        byte[] reused=encode(next,5,null,workspace),fresh=encode(next,5,null,null);
        assertArrayEquals(fresh,reused);
        assertEquals(1,dictionaryResets(reused));independentlyDecode(next,reused);
    }

    @Test void storedLevelAndEmptyFlushesKeepOneDictionaryInitialization()throws Exception{
        byte[] input=new byte[DICTIONARY+17];new Random(94).nextBytes(input);
        byte[] packed=encode(input,0,new int[]{17,65539},null);
        assertEquals(1,dictionaryResets(packed));independentlyDecode(input,packed);
        assertArrayEquals(new byte[]{0},encode(new byte[0],5,new int[]{0,0},null));
    }

    @Test void compressedUnpackedLimitIsTwoMiBInsteadOfSixtyFourKiB()throws Exception{
        byte[] input=new byte[DICTIONARY+3];Arrays.fill(input,(byte)'A');
        byte[] packed=encode(input,5,null,null);
        assertTrue((packed[0]&255)>=224);
        int unpacked=(((packed[0]&31)<<16)|((packed[1]&255)<<8)|(packed[2]&255))+1;
        assertEquals(DICTIONARY,unpacked,"Exercise all 21 unpacked-size bits at the format limit");
        assertEquals(1,dictionaryResets(packed));independentlyDecode(input,packed);
    }

    @Test void packedBudgetStillBoundsChunksWhenLargerInputIsMostlyNoise()throws Exception{
        byte[] input=new byte[DICTIONARY+196611];new Random(941).nextBytes(input);
        // Force repeated-word probe hits, so actual range coding (not only raw
        // bypass) must stop on its packed-output budget while input remains.
        byte[] tag="LZMA2 packed budget boundary".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for(int p=0;p+tag.length<input.length;p+=128)System.arraycopy(tag,0,input,p,tag.length);
        byte[] packed=encode(input,5,null,null);int cursor=0,compressed=0;
        while(true){int control=packed[cursor++]&255;if(control==0)break;
            if(control<128){int size=((packed[cursor]&255)<<8)+(packed[cursor+1]&255)+1;cursor+=2+size;}
            else{int size=((packed[cursor+2]&255)<<8)+(packed[cursor+3]&255)+1;
                assertTrue(size<=65536);cursor+=4+(control>=192?1:0)+size;compressed++;}
            assertTrue(cursor<packed.length);
        }
        assertTrue(compressed>2,"Must exercise the packed-output budget repeatedly");
        assertEquals(packed.length,cursor);independentlyDecode(input,packed);
    }

    private static byte[] encode(byte[] input,int level,int[] cuts,ArkLzma2OutputStream.Workspace workspace)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(var encoder=workspace==null?new ArkLzma2OutputStream(bytes,level):new ArkLzma2OutputStream(bytes,level,workspace)){
            encoder.flush();int position=0;
            if(cuts!=null)for(int size:cuts){int n=Math.min(size,input.length-position);encoder.write(input,position,n);position+=n;encoder.flush();encoder.flush();}
            while(position<input.length){int n=Math.min(7777,input.length-position);encoder.write(input,position,n);position+=n;}
        }
        return bytes.toByteArray();
    }
    private static void independentlyDecode(byte[] input,byte[] packed)throws Exception{
        try(var decoder=new LZMA2InputStream(new ByteArrayInputStream(packed),DICTIONARY)){
            assertArrayEquals(input,decoder.readAllBytes());
        }
        try(var decoder=new cn.shed4329.onezip.engine.lzma.LzmaInputStream(new ByteArrayInputStream(packed),18)){
            assertArrayEquals(input,decoder.readAllBytes());
        }
    }
    private static int dictionaryResets(byte[] bytes){
        int resets=0,p=0;
        while(true){int control=bytes[p++]&255;if(control==0){assertEquals(bytes.length,p);return resets;}
            if(control<128){assertTrue(control==1||control==2);if(control==1)resets++;
                int length=((bytes[p]&255)<<8)+(bytes[p+1]&255)+1;p+=2+length;
            }else{
                if(control>=224)resets++;
                int packed=((bytes[p+2]&255)<<8)+(bytes[p+3]&255)+1;p+=4+(control>=192?1:0)+packed;
            }
            assertTrue(p<bytes.length);
        }
    }
}
