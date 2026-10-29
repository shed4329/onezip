package cn.shed4329.onezip.engine;

import cn.shed4329.onezip.engine.lzma.ArkLzma2OutputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tukaani.xz.LZMA2InputStream;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CodecTest {
    @TempDir Path temp;
    static byte[] encode(byte[] input, int level) throws IOException {
        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        try (ArkLzma2OutputStream stream = new ArkLzma2OutputStream(packed, level)) {
            for (int p = 0; p < input.length; p += 777) stream.write(input, p, Math.min(777, input.length - p));
        }
        return packed.toByteArray();
    }
    static byte[] decode(byte[] packed) throws IOException {
        try (InputStream decoder = new LZMA2InputStream(new ByteArrayInputStream(packed), ArkLzma2OutputStream.BLOCK_SIZE)) { return decoder.readAllBytes(); }
    }
    @Test void independentDecoderRoundTripsAllLevelsAndBoundarySizes() throws Exception {
        Random random = new Random(4329);
        for (int size : new int[]{0,1,2,3,31,272,273,274,65535,65536,65537,131072}) {
            byte[] bytes = new byte[size]; random.nextBytes(bytes);
            for (int level : new int[]{0,1,5,9}) assertArrayEquals(bytes, decode(encode(bytes, level)), "random size=" + size + " level=" + level);
            for (int i = 8; i < bytes.length; ++i) if (i % 17 != 0) bytes[i] = bytes[i - 7];
            for (int level : new int[]{1,5,9}) assertArrayEquals(bytes, decode(encode(bytes, level)), "matched size=" + size + " level=" + level);
        }
    }
    @Test void compressesAndResetsAcrossTwoMiBBoundary() throws Exception {
        byte[] bytes = new byte[ArkLzma2OutputStream.BLOCK_SIZE + 72123];
        byte[] pattern = "OneZip 自行實作 Java LZMA2 / range coder\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; ++i) bytes[i] = pattern[i % pattern.length];
        byte[] compressed = encode(bytes, 5);
        assertTrue(compressed.length < bytes.length / 50);
        assertEquals(0xe0, compressed[0] & 0xe0);
        assertArrayEquals(bytes, decode(compressed));
    }
    @Test void incompressibleDataUsesRawChunks() throws Exception {
        byte[] bytes = new byte[200000]; new Random(123).nextBytes(bytes);
        byte[] compressed = encode(bytes, 9);
        assertEquals(1, compressed[0]); assertArrayEquals(bytes, decode(compressed));
    }
    @Test void boundedPackedChunksDoNotDiscardCompressibleMultiMiBInput()throws Exception{
        StringBuilder text=new StringBuilder();for(int i=0;i<65000;i++)text.append("event id=").append(i).append(" user=").append(i%971).append(" route=/api/files/").append(i%307).append(" status=200 latency=").append((i*31)%5000).append(" token=").append(Integer.toHexString(i*1103515245)).append('\n');
        byte[] input=text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),packed=encode(input,5);
        assertTrue(input.length>2*1024*1024);assertTrue(packed.length<input.length/2,"Useful compression must survive the 64 KiB payload boundary");
        int chunks=0;for(int i=0;i<packed.length;){int control=packed[i++]&255;if(control==0){assertEquals(packed.length,i);break;}int unpacked=((packed[i++]&255)<<8)|(packed[i++]&255);if(control<128)i+=unpacked+1;else{int n=((packed[i++]&255)<<8)|(packed[i++]&255);if(control>=0xc0)i++;i+=n+1;chunks++;}assertTrue(i<=packed.length);}
        assertTrue(chunks>2,"Must exercise multiple bounded compressed chunks");
        assertArrayEquals(input,decode(packed));assertArrayEquals(input,new cn.shed4329.onezip.engine.lzma.LzmaInputStream(new ByteArrayInputStream(packed),18).readAllBytes());
    }
    @Test void alternatingRawAndCompressedChunksPreserveDictionaryAndFlush()throws Exception{
        ByteArrayOutputStream source=new ByteArrayOutputStream();Random random=new Random(572);
        for(int i=0;i<20;i++){byte[] noise=new byte[65536];random.nextBytes(noise);source.write(noise);source.write(("record="+i+" data=OneZip repeated content\n").repeat(2500).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        byte[] input=source.toByteArray();ByteArrayOutputStream packed=new ByteArrayOutputStream();
        try(ArkLzma2OutputStream encoder=new ArkLzma2OutputStream(packed,5)){
            encoder.write(input,0,100003);encoder.flush();encoder.write(input,100003,input.length-100003);
        }
        assertTrue(packed.size()<input.length*0.75);assertArrayEquals(input,decode(packed.toByteArray()));
        assertArrayEquals(input,new cn.shed4329.onezip.engine.lzma.LzmaInputStream(new ByteArrayInputStream(packed.toByteArray()),18).readAllBytes());
    }
    @Test void ownSevenZContainerIsReadableByIndependentReader() throws Exception {
        Path archive = temp.resolve("test.7z");
        byte[] content = "資料與資料與資料與資料".repeat(9000).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (SevenZWriter writer = new SevenZWriter(archive, 5)) {
            writer.begin("資料夾/", true, 0).close();
            writer.begin("資料夾/空白.txt", false, 0).close();
            try (OutputStream entry = writer.begin("資料夾/內容.txt", false, content.length)) { entry.write(content); }
            writer.finish();
        }
        try (SevenZFile reader = SevenZFile.builder().setPath(archive).get()) {
            assertTrue(reader.getNextEntry().isDirectory());
            assertEquals(0, reader.getNextEntry().getSize());
            assertEquals(content.length, reader.getNextEntry().getSize());
            ByteArrayOutputStream decoded = new ByteArrayOutputStream(); byte[] b = new byte[999]; int n;
            while ((n = reader.read(b)) != -1) decoded.write(b, 0, n);
            assertArrayEquals(content, decoded.toByteArray()); assertNull(reader.getNextEntry());
        }
    }
    @Test void crcKnownVector() { assertEquals(0xcbf43926L, Crc32.of("123456789".getBytes())); }
    @Test void ownDeflateAndZipAreReadableByJdkAndZip4j() throws Exception {
        for (int level : new int[]{0,1,5,9}) {
            byte[] data = new byte[180000]; new Random(991).nextBytes(data);
            for(int i=1000;i<100000;++i)data[i]=data[i%997];
            Path archive=temp.resolve("zip-"+level+".zip");
            try(ZipWriter writer=new ZipWriter(archive,level,null)) {
                writer.begin("中文/",true,0).close();
                try(OutputStream entry=writer.begin("中文/data.bin",false,data.length)){entry.write(data);}
                writer.begin("空白",false,0).close();writer.finish();
            }
            try(java.util.zip.ZipFile reader=new java.util.zip.ZipFile(archive.toFile())){
                assertArrayEquals(data,reader.getInputStream(reader.getEntry("中文/data.bin")).readAllBytes());
                assertEquals(0,reader.getEntry("空白").getSize());
            }
        }
    }
    @Test void ownZipAesAuthenticatesWithIndependentReader() throws Exception {
        Path archive=temp.resolve("encrypted.zip");byte[] data="AES UTF-8 密碼與資料".repeat(4000).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try(ZipWriter writer=new ZipWriter(archive,5,"密碼4329".toCharArray())) {
            try(OutputStream entry=writer.begin("secret.txt",false,data.length)){entry.write(data);}writer.finish();
        }
        try(net.lingala.zip4j.ZipFile reader=new net.lingala.zip4j.ZipFile(archive.toFile(),"密碼4329".toCharArray())){
            assertArrayEquals(data,reader.getInputStream(reader.getFileHeader("secret.txt")).readAllBytes());
        }
        try(net.lingala.zip4j.ZipFile wrong=new net.lingala.zip4j.ZipFile(archive.toFile(),"wrong".toCharArray())){
            assertThrows(IOException.class,()->wrong.getInputStream(wrong.getFileHeader("secret.txt")).readAllBytes());
        }
    }
    @Test void alternatingDistancesAndLiteralContextsRoundTrip()throws Exception{
        Random random=new Random(99291);
        for(int sample=0;sample<16;++sample){
            byte[] data=new byte[10000+sample*173];random.nextBytes(data);
            for(int i=1024;i<data.length;){
                int distance=1+random.nextInt(Math.min(i,9000)),length=Math.min(data.length-i,2+random.nextInt(300));
                for(int j=0;j<length;++j)data[i+j]=data[i+j-distance];
                i+=length+Math.min(data.length-i-length,random.nextInt(8));
            }
            assertArrayEquals(data,decode(encode(data,sample%10)),"sample="+sample);
        }
    }
    @Test void sevenZWithOnlyEmptyEntriesAndZip32Limits()throws Exception{
        Path archive=temp.resolve("empty.7z");
        try(SevenZWriter writer=new SevenZWriter(archive,5)){writer.begin("folder/",true,0).close();writer.begin("folder/empty",false,0).close();writer.finish();}
        try(SevenZFile reader=SevenZFile.builder().setPath(archive).get()){assertTrue(reader.getNextEntry().isDirectory());assertEquals(0,reader.getNextEntry().getSize());assertNull(reader.getNextEntry());}
        try(ZipWriter writer=new ZipWriter(temp.resolve("limit.zip"),5,null)){assertThrows(IOException.class,()->writer.begin("huge",false,0xffffffffL));}
    }
}
