package cn.shed4329.onezip.engine;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class RarNewTest {
    @TempDir Path temp;
    static final Path ROOT=Path.of("tests/fixtures/rar");
    @Test void ppm()throws Exception{for(String n:new String[]{"test_read_format_rar_compress_best.safe.rar","test_read_format_rar_ppmd_lzss_conversion.rar","junrar-audio-BoatModernEnglish-audio-text-unpack30.rar","junrar-ppm-preserved-maxmb0.rar","junrar-ppm-legit-maxmb63.rar"}){
        try(var r=new RarArchiveReader(Path.of("tests/fixtures/rar",n),null,null)){for(var e:r.entries())assertEquals(e.size(),r.open(e).transferTo(OutputStream.nullOutputStream()));}catch(IOException e){throw new IOException(n,e);}
    }}
    @Test void crypt()throws Exception{for(String n:new String[]{"junrar-password-rar4-encrypted-junrar.rar","junrar-password-rar4-password-junrar.rar","junrar-password-rar5-encrypted-junrar.rar","junrar-password-rar5-password-junrar.rar","junrar-rar5unpack-m3-enc-p.rar","junrar-rar5unpack-m3-enc-hp.rar","junrar-blake2-rar5-htb-mac.rar","junrar-blake2-rar5-htb.rar"}){
        try(var r=new RarArchiveReader(Path.of("tests/fixtures/rar",n),"junrar".toCharArray(),null)){for(var e:r.entries())assertEquals(e.size(),r.open(e).transferTo(OutputStream.nullOutputStream()));}catch(IOException e){throw new IOException(n,e);}
    }}
    @Test void newPathsMatchIndependentReferenceArchiveOutput()throws Exception{
        Map<String,Map<String,String>> expected=new LinkedHashMap<>();Map<String,String> passwords=new HashMap<>();
        for(String line:Files.readAllLines(ROOT.resolve("new-expected.tsv"))){String[] p=line.split("\t",-1);passwords.put(p[0],p[1]);expected.computeIfAbsent(p[0],ignored->new TreeMap<>()).put(p[2],p[3]);}
        for(var item:expected.entrySet())try(var reader=new RarArchiveReader(ROOT.resolve(item.getKey()),passwords.get(item.getKey()).toCharArray(),null)){
            Map<String,String> actual=new TreeMap<>();for(var e:reader.entries())try(var in=reader.open(e)){MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var checked=new java.security.DigestInputStream(in,digest)){checked.transferTo(OutputStream.nullOutputStream());}if(!e.directory())actual.put(e.name().replace('\\','/'),HexFormat.of().formatHex(digest.digest()));}assertEquals(item.getValue(),actual,item.getKey());
        }
    }
    @Test void encryptedHeadersNeedPasswordButDataOnlyArchivesCanBeListed()throws Exception{
        for(String n:new String[]{"junrar-password-rar4-encrypted-junrar.rar","junrar-password-rar5-encrypted-junrar.rar"}){
            for(char[] password:new char[][]{null,"wrong".toCharArray()})assertThrows(IOException.class,()->{try(var r=new RarArchiveReader(ROOT.resolve(n),password,null)){for(var e:r.entries())r.open(e).transferTo(OutputStream.nullOutputStream());}});
        }
        for(String n:new String[]{"junrar-password-rar4-password-junrar.rar","junrar-password-rar5-password-junrar.rar"}){
            assertEquals(1,new ArchiveEngine().list(ROOT.resolve(n),null,null).size());for(char[] password:new char[][]{null,"wrong".toCharArray()})assertThrows(IOException.class,()->new ArchiveEngine().test(ROOT.resolve(n),password,null,null));
        }
    }
    @Test void correctPasswordsAreCopiedAndWipedWithoutChangingCaller()throws Exception{
        char[] pw="junrar".toCharArray();var reader=new RarArchiveReader(ROOT.resolve("junrar-password-rar5-encrypted-junrar.rar"),pw,null);try(var fork=reader.fork()){reader.close();for(var e:fork.entries())assertEquals(e.size(),fork.open(e).transferTo(OutputStream.nullOutputStream()));}assertArrayEquals("junrar".toCharArray(),pw);
    }
    @Test void encryptedStoredDataAndCbcTruncationCannotSilentlySucceed()throws Exception{
        Path src=ROOT.resolve("junrar-password-rar5-password-junrar.rar");byte[] b=Files.readAllBytes(src);Path truncated=temp.resolve("truncated.rar");Files.write(truncated,Arrays.copyOf(b,b.length-11));assertThrows(IOException.class,()->new ArchiveEngine().test(truncated,"junrar".toCharArray(),null,null));
        var info=new RarCrypto.Info(true,new byte[16],new byte[16],0,null,false);var key=RarCrypto.derive(info,new char[0],null);
        try(var stream=new RarCrypto.Cbc(new ByteArrayInputStream(new byte[15]),key,new byte[16],false)){assertThrows(IOException.class,stream::read);}
    }
    @Test void kdfCancellationAndWorkLimitsFailBeforeExtraction()throws Exception{
        assertThrows(IOException.class,()->RarCrypto.derive(new RarCrypto.Info(true,new byte[16],new byte[16],25,null,false),new char[0],null));
        assertThrows(InterruptedIOException.class,()->RarCrypto.derive(new RarCrypto.Info(true,new byte[16],new byte[16],16,null,false),new char[0],()->true));
        assertThrows(InterruptedIOException.class,()->RarCrypto.derive(new RarCrypto.Info(false,new byte[8],null,0,null,false),new char[0],()->true));
    }
    @Test @Timeout(15) void damagedPpmRangeStreamsFailOrRetainExactVerifiedOutput()throws Exception{
        byte[] original=Files.readAllBytes(ROOT.resolve("junrar-ppm-preserved-maxmb0.rar"));int p=7;while((original[p+2]&255)!=0x74)p+=(original[p+5]&255)|((original[p+6]&255)<<8);p+=(original[p+5]&255)|((original[p+6]&255)<<8);Random random=new Random(773);
        for(int i=0;i<24;i++){byte[] b=original.clone();b[p+8+random.nextInt(Math.min(400,b.length-p-20))]^=(byte)(1<<random.nextInt(8));Path path=temp.resolve("mutant"+i+".rar");Files.write(path,b);try{new ArchiveEngine().test(path,null,null,null);}catch(IOException expected){/* All untrusted model/range errors must surface as checked failures. */}}
    }
    @Test void wrongPasswordsAndCorruptCiphertextRollBackAndPreserveExistingFiles()throws Exception{
        ArchiveEngine engine=new ArchiveEngine();for(String n:new String[]{"junrar-password-rar4-encrypted-junrar.rar","junrar-password-rar4-password-junrar.rar","junrar-rar5unpack-m3-enc-p.rar","junrar-rar5unpack-m3-enc-hp.rar"}){
            Path out=temp.resolve(n);assertThrows(IOException.class,()->engine.extract(ROOT.resolve(n),out,"wrong".toCharArray(),null,null,4));assertFalse(Files.exists(out));
        }
        Path source=ROOT.resolve("junrar-rar5unpack-m3-enc-p.rar");byte[] b=Files.readAllBytes(source);int[] cursor={8};while(cursor[0]<b.length){cursor[0]+=4;int size=(int)number(b,cursor);int end=cursor[0]+size;long type=number(b,cursor),flags=number(b,cursor);if((flags&1)!=0)number(b,cursor);long packed=(flags&2)!=0?number(b,cursor):0;if(type==2){assertTrue(packed>32);b[end+16]^=1;break;}cursor[0]=end+(int)packed;}
        Path corrupt=temp.resolve("corrupt.rar"),out=Files.createDirectory(temp.resolve("existing"));Files.write(corrupt,b);Path keep=Files.writeString(out.resolve("keep"),"preserved");assertThrows(IOException.class,()->engine.extract(corrupt,out,"junrar".toCharArray(),null,null,4));assertEquals("preserved",Files.readString(keep));try(var paths=Files.list(out)){assertEquals(1,paths.count());}
    }
    static long number(byte[] b,int[] p){long v=0;for(int i=0;;i++){int n=b[p[0]++]&255;v|=(long)(n&127)<<(7*i);if(n<128)return v;}}
    @Test void cancellingPpmExtractionRemovesAllNewFiles()throws Exception{
        AtomicBoolean cancelled=new AtomicBoolean();Path out=temp.resolve("cancelled-ppm");assertThrows(InterruptedIOException.class,()->new ArchiveEngine().extract(ROOT.resolve("test_read_format_rar_ppmd_lzss_conversion.rar"),out,null,cancelled::get,(d,t,n)->{if(d>0)cancelled.set(true);},4));assertFalse(Files.exists(out));
    }
    @Test void encryptedSolidArchivesRetainIndependentForkAndReplayState()throws Exception{
        for(String n:new String[]{"crypto-test-rar4-solid-headers.rar","crypto-test-rar5-solid-data.rar","crypto-test-rar5-solid-headers.rar"}){
            char[] pw=(n.contains("headers")?"пароль密码ü":"rar-secret").toCharArray();try(var reader=new RarArchiveReader(ROOT.resolve(n),pw,null);var fork=reader.fork()){
                var entries=reader.entries().stream().filter(e->!e.directory()).toList();for(int i=entries.size()-1;i>=0;i--){var e=entries.get(i);assertArrayEquals(fork.open(e).readAllBytes(),reader.open(e).readAllBytes(),n);}
                reader.open(entries.getFirst()).read();var last=entries.getLast();assertArrayEquals(fork.open(last).readAllBytes(),reader.open(last).readAllBytes());
            }
            Path out=temp.resolve(n);new ArchiveEngine().extract(ROOT.resolve(n),out,pw,null,null,4);assertTrue(Files.isDirectory(out));
        }
    }
}
