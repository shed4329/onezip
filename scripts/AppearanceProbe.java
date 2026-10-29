import cn.shed4329.onezip.themes.fxJava.fluent.*;
import cn.shed4329.onezip.setting.SettingsStore;
import cn.shed4329.onezip.engine.ArchiveEngine;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.awt.Robot;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Real distribution JAR, isolated preferences, desktop captures (including native DWM). */
class AppearanceProbe {
    static Path output;
    static Robot robot;
    static Stage app;
    static MainWindowController controller;
    static <T>T fx(Callable<T> action)throws Exception{var task=new FutureTask<>(action);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    static void check(boolean test,String message){if(!test)throw new AssertionError(message);}
    static BufferedImage capture(String name)throws Exception{
        fx(()->{var screen=Screen.getPrimary().getVisualBounds();app.setX(screen.getMinX()+20);app.setY(screen.getMinY()+20);app.setWidth(Math.min(app.getWidth(),screen.getWidth()-40));app.setHeight(Math.min(app.getHeight(),screen.getHeight()-40));app.getScene().getRoot().applyCss();app.getScene().getRoot().layout();app.toFront();return null;});Thread.sleep(350);
        Rectangle bounds=fx(()->new Rectangle((int)app.getX(),(int)app.getY(),(int)app.getWidth(),(int)app.getHeight()));
        BufferedImage image=robot.createScreenCapture(bounds);ImageIO.write(image,"png",output.resolve(name+".png").toFile());return image;
    }
    static double difference(BufferedImage a,BufferedImage b,int x,int y,int w,int h){double sum=0;for(int yy=y;yy<y+h;yy++)for(int xx=x;xx<x+w;xx++){int p=a.getRGB(xx,yy),q=b.getRGB(xx,yy);for(int shift:new int[]{0,8,16})sum+=Math.abs(((p>>shift)&255)-((q>>shift)&255));}return sum/(w*h*3);}
    static double variation(BufferedImage a,int x,int y,int w,int h){double sum=0;for(int yy=y;yy<y+h;yy++)for(int xx=x+1;xx<x+w;xx++){int p=a.getRGB(xx,yy),q=a.getRGB(xx-1,yy);for(int shift:new int[]{0,8,16})sum+=Math.abs(((p>>shift)&255)-((q>>shift)&255));}return sum/((w-1)*h*3);}
    static void pattern(Canvas canvas,boolean alternate){var g=canvas.getGraphicsContext2D();for(int y=0;y<canvas.getHeight();y+=8)for(int x=0;x<canvas.getWidth();x+=8){boolean on=(x/8+y/8)%2==0;g.setFill(alternate?(on?Color.rgb(255,100,100):Color.rgb(90,0,0)):(on?Color.rgb(130,220,255):Color.rgb(0,20,100)));g.fillRect(x,y,8,8);}}
    static String nativeProbe(String mode)throws Exception{
        String title=fx(()->app.getTitle());String system=System.getenv("SystemRoot");
        var process=new ProcessBuilder(Path.of(system,"System32","WindowsPowerShell","v1.0","powershell.exe").toString(),"-NoProfile","-NonInteractive","-ExecutionPolicy","Bypass","-File",Path.of("scripts/ProbeWindowsBackdrop.ps1").toAbsolutePath().toString(),"-Title",title,"-PeerPid",Long.toString(ProcessHandle.current().pid()),"-Mode",mode).redirectErrorStream(true).start();
        if(!process.waitFor(25,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("DWM probe timed out");}
        String result=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);Files.writeString(output.resolve("native-"+mode.toLowerCase(Locale.ROOT)+".json"),result);check(process.exitValue()==0,"DWM probe failed: "+result);return result;
    }
    static BufferedImage sceneCapture(String name)throws Exception{
        var shot=fx(()->{app.getScene().getRoot().applyCss();app.getScene().getRoot().layout();var image=app.getScene().snapshot(null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] pixels=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);var result=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);result.setRGB(0,0,w,h,pixels,0,w);return result;});ImageIO.write(shot,"png",output.resolve(name+".png").toFile());return shot;
    }
    static BufferedImage guardedDesktopCapture(String name)throws Exception{
        fx(()->{app.setAlwaysOnTop(true);app.setIconified(false);app.toFront();app.requestFocus();return null;});Thread.sleep(250);
        String identity=nativeProbe("Inspect");check(identity.contains("\"foregroundPid\":"+ProcessHandle.current().pid()+","),"Desktop capture lost target foreground ownership");
        var bounds=fx(()->new Rectangle((int)app.getX(),(int)app.getY(),(int)app.getWidth(),(int)app.getHeight()));var shot=new Robot().createScreenCapture(bounds);ImageIO.write(shot,"png",output.resolve(name+".png").toFile());return shot;
    }
    static double nativeTitleContrast(BufferedImage shot)throws Exception{
        return fx(()->{var text=(javafx.scene.text.Text)app.getScene().lookup(".home-title .text");var b=text.localToScreen(text.getBoundsInLocal());int x=(int)(b.getMinX()-app.getX())-2,y=(int)(b.getCenterY()-app.getY());int rgb=shot.getRGB(Math.max(0,x),Math.max(0,y));return contrast((Color)text.getFill(),Color.rgb((rgb>>16)&255,(rgb>>8)&255,rgb&255));});
    }
    static double contrast(Color a,Color b){double[] l=new double[2];int i=0;for(Color color:List.of(a,b)){double[] v={color.getRed(),color.getGreen(),color.getBlue()};for(int j=0;j<3;j++)v[j]=v[j]<=.04045?v[j]/12.92:Math.pow((v[j]+.055)/1.055,2.4);l[i++]=.2126*v[0]+.7152*v[1]+.0722*v[2];}return (Math.max(l[0],l[1])+.05)/(Math.min(l[0],l[1])+.05);}
    /** Any exception or failed visual/contrast check restores the original readable scene. */
    static List<String> safeNativeBackdropCheck()throws Exception{
        var messages=new ArrayList<String>();var oldFill=fx(()->app.getScene().getFill());String oldStyle=fx(()->app.getScene().getRoot().getStyle()),oldTitle=fx(()->app.getTitle());boolean oldDark=ThemeManager.isDark(),oldTop=fx(()->app.isAlwaysOnTop());
        var layer=fx(()->app.getScene().lookup("#application-background"));boolean oldVisible=fx(()->layer.isVisible());Stage[] backing={null};BufferedImage[] before={null};
        try{
            fx(()->{var screen=Screen.getPrimary().getVisualBounds();app.setX(screen.getMinX()+20);app.setY(screen.getMinY()+20);app.setWidth(Math.min(app.getWidth(),screen.getWidth()-40));app.setHeight(Math.min(app.getHeight(),screen.getHeight()-40));return null;});
            before[0]=sceneCapture("native-before-original");
            var canvas=new Canvas(1500,1000);
            fx(()->{ThemeManager.setDark(true);layer.setVisible(false);app.getScene().getRoot().pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("wallpaper"),false);app.setTitle("OneZip Safe Native Probe "+ProcessHandle.current().pid());app.getScene().setFill(Color.TRANSPARENT);app.getScene().getRoot().setStyle("-onezip-surface:transparent;-fx-background-color:transparent;");check(app.getOpacity()==1,"Whole-window opacity is not an acceptable blur substitute");var stage=new Stage(StageStyle.UNDECORATED);pattern(canvas,false);stage.setScene(new Scene(new StackPane(canvas),1500,1000));stage.setX(10);stage.setY(10);stage.show();stage.toBack();backing[0]=stage;return null;});
            String api=nativeProbe("Acrylic");var a=guardedDesktopCapture("native-candidate-a-rejected");fx(()->{pattern(canvas,true);return null;});var b=guardedDesktopCapture("native-candidate-b-rejected");
            double response=difference(a,b,30,100,140,90),edges=variation(a,30,100,140,90),titleContrast=nativeTitleContrast(b);
            boolean visual=response>8&&edges<5&&titleContrast>=3;
            messages.add("native_visual="+(visual?"PASS_EXPERIMENTAL":"FAIL_UNSUPPORTED")+"; API accepted="+api.contains("\"setHResult\":0,")+"; independent backing response="+response+"; edges="+edges+"; title contrast="+titleContrast+"; window opacity=1");
            messages.add("native_decision=ROLLBACK; experimental candidates are never enabled or persisted by this probe");
        }finally{
            try{String off=nativeProbe("None");messages.add("native_off_api="+(off.contains("\"readback\":1")?"PASS":"FAIL"));}
            finally{
                fx(()->{if(backing[0]!=null)backing[0].hide();app.getScene().getRoot().setStyle(oldStyle);app.getScene().setFill(oldFill);ThemeManager.setDark(oldDark);layer.setVisible(oldVisible);app.getScene().getRoot().pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("wallpaper"),oldVisible);return null;});
                try{var restored=sceneCapture("native-fallback-restored");double delta=before[0]==null?Double.NaN:difference(before[0],restored,20,20,60,60);messages.add("native_original_surface_restored="+(delta<.1?"PASS":"FAIL")+"; mean patch delta="+delta);var desktop=guardedDesktopCapture("native-fallback-desktop");double readable=nativeTitleContrast(desktop);messages.add("native_fallback_title_contrast="+(readable>=3?"PASS":"FAIL")+"; ratio="+readable);}
                finally{fx(()->{app.setAlwaysOnTop(oldTop);app.setTitle(oldTitle);return null;});Files.write(output.resolve("safe-native-results.txt"),messages);}
            }
        }
        return messages;
    }
    public static void main(String[] args)throws Exception{
        output=Path.of(args[1]).toAbsolutePath();Files.createDirectories(output);robot=new Robot();Platform.startup(()->Platform.setImplicitExit(false));
        var results=new ArrayList<String>();
        try{
            SettingsStore.shared().put("appearance.dark","true");
            app=fx(()->{BackgroundAppearance.configure("",false);var stage=new Stage();var application=new MainApplication();application.start(stage);var method=MainApplication.class.getDeclaredMethod("controller");method.setAccessible(true);controller=(MainWindowController)method.invoke(application);stage.setX(100);stage.setY(80);return stage;});
            capture("00-baseline-home");
            fx(()->{BackgroundAppearance.configure(args[0],false);return null;});capture("01-image-home");
            Path input=Files.writeString(output.resolve("示例.txt"),"OneZip background integration test\n".repeat(200));
            fx(()->{controller.model().addSources(List.of(input));controller.model().pageProperty().set(MainWindowModel.Page.COMPRESS);return null;});
            var sharp=capture("02-image-compress");
            fx(()->{var title=(Label)app.getScene().lookup(".page-title");Files.writeString(output.resolve("font-info.txt"),"label="+title.getFont()+"\ntext="+((javafx.scene.text.Text)title.lookup(".text")).getFont());return null;});
            fx(()->{
                check(app.getScene().lookup("#sidebar")==null,"Sidebar returned");check(!((TitledPane)app.getScene().lookup(".compression-advanced")).isExpanded(),"Advanced options should start collapsed");
                check(((MenuButton)app.getScene().lookup("#source-add")).getItems().size()==2,"Unified source entry missing");
                new SettingsWindow().start(new Stage());return null;
            });
            Window settingsWindow=fx(()->{var settings=Window.getWindows().stream().filter(w->w!=app&&w.getScene()!=null&&w.getScene().lookup("#settings-personalize")!=null).findFirst().orElseThrow();((Button)settings.getScene().lookup("#settings-personalize")).fire();((org.controlsfx.control.ToggleSwitch)settings.getScene().lookup("#settings-background-blur")).setSelected(true);check(BackgroundAppearance.blurred(),"Blur control did not persist");check(settings.getOpacity()==1,"Window opacity must not simulate blur");((ScrollPane)settings.getScene().lookup("#settings-content-scroll")).setVvalue(1);settings.getScene().getRoot().applyCss();settings.getScene().getRoot().layout();return settings;});
            Thread.sleep(350);Rectangle settingsBounds=fx(()->new Rectangle((int)settingsWindow.getX(),(int)settingsWindow.getY(),(int)settingsWindow.getWidth(),(int)settingsWindow.getHeight()));ImageIO.write(robot.createScreenCapture(settingsBounds),"png",output.resolve("03-settings-background.png").toFile());
            fx(()->{settingsWindow.hide();var layer=(javafx.scene.Parent)app.getScene().lookup("#application-background");check(layer.getChildrenUnmodifiable().stream().anyMatch(n->n instanceof javafx.scene.image.ImageView&&n.getEffect() instanceof javafx.scene.effect.GaussianBlur),"Image-only blur effect missing");check(app.getScene().lookup("#compressionPane").getEffect()==null,"Text/content must remain sharp");return null;});
            var blurred=capture("03-image-blur-compress");double wallpaperDelta=difference(sharp,blurred,30,90,Math.min(sharp.getWidth()-60,900),Math.min(sharp.getHeight()-150,500));check(wallpaperDelta>.2,"Background blur did not change rendering");results.add("image_background=PASS");results.add("image_gaussian_blur=PASS; mean_pixel_delta="+wallpaperDelta);
            String before=BackgroundAppearance.path();fx(()->{try{BackgroundAppearance.configure(output.resolve("missing.jpg").toString(),true);throw new AssertionError("Missing image accepted");}catch(java.io.IOException expected){}check(before.equals(BackgroundAppearance.path()),"Failed image replaced preferences");return null;});results.add("invalid_image_preserves_preferences=PASS");
            Path archive=output.resolve("浏览示例.zip");Files.deleteIfExists(archive);new ArchiveEngine().compress(List.of(input),archive,ArchiveEngine.Format.ZIP,1,null,null,null,1);
            fx(()->{controller.selectArchive(archive);return null;});Thread.sleep(800);capture("04-image-blur-extract");
            fx(()->{app.hide();var replacement=new MainApplication();app=new Stage();replacement.start(app);app.setX(100);app.setY(80);var method=MainApplication.class.getDeclaredMethod("controller");method.setAccessible(true);controller=(MainWindowController)method.invoke(replacement);check(BackgroundAppearance.blurred(),"Blur preference not restored");check(app.getScene().lookup("#application-background").isVisible(),"Wallpaper not restored");return null;});capture("05-reopened-image-home");results.add("reopened_window_background=PASS");
            results.addAll(safeNativeBackdropCheck());
            results.add("windows_10_machine=NOT_TESTED; see native API metadata for this host build");
            results.add("native_file_picker_manual_interaction=NOT_TESTED");
            Files.write(output.resolve("results.txt"),results);for(String result:results)System.out.println(result);
        }finally{fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
    }
}
