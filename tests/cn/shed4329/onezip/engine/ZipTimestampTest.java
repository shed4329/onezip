package cn.shed4329.onezip.engine;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ZipTimestampTest {
    @TempDir Path root;

    @Test void preservesSourceTimesInSequentialParallelAndAesArchives()throws Exception{
        Path folder=Files.createDirectory(root.resolve("source"));
        Path small=Files.writeString(folder.resolve("small.txt"),"text content");
        Path empty=Files.createFile(folder.resolve("empty.txt"));
        Path large=Files.write(folder.resolve("large.bin"),new byte[5*1024*1024]);
        Path subfolder=Files.createDirectory(folder.resolve("subfolder"));
        FileTime modified=FileTime.from(Instant.parse("2024-02-29T01:23:45.123456Z"));
        for(Path path:List.of(small,empty,large,subfolder,folder))Files.setLastModifiedTime(path,modified);
        var engine=new ArchiveEngine();
        for(int workers:new int[]{1,4})for(boolean aes:new boolean[]{false,true}){
            char[] password=aes?"fixture".toCharArray():null;
            Path zip=root.resolve("archive-"+workers+"-"+aes+".zip");
            engine.compress(List.of(folder),zip,ArchiveEngine.Format.ZIP,5,password,null,null,workers);
            if(aes){
                try(var independent=org.apache.commons.compress.archivers.zip.ZipFile.builder().setPath(zip).get()){
                    for(var entry:Collections.list(independent.getEntries()))assertEquals(modified,entry.getLastModifiedTime(),entry.getName());
                }
            }else try(var independent=new ZipFile(zip.toFile())){
                for(var entry:independent.stream().toList())assertEquals(modified,entry.getLastModifiedTime(),entry.getName());
            }
            for(var detail:engine.listDetails(zip,password,null,java.nio.charset.StandardCharsets.UTF_8))
                assertEquals(modified.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),detail.modified());
            Path decoded=root.resolve("decoded-"+workers+"-"+aes);
            engine.extract(zip,decoded,password,null,null,workers);
            for(Path original:List.of(small,empty,large,subfolder,folder)){
                Path target=decoded.resolve("source").resolve(folder.relativize(original));
                assertEquals(Files.getLastModifiedTime(original),Files.getLastModifiedTime(target),target.toString());
            }
        }
    }

    @Test void zipAdditionCapturesNewTimeAndRetainsUntouchedTime()throws Exception{
        Path old=Files.writeString(root.resolve("old.txt"),"old");
        FileTime a=FileTime.from(Instant.parse("2001-01-01T01:02:03Z"));
        FileTime b=FileTime.from(Instant.parse("2025-06-07T08:09:11.654321Z"));
        Files.setLastModifiedTime(old,a);
        Path archive=root.resolve("edited.zip");
        new ArchiveEngine().compress(List.of(old),archive,ArchiveEngine.Format.ZIP,5,null,null,null);
        Path added=Files.writeString(root.resolve("added.txt"),"new");Files.setLastModifiedTime(added,b);
        ZipEditor.update(archive,List.of(added),"",List.of(),java.nio.charset.StandardCharsets.UTF_8,null);
        try(var zip=new ZipFile(archive.toFile())){
            assertEquals(a,zip.getEntry("old.txt").getLastModifiedTime());
            assertEquals(b,zip.getEntry("added.txt").getLastModifiedTime());
        }
    }

    @Test void utcExtraSurvivesTimezoneChangesAndDosRangeLimits()throws Exception{
        TimeZone previous=TimeZone.getDefault();
        try{
            Path input=Files.writeString(root.resolve("clock.txt"),"clock");
            for(String iso:List.of("1975-01-02T03:04:05Z","2026-10-29T01:23:45.123456Z","2150-01-02T03:04:05Z")){
                FileTime modified=FileTime.from(Instant.parse(iso));Files.setLastModifiedTime(input,modified);
                Path archive=root.resolve("clock-"+iso.substring(0,4)+".zip");
                TimeZone.setDefault(TimeZone.getTimeZone("Asia/Taipei"));
                new ArchiveEngine().compress(List.of(input),archive,ArchiveEngine.Format.ZIP,0,null,null,null);
                TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
                try(var reader=new ZipArchiveReader(archive,null,null);var oracle=new ZipFile(archive.toFile())){
                    assertEquals(modified,reader.modifiedTime(reader.entries().getFirst()));
                    assertEquals(modified,oracle.getEntry("clock.txt").getLastModifiedTime());
                }
            }
        }finally{TimeZone.setDefault(previous);}
    }
}
