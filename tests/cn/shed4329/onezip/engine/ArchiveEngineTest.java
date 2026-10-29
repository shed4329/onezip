package cn.shed4329.onezip.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class ArchiveEngineTest {
    @TempDir Path temp;
    final ArchiveEngine engine=new ArchiveEngine();
    @Test void multipleFoldersUnicodeAndEmptyFilesRoundTrip()throws Exception{
        Path a=Files.createDirectories(temp.resolve("來源一/空資料夾"));
        Path text=Files.writeString(a.getParent().resolve("內容.txt"),"OneZip中文資料".repeat(30000));
        Path b=Files.createDirectory(temp.resolve("來源二"));Files.createFile(b.resolve("empty.bin"));
        byte[] bytes=new byte[90000];new Random(64).nextBytes(bytes);Files.write(b.resolve("random.bin"),bytes);
        for(ArchiveEngine.Format format:List.of(ArchiveEngine.Format.ZIP,ArchiveEngine.Format.SEVEN_Z,ArchiveEngine.Format.TAR,ArchiveEngine.Format.TAR_GZIP)){
            Path archive=temp.resolve("result"+switch(format){case ZIP->".zip";case SEVEN_Z->".7z";case TAR->".tar";case TAR_GZIP->".tar.gz";default->throw new AssertionError();});
            List<Long> progress=new ArrayList<>();
            engine.compress(List.of(a.getParent(),b),archive,format,5,null,()->false,(done,total,name)->progress.add(done));
            Path extracted=temp.resolve("out-"+format);engine.extract(archive,extracted,null,()->false,null);
            assertEquals(Files.readString(text),Files.readString(extracted.resolve("來源一/內容.txt")));
            assertTrue(Files.isDirectory(extracted.resolve("來源一/空資料夾")));
            assertEquals(0,Files.size(extracted.resolve("來源二/empty.bin")));
            assertArrayEquals(bytes,Files.readAllBytes(extracted.resolve("來源二/random.bin")));
            for(int i=1;i<progress.size();++i)assertTrue(progress.get(i)>=progress.get(i-1));
        }
    }
    @Test void existingOutputAndInputFolderAreProtected()throws Exception{
        Path input=Files.writeString(temp.resolve("source.txt"),"original");
        Path output=Files.writeString(temp.resolve("keep.7z"),"do not replace");
        assertThrows(FileAlreadyExistsException.class,()->engine.compress(List.of(input),output,ArchiveEngine.Format.SEVEN_Z,5,null,null,null));
        assertEquals("do not replace",Files.readString(output));
        assertThrows(IOException.class,()->engine.compress(List.of(temp),temp.resolve("inside.7z"),ArchiveEngine.Format.SEVEN_Z,5,null,null,null));
    }
    @Test void cancellingLeavesNoFinalArchiveOrStagingFiles()throws Exception{
        Path input=Files.write(temp.resolve("large.bin"),new byte[700000]);Path out=temp.resolve("cancel.7z");AtomicBoolean stop=new AtomicBoolean();
        assertThrows(InterruptedIOException.class,()->engine.compress(List.of(input),out,ArchiveEngine.Format.SEVEN_Z,5,null,stop::get,(done,total,name)->{if(done>0)stop.set(true);}));
        assertFalse(Files.exists(out));
        try(var files=Files.list(temp)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".onezip-")));}
    }
    @Test void traversalIsRejectedAndPreviouslyExistingFilesSurvive()throws Exception{
        Path zip=temp.resolve("bad.zip");
        try(java.util.zip.ZipOutputStream writer=new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))){
            writer.putNextEntry(new java.util.zip.ZipEntry("../escape.txt"));writer.write(1);writer.closeEntry();
        }
        Path out=Files.createDirectory(temp.resolve("extract"));Path keep=Files.writeString(out.resolve("keep.txt"),"keep");
        assertThrows(IOException.class,()->engine.extract(zip,out,null,null,null));
        assertFalse(Files.exists(temp.resolve("escape.txt")));assertEquals("keep",Files.readString(keep));
    }
    @Test void encryptedZipRoundTripAndWrongPasswordDoNotLeaveOutput()throws Exception{
        Path input=Files.writeString(temp.resolve("secret.txt"),"secret data".repeat(2000));Path archive=temp.resolve("aes.zip");
        engine.compress(List.of(input),archive,ArchiveEngine.Format.ZIP,5,"密碼".toCharArray(),null,null);
        Path out=temp.resolve("correct");engine.extract(archive,out,"密碼".toCharArray(),null,null);assertEquals(Files.readString(input),Files.readString(out.resolve("secret.txt")));
        Path wrong=temp.resolve("wrong");assertThrows(IOException.class,()->engine.extract(archive,wrong,"bad".toCharArray(),null,null));assertFalse(Files.exists(wrong));
    }
    @Test void cachedPreflightDoesNotSkipParentChecksAtWriteTime()throws Exception{
        Path archive=temp.resolve("changed-parent.zip");
        try(java.util.zip.ZipOutputStream writer=new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))){
            for(String name:List.of("first.txt","existing/second.txt")){writer.putNextEntry(new java.util.zip.ZipEntry(name));writer.write("data".getBytes());writer.closeEntry();}
        }
        Path root=Files.createDirectory(temp.resolve("out")),parent=Files.createDirectory(root.resolve("existing"));AtomicBoolean changed=new AtomicBoolean();
        assertThrows(IOException.class,()->engine.extract(archive,root,null,null,(done,total,name)->{
            if(name.equals("first.txt")&&changed.compareAndSet(false,true))try{Files.delete(parent);Files.writeString(parent,"changed after preflight");}catch(IOException ex){throw new UncheckedIOException(ex);}
        }));
        assertTrue(changed.get());assertFalse(Files.exists(root.resolve("first.txt")));assertEquals("changed after preflight",Files.readString(parent));
    }
}
