package cn.shed4329.onezip.engine;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ExtractionLayoutTest {
    private final Path base=Path.of("target/smart-test/output").toAbsolutePath().normalize();
    private ArchiveEngine.ArchiveItem file(String name){return new ArchiveEngine.ArchiveItem(name,false,1);}
    private Path choose(String name,ArchiveEngine.ArchiveItem...entries)throws IOException{return ExtractionLayout.choose(Path.of(name),base,List.of(entries),()->false);}
    @Test void singleFileDoesNotAddFolder()throws Exception{assertEquals(base,choose("one.zip",file("readme.txt")));}
    @Test void sharedRootIncludingImplicitAndDotPrefixedFoldersDoesNotNest()throws Exception{assertEquals(base,choose("folder.zip",file("project/a.txt"),file("project/docs/b.txt")));assertEquals(base,choose("folder.tar",file("./project/a"),new ArchiveEngine.ArchiveItem("./project/",true,0)));}
    @Test void multipleRootsUseArchiveName()throws Exception{assertEquals(base.resolve("mixed"),choose("mixed.zip",file("a.txt"),file("b.txt")));assertEquals(base.resolve("mixed"),choose("mixed.tar.gz",file("one/a"),file("two/b")));assertEquals(base.resolve("mixed"),choose("mixed.tar.gzip",file("a"),file("b")));}
    @Test void emptyArchivesAndUnusableBasenamesHaveSafeContainer()throws Exception{assertEquals(base.resolve("empty"),choose("empty.zip"));assertEquals("Archive",ExtractionLayout.folderName(Path.of(".zip")));}
    @Test void unsafePathsAreRejectedBeforeChoosingDestination(){for(String name:List.of("../escape","root/../../escape","/absolute","C:/absolute","\\\\server\\file"))assertThrows(IOException.class,()->choose("bad.zip",file(name)),name);}
    @Test void layoutIsCancellable(){assertThrows(java.util.concurrent.CancellationException.class,()->ExtractionLayout.choose(Path.of("a.zip"),base,List.of(file("one")),()->true));}
}
