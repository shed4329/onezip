package cn.shed4329.onezip.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class CommandLineTest {
    @Test void xzBzip2AndTarAliasesUseJavaArgsForEveryOperation()throws Exception{
        Path source=Files.writeString(temp.resolve("input.txt"),"XZ BZIP2 自製串流\n".repeat(3000));
        for(String suffix:List.of("xz","bz2","bzip2","tar.xz","txz","tar.bz2","tar.bzip2","tbz","tbz2")){
            String format=switch(suffix){case "txz"->"tar.xz";case "bz2"->"bzip2";case "tbz","tbz2","tar.bzip2"->"tar.bz2";default->suffix;};Path archive=temp.resolve("input.txt."+suffix),out=temp.resolve("out-"+suffix);
            Result c=run("c",archive.toString(),source.toString(),"--format",format,"--level","1","--threads=1","-q");assertEquals(0,c.code,c.err);
            Result l=run("l",archive.toString());assertEquals(0,l.code,l.err);assertTrue(l.out.contains("input.txt"));Result t=run("t",archive.toString(),"-q");assertEquals(0,t.code,t.err);Result x=run("x",archive.toString(),"-o",out.toString(),"--threads","4","-q");assertEquals(0,x.code,x.err);assertEquals(Files.readString(source),Files.readString(out.resolve("input.txt")));
        }
        assertEquals(2,run("c",temp.resolve("mismatch.xz").toString(),source.toString(),"--format","bzip2").code);
        assertEquals(1,run("c",temp.resolve("secret.bz2").toString(),source.toString(),"-p:secret").code);
    }
    @Test void lz4AndTarLz4UseJavaArgsAndStandardFrameNames()throws Exception{
        Path source=Files.writeString(temp.resolve("input.txt"),"LZ4 自製 frame\n".repeat(5000));
        for(String format:List.of("lz4","tar.lz4")){
            Path archive=temp.resolve("input.txt."+format),out=temp.resolve("out-"+format);Result c=run("c",archive.toString(),source.toString(),"--format",format,"--threads","1","-q");assertEquals(0,c.code,c.err);
            assertEquals(0,run("t",archive.toString(),"-q").code);assertEquals(0,run("l",archive.toString()).code);Result x=run("x",archive.toString(),"-o",out.toString(),"--threads","4","-q");assertEquals(0,x.code,x.err);assertEquals(Files.readString(source),Files.readString(out.resolve("input.txt")));
        }
    }
    @Test void tarGzipCommandsUseJavaArgsForAllAliases()throws Exception{
        Path source=Files.writeString(temp.resolve("input.txt"),"tar gzip CLI\n".repeat(2000));
        for(String suffix:List.of("tar","gz","gzip","tar.gz","tgz","tar.gzip")){
            Path archive=temp.resolve("cli."+suffix),out=temp.resolve("out-"+suffix);
            String format=suffix.equals("tgz")||suffix.equals("tar.gzip")?"tar.gz":suffix;
            Result c=run("c",archive.toString(),source.toString(),"--format",format,"--level","3","--threads","4","-q");assertEquals(0,c.code,c.err);
            assertEquals(0,run("l",archive.toString()).code);Result t=run("t",archive.toString(),"-q");assertEquals(0,t.code,t.err);
            Result x=run("x",archive.toString(),"-o",out.toString(),"--threads=4","-q");assertEquals(0,x.code,x.err);assertEquals(Files.readString(source),Files.readString(out.resolve("input.txt")));
        }
        assertEquals(2,run("c",temp.resolve("bad.tar").toString(),source.toString(),"--format","gzip").code);
        assertEquals(1,run("c",temp.resolve("password.gz").toString(),source.toString(),"-p:secret").code);
    }
    @Test void encryptedRarCommandsUseExistingPasswordArguments()throws Exception{
        for(String name:new String[]{"junrar-password-rar4-encrypted-junrar.rar","junrar-password-rar5-encrypted-junrar.rar","junrar-rar5unpack-m3-enc-p.rar"}){
            String file=Path.of("tests/fixtures/rar",name).toString();Result list=run("l",file,"-p:junrar");assertEquals(0,list.code,list.err);
            Result test=run("t",file,"--password-env","RAR_TEST_PASSWORD");assertEquals(2,test.code);
            Result env=run(Map.of("RAR_TEST_PASSWORD","junrar"),()->null,"t",file,"--password-env","RAR_TEST_PASSWORD","-q");assertEquals(0,env.code,env.err);
            Result extract=run("x",file,"-p:junrar","-o",temp.resolve(name).toString(),"--threads","4","-q");assertEquals(0,extract.code,extract.err);
            Result bad=run("t",file,"-p:private-wrong-password");assertEquals(1,bad.code);assertFalse((bad.out+bad.err).contains("private-wrong-password"));
        }
        Result ppm=run("t","tests/fixtures/rar/test_read_format_rar_compress_best.safe.rar","-q");assertEquals(0,ppm.code,ppm.err);
    }
    @Test void rarAndRar5CommandsUseJavaArgsAndIndependentReader()throws Exception{
        for(String name:new String[]{"test_read_format_rar_filter.rar","test_read_format_rar5_multiple_files_solid.rar"}){
            Path archive=Path.of("tests/fixtures/rar",name),output=temp.resolve(name);
            Result listed=run("l",archive.toString());assertEquals(0,listed.code,listed.err);assertTrue(listed.out.contains("Name"));
            Result tested=run("t",archive.toString(),"-q");assertEquals(0,tested.code,tested.err);
            Result extracted=run("x",archive.toString(),"-o",output.toString(),"--threads","4","-q");assertEquals(0,extracted.code,extracted.err);assertTrue(Files.isDirectory(output));
        }
        Result unsupported=run("t","tests/fixtures/rar/test_read_format_rar5_encrypted.rar","-p:secret");assertEquals(1,unsupported.code);assertFalse(unsupported.err.contains("secret"));
    }
    @TempDir Path temp;
    private record Result(int code,String out,String err) { }
    private Result run(String... args){return run(Map.of(),()->{throw new AssertionError("Unexpected interactive prompt");},args);}
    private Result run(Map<String,String> env,CommandLine.PasswordPrompt prompt,String... args){
        ByteArrayOutputStream out=new ByteArrayOutputStream(),err=new ByteArrayOutputStream();
        int code=CommandLine.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8),env,prompt);
        return new Result(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    @Test void createListTestExtractForBothFormatsAndAliases()throws Exception{
        Path folder=Files.createDirectories(temp.resolve("中文 source/empty folder"));Path input=Files.writeString(folder.getParent().resolve("hello 世界.txt"),"CLI 獨立核心\n".repeat(5000));Files.createFile(folder.getParent().resolve("empty.txt"));
        for(String format:new String[]{"zip","7z"}){
            Path archive=temp.resolve("archive with spaces."+format),out=temp.resolve("解壓 "+format);
            Result create=run(format.equals("zip")?"a":"compress",archive.toString(),folder.getParent().toString(),"--format",format,"--level=3","-q");assertEquals(0,create.code,create.err);assertEquals("",create.out);assertEquals("",create.err);
            Result list=run("l",archive.toString());assertEquals(0,list.code,list.err);assertTrue(list.out.startsWith("Type\tSize\tName"));assertTrue(list.out.contains("hello 世界.txt"));assertTrue(list.out.contains("D\t0\t"));assertEquals("",list.err);
            Result test=run("test",archive.toString());assertEquals(0,test.code,test.err);assertTrue(test.out.startsWith("OK:"));assertTrue(test.err.contains("100.0%"));
            Result extract=run("x",archive.toString(),"-o:"+out,"--quiet");assertEquals(0,extract.code,extract.err);assertEquals(Files.readString(input),Files.readString(out.resolve("中文 source/hello 世界.txt")));assertTrue(Files.isDirectory(out.resolve("中文 source/empty folder")));assertEquals(0,Files.size(out.resolve("中文 source/empty.txt")));
            assertEquals(1,run("c",archive.toString(),input.toString()).code);assertEquals(1,run("x",archive.toString(),"-o",out.toString()).code);assertEquals(Files.readString(input),Files.readString(out.resolve("中文 source/hello 世界.txt")));
        }
    }
    @Test void argumentEnvironmentAndHiddenPasswordsNeverEchoAndPromptBufferIsWiped()throws Exception{
        Path input=Files.writeString(temp.resolve("secret.txt"),"secret content".repeat(100)),archive=temp.resolve("secret.zip");String secret="private中文42";
        Result create=run(Map.of("ONEZIP_SECRET",secret),()->null,"c",archive.toString(),input.toString(),"--password-env","ONEZIP_SECRET");assertEquals(0,create.code,create.err);assertFalse((create.out+create.err).contains(secret));
        char[] entered=secret.toCharArray();Result test=run(Map.of(),()->entered,"t",archive.toString(),"--ask-password","-q");assertEquals(0,test.code,test.err);for(char c:entered)assertEquals('\0',c);assertEquals("",test.out+test.err);
        Result extract=run("extract",archive.toString(),"--output",temp.resolve("out").toString(),"--password="+secret);assertEquals(0,extract.code,extract.err);assertFalse((extract.out+extract.err).contains(secret));assertEquals(Files.readString(input),Files.readString(temp.resolve("out/secret.txt")));
        Result bad=run("t",archive.toString(),"-p:wrong-password");assertEquals(1,bad.code);assertFalse((bad.out+bad.err).contains("wrong-password"));assertFalse(bad.err.contains("Exception"));
        assertEquals(2,run("t",archive.toString(),"--password-env","MISSING").code);
        Path emptyPassword=temp.resolve("empty-password.zip");assertEquals(2,run("c",emptyPassword.toString(),input.toString(),"-p:").code);assertFalse(Files.exists(emptyPassword));
    }
    @Test void encryptedSolidReferenceArchiveFixtureCanBeListedAndTestedWithoutExtraction()throws Exception{
        Path archive=Path.of("tests/fixtures/referenceArchive-7.47-aes.7z").toAbsolutePath();
        Set<Path> before;try(var files=Files.list(temp)){before=new HashSet<>(files.toList());}
        Result test=run("t",archive.toString(),"-p:onezip-fixture");assertEquals(0,test.code,test.err);
        Result list=run("list",archive.toString(),"--password","onezip-fixture");assertEquals(0,list.code,list.err);assertTrue(list.out.contains("alpha.txt"));assertTrue(list.out.contains("beta.txt"));
        try(var files=Files.list(temp)){assertEquals(before,new HashSet<>(files.toList()));}
    }
    @Test void testReallyDecodesPayloadAndReportsCrcFailure()throws Exception{
        Path archive=temp.resolve("bad.zip");byte[] data="known contents".getBytes(StandardCharsets.UTF_8);CRC32 crc=new CRC32();crc.update(data);
        try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(archive))){ZipEntry e=new ZipEntry("bad");e.setMethod(ZipEntry.STORED);e.setSize(data.length);e.setCrc(crc.getValue());out.putNextEntry(e);out.write(data);out.closeEntry();}
        byte[] bytes=Files.readAllBytes(archive);bytes[34]^=1;Files.write(archive,bytes);
        assertEquals(0,run("l",archive.toString()).code,"Metadata listing does not validate the payload");
        Result result=run("t",archive.toString());assertEquals(1,result.code);assertEquals("",result.out);assertTrue(result.err.contains("CRC"));assertFalse(result.err.contains("100.0%"));
    }
    @Test void usageErrorsAreDeterministicAndDoNotWriteFiles()throws Exception{
        for(String[] args:new String[][]{
            {},{"bad"},{"c"},{"c","out.rar","input"},{"c","out.zip","input","-l","10"},
            {"c","out.zip","input","--format","7z"},{"c","out.zip","input","--format","rar"},
            {"t","x.zip","--level","5"},{"l","x.zip","-o","dest"},{"x","x.zip","--unknown"},
            {"x","x.zip","--output"},{"x","x.zip","--output","--quiet"},{"x","x.zip","--quiet=false"},
            {"c","out.zip","input","--level","1","-l:2"},{"t","x.zip","-p:one","--ask-password"},
            {"l","x.zip","extra"},{"--version","extra"}
            ,{"c","out.7z","input","-t:65"},{"c","out.7z","input","--threads=-1"},
            {"x","x.zip","--threads=65"},{"t","x.7z","--threads=2"},
            {"c","out.7z","input","-t:2","--threads=3"}
        }){Result result=run(args);assertEquals(2,result.code,Arrays.toString(args)+result.err);assertEquals("",result.out);assertTrue(result.err.startsWith("Usage error:"));assertFalse(result.err.contains("Exception"));}
        try(var paths=Files.list(temp)){assertEquals(0,paths.count());}
        assertEquals(1,run("t",temp.resolve("missing.zip").toString()).code);
        assertEquals(1,run("t",temp.toAbsolutePath().getRoot().toString()).code);
    }
    @Test void helpAndVersionWorkWithoutReadingFilesOrPasswords(){
        for(String[] args:new String[][]{{"--help"},{"help"},{"c","--help"},{"x","--help"}}){Result result=run(args);assertEquals(0,result.code);assertTrue(result.out.contains("Usage:"));assertEquals("",result.err);}
        assertEquals("OneZip "+CommandLine.VERSION+System.lineSeparator(),run("--version").out);
        assertEquals("line\\nname\\t\\u001b\\\\",CommandLine.escaped("line\nname\t\u001b\\"));
    }
    @Test void threadsAreParsedByJavaForSevenZCompression()throws Exception{
        Path input=Files.writeString(temp.resolve("input"),"shared text\n".repeat(1000)),archive=temp.resolve("parallel.7z");
        Result created=run("c",archive.toString(),input.toString(),"--threads=2","-q");assertEquals(0,created.code,created.err);
        assertEquals(0,run("t",archive.toString(),"-q").code);
        Result extracted=run("x",archive.toString(),"--threads","2","-o",temp.resolve("decoded-parallel").toString(),"-q");assertEquals(0,extracted.code,extracted.err);
        Path zip=temp.resolve("parallel.zip");Result zipped=run("c",zip.toString(),input.toString(),"-t:2","-q");assertEquals(0,zipped.code,zipped.err);
        Result unzipped=run("x",zip.toString(),"--threads=2","-o",temp.resolve("decoded-zip").toString(),"-q");assertEquals(0,unzipped.code,unzipped.err);
    }
    @Test void interruptedOperationReturns130AndCreatesNoOutput()throws Exception{
        Path input=Files.writeString(temp.resolve("source"),"source"),archive=temp.resolve("cancel.zip");
        try{Thread.currentThread().interrupt();Result result=run("c",archive.toString(),input.toString());assertEquals(130,result.code);assertFalse(Files.exists(archive));}
        finally{Thread.interrupted();}
    }
    @Test void plainJvmNeedsNoJavaFxClassesAndPreservesCallerDirectory()throws Exception{
        Files.writeString(temp.resolve("-dash 中文.txt"),"source content");
        assertEquals(0,process("--help").code);assertEquals(2,process("unknown-command").code);
        assertEquals(0,process("c","relative archive.zip","-q","--","-dash 中文.txt").code);
        assertTrue(Files.isRegularFile(temp.resolve("relative archive.zip")));
        assertEquals(0,process("x","relative archive.zip","-q").code);
        assertEquals("source content",Files.readString(temp.resolve("relative archive/-dash 中文.txt")));
        assertEquals(0,process("t","relative archive.zip","--quiet").code);
        assertEquals(1,process("t","missing.zip").code);
    }
    private Result process(String... args)throws Exception{
        List<String> command=new ArrayList<>();command.add(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString());command.add("-Djava.awt.headless=true");command.add("-cp");command.add(Path.of("target/classes").toAbsolutePath().toString());command.add("cn.shed4329.onezip.Launcher");command.add("--cli");command.addAll(List.of(args));
        Path log=Files.createTempFile(temp,"cli-",".txt");Process process=new ProcessBuilder(command).directory(temp.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{assertTrue(process.waitFor(30,TimeUnit.SECONDS),"CLI hung or tried to open the GUI");return new Result(process.exitValue(),Files.readString(log),"");}
        finally{process.destroyForcibly();}
    }
}
