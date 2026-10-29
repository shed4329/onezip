import cn.shed4329.onezip.themes.fxJava.fluent.*;
import javafx.application.Platform;
import javafx.stage.*;
import java.awt.Robot;
import java.awt.Rectangle;
import java.nio.file.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/** Fresh JVM restore check against the preferences persisted by AppearanceProbe. */
class AppearanceRestoreProbe {
    static <T>T fx(Callable<T> code)throws Exception{var task=new FutureTask<>(code);Platform.runLater(task);return task.get(15,TimeUnit.SECONDS);}
    public static void main(String[] args)throws Exception{
        Platform.startup(()->Platform.setImplicitExit(false));
        try{
            boolean enabled=args.length<3||Boolean.parseBoolean(args[2]);
            boolean blurred=args.length<5||Boolean.parseBoolean(args[4]);
            boolean captureSettings=args.length>3&&args[3].equals("settings");
            Stage stage=fx(()->{var s=new Stage();new MainApplication().start(s);s.setX(20);s.setY(20);if(BackgroundAppearance.blurred()!=blurred||BackgroundAppearance.enabled()!=enabled||!BackgroundAppearance.path().equals(Path.of(args[0]).toAbsolutePath().normalize().toString())||s.getScene().lookup("#application-background").isVisible()!=enabled)throw new AssertionError("Wallpaper preferences did not survive a fresh JVM");return s;});
            javafx.scene.Scene captureScene=fx(()->{
                if(captureSettings){
                    ThemeManager.setDark(true);((javafx.scene.control.Button)stage.getScene().lookup("#home-settings")).fire();
                    var window=Window.getWindows().stream().filter(w->w!=stage&&w.getScene()!=null&&w.getScene().lookup("#settings-personalize")!=null).findFirst().orElseThrow();
                    ((javafx.scene.control.Button)window.getScene().lookup("#settings-personalize")).fire();
                    ((javafx.scene.control.ScrollPane)window.getScene().lookup("#settings-content-scroll")).setVvalue(1);window.getScene().getRoot().applyCss();window.getScene().getRoot().layout();return window.getScene();
                }return stage.getScene();
            });
            Thread.sleep(200);var shot=fx(()->{var rendered=captureScene.snapshot(null);int w=(int)rendered.getWidth(),h=(int)rendered.getHeight();int[] pixels=new int[w*h];rendered.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);var bitmap=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);bitmap.setRGB(0,0,w,h,pixels,0,w);return bitmap;});
            ImageIO.write(shot,"png",Path.of(args[1],captureSettings?"11-background-settings.png":enabled?blurred?"10-fresh-jvm-restored.png":"10-fresh-jvm-blur-off.png":"10-fresh-jvm-off.png").toFile());
            Files.writeString(Path.of(args[1],"persistence-results.txt"),"fresh_jvm_background_restore_enabled="+enabled+",blur="+blurred+"=PASS\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);System.out.println("PASS: background enabled="+enabled+", blur="+blurred+", path restored in a fresh JVM");
        }finally{fx(()->{new java.util.ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
    }
}
