package cn.shed4329.onezip.engine;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RarReadTest {
    @TempDir Path temp;
    static final Path FIX=Path.of("tests/fixtures/rar");
    @Test void independentRar5Fixtures()throws Exception{
        for(String name:new String[]{"stored","compressed","multiple_files","multiple_files_solid","solid","arm","win32","blake2"}){
            try(var reader=new RarArchiveReader(FIX.resolve("test_read_format_rar5_"+name+".rar"),null,null)){
                assertFalse(reader.entries().isEmpty());for(var entry:reader.entries())try(InputStream in=reader.open(entry)){assertEquals(entry.size(),in.transferTo(OutputStream.nullOutputStream()),name+":"+entry.name());}
            }catch(IOException e){throw new IOException(name+": "+e.getMessage(),e);}
        }
    }
    @Test void independentRar4Fixtures()throws Exception{
        for(String name:new String[]{"test_read_format_rar.safe.rar","test_read_format_rar_windows.rar","test_read_format_rar_compress_normal.safe.rar","test_read_format_rar_multi_lzss_blocks.rar","test_read_format_rar3_lowdist_reset.rar","test_read_format_rar_filter.rar","test_read_format_rar_unicode.safe.rar"}){
            try(var reader=new RarArchiveReader(FIX.resolve(name),null,null)){
                assertFalse(reader.entries().isEmpty());for(var entry:reader.entries())try(InputStream in=reader.open(entry)){assertEquals(entry.size(),in.transferTo(OutputStream.nullOutputStream()),name+":"+entry.name());}
            }catch(IOException e){throw new IOException(name+": "+e.getMessage(),e);}
        }
    }
    @Test void otherProducerFixtures()throws Exception{
        try(var paths=Files.list(FIX)){for(Path path:paths.filter(p->p.getFileName().toString().startsWith("junrar-")&&p.toString().endsWith(".rar")&&!p.toString().contains("audio-")&&!p.toString().contains("rar7-")&&!p.toString().contains("password-")&&!p.toString().contains("-enc-")&&!p.toString().contains("blake2-")).sorted().toList()){
            try(var reader=new RarArchiveReader(path,null,null)){
                for(var e:reader.entries())assertEquals(e.size(),reader.open(e).transferTo(OutputStream.nullOutputStream()));
            }catch(IOException e){throw new IOException(path.getFileName()+": "+e.getMessage(),e);}
        }}
    }
    static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    static Map<String,Map<String,String>> expected()throws Exception{
        Map<String,Map<String,String>> map=new LinkedHashMap<>();for(String line:Files.readAllLines(FIX.resolve("expected.tsv"))){String[] parts=line.split("\t");map.computeIfAbsent(parts[0],ignored->new TreeMap<>()).put(parts[1],parts[2]);}return map;
    }
    @Test void allDecodedFilesMatchIndependentReferenceArchiveSha256()throws Exception{
        for(var archive:expected().entrySet())try(var reader=new RarArchiveReader(FIX.resolve(archive.getKey()),null,null)){
            Map<String,String> actual=new TreeMap<>();for(var e:reader.entries()){try(var in=reader.open(e)){byte[] b=in.readAllBytes();if(!e.directory())actual.put(e.name().replace('\\','/'),sha(b));}}
            assertEquals(archive.getValue(),actual,archive.getKey());
        }
    }
    @Test void solidRandomAccessReplaysPrefixAndForksRemainIndependent()throws Exception{
        Path archive=FIX.resolve("test_read_format_rar5_multiple_files_solid.rar");
        try(var reader=new RarArchiveReader(archive,null,null);var fork=reader.fork()){
            assertEquals(1,reader.groups().size());var entries=reader.entries();var last=entries.get(3);
            byte[] expected=fork.open(last).readAllBytes();assertArrayEquals(expected,reader.open(last).readAllBytes());
            for(int i=3;i>=0;i--){var e=entries.get(i);assertArrayEquals(fork.open(e).readAllBytes(),reader.open(e).readAllBytes());}
            reader.open(entries.get(0)).readNBytes(17);assertArrayEquals(expected,reader.open(last).readAllBytes());
        }
    }
    @Test void engineParallelAndSolidExtractionMatchIndependentHashes()throws Exception{
        var oracle=expected();ArchiveEngine engine=new ArchiveEngine();
        for(String name:new String[]{"test_read_format_rar5_multiple_files.rar","test_read_format_rar5_multiple_files_solid.rar","junrar-solid-rar4-solid.rar","test_read_format_rar_filter.rar"}){
            Set<String> threads=new HashSet<>();Path out=temp.resolve(name);engine.extract(FIX.resolve(name),out,null,null,(d,t,n)->threads.add(Thread.currentThread().getName()),4);
            Map<String,String> actual=new TreeMap<>();try(var paths=Files.walk(out)){for(Path p:paths.filter(Files::isRegularFile).toList())actual.put(out.relativize(p).toString().replace('\\','/'),sha(Files.readAllBytes(p)));}assertEquals(oracle.get(name),actual);
            if(name.equals("test_read_format_rar5_multiple_files.rar"))assertTrue(threads.stream().filter(t->t.startsWith("onezip-extract-")).count()>1);
            engine.test(FIX.resolve(name),null,null,null);assertFalse(engine.list(FIX.resolve(name),null,null).isEmpty());
        }
    }
    @Test void blake2spMatchesPythonHashlibVectorsAcrossStripingBoundaries()throws Exception{
        for(String line:Files.readAllLines(FIX.resolve("blake2sp.tsv"))){String[] p=line.split("\t");int size=Integer.parseInt(p[0]);byte[] data=new byte[size];for(int i=0;i<size;i++)data[i]=(byte)(i%251);
            RarBlake2sp hash=new RarBlake2sp();for(int i=0;i<size;){int n=Math.min(73,size-i);hash.update(data,i,n);i+=n;}assertEquals(p[1],HexFormat.of().formatHex(hash.finish()));
        }
    }
    @Test void unsupportedVariantsFailClearlyWithoutNativeFallback()throws Exception{
        for(String name:new String[]{"test_read_format_rar5_unicode.rar","test_read_format_rar.rar"})assertThrows(IOException.class,()->new RarArchiveReader(FIX.resolve(name),null,null),name);
        for(String name:new String[]{"junrar-rar7-rar7-md6g.rar","test_read_format_rar4_encrypted.rar","test_read_format_rar5_encrypted.rar"}){
            try(var reader=new RarArchiveReader(FIX.resolve(name),null,null)){assertThrows(IOException.class,()->{for(var e:reader.entries())reader.open(e).transferTo(OutputStream.nullOutputStream());},name);}
        }
    }
    @Test void malformedRegressionInputsAndTruncationAreRejected()throws Exception{
        for(String name:new String[]{"test_read_format_rar5_different_window_size.rar","test_read_format_rar5_different_solid_window_size.rar"})assertThrows(IOException.class,()->new RarArchiveReader(FIX.resolve(name),null,null));
        byte[] original=Files.readAllBytes(FIX.resolve("test_read_format_rar5_compressed.rar"));
        for(int n:new int[]{0,6,7,8,15,original.length-1}){Path file=temp.resolve("truncated"+n+".rar");Files.write(file,Arrays.copyOf(original,n));assertThrows(IOException.class,()->new ArchiveEngine().test(file,null,null,null));}
        byte[] crc=original.clone();crc[12]^=1;Path bad=temp.resolve("crc.rar");Files.write(bad,crc);assertThrows(IOException.class,()->new ArchiveEngine().list(bad,null,null));
    }
    @Test void corruptStoredPayloadAndCancellationRollBackAllOutput()throws Exception{
        Path original=FIX.resolve("test_read_format_rar5_stored.rar"),bad=temp.resolve("bad.rar");byte[] bytes=Files.readAllBytes(original);long offset;
        try(var reader=new RarArchiveReader(original,null,null);var in=reader.open(reader.entries().getFirst())){byte[] payload=in.readAllBytes();offset=-1;outer:for(int i=0;i<=bytes.length-payload.length;i++){for(int j=0;j<payload.length;j++)if(bytes[i+j]!=payload[j])continue outer;offset=i;break;}assertTrue(offset>=0);}
        bytes[(int)offset]^=1;Files.write(bad,bytes);Path out=temp.resolve("bad-out");assertThrows(IOException.class,()->new ArchiveEngine().extract(bad,out,null,null,null,4));assertFalse(Files.exists(out));
        AtomicBoolean stop=new AtomicBoolean();Path cancelled=temp.resolve("cancelled");assertThrows(InterruptedIOException.class,()->new ArchiveEngine().extract(FIX.resolve("junrar-rar5unpack-m3-plain-32m.rar"),cancelled,null,stop::get,(d,t,n)->{if(d>0)stop.set(true);},1));assertFalse(Files.exists(cancelled));
    }
    @Test void unsafeRarPathsCannotEscapeAndExistingFilesRemainIntact()throws Exception{
        byte[] bytes=Files.readAllBytes(FIX.resolve("test_read_format_rar.safe.rar"));
        int p=7;while((bytes[p+2]&255)!=0x74)p+=(bytes[p+5]&255)|((bytes[p+6]&255)<<8);
        int n=(bytes[p+5]&255)|((bytes[p+6]&255)<<8),nameLength=(bytes[p+26]&255)|((bytes[p+27]&255)<<8);assertEquals(8,nameLength);
        System.arraycopy("../x.txt".getBytes(java.nio.charset.StandardCharsets.UTF_8),0,bytes,p+32,8);long crc=Crc32.of(Arrays.copyOfRange(bytes,p+2,p+n));bytes[p]=(byte)crc;bytes[p+1]=(byte)(crc>>>8);
        Path archive=temp.resolve("unsafe.rar"),out=temp.resolve("out");Files.write(archive,bytes);assertThrows(IOException.class,()->new ArchiveEngine().extract(archive,out,null,null,null,4));assertFalse(Files.exists(temp.resolve("x.txt")));assertFalse(Files.exists(out));
        Files.createDirectory(out);Path keep=Files.writeString(out.resolve("test1.bin"),"preserve");assertThrows(IOException.class,()->new ArchiveEngine().extract(FIX.resolve("test_read_format_rar5_multiple_files.rar"),out,null,null,null,4));assertEquals("preserve",Files.readString(keep));try(var paths=Files.list(out)){assertEquals(1,paths.count());}
    }
    @Test void blakeHashFailureIsAnErrorAtEof()throws Exception{
        try(var stream=RarBlake2sp.verified(new ByteArrayInputStream(new byte[]{1,2,3}),new byte[32])){assertThrows(IOException.class,()->stream.transferTo(OutputStream.nullOutputStream()));}
    }
}
