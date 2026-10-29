package cn.shed4329.onezip.engine;

import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SevenZHeaderTest {
    @TempDir Path temp;
    @Test void encodedHeaderCompressesNamesAndSubstreamsWithoutChangingContents()throws Exception{
        Map<String,byte[]> expected=new LinkedHashMap<>();expected.put("資料/",null);
        for(int i=0;i<160;i++)expected.put("資料/shared long path/document-"+i+".txt",("entry "+i+" common text\n").repeat(20).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        expected.put("資料/empty.txt",new byte[0]);expected.put("資料/空目錄/",null);
        Path compressed=write("compressed",5,expected),stored=write("stored",0,expected);
        assertEquals(23,headerType(compressed));assertEquals(1,headerType(stored));
        verify(compressed,expected);verify(stored,expected);
        // The encoded descriptor itself should stay tiny even with many names.
        assertTrue(headerSize(compressed)<100);assertTrue(headerSize(stored)>10000);
    }
    @Test void metadataOnlyArchiveHasAnIndependentEncodedHeaderStream()throws Exception{
        Map<String,byte[]> expected=new LinkedHashMap<>();
        for(int i=0;i<120;i++){expected.put("folder-"+i+"/",null);expected.put("folder-"+i+"/empty document.txt",new byte[0]);}
        Path archive=write("metadata-only",5,expected);assertEquals(23,headerType(archive));verify(archive,expected);
    }
    @Test void tinyHeadersKeepTheDirectHeaderAndCorruptMetadataIsRejected()throws Exception{
        Map<String,byte[]> tiny=new LinkedHashMap<>();tiny.put("a",new byte[]{1,2,3});
        Path archive=write("tiny",5,tiny);assertEquals(1,headerType(archive));verify(archive,tiny);
        Map<String,byte[]> many=new LinkedHashMap<>();
        for(int i=0;i<100;i++)many.put("directory with long common name/"+i+".txt",new byte[0]);
        archive=write("corrupt",5,many);assertEquals(23,headerType(archive));
        byte[] bytes=Files.readAllBytes(archive);bytes[35]^=0x42;Files.write(archive,bytes);
        Path bad=archive;assertThrows(IOException.class,()->new SevenZArchiveReader(bad,null,null));
        assertThrows(IOException.class,()->SevenZFile.builder().setPath(bad).get());
    }
    private Path write(String name,int level,Map<String,byte[]> contents)throws Exception{
        Path archive=temp.resolve(name+".7z");
        try(SevenZWriter writer=new SevenZWriter(archive,level)){
            for(var e:contents.entrySet())try(OutputStream out=writer.begin(e.getKey(),e.getValue()==null,e.getValue()==null?0:e.getValue().length)){
                if(e.getValue()!=null)out.write(e.getValue());
            }
            writer.finish();
        }
        return archive;
    }
    private void verify(Path archive,Map<String,byte[]> expected)throws Exception{
        try(SevenZArchiveReader reader=new SevenZArchiveReader(archive,null,null)){
            assertEquals(expected.size(),reader.entries().size());
            for(var entry:reader.entries())assertArrayEquals(expected.get(entry.name())==null?new byte[0]:expected.get(entry.name()),reader.open(entry).readAllBytes());
        }
        try(SevenZFile reader=SevenZFile.builder().setPath(archive).get()){
            int count=0;byte[] buffer=new byte[4096];
            for(var e=reader.getNextEntry();e!=null;e=reader.getNextEntry()){
                assertTrue(expected.containsKey(e.getName()));ByteArrayOutputStream bytes=new ByteArrayOutputStream();int n;
                while((n=reader.read(buffer))!=-1)bytes.write(buffer,0,n);
                assertArrayEquals(expected.get(e.getName())==null?new byte[0]:expected.get(e.getName()),bytes.toByteArray());count++;
            }
            assertEquals(expected.size(),count);
        }
    }
    private static long headerSize(Path path)throws IOException{return ByteBuffer.wrap(Files.readAllBytes(path),20,8).order(ByteOrder.LITTLE_ENDIAN).getLong();}
    private static int headerType(Path path)throws IOException{
        byte[] b=Files.readAllBytes(path);long offset=ByteBuffer.wrap(b,12,8).order(ByteOrder.LITTLE_ENDIAN).getLong();return b[Math.toIntExact(32+offset)]&255;
    }
}
