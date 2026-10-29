import cn.shed4329.onezip.Launcher;
import cn.shed4329.onezip.setting.SettingsStore;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Real Launcher.main/JavaFX startup with shell paths; all settings and output are test-local. */
public class ShellOpenProbe {
    static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }
    static void require(boolean okay, String message) {if (!okay) throw new AssertionError(message);}
    static void screenshot(Stage stage, Path output) throws Exception {
        stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();
        var image=stage.getScene().snapshot(null);
        int width=(int)image.getWidth(),height=(int)image.getHeight();
        int[] pixels=new int[width*height];
        image.getPixelReader().getPixels(0,0,width,height,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,width);
        BufferedImage bitmap=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        bitmap.setRGB(0,0,width,height,pixels,0,width);
        ImageIO.write(bitmap,"png",output.toFile());
    }
    static void captions(Parent root) {
        for(var item:root.lookupAll(".command-scroll"))if(item instanceof ScrollPane toolbar&&toolbar.isVisible()) {
            var viewport=toolbar.lookup(".viewport");
            if(viewport==null)continue;
            var bounds=viewport.localToScene(viewport.getBoundsInLocal());
            for(var node:((Parent)toolbar.getContent()).lookupAll(".button")) {
                var button=node.localToScene(node.getBoundsInLocal());
                require(button.getMinY()>=bounds.getMinY()-1&&button.getMaxY()<=bounds.getMaxY()+1,"Toolbar clips button vertically");
            }
            require(toolbar.getHvalue()==0,"Resized toolbar did not restore its first action");
        }
        for(var node:root.lookupAll(".button")) {
            if(!(node instanceof ButtonBase button)||button.getText().isBlank())continue;
            boolean visible=true;for(var p=node;p!=null;p=p.getParent())visible&=p.isVisible();
            if(!visible)continue;
            for(var text:button.lookupAll(".text"))if(text instanceof Text t)
                require(t.getText().equals(button.getText())||(!t.getText().contains("…")&&!t.getText().endsWith("...")),"Clipped action: "+button.getText()+" -> "+t.getText());
        }
    }
    public static void main(String[] args) throws Exception {
        Path output=Files.createDirectories(Path.of(args[0]));
        String mode=args[1],language=args[2];
        SettingsStore.shared().put("appearance.language",language);
        SettingsStore.shared().put("appearance.dark","true");
        String[] paths=Arrays.copyOfRange(args,3,args.length);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        Thread observer=new Thread(()->{
            try {
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
                while(true){
                    try {
                        boolean ready=fx(()->{
                            var windows=Window.getWindows().stream().filter(Window::isShowing).toList();
                            if(mode.equals("missing"))return windows.stream().anyMatch(w->w.getScene().getRoot() instanceof DialogPane);
                            return windows.size()>=paths.length&&windows.stream().filter(w->w.getScene().lookup("#zip-entry-table") instanceof TableView<?> t&&!t.getItems().isEmpty()).count()>=paths.length;
                        });
                        if(ready)break;
                    }catch(IllegalStateException notStarted){}
                    if(System.nanoTime()>deadline)throw new AssertionError("Shell startup timeout");
                    Thread.sleep(100);
                }
                var windows=fx(()->new ArrayList<>(Window.getWindows()));
                    if(mode.equals("missing")){
                        fx(()->{
                        var error=windows.stream().filter(w->w.isShowing()&&w.getScene().getRoot() instanceof DialogPane).findFirst().orElseThrow();
                        screenshot((Stage)error,output.resolve("missing-file.png"));
                        DialogPane pane=(DialogPane)error.getScene().getRoot();
                        ((Button)pane.lookupButton(ButtonType.OK)).fire();
                        return null;});
                        Thread.sleep(200);
                    } else {
                        int index=0;
                        for(var window:windows)if(window.isShowing()){
                            Stage stage=(Stage)window;
                            final int number=++index;
                            fx(()->{
                            require(stage.getScene().lookup("#zip-entry-table") instanceof TableView<?> t&&!t.getItems().isEmpty(),"Archive listing missing");
                            screenshot(stage,output.resolve("shell-archive-"+number+".png"));
                            stage.setWidth(680);stage.setHeight(600);return null;});
                            Thread.sleep(250);
                            fx(()->{
                            require(stage.getScene().getWidth()<700,"Narrow resize did not settle");
                            stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();
                            captions(stage.getScene().getRoot());screenshot(stage,output.resolve("narrow-extract-"+number+".png"));
                            for(var node:stage.getScene().getRoot().lookupAll(".button"))if(node instanceof Button b&&b.getText().equals(cn.shed4329.onezip.i18n.I18n.t("action.new"))){b.fire();break;}
                            return null;});
                            Thread.sleep(250);
                            fx(()->{
                            stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();captions(stage.getScene().getRoot());
                            screenshot(stage,output.resolve("narrow-compress-"+number+".png"));return null;});
                        }
                    }
                fx(()->{
                    new ArrayList<>(Window.getWindows()).forEach(Window::hide);
                    return null;
                });
                Files.writeString(output.resolve("results.txt"),"PASS "+mode+" "+language+": real Launcher.main preserves shell paths; archive listing / narrow action captions verified\n");
                Platform.exit();
            }catch(Throwable error){failure.set(error);try{fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);Platform.exit();return null;});}catch(Exception ignored){}}
        },"shell-startup-acceptance");
        observer.start();Launcher.main(paths);observer.join();
        if(failure.get()!=null)throw new AssertionError("Shell/UI acceptance failed",failure.get());
    }
}
