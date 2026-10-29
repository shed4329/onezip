package cn.shed4329.onezip.engine;

import cn.shed4329.onezip.engine.lzma.LzmaInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tukaani.xz.*;
import org.apache.commons.compress.archivers.sevenz.*;
import org.apache.commons.compress.archivers.zip.*;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import static org.junit.jupiter.api.Assertions.*;

/** Other implementations generate the input. Production has no access to these test-only libraries. */
class DecoderTest {
    @TempDir Path temp;
    private static byte[] sample(int size,int seed){byte[] b=new byte[size];Random r=new Random(seed);r.nextBytes(b);for(int i=1000;i<size;i++)if(i%137<120)b[i]=b[i-997];return b;}
    private static byte[] lzma2(byte[] data,LZMA2Options options)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(FinishableOutputStream out=options.getOutputStream(new FinishableWrapperOutputStream(bytes))){out.write(data);}return bytes.toByteArray();
    }
    @Test void independentlyEncodedDeflateStoredFixedDynamicAndWindowWrap()throws Exception{
        for(int size:new int[]{0,1,31,65535,65536,200003})for(int level:new int[]{0,1,6,9}){
            byte[] input=sample(size,level+size);ByteArrayOutputStream bytes=new ByteArrayOutputStream();Deflater encoder=new Deflater(level,true);
            try(DeflaterOutputStream out=new DeflaterOutputStream(bytes,encoder)){out.write(input);}finally{encoder.end();}
            assertArrayEquals(input,new DeflateInputStream(new ByteArrayInputStream(bytes.toByteArray())).readAllBytes(),"size="+size+" level="+level);
        }
        byte[] text="abcxyz abcxyz abcxyz compressed data\n".repeat(10000).getBytes(StandardCharsets.UTF_8);ByteArrayOutputStream b=new ByteArrayOutputStream();Deflater d=new Deflater(6,true);try(DeflaterOutputStream out=new DeflaterOutputStream(b,d)){out.write(text);}finally{d.end();}
        assertEquals(2,(b.toByteArray()[0]>>>1)&3,"Must exercise a dynamic tree");assertArrayEquals(text,new DeflateInputStream(new ByteArrayInputStream(b.toByteArray())).readAllBytes());
    }
    @Test void independentLzma2ContextsRawChunksAndContinuation()throws Exception{
        for(int i=0;i<8;i++){
            LZMA2Options options=new LZMA2Options(i);options.setDictSize(1<<20);options.setLcLp(i%5,0);options.setPb(i%5);
            byte[] input=sample(180000+i*123,91+i);byte[] packed=lzma2(input,options);
            assertArrayEquals(input,new LzmaInputStream(new ByteArrayInputStream(packed),16).readAllBytes(),"context "+i);
        }
        byte[] input=sample(3*1024*1024+1123,543);new Random(729).nextBytes(input);for(int i=70000;i<input.length;i++)if(i%100000<95000)input[i]=input[i-65001];
        LZMA2Options options=new LZMA2Options(5);options.setDictSize(1<<20);
        assertArrayEquals(input,new LzmaInputStream(new ByteArrayInputStream(lzma2(input,options)),16).readAllBytes());
    }
    @Test void rawLzma2ChunkMayResumePreviousProbabilityState()throws Exception{
        byte[] block=new byte[20000];Arrays.fill(block,(byte)'A');
        LZMA2Options options=new LZMA2Options(5);options.setDictSize(1<<20);ByteArrayOutputStream bytes=new ByteArrayOutputStream();int boundary;
        try(FinishableOutputStream out=options.getOutputStream(new FinishableWrapperOutputStream(bytes))){out.write(block);out.flush();boundary=bytes.size();out.write(block);}
        byte[] original=bytes.toByteArray();assertEquals(0x80,original[boundary]&0xe0,"Must resume LZMA probability state");
        // Four A bytes preserve pb=2 position context and distance-1 matches. Raw control 2
        // updates the dictionary but does not require resetting the probability model.
        ByteArrayOutputStream mixed=new ByteArrayOutputStream();mixed.write(original,0,boundary);mixed.write(new byte[]{2,0,3,'A','A','A','A'});mixed.write(original,boundary,original.length-boundary);
        byte[] expected=new byte[40004];Arrays.fill(expected,(byte)'A');
        assertArrayEquals(expected,new LZMA2InputStream(new ByteArrayInputStream(mixed.toByteArray()),1<<20).readAllBytes());
        assertArrayEquals(expected,new LzmaInputStream(new ByteArrayInputStream(mixed.toByteArray()),16).readAllBytes());
    }
    @Test void independentBcjBranchSignMasksAndChunkBoundaries()throws Exception{
        Random random=new Random(811);
        for(int sample=0;sample<120;sample++){
            byte[] input=new byte[sample<16?sample:4096+sample*31];random.nextBytes(input);
            for(int i=0;i+4<input.length;i+=1+random.nextInt(9)){input[i]=(byte)(random.nextBoolean()?0xe8:0xe9);input[i+4]=(byte)(random.nextBoolean()?0:255);}
            X86Options options=new X86Options();int start=sample%3==0?0:sample%3==1?4096:0xfffff000;options.setStartOffset(start);ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(FinishableOutputStream out=options.getOutputStream(new FinishableWrapperOutputStream(bytes))){for(int p=0;p<input.length;p+=77)out.write(input,p,Math.min(77,input.length-p));}
            assertArrayEquals(input,new X86BcjInputStream(new ByteArrayInputStream(bytes.toByteArray()),Integer.toUnsignedLong(start)).readAllBytes(),"BCJ sample "+sample);
        }
    }
    @Test void independentRawLzma1WithAndWithoutEndMarker()throws Exception{
        for(boolean endMarker:new boolean[]{false,true})for(int size:new int[]{0,1,350000}){
            byte[] data=sample(size,98);LZMA2Options options=new LZMA2Options(4);options.setDictSize(1<<20);ByteArrayOutputStream bytes=new ByteArrayOutputStream();int property;
            try(LZMAOutputStream encoder=new LZMAOutputStream(bytes,options,endMarker)){property=encoder.getProps();encoder.write(data);}
            byte[] props={(byte)property,0,0,16,0};assertArrayEquals(data,new LzmaInputStream(new ByteArrayInputStream(bytes.toByteArray()),props,data.length).readAllBytes());
        }
    }
    @Test void independentZipWithDescriptorsZip64AndUnicode()throws Exception{
        byte[] data=sample(220000,1);Path zip=temp.resolve("external.zip");
        try(ZipArchiveOutputStream out=new ZipArchiveOutputStream(zip)){out.setUseZip64(Zip64Mode.Always);ZipArchiveEntry entry=new ZipArchiveEntry("資料/中文.bin");out.putArchiveEntry(entry);out.write(data);out.closeArchiveEntry();}
        Path destination=temp.resolve("out");new ArchiveEngine().extract(zip,destination,null,null,null);assertArrayEquals(data,Files.readAllBytes(destination.resolve("資料/中文.bin")));
    }
    @Test void independentEncryptedZip128And256AndLegacyCrypto()throws Exception{
        byte[] data=sample(140000,7);char[] password="test中文123".toCharArray();
        for(EncryptionMethod encryption:new EncryptionMethod[]{EncryptionMethod.AES,EncryptionMethod.ZIP_STANDARD})for(AesKeyStrength strength:new AesKeyStrength[]{AesKeyStrength.KEY_STRENGTH_128,AesKeyStrength.KEY_STRENGTH_256}){
            String id=encryption+"-"+strength;Path source=Files.write(temp.resolve(id+".bin"),data),archive=temp.resolve(id+".zip");ZipParameters parameters=new ZipParameters();parameters.setEncryptFiles(true);parameters.setEncryptionMethod(encryption);parameters.setAesKeyStrength(strength);
            try(net.lingala.zip4j.ZipFile writer=new net.lingala.zip4j.ZipFile(archive.toFile(),password)){writer.addFile(source.toFile(),parameters);}
            Path destination=temp.resolve(id);new ArchiveEngine().extract(archive,destination,password,null,null);assertArrayEquals(data,Files.readAllBytes(destination.resolve(source.getFileName())));
        }
    }
    @Test void independentSevenZCopyLzmaLzma2DeflateAndDelta()throws Exception{
        byte[] data=sample(240000,67);
        for(SevenZMethod method:new SevenZMethod[]{SevenZMethod.COPY,SevenZMethod.LZMA,SevenZMethod.LZMA2,SevenZMethod.DEFLATE,SevenZMethod.DELTA_FILTER,SevenZMethod.BCJ_X86_FILTER}){
            Path archive=temp.resolve(method+".7z");
            try(SevenZOutputFile out=new SevenZOutputFile(archive.toFile())){
                if(method==SevenZMethod.DELTA_FILTER)out.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.DELTA_FILTER,5),new SevenZMethodConfiguration(SevenZMethod.LZMA2)));
                else if(method==SevenZMethod.BCJ_X86_FILTER)out.setContentMethods(List.of(new SevenZMethodConfiguration(method),new SevenZMethodConfiguration(SevenZMethod.LZMA2)));
                else out.setContentCompression(method);
                SevenZArchiveEntry e=new SevenZArchiveEntry();e.setName("資料.bin");out.putArchiveEntry(e);out.write(data);out.closeArchiveEntry();
                e=new SevenZArchiveEntry();e.setName("empty");out.putArchiveEntry(e);out.closeArchiveEntry();
            }
            Path dest=temp.resolve(method.name());new ArchiveEngine().extract(archive,dest,null,null,null);assertArrayEquals(data,Files.readAllBytes(dest.resolve("資料.bin")));assertEquals(0,Files.size(dest.resolve("empty")));
        }
    }
    @Test void solidSevenZWithEncodedHeaderAndSubstreamCrc()throws Exception{
        byte[] a=sample(70003,77),b=sample(83001,19);Path archive=solidArchive(a,b);Path out=temp.resolve("solid");new ArchiveEngine().extract(archive,out,null,null,null);assertArrayEquals(a,Files.readAllBytes(out.resolve("a.bin")));assertArrayEquals(b,Files.readAllBytes(out.resolve("b.bin")));
    }
    /** Hand-assembled standard 7z metadata with payload/header compressed by the independent XZ encoder. */
    private Path solidArchive(byte[] a,byte[] b)throws Exception{
        LZMA2Options options=new LZMA2Options(4);options.setDictSize(1<<20);ByteArrayOutputStream content=new ByteArrayOutputStream();content.write(a);content.write(b);byte[] packed=lzma2(content.toByteArray(),options);
        BinaryWriter h=new BinaryWriter();h.write(1);h.write(4);writeStreams(h,0,packed.length,content.size(),true,a,b);h.write(5);h.uint64(2);BinaryWriter names=new BinaryWriter();names.write(0);names.append("a.bin\0b.bin\0".getBytes(StandardCharsets.UTF_16LE));h.write(17);h.uint64(names.size());h.append(names.toByteArray());h.write(0);h.write(0);
        byte[] header=lzma2(h.toByteArray(),options);BinaryWriter encoded=new BinaryWriter();encoded.write(23);writeStreams(encoded,packed.length,header.length,h.size(),false,null,null);byte[] next=encoded.toByteArray();
        BinaryWriter start=new BinaryWriter();start.le(packed.length+header.length,8);start.le(next.length,8);start.le(Crc32.of(next),4);BinaryWriter all=new BinaryWriter();all.append(new byte[]{0x37,0x7a,(byte)0xbc,(byte)0xaf,0x27,0x1c,0,4});all.le(Crc32.of(start.toByteArray()),4);all.append(start.toByteArray());all.append(packed);all.append(header);all.append(next);return Files.write(temp.resolve("solid.7z"),all.toByteArray());
    }
    private static void writeStreams(BinaryWriter h,long position,long packed,long size,boolean solid,byte[] a,byte[] b){
        h.write(6);h.uint64(position);h.uint64(1);h.write(9);h.uint64(packed);h.write(0);h.write(7);h.write(11);h.uint64(1);h.write(0);h.uint64(1);h.write(0x21);h.write(0x21);h.uint64(1);h.write(16);h.write(12);h.uint64(size);h.write(0);
        if(solid){h.write(8);h.write(13);h.uint64(2);h.write(9);h.uint64(a.length);h.write(10);h.write(1);h.le(Crc32.of(a),4);h.le(Crc32.of(b),4);h.write(0);}h.write(0);
    }
    @Test void invalidDecoderInputIsRejected()throws Exception{
        assertThrows(IOException.class,()->new DeflateInputStream(new ByteArrayInputStream(new byte[]{7})).readAllBytes()); // reserved block
        assertThrows(IOException.class,()->new DeflateInputStream(new ByteArrayInputStream(new byte[]{1,3,0,0,0,1,2,3})).readAllBytes()); // bad stored length complement
        assertThrows(IOException.class,()->new LzmaInputStream(new ByteArrayInputStream(new byte[]{2,0,0,1,0}),16).readAllBytes()); // missing dictionary reset
        assertThrows(IOException.class,()->new LzmaInputStream(new ByteArrayInputStream(new byte[]{(byte)0xe0,0,0,0,0,93,0}),16).readAllBytes()); // truncated range init
        assertThrows(IOException.class,()->new LzmaInputStream(new ByteArrayInputStream(new byte[]{0}),40)); // dictionary cap
        byte[] packed=lzma2(sample(50000,3),new LZMA2Options(5));assertThrows(IOException.class,()->new LzmaInputStream(new ByteArrayInputStream(Arrays.copyOf(packed,packed.length-1)),22).readAllBytes());
    }
    @Test void corruptionAndCancellationRollBackCreatedFiles()throws Exception{
        byte[] data=sample(190000,123);Path archive=temp.resolve("crc.zip");try(ZipWriter writer=new ZipWriter(archive,0,null)){try(OutputStream out=writer.begin("data.bin",false,data.length)){out.write(data);}writer.finish();}
        try(RandomAccessFile f=new RandomAccessFile(archive.toFile(),"rw")){f.seek(40);f.write(f.read()^8);}
        Path dest=temp.resolve("bad");assertThrows(IOException.class,()->new ArchiveEngine().extract(archive,dest,null,null,null));assertFalse(Files.exists(dest));
        Path solid=solidArchive(data,data);AtomicBoolean stop=new AtomicBoolean();Path cancelled=temp.resolve("cancelled");assertThrows(InterruptedIOException.class,()->new ArchiveEngine().extract(solid,cancelled,null,stop::get,(done,total,name)->stop.set(true)));assertFalse(Files.exists(cancelled));
    }
    @Test void independentSevenZAesDecryptsAndRejectsWrongPassword()throws Exception{
        Path archive=temp.resolve("encrypted.7z");try(SevenZOutputFile out=new SevenZOutputFile(archive.toFile(),"pw".toCharArray())){SevenZArchiveEntry e=new SevenZArchiveEntry();e.setName("data");out.putArchiveEntry(e);out.write(new byte[]{1,2,3});out.closeArchiveEntry();}
        Path good=temp.resolve("aes7z");new ArchiveEngine().extract(archive,good,"pw".toCharArray(),null,null);assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(good.resolve("data")));
        assertThrows(IOException.class,()->new ArchiveEngine().extract(archive,temp.resolve("wrong7z"),"bad".toCharArray(),null,null));assertFalse(Files.exists(temp.resolve("wrong7z")));
    }
    @Test void commentsAndExistingOutputProtectionWithoutLibraries()throws Exception{
        Path archive=temp.resolve("comments.zip");try(ZipWriter out=new ZipWriter(archive,0,null)){try(OutputStream e=out.begin("data",false,3)){e.write(new byte[]{1,2,3});}out.finish();}
        ZipComments.write(archive,"中文註解");assertEquals("中文註解",ZipComments.read(archive));Path dest=Files.createDirectory(temp.resolve("keep"));Files.writeString(dest.resolve("data"),"keep");assertThrows(FileAlreadyExistsException.class,()->new ArchiveEngine().extract(archive,dest,null,null,null));assertEquals("keep",Files.readString(dest.resolve("data")));
    }
    @Test void realReferenceArchive747ArchivesDecodeWithoutReferenceArchiveInstalled()throws Exception{
        for(String suffix:new String[]{"zip","7z","aes.7z"}){
            String fixture=suffix.equals("aes.7z")?"referenceArchive-7.47-aes.7z":"referenceArchive-7.47."+suffix;
            Path out=temp.resolve("referenceArchive-"+suffix);new ArchiveEngine().extract(Path.of("tests/fixtures/"+fixture),out,suffix.equals("aes.7z")?"onezip-fixture".toCharArray():null,null,null);
            try(var paths=Files.walk(out)){List<Path> files=paths.filter(Files::isRegularFile).toList();assertEquals(3,files.size());for(Path p:files){String name=p.getFileName().toString();String expected=name.equals("alpha.txt")?"eb995a418d062e17fb5b40151ac69f98b37d7e8455254d27ee2f60db7256d532":name.equals("beta.txt")?"59cd0a5ca5302a44e0543a7849304e22cc78ce6c06fb55768016d66d35a6ffe2":null;if(expected!=null)assertEquals(expected,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))));else assertEquals(0,Files.size(p));}}
        }
    }
    @Test void zipAesAuthenticationTamperingRollsBackOutput()throws Exception{
        byte[] input=sample(30000,6);Path archive=temp.resolve("tampered.zip");try(ZipWriter w=new ZipWriter(archive,5,"pw".toCharArray())){try(OutputStream out=w.begin("data",false,input.length)){out.write(input);}w.finish();}
        try(ZipArchiveReader r=new ZipArchiveReader(archive,"pw".toCharArray(),null)){assertArrayEquals(input,r.open(r.entries().getFirst()).readAllBytes());}
        byte[] bytes=Files.readAllBytes(archive);int central=-1;for(int i=0;i<bytes.length-4;i++)if(bytes[i]==0x50&&bytes[i+1]==0x4b&&bytes[i+2]==1&&bytes[i+3]==2){central=i;break;}assertTrue(central>30);bytes[central-17]^=1;Files.write(archive,bytes);
        Path out=temp.resolve("tampered");assertThrows(IOException.class,()->new ArchiveEngine().extract(archive,out,"pw".toCharArray(),null,null));assertFalse(Files.exists(out));
    }
    @Test void unsupportedMethodsAndOversizedKdfFailExplicitly()throws Exception{
        Path archive=temp.resolve("bzip.7z");try(SevenZOutputFile out=new SevenZOutputFile(archive.toFile())){out.setContentCompression(SevenZMethod.BZIP2);SevenZArchiveEntry e=new SevenZArchiveEntry();e.setName("file");out.putArchiveEntry(e);out.write(new byte[]{1,2,3});out.closeArchiveEntry();}
        IOException error=assertThrows(IOException.class,()->new ArchiveEngine().extract(archive,temp.resolve("unsupported"),null,null,null));assertTrue(error.getMessage().contains("coder"));assertFalse(Files.exists(temp.resolve("unsupported")));
        assertThrows(IOException.class,()->SevenZAesInputStream.shape(new byte[]{25}));
    }
}
