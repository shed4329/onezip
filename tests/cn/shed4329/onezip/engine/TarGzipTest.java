package cn.shed4329.onezip.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.commons.compress.archivers.tar.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class TarGzipTest {
    @TempDir Path temp;
    final ArchiveEngine engine=new ArchiveEngine();
    @Test void absentParentPreflightCacheNeverBypassesWriteTimeCollisionChecks()throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(var tar=new TarArchiveOutputStream(bytes)){for(String name:List.of("first.txt","late/child.txt")){TarArchiveEntry e=new TarArchiveEntry(name);e.setSize(1);tar.putArchiveEntry(e);tar.write(1);tar.closeArchiveEntry();}}
        for(String suffix:List.of(".tar",".tar.gz")){
            Path archive=Files.write(temp.resolve("race"+suffix),suffix.equals(".tar")?bytes.toByteArray():gzip(bytes.toByteArray()));Path out=Files.createDirectory(temp.resolve("out"+suffix));AtomicBoolean changed=new AtomicBoolean();
            assertThrows(IOException.class,()->engine.extract(archive,out,null,null,(done,total,name)->{if(done>0&&changed.compareAndSet(false,true))try{Files.createDirectory(out.resolve("late"));Files.writeString(out.resolve("late/child.txt"),"created after preflight");}catch(IOException e){throw new UncheckedIOException(e);}},1));
            assertTrue(changed.get());assertFalse(Files.exists(out.resolve("first.txt")));assertEquals("created after preflight",Files.readString(out.resolve("late/child.txt")));
        }
    }
    private static byte[] payload(){byte[] b=new byte[500000];new Random(2468).nextBytes(b);Arrays.fill(b,1000,200000,(byte)'a');return b;}
    private static byte[] gzip(byte[] data)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();try(var gz=new GZIPOutputStream(out)){gz.write(data);}return out.toByteArray();}
    private static byte[] tar(String name,byte[] data,int type)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();try(var out=new TarArchiveOutputStream(b,"UTF-8")){out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);TarArchiveEntry e=new TarArchiveEntry(name,(byte)type);if(type=='0')e.setSize(data.length);if(type=='2')e.setLinkName("../outside");out.putArchiveEntry(e);if(type=='0')out.write(data);out.closeArchiveEntry();}return b.toByteArray();
    }
    @Test void ownArchivesInteroperateWithIndependentReadersAtAllLevelsAndWorkerSettings()throws Exception{
        Path folder=Files.createDirectory(temp.resolve("資料"));Files.createDirectory(folder.resolve("empty"));Files.write(folder.resolve("empty.txt"),new byte[0]);byte[] data=payload();String longName="長檔名".repeat(40)+".bin";Files.write(folder.resolve(longName),data);
        for(var format:List.of(ArchiveEngine.Format.TAR,ArchiveEngine.Format.TAR_GZIP))for(int threads:List.of(1,4)){
            Path archive=temp.resolve(format+"-"+threads+(format==ArchiveEngine.Format.TAR?".tar":".tgz"));engine.compress(List.of(folder),archive,format,5,null,null,null,threads);
            Set<String> names=new HashSet<>();try(InputStream raw=Files.newInputStream(archive);InputStream body=format==ArchiveEngine.Format.TAR_GZIP?new GZIPInputStream(raw):raw;TarArchiveInputStream in=new TarArchiveInputStream(body,"UTF-8")){
                TarArchiveEntry e;while((e=in.getNextEntry())!=null){names.add(e.getName());if(e.getName().endsWith(longName))assertArrayEquals(data,in.readAllBytes());}
            }assertTrue(names.contains("資料/"+longName));assertTrue(names.contains("資料/empty/"));
            Path out=temp.resolve(format+"-"+threads+"-out");engine.extract(archive,out,null,null,null,threads);assertArrayEquals(data,Files.readAllBytes(out.resolve("資料/"+longName)));engine.test(archive,null,null,null);
        }
        Path input=Files.write(temp.resolve("data.bin"),data);
        for(int level:new int[]{0,1,5,9}){
            Path archive=temp.resolve("data-"+level+".gzip");engine.compress(List.of(input),archive,ArchiveEngine.Format.GZIP,level,null,null,null,4);
            try(var in=new GZIPInputStream(Files.newInputStream(archive))){assertArrayEquals(data,in.readAllBytes());}
            Path out=temp.resolve("gz-"+level);engine.extract(archive,out,null,null,null);assertArrayEquals(data,Files.readAllBytes(out.resolve("data.bin")));
        }
    }
    @Test void parallelGzipAndTarGzipProduceValidHistoryAcrossFragmentBoundaries()throws Exception{
        byte[] data=new byte[5*1024*1024+73];for(int i=0;i<data.length;i++)data[i]=(byte)(i%103);Path input=Files.write(temp.resolve("parallel.bin"),data);
        for(var format:List.of(ArchiveEngine.Format.GZIP,ArchiveEngine.Format.TAR_GZIP)){
            Path archive=temp.resolve(format==ArchiveEngine.Format.GZIP?"parallel.gz":"parallel.tar.gz");engine.compress(List.of(input),archive,format,3,null,null,null,4);
            try(var gz=new GZIPInputStream(Files.newInputStream(archive))){if(format==ArchiveEngine.Format.GZIP)assertArrayEquals(data,gz.readAllBytes());else try(var tar=new TarArchiveInputStream(gz)){assertNotNull(tar.getNextEntry());assertArrayEquals(data,tar.readAllBytes());}}
            Path out=temp.resolve(format+"-parallel-out");engine.extract(archive,out,null,null,null,4);assertArrayEquals(data,Files.readAllBytes(out.resolve("parallel.bin")));
        }
    }
    @Test void readsExternalPaxGnuUstarPrefixAndV7Archives()throws Exception{
        byte[] data=payload();String name="nested/"+"long/".repeat(40)+"世界.txt";
        for(int mode:new int[]{TarArchiveOutputStream.LONGFILE_POSIX,TarArchiveOutputStream.LONGFILE_GNU}){
            ByteArrayOutputStream b=new ByteArrayOutputStream();try(var writer=new TarArchiveOutputStream(b,"UTF-8")){writer.setLongFileMode(mode);writer.setAddPaxHeadersForNonAsciiNames(true);TarArchiveEntry e=new TarArchiveEntry(name);e.setSize(data.length);writer.putArchiveEntry(e);writer.write(data);writer.closeArchiveEntry();}
            Path archive=Files.write(temp.resolve("external-"+mode+".tar.gz"),gzip(b.toByteArray()));Path out=temp.resolve("external-"+mode);engine.extract(archive,out,null,null,null,4);assertArrayEquals(data,Files.readAllBytes(out.resolve(name)));assertEquals(data.length,engine.list(archive,null,null).getFirst().size());
        }
        byte[] ustar=tar("prefix/".repeat(20)+"file.txt",data,'0');Path archive=Files.write(temp.resolve("ustar.tar"),ustar);engine.test(archive,null,null,null);
        byte[] v7=tar("old.txt",data,'0');Arrays.fill(v7,257,512,(byte)0);checksum(v7);Path old=Files.write(temp.resolve("old.tar"),v7);engine.extract(old,temp.resolve("v7"),null,null,null);assertArrayEquals(data,Files.readAllBytes(temp.resolve("v7/old.txt")));
    }
    @Test void gzipConcatenationOptionalHeadersAndEmptyMembersAreValidated()throws Exception{
        byte[] data=payload(),plain=gzip(data);ByteArrayOutputStream b=new ByteArrayOutputStream();
        byte[] header={31,(byte)139,8,30,0,0,0,0,0,3,3,0,1,2,3};b.write(header);b.write("dir/test.bin\0comment\0".getBytes(StandardCharsets.ISO_8859_1));java.util.zip.CRC32 hc=new java.util.zip.CRC32();hc.update(b.toByteArray());b.write((int)hc.getValue()&255);b.write((int)(hc.getValue()>>>8)&255);int headerSize=b.size();b.write(plain,10,plain.length-10);b.write(gzip(new byte[0]));b.write(gzip(new byte[]{7,8}));byte[] wrapped=b.toByteArray();
        Path archive=Files.write(temp.resolve("concat.gz"),wrapped);Path out=temp.resolve("concat");engine.extract(archive,out,null,null,null);byte[] expected=Arrays.copyOf(data,data.length+2);expected[data.length]=7;expected[data.length+1]=8;assertArrayEquals(expected,Files.readAllBytes(out.resolve("test.bin")));assertEquals(expected.length,engine.list(archive,null,null).getFirst().size());
        for(int offset:new int[]{3,headerSize-1,wrapped.length-8,wrapped.length-4}){byte[] bad=wrapped.clone();bad[offset]^=(byte)128;Path broken=Files.write(temp.resolve("bad-"+offset+".gz"),bad);assertThrows(IOException.class,()->engine.test(broken,null,null,null));}
        Files.write(temp.resolve("junk.gz"),Arrays.copyOf(wrapped,wrapped.length+1));assertThrows(IOException.class,()->engine.test(temp.resolve("junk.gz"),null,null,null));
    }
    @Test void rejectsTruncationBadTarChecksumsLinksTraversalAndDuplicatesWithoutOverwriting()throws Exception{
        byte[] data=payload(),gz=gzip(data),validTar=tar("data.bin",data,'0');
        for(String suffix:List.of(".gz",".tar")){byte[] valid=suffix.equals(".gz")?gz:validTar;
            for(int length:new int[]{0,1,valid.length-1}){Path a=Files.write(temp.resolve("trunc-"+length+suffix),Arrays.copyOf(valid,length));assertThrows(IOException.class,()->engine.extract(a,temp.resolve("out-"+suffix+length),null,null,null));assertFalse(Files.exists(temp.resolve("out-"+suffix+length)));}}
        byte[] bad=validTar.clone();bad[0]^=1;Path checksum=Files.write(temp.resolve("checksum.tar"),bad);assertThrows(IOException.class,()->engine.test(checksum,null,null,null));
        for(var entry:Map.of("escape",tar("../escape",new byte[]{1},'0'),"link",tar("link",new byte[0],'2'),"device",tar("device",new byte[0],'3')).entrySet()){
            Path archive=Files.write(temp.resolve(entry.getKey()+".tar"),entry.getValue());Path out=temp.resolve("bad-"+entry.getKey());assertThrows(IOException.class,()->engine.extract(archive,out,null,null,null,4));assertFalse(Files.exists(out));}
        Path source=Files.write(temp.resolve("keep.txt"),data),archive=temp.resolve("keep.tar");engine.compress(List.of(source),archive,ArchiveEngine.Format.TAR,5,null,null,null);Path out=Files.createDirectory(temp.resolve("keep"));Files.writeString(out.resolve("keep.txt"),"existing");assertThrows(IOException.class,()->engine.extract(archive,out,null,null,null));assertEquals("existing",Files.readString(out.resolve("keep.txt")));
        ByteArrayOutputStream duplicate=new ByteArrayOutputStream();try(var writer=new TarArchiveOutputStream(duplicate)){for(int i=0;i<2;i++){TarArchiveEntry e=new TarArchiveEntry("same");e.setSize(1);writer.putArchiveEntry(e);writer.write(i);writer.closeArchiveEntry();}}
        Path dup=Files.write(temp.resolve("dup.tar.gz"),gzip(duplicate.toByteArray()));assertThrows(IOException.class,()->engine.extract(dup,temp.resolve("dup"),null,null,null,4));assertFalse(Files.exists(temp.resolve("dup")));
    }
    @Test void formatConstraintsAndCancellationPreserveAtomicOutput()throws Exception{
        Path folder=Files.createDirectory(temp.resolve("folder")),input=Files.write(folder.resolve("source.bin"),payload());
        for(var format:List.of(ArchiveEngine.Format.TAR,ArchiveEngine.Format.GZIP,ArchiveEngine.Format.TAR_GZIP)){
            Path output=temp.resolve(format+".archive");assertThrows(IOException.class,()->engine.compress(List.of(input),output,format,5,"secret".toCharArray(),null,null));assertFalse(Files.exists(output));
            AtomicBoolean stop=new AtomicBoolean();assertThrows(InterruptedIOException.class,()->engine.compress(List.of(input),output,format,5,null,stop::get,(done,total,name)->{if(done>0)stop.set(true);},4));assertFalse(Files.exists(output));
        }
        assertThrows(IOException.class,()->engine.compress(List.of(folder),temp.resolve("directory.gz"),ArchiveEngine.Format.GZIP,5,null,null,null));
        Path archive=temp.resolve("cancel.tar.gz");engine.compress(List.of(input),archive,ArchiveEngine.Format.TAR_GZIP,5,null,null,null);
        AtomicBoolean stop=new AtomicBoolean();Path out=temp.resolve("cancel-out");assertThrows(InterruptedIOException.class,()->engine.extract(archive,out,null,stop::get,(done,total,name)->stop.set(true),1));assertFalse(Files.exists(out));
        try(var files=Files.list(temp)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".onezip-")));}
    }
    private static void checksum(byte[] h){Arrays.fill(h,148,156,(byte)32);long n=0;for(int i=0;i<512;i++)n+=h[i]&255;byte[] s=String.format(Locale.ROOT,"%06o\0 ",n).getBytes(StandardCharsets.US_ASCII);System.arraycopy(s,0,h,148,8);}
}
