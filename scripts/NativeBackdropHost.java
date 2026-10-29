import cn.shed4329.onezip.themes.fxJava.fluent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
class NativeBackdropHost extends NativeBackdropMatrix {
    static native boolean start(String title);
    static native int mode(int material);
    static native long handle();
    static native void stop();
    static native boolean ownsForeground();
    static native boolean hostActive();
    static boolean activeControl;
    static java.awt.image.BufferedImage shot(String id)throws Exception{
        if(!activeControl)return captureOwned(id);
        Thread.sleep(350);check(ownsForeground()&&hostActive(),"Native activation control not foreground");
        var r=fx(()->new java.awt.Rectangle((int)app.getX(),(int)app.getY(),(int)app.getWidth(),(int)app.getHeight()));var image=new java.awt.Robot().createScreenCapture(r);javax.imageio.ImageIO.write(image,"png",output.resolve(id+".png").toFile());return image;
    }
    static Canvas canvas;
    static List<String> results=new ArrayList<>();
    static double probe(String id)throws Exception{
        fx(()->{pattern(canvas,false);return null;});var a=shot(id+"-a");fx(()->{pattern(canvas,true);return null;});var b=shot(id+"-b");double response=difference(a,b,40,100,140,90),edges=variation(a,40,100,140,90),contrast=nativeTitleContrast(b);if(id.startsWith("00-"))check(response>20&&edges>8,"Backing fixture occluded; experiment invalid");String s=id+" response="+response+" edges="+edges+" titleContrast="+contrast+" opacity="+fx(()->app.getOpacity());System.out.println(s);results.add(s);return response>8&&edges<5&&contrast>=3?edges:-1;
    }
    public static void main(String[] args)throws Exception{
        output=Files.createDirectories(Path.of(args[0]).toAbsolutePath());System.load(Path.of("target/native-research/jni/onezip_backdrop_probe.dll").toAbsolutePath().toString());Platform.startup(()->Platform.setImplicitExit(false));
        try{
            canvas=new Canvas(1500,1000);fx(()->{pattern(canvas,false);var s=new Stage(StageStyle.UNDECORATED);s.setScene(new Scene(new StackPane(canvas),1500,1000));s.setX(0);s.setY(0);s.setAlwaysOnTop(true);s.show();return null;});
            app=fx(()->{var s=new Stage(StageStyle.TRANSPARENT);var a=new MainApplication();a.start(s);var m=MainApplication.class.getDeclaredMethod("controller");m.setAccessible(true);controller=(MainWindowController)m.invoke(a);ThemeManager.setDark(true);BackgroundAppearance.configure("",false,false);controller.model().pageProperty().set(MainWindowModel.Page.HOME);s.setTitle("OneZip JNI Host Probe "+ProcessHandle.current().pid());s.setWidth(1180);s.setHeight(760);s.setX(40);s.setY(40);s.setAlwaysOnTop(true);s.getScene().setFill(Color.TRANSPARENT);s.getScene().getRoot().setStyle("-onezip-surface:transparent;-fx-background-color:rgba(12,12,12,.12);");return s;});
            probe("00-plain-transparency-control");check(start(fx(()->app.getTitle())),"JNI host creation failed");results.add("JNI host="+handle());
            int winner=0;
            for(int material:(args.length>1&&args[1].equals("--all")?new int[]{1,2,3,4}:new int[]{4})){activeControl=material==4;int hr=mode(material);results.add("material="+material+" apiHResult="+hr);double pass=probe("host-material-"+material);if(pass>=0&&winner==0)winner=material;}
            results.add("visualWinner="+winner+" (1=documented DWM,2=Accent blur,3=Accent acrylic)");
            activeControl=false;
            if(winner!=0&&winner!=4){
                mode(winner);
                fx(()->{BackgroundAppearance.previewControlsTransparency(100);var root=app.getScene().getRoot();root.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("wallpaper"),true);root.setStyle(root.getStyle()+"-onezip-surface:transparent;-fx-background-color:rgba(12,12,12,.35);");return null;});
                captureOwned("passed-home");Path source=Files.writeString(output.resolve("sample.txt"),"JNI native background experiment\n".repeat(100));fx(()->{controller.model().addSources(List.of(source));controller.model().pageProperty().set(MainWindowModel.Page.COMPRESS);return null;});captureOwned("passed-compress");
                Path archive=output.resolve("fixture.zip");new cn.shed4329.onezip.engine.ArchiveEngine().compress(List.of(source),archive,cn.shed4329.onezip.engine.ArchiveEngine.Format.ZIP,5,null,null,null,1);fx(()->{controller.selectArchive(archive);return null;});Thread.sleep(350);captureOwned("passed-extract");
            }
        }finally{
            mode(0);stop();results.add("JNI host destroyed="+(handle()==0));
            if(app!=null){fx(()->{app.getScene().getRoot().setStyle("");app.getScene().getRoot().pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("wallpaper"),false);app.getScene().setFill(Color.WHITE);controller.model().pageProperty().set(MainWindowModel.Page.HOME);return null;});captureOwned("safe-solid-fallback");}
            Files.write(output.resolve("results.txt"),results);fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();
        }
    }
}
