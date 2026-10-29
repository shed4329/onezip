package cn.shed4329.onezip.engine;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Crc32BulkTest {
    @Test void standardVectorAndUnalignedBulkBoundariesMatchIndependentCrc() {
        assertEquals(0xcbf43926L,Crc32.of("123456789".getBytes(StandardCharsets.US_ASCII)));
        byte[] data=new byte[1024*1024+64];new Random(662799).nextBytes(data);
        for(int offset=0;offset<32;offset++)for(int n:new int[]{0,1,2,3,4,7,8,9,15,16,17,31,32,33,63,64,65,127,128,129,4095,4096,4097,65535,65536,65537,1024*1024}){
            java.util.zip.CRC32 oracle=new java.util.zip.CRC32();oracle.update(data,offset,n);Crc32 actual=new Crc32();actual.update(data,offset,n);assertEquals(oracle.getValue(),actual.value(),"offset="+offset+" length="+n);
        }
    }
    @Test void mixedByteAndChunkUpdatesPreserveIncrementalState(){
        byte[] data=new byte[1024*1024+31];Random random=new Random(99028);random.nextBytes(data);Crc32 actual=new Crc32();java.util.zip.CRC32 oracle=new java.util.zip.CRC32();
        for(int p=0;p<data.length;){if(random.nextBoolean()){actual.update(data[p]);oracle.update(data[p]);p++;}else{int n=Math.min(data.length-p,random.nextInt(2000));actual.update(data,p,n);oracle.update(data,p,n);p+=n;}assertEquals(oracle.getValue(),actual.value());}
        assertThrows(IndexOutOfBoundsException.class,()->actual.update(data,-1,1));assertThrows(IndexOutOfBoundsException.class,()->actual.update(data,data.length,1));
    }
}
