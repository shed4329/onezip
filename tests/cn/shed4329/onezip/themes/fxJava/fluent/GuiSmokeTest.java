package cn.shed4329.onezip.themes.fxJava.fluent;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicitly invoked desktop smoke test, not part of the headless unit suite. */
class GuiSmokeTest {
    @SuppressWarnings("unchecked")
    private static TableView<cn.shed4329.onezip.engine.ArchiveEngine.ArchiveItem> zipTable(Stage stage){return (TableView<cn.shed4329.onezip.engine.ArchiveEngine.ArchiveItem>)stage.getScene().lookup("#zip-entry-table");}
    private static void enterFolder(Stage stage,String path){
        var table=zipTable(stage);table.getSelectionModel().select(table.getItems().stream().filter(i->i.directory()&&i.name().equals(path)).findFirst().orElseThrow());
        table.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,"","",javafx.scene.input.KeyCode.ENTER,false,false,false,false));
    }
    private static void clickRow(TableView<?> table,int index,boolean control,boolean shift){
        table.applyCss();table.layout();
        var row=(TableRow<?>)table.lookupAll(".table-row-cell").stream().filter(n->n instanceof TableRow<?> r&&r.getIndex()==index&&!r.isEmpty()).findFirst().orElseThrow();
        var cell=(TableCell<?,?>)row.lookupAll(".table-cell").stream().filter(n->n instanceof TableCell<?,?> c&&"archive-name".equals(c.getTableColumn().getId())).findFirst().orElseThrow();
        var point=cell.localToScene(5,5);var screen=cell.localToScreen(5,5);
        for(var type:List.of(javafx.scene.input.MouseEvent.MOUSE_PRESSED,javafx.scene.input.MouseEvent.MOUSE_RELEASED,javafx.scene.input.MouseEvent.MOUSE_CLICKED))
            cell.fireEvent(new javafx.scene.input.MouseEvent(type,point.getX(),point.getY(),screen.getX(),screen.getY(),javafx.scene.input.MouseButton.PRIMARY,1,shift,control,false,false,type==javafx.scene.input.MouseEvent.MOUSE_PRESSED,false,false,false,false,true,new javafx.scene.input.PickResult(cell,new javafx.geometry.Point3D(5,5,0),1)));
    }
    private static MenuItem archiveMenu(Stage stage,String id){return zipTable(stage).getContextMenu().getItems().stream().filter(item->id.equals(item.getId())).findFirst().orElseThrow();}
    private static String awaitZipPreview(Stage stage)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<deadline){String status=fx(()->stage.getScene().lookup("#zip-preview-progress").isVisible()?null:((Label)stage.getScene().lookup("#zip-preview-status")).getText());if(status!=null)return status;Thread.sleep(25);}
        throw new AssertionError("ZIP preview did not complete");
    }
    private static <T>T fx(Callable<T> operation)throws Exception{
        FutureTask<T> future=new FutureTask<>(operation);Platform.runLater(future);return future.get(20,TimeUnit.SECONDS);
    }
    @Test void existingGuiCompressesAndExtractsWithCustomCodecs()throws Exception{
        Path root=Path.of("target/gui-smoke").toAbsolutePath();Files.createDirectories(root);
        if(System.getProperty("onezip.settings.dir")==null)System.setProperty("onezip.settings.dir",root.resolve("settings").toString());
        if(System.getProperty("onezip.settings.legacy-dir")==null)System.setProperty("onezip.settings.legacy-dir",root.resolve("empty-legacy").toString());
        Path input=Files.writeString(root.resolve("GUI中文.txt"),"JavaFX 自行實作 LZMA2 整合測試\n".repeat(10000));
        Path archive=root.resolve("gui-result.7z");Files.deleteIfExists(archive);
        CountDownLatch started=new CountDownLatch(1);Platform.startup(()->{Platform.setImplicitExit(false);started.countDown();});assertTrue(started.await(15,TimeUnit.SECONDS));
        try{
            MainApplication[] appRef=new MainApplication[1];
            Stage main=fx(()->{
                MainApplication app=new MainApplication();Stage stage=new Stage();app.start(stage);
                appRef[0]=app;
                assertEquals(MainWindowModel.Page.HOME,app.controller().model().pageProperty().get());
                assertTrue(stage.getScene().lookup("#home-page").isVisible());assertNull(stage.getScene().lookup("#sidebar"));
                assertEquals(4,stage.getScene().getRoot().lookupAll(".home-tile").size());
                for(String id:List.of("home-compress","home-extract","home-settings","home-about")){
                    var tile=(Button)stage.getScene().lookup("#"+id);assertNotNull(tile.getOnAction());assertTrue(tile.getGraphic() instanceof javafx.scene.image.ImageView);assertFalse(((javafx.scene.image.ImageView)tile.getGraphic()).getImage().isError());
                }
                ImageIO.write(SwingFXUtils.fromFXImage(stage.getScene().snapshot(null),null),"png",root.resolve("home-page.png").toFile());
                ((Button)stage.getScene().lookup("#home-settings")).fire();
                assertTrue(Window.getWindows().stream().anyMatch(w->w instanceof Stage other&&other.getTitle().equals("设置")));
                for(Window window:new ArrayList<>(Window.getWindows()))if(window!=stage)window.hide();
                ((Button)stage.getScene().lookup("#home-about")).fire();
                var about=Window.getWindows().stream().filter(w->w instanceof Stage other&&other.getTitle().equals("关于 OneZip")).findFirst().orElseThrow();
                assertNotNull(about.getScene().lookup("#settings-about-onezip"));assertNull(about.getScene().lookup("#settings-background-path"));
                for(Window window:new ArrayList<>(Window.getWindows()))if(window!=stage)window.hide();
                ((Button)stage.getScene().lookup("#home-compress")).fire();
                assertEquals(MainWindowModel.Page.COMPRESS,app.controller().model().pageProperty().get());
                assertNull(stage.getScene().lookup("#sidebar"));
                assertNull(stage.getScene().lookup("#compression-home"));assertNull(stage.getScene().lookup("#compression-settings"));
                assertTrue(stage.getScene().getRoot().lookupAll(".button").stream().noneMatch(n->n instanceof Button b&&b.getText().isBlank()));
                app.controller().model().addSources(List.of(input));
                assertEquals(1,((TableView<?>)stage.getScene().lookup("#source-list")).getItems().size());
                assertEquals("已选择 1 个项目",((Label)stage.getScene().lookup("#sourceCount")).getText());
                cn.shed4329.onezip.themes.fxJava.fluent.component.ArchiveDialogs.showCompression(stage,List.of(input),ThemeManager.isDark());return stage;
            });
            fx(()->{
                Stage options=(Stage)Window.getWindows().stream().filter(w->w instanceof Stage s&&s.getTitle().equals("壓縮選項")).findFirst().orElseThrow();
                ((TextField)options.getScene().lookup("#archive-directory")).setText(root.toString());
                ((TextField)options.getScene().lookup("#archive-name")).setText("gui-result");
                @SuppressWarnings("unchecked") ChoiceBox<String> format=(ChoiceBox<String>)options.getScene().lookup("#archive-format");format.setValue("7z");
                @SuppressWarnings("unchecked") Spinner<Integer> threads=(Spinner<Integer>)options.getScene().lookup("#archive-threads");assertEquals(0,threads.getValue());threads.getValueFactory().setValue(2);
                ImageIO.write(SwingFXUtils.fromFXImage(options.getScene().snapshot(null),null),"png",root.resolve("compression-options.png").toFile());
                ImageIO.write(SwingFXUtils.fromFXImage(main.getScene().snapshot(null),null),"png",root.resolve("main-window.png").toFile());
                ((Button)options.getScene().lookup("#archive-start")).fire();return null;
            });
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(40);String status="";
            while(System.nanoTime()<deadline){
                status=fx(()->Window.getWindows().stream().filter(w->w.getScene()!=null&&w.getScene().lookup("#archive-status")!=null).map(w->((Label)w.getScene().lookup("#archive-status")).getText()).findFirst().orElse("missing"));
                if(status.equals("已完成")||status.equals("操作失敗"))break;
                Thread.sleep(100);
            }
            assertEquals("已完成",status);assertTrue(Files.size(archive)>32);
            Path decoded=root.resolve("decoded-"+System.nanoTime());
            fx(()->{for(Window window:new ArrayList<>(Window.getWindows()))if(window.getScene()!=null&&window.getScene().lookup("#archive-status")!=null)ImageIO.write(SwingFXUtils.fromFXImage(window.getScene().snapshot(null),null),"png",root.resolve("completed.png").toFile());return null;});
            Stage extraction=fx(()->{
                for(Window w:new ArrayList<>(Window.getWindows()))if(w!=main)w.hide();
                return cn.shed4329.onezip.themes.fxJava.fluent.component.ArchiveDialogs.showProgress(main,cn.shed4329.onezip.application.ArchiveTask.extract(archive,decoded,null,2),"自行實作解碼器測試",false);
            });
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(40);status="";
            while(System.nanoTime()<deadline){
                status=fx(()->((Label)extraction.getScene().lookup("#archive-status")).getText());
                if(status.equals("已完成")||status.equals("操作失敗"))break;
                Thread.sleep(100);
            }
            assertEquals("已完成",status);assertEquals(Files.readString(input),Files.readString(decoded.resolve("GUI中文.txt")));
            fx(()->{ImageIO.write(SwingFXUtils.fromFXImage(extraction.getScene().snapshot(null),null),"png",root.resolve("extraction-completed.png").toFile());return null;});
            Path rar=Path.of("tests/fixtures/rar/test_read_format_rar5_multiple_files_solid.rar").toAbsolutePath(),rarOut=root.resolve("rar-decoded-"+System.nanoTime());
            Stage rarProgress=fx(()->cn.shed4329.onezip.themes.fxJava.fluent.component.ArchiveDialogs.showProgress(main,cn.shed4329.onezip.application.ArchiveTask.extract(rar,rarOut,null,4),"RAR5 解壓測試",false));
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);status="";
            while(System.nanoTime()<deadline){status=fx(()->((Label)rarProgress.getScene().lookup("#archive-status")).getText());if(status.equals("已完成")||status.equals("操作失敗"))break;Thread.sleep(100);}
            assertEquals("已完成",status);for(int i=1;i<=4;i++)assertEquals(4096,Files.size(rarOut.resolve("test"+i+".bin")));
            fx(()->{ImageIO.write(SwingFXUtils.fromFXImage(rarProgress.getScene().snapshot(null),null),"png",root.resolve("rar-extraction-completed.png").toFile());return null;});
            Path encrypted=Path.of("tests/fixtures/rar/junrar-password-rar5-encrypted-junrar.rar").toAbsolutePath(),secretOut=root.resolve("rar-encrypted-"+System.nanoTime());
            Stage secretProgress=fx(()->cn.shed4329.onezip.themes.fxJava.fluent.component.ArchiveDialogs.showProgress(main,cn.shed4329.onezip.application.ArchiveTask.extract(encrypted,secretOut,"junrar".toCharArray(),4),"RAR5 密碼解壓測試",false));
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);status="";
            while(System.nanoTime()<deadline){status=fx(()->((Label)secretProgress.getScene().lookup("#archive-status")).getText());if(status.equals("已完成")||status.equals("操作失敗"))break;Thread.sleep(100);}
            assertEquals("已完成",status);try(var paths=Files.walk(secretOut)){assertTrue(paths.anyMatch(Files::isRegularFile));}
            fx(()->{ImageIO.write(SwingFXUtils.fromFXImage(secretProgress.getScene().snapshot(null),null),"png",root.resolve("rar-encrypted-completed.png").toFile());return null;});
            Path tarball=root.resolve("gui-tar.tar.gz");Files.deleteIfExists(tarball);
            fx(()->{
                for(Window w:new ArrayList<>(Window.getWindows()))if(w!=main)w.hide();
                cn.shed4329.onezip.themes.fxJava.fluent.component.ArchiveDialogs.showCompression(main,List.of(input),false);
                Stage options=(Stage)Window.getWindows().stream().filter(w->w instanceof Stage s&&s.getTitle().equals("壓縮選項")).findFirst().orElseThrow();
                ((TextField)options.getScene().lookup("#archive-directory")).setText(root.toString());((TextField)options.getScene().lookup("#archive-name")).setText("gui-tar");
                @SuppressWarnings("unchecked") ChoiceBox<String> format=(ChoiceBox<String>)options.getScene().lookup("#archive-format");assertTrue(format.getItems().containsAll(List.of("TAR","GZIP","TAR.GZ")));format.setValue("TAR");assertTrue(options.getScene().lookup("#archive-level").isDisabled());format.setValue("TAR.GZ");assertFalse(options.getScene().lookup("#archive-level").isDisabled());assertTrue(options.getScene().lookup("#archive-encryption").isDisabled());
                ImageIO.write(SwingFXUtils.fromFXImage(options.getScene().snapshot(null),null),"png",root.resolve("tar-gzip-options.png").toFile());((Button)options.getScene().lookup("#archive-start")).fire();return null;
            });
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);status="";
            while(System.nanoTime()<deadline){status=fx(()->Window.getWindows().stream().filter(w->w.getScene()!=null&&w.getScene().lookup("#archive-status")!=null).map(w->((Label)w.getScene().lookup("#archive-status")).getText()).findFirst().orElse("missing"));if(status.equals("已完成")||status.equals("操作失敗"))break;Thread.sleep(100);}
            assertEquals("已完成",status);Path tarOut=root.resolve("tar-decoded-"+System.nanoTime());
            Stage tarProgress=fx(()->cn.shed4329.onezip.themes.fxJava.fluent.component.ArchiveDialogs.showProgress(main,cn.shed4329.onezip.application.ArchiveTask.extract(tarball,tarOut,null,4),"TAR.GZ 解壓測試",false));
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);status="";
            while(System.nanoTime()<deadline){status=fx(()->((Label)tarProgress.getScene().lookup("#archive-status")).getText());if(status.equals("已完成")||status.equals("操作失敗"))break;Thread.sleep(100);}
            assertEquals("已完成",status);assertEquals(Files.readString(input),Files.readString(tarOut.resolve(input.getFileName())));
            Path lz4=root.resolve("gui-lz4.tar.lz4");Files.deleteIfExists(lz4);
            fx(()->{
                for(Window w:new ArrayList<>(Window.getWindows()))if(w!=main)w.hide();cn.shed4329.onezip.themes.fxJava.fluent.component.ArchiveDialogs.showCompression(main,List.of(input),false);
                Stage options=(Stage)Window.getWindows().stream().filter(w->w instanceof Stage s&&s.getTitle().equals("壓縮選項")).findFirst().orElseThrow();
                ((TextField)options.getScene().lookup("#archive-directory")).setText(root.toString());((TextField)options.getScene().lookup("#archive-name")).setText("gui-lz4");
                @SuppressWarnings("unchecked") ChoiceBox<String> format=(ChoiceBox<String>)options.getScene().lookup("#archive-format");assertTrue(format.getItems().containsAll(List.of("LZ4","TAR.LZ4")));format.setValue("TAR.LZ4");assertTrue(options.getScene().lookup("#archive-encryption").isDisabled());
                ImageIO.write(SwingFXUtils.fromFXImage(options.getScene().snapshot(null),null),"png",root.resolve("lz4-options.png").toFile());((Button)options.getScene().lookup("#archive-start")).fire();return null;
            });
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);status="";
            while(System.nanoTime()<deadline){status=fx(()->Window.getWindows().stream().filter(w->w.getScene()!=null&&w.getScene().lookup("#archive-status")!=null).map(w->((Label)w.getScene().lookup("#archive-status")).getText()).findFirst().orElse("missing"));if(status.equals("已完成")||status.equals("操作失敗"))break;Thread.sleep(100);}
            assertEquals("已完成",status);Path lz4Out=root.resolve("lz4-decoded-"+System.nanoTime());
            Stage lz4Progress=fx(()->cn.shed4329.onezip.themes.fxJava.fluent.component.ArchiveDialogs.showProgress(main,cn.shed4329.onezip.application.ArchiveTask.extract(lz4,lz4Out,null,1),"LZ4 解壓測試",false));
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);status="";
            while(System.nanoTime()<deadline){status=fx(()->((Label)lz4Progress.getScene().lookup("#archive-status")).getText());if(status.equals("已完成")||status.equals("操作失敗"))break;Thread.sleep(100);}
            assertEquals("已完成",status);assertEquals(Files.readString(input),Files.readString(lz4Out.resolve(input.getFileName())));
            for(String selectedFormat:List.of("XZ","BZIP2","TAR.XZ","TAR.BZ2")){
                String suffix=switch(selectedFormat){case "XZ"->".xz";case "BZIP2"->".bz2";case "TAR.XZ"->".tar.xz";default->".tar.bz2";};
                String base="gui-"+selectedFormat.toLowerCase(Locale.ROOT).replace('.','-');Path encoded=root.resolve(base+suffix);Files.deleteIfExists(encoded);
                fx(()->{
                    for(Window w:new ArrayList<>(Window.getWindows()))if(w!=main)w.hide();cn.shed4329.onezip.themes.fxJava.fluent.component.ArchiveDialogs.showCompression(main,List.of(input),false);
                    Stage options=(Stage)Window.getWindows().stream().filter(w->w instanceof Stage s&&s.getTitle().equals("壓縮選項")).findFirst().orElseThrow();
                    ((TextField)options.getScene().lookup("#archive-directory")).setText(root.toString());((TextField)options.getScene().lookup("#archive-name")).setText(base);
                    @SuppressWarnings("unchecked") ChoiceBox<String> format=(ChoiceBox<String>)options.getScene().lookup("#archive-format");assertTrue(format.getItems().containsAll(List.of("XZ","BZIP2","TAR.XZ","TAR.BZ2")));format.setValue(selectedFormat);
                    @SuppressWarnings("unchecked") ChoiceBox<String> level=(ChoiceBox<String>)options.getScene().lookup("#archive-level");assertEquals(selectedFormat.contains("BZ")?4:5,level.getItems().size());assertTrue(options.getScene().lookup("#archive-encryption").isDisabled());
                    ImageIO.write(SwingFXUtils.fromFXImage(options.getScene().snapshot(null),null),"png",root.resolve(base+"-options.png").toFile());((Button)options.getScene().lookup("#archive-start")).fire();return null;
                });
                deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);status="";
                while(System.nanoTime()<deadline){status=fx(()->Window.getWindows().stream().filter(w->w.getScene()!=null&&w.getScene().lookup("#archive-status")!=null).map(w->((Label)w.getScene().lookup("#archive-status")).getText()).findFirst().orElse("missing"));if(status.equals("已完成")||status.equals("操作失敗"))break;Thread.sleep(100);}
                assertEquals("已完成",status);Path decodedNew=root.resolve(base+"-decoded-"+System.nanoTime());
                Stage progressNew=fx(()->cn.shed4329.onezip.themes.fxJava.fluent.component.ArchiveDialogs.showProgress(main,cn.shed4329.onezip.application.ArchiveTask.extract(encoded,decodedNew,null,1),selectedFormat+" 解壓測試",false));
                deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);status="";
                while(System.nanoTime()<deadline){status=fx(()->((Label)progressNew.getScene().lookup("#archive-status")).getText());if(status.equals("已完成")||status.equals("操作失敗"))break;Thread.sleep(100);}
                assertEquals("已完成",status);assertEquals(Files.readString(input),Files.readString(decodedNew.resolve(selectedFormat.startsWith("TAR.")?input.getFileName().toString():base)));
            }
            Path previewZip=root.resolve("preview.zip");
            Map<String,byte[]> entries=new LinkedHashMap<>();entries.put("資料/",new byte[0]);entries.put("資料/說明.txt","ZIP 目錄預覽\n".repeat(100).getBytes(java.nio.charset.StandardCharsets.UTF_8));entries.put("empty.txt",new byte[0]);entries.put("nested/deep/data.bin",new byte[4096]);
            try(var out=new java.util.zip.ZipOutputStream(Files.newOutputStream(previewZip))){for(var e:entries.entrySet()){out.putNextEntry(new java.util.zip.ZipEntry(e.getKey()));out.write(e.getValue());out.closeEntry();}}
            fx(()->{
                for(Window w:new ArrayList<>(Window.getWindows()))if(w!=main)w.hide();
                appRef[0].controller().model().pageProperty().set(MainWindowModel.Page.EXTRACT);
                assertEquals(MainWindowModel.Page.EXTRACT,appRef[0].controller().model().pageProperty().get());
                assertFalse(main.getScene().lookup("#compressionPane").isManaged());
                assertTrue(((Button)main.getScene().lookup("#extractAll")).isDisabled());
                appRef[0].controller().selectArchive(previewZip);
                assertFalse(((Button)main.getScene().lookup("#extractAll")).isDisabled());return null;
            });
            assertTrue(awaitZipPreview(main).startsWith("共 3"));
            fx(()->{
                var table=zipTable(main);assertEquals(Set.of("資料/","nested/","empty.txt"),new HashSet<>(table.getItems().stream().map(cn.shed4329.onezip.engine.ArchiveEngine.ArchiveItem::name).toList()));
                assertNull(main.getScene().lookup("#sidebar"));
                assertNull(main.getScene().lookup("#archive-folder-tree"));
                assertNotNull(main.getScene().lookup("#archive-comment-pane"));assertNull(main.getScene().lookup("#archive-content-tabs"));
                assertNotNull(main.getScene().lookup("#archive-add"));assertNotNull(main.getScene().lookup("#archive-delete"));
                assertEquals(2,table.getItems().stream().filter(cn.shed4329.onezip.engine.ArchiveEngine.ArchiveItem::directory).count());
                table.getSortOrder().add(table.getColumns().stream().filter(c->"archive-original-size".equals(c.getId())).findFirst().orElseThrow());table.sort();assertTrue(table.getItems().getFirst().directory());assertEquals("empty.txt",table.getItems().getLast().name());
                table.applyCss();table.layout();
                var nameCells=table.lookupAll(".table-cell").stream().filter(n->n instanceof TableCell<?,?> c
                        &&"archive-name".equals(c.getTableColumn().getId())&&!c.isEmpty()).map(n->(TableCell<?,?>)n).toList();
                assertEquals(3,nameCells.size());
                for(var cell:nameCells){
                    assertTrue(cell.getGraphic() instanceof javafx.scene.image.ImageView);
                    assertEquals(cell.getText().endsWith(".txt")?"txt.png":"folder.png",cell.getGraphic().getUserData());
                }
                var icons=cn.shed4329.onezip.themes.fxJava.fluent.component.FileIcons.view("dir.dot/PHOTO.PNG",false);
                assertEquals("png.png",icons.getUserData());
                assertSame(icons.getImage(),cn.shed4329.onezip.themes.fxJava.fluent.component.FileIcons.view("photo.png",false).getImage());
                ImageIO.write(SwingFXUtils.fromFXImage(main.getScene().snapshot(null),null),"png",root.resolve("zip-entry-preview.png").toFile());
                assertTrue(((Button)main.getScene().lookup("#zip-nav-up")).isDisabled());
                enterFolder(main,"nested/");assertEquals("nested/deep/",table.getItems().getFirst().name());
                enterFolder(main,"nested/deep/");assertEquals(4096,table.getItems().getFirst().size());
                ImageIO.write(SwingFXUtils.fromFXImage(main.getScene().snapshot(null),null),"png",root.resolve("zip-folder-preview.png").toFile());
                ((Button)main.getScene().lookup("#zip-nav-back")).fire();assertEquals("nested/deep/",table.getItems().getFirst().name());
                ((Button)main.getScene().lookup("#zip-nav-forward")).fire();assertEquals("nested/deep/data.bin",table.getItems().getFirst().name());
                ((Button)main.getScene().lookup("#zip-nav-up")).fire();
                var crumbs=(javafx.scene.layout.HBox)main.getScene().lookup("#zip-breadcrumbs");((Button)crumbs.getChildren().getFirst()).fire();assertEquals(3,table.getItems().size());
                assertTrue(((Button)main.getScene().lookup("#zip-nav-forward")).isDisabled());
                // Double click uses the same navigation, including an explicitly recorded folder.
                table.applyCss();table.layout();
                var row=(TableRow<?>)table.lookupAll(".table-row-cell").stream().filter(n->n instanceof TableRow<?> r&&r.getItem() instanceof cn.shed4329.onezip.engine.ArchiveEngine.ArchiveItem item&&item.name().equals("資料/")).findFirst().orElseThrow();
                row.fireEvent(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED,0,0,0,0,javafx.scene.input.MouseButton.PRIMARY,2,false,false,false,false,false,false,false,false,false,true,null));
                assertEquals("資料/說明.txt",table.getItems().getFirst().name());
                assertEquals(entries.get("資料/說明.txt").length,table.getItems().getFirst().size());
                ((Button)main.getScene().lookup("#zip-nav-up")).fire();
                archiveMenu(main,"zip-preview-reload").fire();return null;
            });assertTrue(awaitZipPreview(main).startsWith("共 3"));
            Path encryptedZip=root.resolve("preview-aes.zip");Files.deleteIfExists(encryptedZip);new cn.shed4329.onezip.engine.ArchiveEngine().compress(List.of(input),encryptedZip,cn.shed4329.onezip.engine.ArchiveEngine.Format.ZIP,1,"secret".toCharArray(),null,null,1);
            fx(()->{appRef[0].controller().selectArchive(encryptedZip);return null;});assertTrue(awaitZipPreview(main).startsWith("共 1"));assertEquals(input.getFileName().toString(),fx(()->zipTable(main).getItems().getFirst().name()));
            // A valid directory with a damaged STORE payload still lists: preview never decodes it.
            Path payloadZip=root.resolve("preview-damaged-payload.zip");
            try(var out=new java.util.zip.ZipOutputStream(Files.newOutputStream(payloadZip))){var e=new java.util.zip.ZipEntry("payload.txt");e.setMethod(java.util.zip.ZipEntry.STORED);e.setSize(3);e.setCompressedSize(3);var crc=new java.util.zip.CRC32();crc.update(new byte[]{1,2,3});e.setCrc(crc.getValue());out.putNextEntry(e);out.write(new byte[]{1,2,3});out.closeEntry();}
            try(var file=new java.io.RandomAccessFile(payloadZip.toFile(),"rw")){file.seek(30+"payload.txt".length());file.write(9);}
            assertThrows(java.io.IOException.class,()->new cn.shed4329.onezip.engine.ArchiveEngine().test(payloadZip,null,null,null));fx(()->{appRef[0].controller().selectArchive(payloadZip);return null;});assertTrue(awaitZipPreview(main).startsWith("共 1"));
            Path emptyZip=root.resolve("preview-empty.zip");try(var out=new java.util.zip.ZipOutputStream(Files.newOutputStream(emptyZip))){}
            Path manyZip=root.resolve("preview-many.zip");try(var out=new java.util.zip.ZipOutputStream(Files.newOutputStream(manyZip))){for(int i=0;i<10000;i++){out.putNextEntry(new java.util.zip.ZipEntry("old/file-"+i));out.closeEntry();}}
            fx(()->{appRef[0].controller().selectArchive(manyZip);appRef[0].controller().selectArchive(emptyZip);return null;});assertTrue(awaitZipPreview(main).startsWith("共 0"));Thread.sleep(100);assertEquals(0,fx(()->zipTable(main).getItems().size()));
            Path badZip=Files.write(root.resolve("preview-bad.zip"),new byte[]{1,2,3});fx(()->{appRef[0].controller().selectArchive(badZip);return null;});assertTrue(awaitZipPreview(main).startsWith("无法读取 ZIP"));assertEquals(0,fx(()->zipTable(main).getItems().size()));
            fx(()->{appRef[0].controller().selectArchive(archive);return null;});assertTrue(awaitZipPreview(main).startsWith("共 1"));assertEquals(1,fx(()->zipTable(main).getItems().size()));
            fx(()->{appRef[0].controller().selectArchive(previewZip);return null;});assertTrue(awaitZipPreview(main).startsWith("共 3"));
            // The compression workspace fills the available width at both window sizes.
            fx(()->{appRef[0].controller().model().pageProperty().set(MainWindowModel.Page.COMPRESS);main.setWidth(680);main.setHeight(420);return null;});Thread.sleep(200);
            double small=fx(()->{main.getScene().getRoot().applyCss();main.getScene().getRoot().layout();
                ImageIO.write(SwingFXUtils.fromFXImage(main.getScene().snapshot(null),null),"png",root.resolve("mvc-small.png").toFile());
                return ((ScrollPane)main.getScene().lookup("#compressionScroll")).getWidth();});
            fx(()->{main.setWidth(1440);main.setHeight(800);return null;});Thread.sleep(200);
            final double smallWidth=small;
            fx(()->{
                main.getScene().getRoot().applyCss();main.getScene().getRoot().layout();
                assertNull(main.getScene().lookup("#sidebar"));
                var workspace=(ScrollPane)main.getScene().lookup("#compressionScroll");
                assertTrue(workspace.getWidth()>smallWidth+700);
                assertEquals(main.getScene().getWidth()-40,workspace.getWidth(),1);
                assertNull(main.getScene().lookup("#compression-settings"));
                appRef[0].controller().model().pageProperty().set(MainWindowModel.Page.COMPRESS);
                assertEquals(1,((TableView<?>)main.getScene().lookup("#source-list")).getItems().size());
                appRef[0].controller().model().pageProperty().set(MainWindowModel.Page.EXTRACT);
                ImageIO.write(SwingFXUtils.fromFXImage(main.getScene().snapshot(null),null),"png",root.resolve("mvc-large.png").toFile());
                MainApplication.settingTimes=1; // Simulate the stale counter that used to suppress opening.
                ((Button)main.getScene().lookup("#archive-settings")).fire();
                assertEquals(1,MainApplication.settingTimes);
                var settingsWindow=Window.getWindows().stream().filter(w->w!=main&&w.getScene()!=null&&w.getScene().lookup("#settings-personalize")!=null).findFirst().orElseThrow();
                for(String id:List.of("settings-general","settings-personalize","settings-language","settings-about"))assertNotNull(settingsWindow.getScene().lookup("#"+id));
                assertFalse(CursorImages.apply(new javafx.scene.Scene(new javafx.scene.layout.Pane()),root.resolve("missing # cursor.png").toString()));
                ImageIO.write(SwingFXUtils.fromFXImage(settingsWindow.getScene().snapshot(null),null),"png",root.resolve("settings-window.png").toFile());
                ((Button)settingsWindow.getScene().lookup("#settings-personalize")).fire();
                
                var toggle=(org.controlsfx.control.ToggleSwitch)settingsWindow.getScene().lookup("#settings-dark-mode");boolean savedTheme=ThemeManager.isDark();
                Stage settingsStage=(Stage)settingsWindow;settingsStage.setIconified(true);((Button)main.getScene().lookup("#archive-settings")).fire();assertFalse(settingsStage.isIconified());
                assertEquals(1,Window.getWindows().stream().filter(w->w instanceof Stage stage&&stage.getTitle().equals("设置")).count());
                for(MainWindowModel.Page page:MainWindowModel.Page.values()){
                    appRef[0].controller().model().pageProperty().set(page);
                    for(boolean dark:new boolean[]{true,false}){
                        toggle.setSelected(dark);assertEquals(dark,cn.shed4329.onezip.setting.SettingsStore.shared().flag("appearance.dark"));assertEquals(dark,ThemeManager.isDark());
                        assertTrue(main.getScene().getRoot().getStyleClass().contains(dark?"dark":"light"));assertTrue(settingsWindow.getScene().getRoot().getStyleClass().contains(dark?"dark":"light"));
                        main.getScene().getRoot().applyCss();settingsWindow.getScene().getRoot().applyCss();
                        var background=(javafx.scene.paint.Color)((javafx.scene.layout.Region)main.getScene().getRoot()).getBackground().getFills().getFirst().getFill();assertTrue(dark?background.getBrightness()<.25:background.getBrightness()>.8);
                        var settingsBackground=(javafx.scene.paint.Color)((javafx.scene.layout.Region)settingsWindow.getScene().getRoot()).getBackground().getFills().getFirst().getFill();assertTrue(dark?settingsBackground.getBrightness()<.3:settingsBackground.getBrightness()>.8);
                        ImageIO.write(SwingFXUtils.fromFXImage(main.getScene().snapshot(null),null),"png",root.resolve("theme-"+page.name().toLowerCase(java.util.Locale.ROOT)+"-"+(dark?"dark":"light")+".png").toFile());
                    }
                }
                toggle.setSelected(savedTheme);appRef[0].controller().model().pageProperty().set(MainWindowModel.Page.EXTRACT);
                var reloadedSettings=new cn.shed4329.onezip.setting.SettingsStore(cn.shed4329.onezip.setting.SettingsStore.shared().path().getParent(),root.resolve("empty-legacy"));assertEquals(savedTheme,reloadedSettings.flag("appearance.dark"));
                for(Window window:new ArrayList<>(Window.getWindows()))if(window!=main)window.hide();assertEquals(0,MainApplication.settingTimes);
                return null;
            });
            Path managerZip=root.resolve("archive-manager.zip");String base="typora-spring-theme-release/";
            Map<String,String> sample=new LinkedHashMap<>();sample.put(".assets/icon.svg","<svg xmlns=\"http://www.w3.org/2000/svg\"/>");sample.put("template/index.html","<html><body>OneZip example</body></html>");
            sample.put("CHANGELOG.md","# Changelog\n\nArchive manager example\n".repeat(60));sample.put("credits.md","# Credits\nJavaFX / JMetro\n".repeat(40));sample.put("LICENSE","Example fixture\n".repeat(30));
            sample.put("README.md","# OneZip\nFile tree and archive metadata\n".repeat(150));sample.put("README_cn.md","# OneZip 示例\n目录树与归档详细信息\n".repeat(150));sample.put("spring.css","body { color: #0078d7; background: white; }\n".repeat(1200));
            try(var out=new java.util.zip.ZipOutputStream(Files.newOutputStream(managerZip))){out.setComment("OneZip 示例归档：仅显示压缩包注释。");for(var entry:sample.entrySet()){
                var item=new java.util.zip.ZipEntry(base+entry.getKey());item.setTimeLocal(java.time.LocalDateTime.of(2025,2,28,14,47,36));if(entry.getKey().equals("README.md"))item.setComment("OneZip 示例文档");
                out.putNextEntry(item);out.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.closeEntry();}}
            fx(()->{appRef[0].controller().selectArchive(managerZip);return null;});
            assertTrue(awaitZipPreview(main).startsWith("共 1"));
            fx(()->{enterFolder(main,base);var table=zipTable(main);table.getSortOrder().clear();table.sort();assertEquals(8,table.getItems().size());
                assertNull(main.getScene().lookup("#archive-folder-tree"));
                table.getSelectionModel().select(table.getItems().stream().filter(i->i.name().equals(base+"README.md")).findFirst().orElseThrow());
                Platform.runLater(()->{
                    var prompt=Window.getWindows().stream().filter(w->w.getScene()!=null&&w.getScene().lookup("#archive-test-password")!=null).findFirst().orElseThrow();
                    var pane=(DialogPane)prompt.getScene().getRoot();((Button)pane.lookupButton(ButtonType.OK)).fire();
                });
                archiveMenu(main,"archive-test").fire();return null;
            });
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);status="";
            while(System.nanoTime()<deadline){status=fx(()->Window.getWindows().stream().filter(w->w.getScene()!=null&&w.getScene().lookup("#archive-status")!=null).map(w->((Label)w.getScene().lookup("#archive-status")).getText()).findFirst().orElse("missing"));if(status.equals("已完成")||status.equals("操作失敗"))break;Thread.sleep(50);}
            assertEquals("已完成",status);
            fx(()->{for(Window window:new ArrayList<>(Window.getWindows()))if(window!=main)window.hide();return null;});
            fx(()->{
                assertTrue(((TextArea)main.getScene().lookup("#archive-comment-text")).getText().contains("仅显示压缩包注释"));
                assertNull(main.getScene().lookup("#archive-text-preview"));assertNull(main.getScene().lookup("#archive-content-tabs"));
                ImageIO.write(SwingFXUtils.fromFXImage(main.getScene().snapshot(null),null),"png",root.resolve("archive-manager.png").toFile());
                var table=zipTable(main);assertNull(main.getScene().lookup("#archive-search"));assertNull(main.getScene().lookup("#archive-more"));assertNull(main.getScene().lookup(".archive-heading"));
                var toolbar=(javafx.scene.layout.HBox)main.getScene().lookup(".archive-commandbar");assertEquals(javafx.geometry.Insets.EMPTY,toolbar.getPadding());assertNull(javafx.scene.layout.VBox.getMargin(toolbar));
                assertEquals("archive-settings",toolbar.getChildren().getLast().getId());assertEquals(0,toolbar.getLayoutX(),.01);assertEquals(0,toolbar.getLayoutY(),.01);assertNull(main.getScene().lookup("#archive-select-all"));
                assertTrue(table.getColumns().stream().noneMatch(column->"archive-selection".equals(column.getId())));
                clickRow(table,0,false,false);assertEquals(List.of(0),List.copyOf(table.getSelectionModel().getSelectedIndices()));
                clickRow(table,2,true,false);assertEquals(Set.of(0,2),new HashSet<>(table.getSelectionModel().getSelectedIndices()));
                clickRow(table,0,true,false);assertEquals(List.of(2),List.copyOf(table.getSelectionModel().getSelectedIndices()));
                clickRow(table,1,false,false);clickRow(table,4,false,true);assertEquals(List.of(1,2,3,4),List.copyOf(table.getSelectionModel().getSelectedIndices()));
                table.requestFocus();table.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,"","",javafx.scene.input.KeyCode.A,false,true,false,false));assertEquals(8,table.getSelectionModel().getSelectedItems().size());
                table.getSelectionModel().clearSelection();
                var commentPane=(javafx.scene.layout.VBox)main.getScene().lookup("#archive-comment-pane");var commentText=(TextArea)main.getScene().lookup("#archive-comment-text");
                assertEquals(javafx.geometry.Insets.EMPTY,commentPane.getPadding());assertEquals(javafx.geometry.Insets.EMPTY,commentText.getPadding());assertEquals(commentPane.getWidth(),commentText.getWidth(),1);assertEquals(commentPane.getHeight(),commentText.getHeight(),1);
                Platform.runLater(()->{var prompt=Window.getWindows().stream().filter(w->w.getScene()!=null&&w.getScene().lookup("#archive-directory-password")!=null).findFirst().orElseThrow();((Button)((DialogPane)prompt.getScene().getRoot()).lookupButton(ButtonType.CANCEL)).fire();});
                archiveMenu(main,"archive-password").fire();assertEquals(8,table.getItems().size());
                table.getSelectionModel().select(table.getItems().stream().filter(i->i.name().equals(base+"README.md")).findFirst().orElseThrow());
                assertFalse(((Button)main.getScene().lookup("#archive-delete")).isDisabled());
                Platform.runLater(()->{
                    var pane=(DialogPane)Window.getWindows().stream().filter(w->w.getScene()!=null&&w.getScene().getRoot() instanceof DialogPane d&&d.getHeaderText()!=null&&d.getHeaderText().startsWith("从 ZIP 删除")).findFirst().orElseThrow().getScene().getRoot();
                    ((Button)pane.lookupButton(ButtonType.OK)).fire();
                });
                ((Button)main.getScene().lookup("#archive-delete")).fire();return null;
            });
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);status="";
            while(System.nanoTime()<deadline){status=fx(()->Window.getWindows().stream().filter(w->w.getScene()!=null&&w.getScene().lookup("#archive-status")!=null).map(w->((Label)w.getScene().lookup("#archive-status")).getText()).findFirst().orElse("missing"));if(status.equals("已完成")||status.equals("操作失敗"))break;Thread.sleep(50);}
            assertEquals("已完成",status);assertTrue(awaitZipPreview(main).startsWith("共 7"));
            assertFalse(new cn.shed4329.onezip.engine.ArchiveEngine().list(managerZip,null,null).stream().anyMatch(i->i.name().equals(base+"README.md")));
            fx(()->{for(Window window:new ArrayList<>(Window.getWindows()))if(window!=main)window.hide();archiveMenu(main,"archive-home").fire();assertEquals(MainWindowModel.Page.HOME,appRef[0].controller().model().pageProperty().get());assertTrue(main.getScene().lookup("#home-page").isVisible());assertFalse(main.getScene().lookup("#extractionPane").isManaged());return null;});
            Path uiSources=Files.createDirectories(root.resolve("compression-source-fixture"));List<Path> sourcePaths=new ArrayList<>();sourcePaths.add(Files.createDirectories(uiSources.resolve("template")));
            for(var entry:sample.entrySet())if(!entry.getKey().contains("/"))sourcePaths.add(Files.writeString(uiSources.resolve(entry.getKey()),entry.getValue()));
            fx(()->{appRef[0].controller().model().clearSources();appRef[0].controller().model().addSources(sourcePaths);appRef[0].controller().model().addSources(sourcePaths);assertEquals(sourcePaths.size(),appRef[0].controller().model().sources().size());((Button)main.getScene().lookup("#home-compress")).fire();return null;});
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(fx(()->((Label)main.getScene().lookup("#sourceSize")).getText().startsWith("正在"))){if(System.nanoTime()>deadline)throw new AssertionError("Source metadata timeout");Thread.sleep(25);}
            Path inline=root.resolve("inline-compression.zip");Files.deleteIfExists(inline);
            Stage inlineProgress=fx(()->{
                var sources=(TableView<?>)main.getScene().lookup("#source-list");assertEquals(5,sources.getColumns().size());assertEquals(sourcePaths.size(),sources.getItems().size());
                var formatButton=(ToggleButton)main.getScene().lookup("#formatSevenZip");formatButton.fire();assertTrue(formatButton.isSelected());assertEquals(".7z",((Label)main.getScene().lookup("#compression-extension")).getText());formatButton.fire();assertTrue(formatButton.isSelected());((ToggleButton)main.getScene().lookup("#formatZip")).fire();
                boolean currentTheme=ThemeManager.isDark();ThemeManager.setDark(true);
                TextField name=(TextField)main.getScene().lookup("#compression-name"),location=(TextField)main.getScene().lookup("#compression-location");
                name.setText("../invalid");assertNull(appRef[0].controller().startConfiguredCompression());
                name.setText("CON.txt");assertNull(appRef[0].controller().startConfiguredCompression());assertTrue(((Label)main.getScene().lookup("#compression-validation")).getText().contains("保留"));
                name.setText("inline-compression");location.setText(root.resolve("missing-directory").toString());assertNull(appRef[0].controller().startConfiguredCompression());
                location.setText(inline.toAbsolutePath().getParent().toString());
                var advanced=(TitledPane)main.getScene().lookup(".titled-pane");advanced.setExpanded(false);main.getScene().getRoot().applyCss();main.getScene().getRoot().layout();
                assertTrue(name.isVisible());assertTrue(main.getScene().lookup("#compression-format").isVisible());
                assertFalse(advanced.getContent().isVisible());assertTrue(main.getScene().lookup("#compression-password").getParent()!=advanced.getContent());
                ((Label)main.getScene().lookup("#compression-validation")).setText("");
                ImageIO.write(SwingFXUtils.fromFXImage(main.getScene().snapshot(null),null),"png",root.resolve("compression-workspace.png").toFile());ThemeManager.setDark(currentTheme);
                Object first=sources.getItems().getFirst();sources.getSelectionModel().clearAndSelect(0);((Button)main.getScene().lookup("#source-down")).fire();assertEquals(first,sources.getItems().get(1));
                ((Button)main.getScene().lookup("#source-up")).fire();assertEquals(first,sources.getItems().getFirst());sources.getSelectionModel().clearSelection();
                sources.getSelectionModel().select(0);((Button)main.getScene().lookup("#source-remove")).fire();assertEquals(sourcePaths.size()-1,appRef[0].controller().model().sources().size());
                ((Button)main.getScene().lookup("#source-clear")).fire();assertTrue(appRef[0].controller().model().sources().isEmpty());
                appRef[0].controller().model().addSources(List.of(input));
                @SuppressWarnings("unchecked") ChoiceBox<String> format=(ChoiceBox<String>)main.getScene().lookup("#compression-format");format.setValue("BZIP2");assertEquals(4,((ChoiceBox<?>)main.getScene().lookup("#compression-level")).getItems().size());assertTrue(main.getScene().lookup("#compression-password").isDisabled());format.setValue("ZIP");
                assertEquals(".zip",((Label)main.getScene().lookup("#compression-extension")).getText());advanced.setExpanded(true);
                main.getScene().getRoot().applyCss();main.getScene().getRoot().layout();assertTrue(((TextArea)main.getScene().lookup("#compression-comment")).getHeight()>=160);
                ImageIO.write(SwingFXUtils.fromFXImage(main.getScene().snapshot(null),null),"png",root.resolve("compression-advanced.png").toFile());
                ((PasswordField)main.getScene().lookup("#compression-password")).setText("inline-secret");
                ((TextArea)main.getScene().lookup("#compression-comment")).setText("GUI 创建注释\n中文第二行");
                return appRef[0].controller().startConfiguredCompression();
            });assertNotNull(inlineProgress);
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);status="";
            while(System.nanoTime()<deadline){status=fx(()->((Label)inlineProgress.getScene().lookup("#archive-status")).getText());if(status.equals("已完成")||status.equals("操作失敗"))break;Thread.sleep(50);}assertEquals("已完成",status);
            new cn.shed4329.onezip.engine.ArchiveEngine().test(inline,"inline-secret".toCharArray(),null,null);
            assertEquals("GUI 创建注释\n中文第二行",cn.shed4329.onezip.engine.ZipComments.read(inline));
            fx(()->{
                assertEquals("当前文件：1 / 1",((Label)inlineProgress.getScene().lookup("#archive-file-position")).getText());
                assertTrue(((Label)inlineProgress.getScene().lookup("#archive-processed")).getText().contains(" MB"));
                assertTrue(((Label)inlineProgress.getScene().lookup("#archive-elapsed")).getText().matches("已用时间：\\d+:\\d{2}:\\d{2}"));
                inlineProgress.getScene().getRoot().applyCss();var progress=(ProgressBar)inlineProgress.getScene().lookup("#archive-progress");
                var color=(javafx.scene.paint.Color)((javafx.scene.layout.Region)progress.lookup(".bar")).getBackground().getFills().getFirst().getFill();assertTrue(color.getGreen()>color.getRed()*2);
                return null;
            });
            fx(()->{assertEquals("100.0%",((Label)inlineProgress.getScene().lookup("#archive-percentage")).getText());assertTrue(((Label)inlineProgress.getScene().lookup("#archive-speed")).getText().contains("/s"));assertTrue(inlineProgress.getTitle().startsWith("100.0%"));return null;});
            var progressReady=new CountDownLatch(1);
            javafx.concurrent.Task<Path> cancellable=new javafx.concurrent.Task<>(){@Override protected Path call()throws Exception{updateProgress(40,100);updateMessage("可取消的界面回归任务");progressReady.countDown();while(!isCancelled())Thread.sleep(20);return inline;}};
            Stage cancelledProgress=fx(()->{inlineProgress.hide();return cn.shed4329.onezip.themes.fxJava.fluent.component.ArchiveDialogs.showProgress(main,cancellable,"取消状态回归",false);});
            assertTrue(progressReady.await(5,TimeUnit.SECONDS));Thread.sleep(900);
            fx(()->{assertEquals("40.0%",((Label)cancelledProgress.getScene().lookup("#archive-percentage")).getText());((Button)cancelledProgress.getScene().lookup("#archive-cancel")).fire();return null;});
            fx(()->{assertEquals("已取消",((Label)cancelledProgress.getScene().lookup("#archive-status")).getText());var close=(Button)cancelledProgress.getScene().lookup("#archive-cancel");assertFalse(close.isDisabled());assertEquals("關閉",close.getText());
                var progress=(ProgressBar)cancelledProgress.getScene().lookup("#archive-progress");assertFalse(progress.progressProperty().isBound());assertEquals(.4,progress.getProgress(),.001);assertTrue(progress.getStyleClass().contains("stopped"));
                cancelledProgress.getScene().getRoot().applyCss();cancelledProgress.getScene().getRoot().layout();
                var fill=((javafx.scene.layout.Region)progress.lookup(".bar")).getBackground().getFills().getFirst().getFill();assertInstanceOf(javafx.scene.paint.Color.class,fill);var color=(javafx.scene.paint.Color)fill;assertTrue(color.getRed()>color.getGreen()*2);
                ImageIO.write(SwingFXUtils.fromFXImage(cancelledProgress.getScene().snapshot(null),null),"png",root.resolve("progress-cancelled.png").toFile());return null;});
            fx(()->{for(Window window:new ArrayList<>(Window.getWindows()))if(window!=main)window.hide();appRef[0].controller().selectArchive(inline);return null;});
            assertTrue(awaitZipPreview(main).startsWith("共 1"));
            fx(()->{Platform.runLater(()->((Button)main.getScene().lookup("#comment")).fire());return null;});
            fx(()->{
                var dialog=Window.getWindows().stream().filter(w->w.getScene()!=null&&w.getScene().lookup("#zip-comment-editor")!=null).findFirst().orElseThrow();
                var editor=(TextArea)dialog.getScene().lookup("#zip-comment-editor");assertEquals("GUI 创建注释\n中文第二行",editor.getText());assertTrue(editor.getHeight()>=160);
                editor.setText("修改后的文件说明\n第二行\n第三行");var pane=(DialogPane)dialog.getScene().lookup(".dialog-pane");((Button)pane.lookupButton(ButtonType.OK)).fire();return null;
            });
            fx(()->{assertEquals("修改后的文件说明\n第二行\n第三行",cn.shed4329.onezip.engine.ZipComments.read(inline));return null;});
            fx(()->{appRef[0].controller().model().pageProperty().set(MainWindowModel.Page.COMPRESS);main.setWidth(680);main.setHeight(420);return null;});Thread.sleep(200);
            fx(()->{
                var scroll=(ScrollPane)main.getScene().lookup("#compressionScroll");main.getScene().getRoot().applyCss();main.getScene().getRoot().layout();
                assertTrue(((TextArea)main.getScene().lookup("#compression-comment")).getHeight()>=160);
                assertTrue(((javafx.scene.layout.Region)scroll.getContent()).getHeight()>scroll.getViewportBounds().getHeight());
                scroll.setVvalue(1);main.getScene().getRoot().layout();var start=main.getScene().lookup("#compression-start");
                assertTrue(start.localToScene(start.getBoundsInLocal()).getMaxY()<=main.getScene().getHeight()+1);
                ImageIO.write(SwingFXUtils.fromFXImage(main.getScene().snapshot(null),null),"png",root.resolve("compression-advanced-small.png").toFile());return null;
            });
            Path completionName=Files.writeString(root.resolve("完成"),"progress filename regression\n".repeat(100000)),namedOutput=root.resolve("completion-name.7z");Files.deleteIfExists(namedOutput);
            var premature=new java.util.concurrent.atomic.AtomicBoolean();
            Stage namedProgress=fx(()->{
                var task=cn.shed4329.onezip.application.ArchiveTask.compress(List.of(completionName),namedOutput,cn.shed4329.onezip.engine.ArchiveEngine.Format.SEVEN_Z,5,null,1);
                task.progressProperty().addListener((o,old,value)->{if(value.doubleValue()>=1&&!Files.exists(namedOutput))premature.set(true);});
                return cn.shed4329.onezip.themes.fxJava.fluent.component.ArchiveDialogs.showProgress(main,task,"文件名完成回归",false);
            });
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);status="";
            while(System.nanoTime()<deadline){status=fx(()->((Label)namedProgress.getScene().lookup("#archive-status")).getText());if(status.equals("已完成")||status.equals("操作失敗"))break;Thread.sleep(25);}
            assertEquals("已完成",status);assertFalse(premature.get());new cn.shed4329.onezip.engine.ArchiveEngine().test(namedOutput,null,null,null);
        }finally{fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
        long stopped=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(Thread.getAllStackTraces().keySet().stream().anyMatch(t->t.isAlive()&&(t.getName().equals("onezip-zip-preview")||t.getName().equals("onezip-content-preview")||t.getName().equals("onezip-source-metadata")))&&System.nanoTime()<stopped)Thread.sleep(25);
        assertFalse(Thread.getAllStackTraces().keySet().stream().anyMatch(t->t.isAlive()&&(t.getName().equals("onezip-zip-preview")||t.getName().equals("onezip-content-preview")||t.getName().equals("onezip-source-metadata"))));
    }
}
