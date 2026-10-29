package cn.shed4329.onezip.themes.fxJava.fluent;

import cn.shed4329.onezip.setting.SettingsStore;
import cn.shed4329.onezip.themes.fxJava.fluent.component.FileIcons;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Run with only the distribution JAR on the classpath; catches resource bugs hidden by target/classes. */
class PackagedGuiSmoke {
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task=new FutureTask<>(action);Platform.runLater(task);return task.get(15,TimeUnit.SECONDS);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        Platform.startup(()->Platform.setImplicitExit(false));
        try {
            MainWindowController[] controller=new MainWindowController[1];
            Path folder=Path.of(args[0]).toAbsolutePath();Files.createDirectories(folder);
            Path input=Files.writeString(folder.resolve("拖入 # 中文.txt"),"packaged UI file icon regression");
            SettingsStore.shared().put("cursor.path",input.toString());
            Stage stage=fx(()->{
                MainApplication app=new MainApplication();Stage window=new Stage();app.start(window);
                var getter=MainApplication.class.getDeclaredMethod("controller");getter.setAccessible(true);
                controller[0]=(MainWindowController)getter.invoke(app);check(controller[0].model().pageProperty().get()==MainWindowModel.Page.HOME,"Home should open first");check(window.getScene().getRoot().lookupAll(".home-tile").size()==4,"Home tiles missing");((Button)window.getScene().lookup("#home-compress")).fire();controller[0].model().addSources(List.of(input));
                window.getScene().snapshot(null);
                var cell=(TableCell<?,?>)window.getScene().getRoot().lookupAll(".table-cell").stream().filter(n->n instanceof TableCell<?,?> c&&!c.isEmpty()&&"source-name".equals(c.getTableColumn().getId())).findFirst().orElseThrow();
                check(cell.getGraphic() instanceof ImageView,"File icon missing in packaged UI");
                check(!((ImageView)cell.getGraphic()).getImage().isError(),"Packaged file icon failed to decode");
                for(String ext:List.of("txt","md","java","json","pdf","docx","xlsx","pptx","png","mp3","mp4","zip","exe","bin")){
                    check(!FileIcons.view("test."+ext,false).getImage().isError(),"Icon error: "+ext);
                }
                check(!FileIcons.view("folder",true).getImage().isError(),"Folder icon error");
                check(window.getScene().getCursor()==javafx.scene.Cursor.DEFAULT,"Invalid cursor must fall back");
                SettingsStore.shared().put("cursor.path","");
                controller[0].model().pageProperty().set(MainWindowModel.Page.HOME);((Button)window.getScene().lookup("#home-settings")).fire();return window;
            });
            fx(()->{
                Stage settings=(Stage)Window.getWindows().stream().filter(w->w instanceof Stage s&&s.getTitle().equals("设置")).findFirst().orElseThrow();
                for(String id:List.of("settings-general","settings-personalize","settings-language","settings-about"))check(settings.getScene().lookup("#"+id)!=null,"Original settings section missing: "+id);
                ((Button)settings.getScene().lookup("#settings-personalize")).fire();
                ((Button)settings.getScene().lookup("#settings-background")).fire();
                check(settings.getScene().lookup("#settings-dark-mode")!=null,"Personalization controls missing");
                var toggle=(org.controlsfx.control.ToggleSwitch)settings.getScene().lookup("#settings-dark-mode");toggle.setSelected(true);check(SettingsStore.shared().flag("appearance.dark"),"Dark setting failed");check(stage.getScene().getRoot().getStyleClass().contains("dark"),"Live dark theme failed");
                toggle.setSelected(false);check(!SettingsStore.shared().flag("appearance.dark"),"Light setting failed");check(stage.getScene().getRoot().getStyleClass().contains("light"),"Live light theme failed");
                settings.hide();return null;
            });
            Path archive=folder.resolve("packaged-browser.zip");Files.deleteIfExists(archive);
            new cn.shed4329.onezip.engine.ArchiveEngine().compress(List.of(input),archive,cn.shed4329.onezip.engine.ArchiveEngine.Format.ZIP,1,null,null,null,1);
            fx(()->{controller[0].model().pageProperty().set(MainWindowModel.Page.EXTRACT);controller[0].selectArchive(archive);return null;});
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(fx(()->stage.getScene().lookup("#zip-preview-progress").isVisible())){if(System.nanoTime()>deadline)throw new AssertionError("Packaged browser timeout");Thread.sleep(50);}
            fx(()->{
                check(((Label)stage.getScene().lookup("#zip-preview-status")).getText().startsWith("共 1"),"Packaged archive did not list");
                check(stage.getScene().lookup("#archive-folder-tree")==null,"Folder tree should be removed");check(stage.getScene().lookup("#archive-comment-pane")!=null,"Packaged comment panel missing");
                var table=(TableView<?>)stage.getScene().lookup("#zip-entry-table");check(table.getColumns().size()==8,"Detail columns missing");
                check(stage.getScene().lookup("#sidebar")==null,"Sidebar should be removed");
                check(stage.getScene().lookup("#archive-more")==null,"More button should be removed");check(stage.getScene().lookup("#archive-settings")!=null,"Direct settings button missing");check(table.getContextMenu()!=null,"Archive context actions missing");
                return null;
            });
            System.out.println("PASS: packaged archive manager, main FXML, original settings layout, file icons, cursor fallback and theme persistence");
        } finally { fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit(); }
    }
}
