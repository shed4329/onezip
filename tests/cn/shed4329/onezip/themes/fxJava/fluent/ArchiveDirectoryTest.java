package cn.shed4329.onezip.themes.fxJava.fluent;

import cn.shed4329.onezip.engine.ArchiveEngine.ArchiveItem;
import cn.shed4329.onezip.themes.fxJava.fluent.component.ArchiveDirectory;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class ArchiveDirectoryTest {
    @Test void infersParentsAndMergesExplicitFoldersInAnyOrder() {
        var file=new ArchiveItem("a/b/file.txt",false,42);
        var directory=new ArchiveDirectory(List.of(file,new ArchiveItem("a/",true,0),new ArchiveItem("a/b/",true,0),new ArchiveItem("empty/",true,0)),()->false);
        assertEquals(2,directory.root().children().size());
        var a=directory.folder(directory.root().children().getFirst());
        assertEquals("a",a.label());assertSame(directory.root(),a.parent());assertEquals(1,a.children().size());
        var b=directory.folder(a.children().getFirst());assertEquals(List.of(file),b.children());
        assertTrue(directory.folder(directory.root().children().getLast()).children().isEmpty());
        assertThrows(UnsupportedOperationException.class,()->a.children().clear());
    }
    @Test void preservesDuplicateFilesAndFileFolderNameCollisions() {
        var file=new ArchiveItem("same",false,1);var duplicate=new ArchiveItem("same",false,2);
        var directory=new ArchiveDirectory(List.of(file,duplicate,new ArchiveItem("same/child",false,3)),()->false);
        assertEquals(3,directory.root().children().size());
        assertNotNull(directory.folder(directory.root().children().getFirst()));
        assertNull(directory.folder(file));assertNull(directory.folder(duplicate));
        assertEquals(List.of(file,duplicate),directory.root().children().subList(1,3));
    }
    @Test void keepsUnusualNamesInsideVirtualTree() {
        var directory=new ArchiveDirectory(List.of(new ArchiveItem("../資料/UPPER.TXT",false,8),new ArchiveItem("dir.with.dot/no-extension",false,0)),()->false);
        var parent=directory.folder(directory.root().children().getFirst());
        assertEquals("..",parent.label());assertSame(directory.root(),parent.parent());
        assertEquals("UPPER.TXT",ArchiveDirectory.fileName(directory.folder(parent.children().getFirst()).children().getFirst()));
    }
    @Test void emptyArchivesAndCancellation() {
        assertTrue(new ArchiveDirectory(List.of(),()->false).root().children().isEmpty());
        assertThrows(CancellationException.class,()->new ArchiveDirectory(List.of(new ArchiveItem("x",false,0)),()->true));
    }
}
