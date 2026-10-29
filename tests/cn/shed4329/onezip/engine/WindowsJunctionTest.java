package cn.shed4329.onezip.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Windows junctions are reparse points, but BasicFileAttributes.isSymbolicLink() is false. */
@EnabledOnOs(OS.WINDOWS)
class WindowsJunctionTest {
    @TempDir Path root;

    void junction(Path link,Path target)throws Exception{
        // Use only controlled temporary paths, never arbitrary shell text or environment expansion.
        for(Path path:List.of(link,target))assertFalse(path.toString().matches(".*[%!&|<>^\"\\r\\n].*"),"Unsupported shell metacharacter in test temp path");
        var process=new ProcessBuilder(Path.of(System.getenv("SystemRoot"),"System32/cmd.exe").toString(),"/d","/c","mklink","/J",link.toString(),target.toString()).redirectErrorStream(true).start();
        process.getOutputStream().close();
        if(!process.waitFor(10,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Junction fixture timed out");}
        assertEquals(0,process.exitValue(),new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
        var attrs=Files.readAttributes(link,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        assertTrue(attrs.isDirectory());assertTrue(attrs.isOther());assertFalse(attrs.isSymbolicLink());
    }

    @Test void existingJunctionCannotRedirectExtraction()throws Exception{
        Path outside=Files.createDirectory(root.resolve("outside"));
        Path keep=Files.writeString(outside.resolve("keep.txt"),"preserve");
        Path destination=Files.createDirectory(root.resolve("restored"));
        Path link=destination.resolve("link");junction(link,outside);
        try{
            Path archive=root.resolve("attack.zip");
            try(var zip=new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))){
                zip.putNextEntry(new java.util.zip.ZipEntry("link/escape.txt"));zip.write(new byte[]{1,2,3});zip.closeEntry();
            }
            for(int threads:new int[]{1,4}){
                assertThrows(IOException.class,()->new ArchiveEngine().extract(archive,destination,null,null,null,threads));
                assertFalse(Files.exists(outside.resolve("escape.txt")));assertEquals("preserve",Files.readString(keep));
            }
        }finally{Files.deleteIfExists(link);}
    }

    @Test void sourceJunctionsAreRefusedDuringCreateAndEdit()throws Exception{
        Path outside=Files.createDirectory(root.resolve("outside"));Files.writeString(outside.resolve("secret.txt"),"not selected");
        Path link=root.resolve("link");junction(link,outside);
        try{
            Path output=root.resolve("new.zip");
            assertThrows(IOException.class,()->new ArchiveEngine().compress(List.of(link),output,ArchiveEngine.Format.ZIP,5,null,null,null));
            assertFalse(Files.exists(output));
            Path input=Files.writeString(root.resolve("plain.txt"),"original");
            new ArchiveEngine().compress(List.of(input),output,ArchiveEngine.Format.ZIP,5,null,null,null);
            byte[] before=Files.readAllBytes(output);
            assertThrows(IOException.class,()->ZipEditor.update(output,List.of(link),"",List.of(),StandardCharsets.UTF_8,null));
            assertArrayEquals(before,Files.readAllBytes(output));
        }finally{Files.deleteIfExists(link);}
    }
}
