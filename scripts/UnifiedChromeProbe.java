import cn.shed4329.onezip.themes.fxJava.fluent.*;
import cn.shed4329.onezip.themes.fxJava.fluent.component.ArchiveDialogs;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
class UnifiedChromeProbe extends UiAcceptance {
    static void chrome(Stage s,String id)throws Exception{fx(()->{require(s.getStyle()==StageStyle.UNDECORATED,id+" still decorated");require(s.getScene().lookup("#window-controls")!=null,id+" has no caption");for(var n:s.getScene().getRoot().lookupAll(".caption-command")){var b=(Button)n;require(b.getText().isEmpty()&&b.getGraphic() instanceof javafx.scene.layout.Pane,"Caption still uses text glyph");}return null;});pass(id,"Undecorated with SVG controls");}
    public static void main(String[] args)throws Exception{
        output=Files.createDirectories(Path.of(args[0]).toAbsolutePath());Platform.startup(()->Platform.setImplicitExit(false));
        try{
            app=fx(()->{ThemeManager.setDark(true);AccentTheme.set(javafx.scene.paint.Color.web("#8764b8"));var s=new Stage();var a=new MainApplication();a.start(s);var m=MainApplication.class.getDeclaredMethod("controller");m.setAccessible(true);controller=(MainWindowController)m.invoke(a);return s;});
            chrome(app,"main");capture("01-main-home");
            fx(()->{((Button)app.getScene().lookup("#window-maximize")).fire();return null;});Thread.sleep(250);
            fx(()->{var b=(Button)app.getScene().lookup("#window-maximize");require("restore".equals(b.getGraphic().getUserData()),"Restore icon not updated");b.fire();return null;});Thread.sleep(250);pass("restore-icon","Maximize switches to restore SVG");
            input=Files.createDirectories(output.resolve("source"));Files.writeString(input.resolve("说明.txt"),"Unified window controls\n".repeat(100));fx(()->{controller.model().addSources(List.of(input));return null;});Path archive=compress("chrome","ZIP");capture("02-main-extract");
            openArchive(archive);fx(()->{((Button)app.getScene().lookup("#extractAll")).fire();return null;});
            Stage prompt=fx(()->(Stage)Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene()!=null&&w.getScene().lookup("#extract-destination")!=null).findFirst().orElseThrow());chrome(prompt,"extract-dialog");
            fx(()->{((Button)prompt.getScene().lookup("#window-close")).fire();return null;});require(fx(()->!prompt.isShowing()),"Dialog caption close failed");pass("dialog-cancel","Caption closes extraction prompt without starting task");extract("chrome",archive);
            for(boolean error:new boolean[]{false,true}){
                Dialog<?> d=fx(()->{Dialog<?> p=error?new Alert(Alert.AlertType.ERROR,"Sample",ButtonType.OK):new Dialog<ButtonType>();p.setTitle(error?"错误提示":"密码与说明测试");if(!error){p.setHeaderText("测试内容");p.getDialogPane().setContent(new PasswordField());p.getDialogPane().getButtonTypes().setAll(ButtonType.OK,ButtonType.CANCEL);}WindowChrome.show(p);return p;});Stage stage=fx(()->(Stage)d.getDialogPane().getScene().getWindow());chrome(stage,error?"error-dialog":"generic-dialog");fx(()->{((Button)stage.getScene().lookup("#window-close")).fire();return null;});require(fx(()->!d.isShowing()),"Dialog close blocked");
            }
            javafx.concurrent.Task<Path> work=new javafx.concurrent.Task<>(){protected Path call()throws Exception{while(!isCancelled())Thread.sleep(20);return output;}};
            Stage progress=fx(()->ArchiveDialogs.showProgress(app,work,"取消测试",true));chrome(progress,"progress-dialog");fx(()->{((Button)progress.getScene().lookup("#window-close")).fire();return null;});Thread.sleep(300);
            require(work.isCancelled(),"Caption close bypassed cancel");require(fx(()->progress.isShowing()),"Progress hidden before cancel summary");pass("progress-close","Caption requests cancellation and retains summary");fx(()->{((Button)progress.getScene().lookup("#window-close")).fire();return null;});
        }catch(Throwable e){results.add("FAIL chrome: "+e);throw e;}
        finally{report();fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
    }
}
