import cn.shed4329.onezip.themes.fxJava.fluent.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.paint.Color;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;

/** Real packaged UI, isolated preferences, live color and original icon-mask checks. */
class AccentThemeProbe extends UiAcceptance {
    static Color fill(String id)throws Exception{return fx(()->{layout();return (Color)((javafx.scene.layout.Region)app.getScene().lookup(id)).getBackground().getFills().getFirst().getFill();});}
    static void icon(String id)throws Exception{fx(()->{var view=(ImageView)((Labeled)app.getScene().lookup(id)).getGraphic();Image actual=view.getImage();Color expected=AccentTheme.foreground();int samples=0;for(int y=0;y<actual.getHeight();y++)for(int x=0;x<actual.getWidth();x++){Color c=actual.getPixelReader().getColor(x,y);if(c.getOpacity()>.95){require(Math.abs(c.getRed()-expected.getRed())<.01&&Math.abs(c.getGreen()-expected.getGreen())<.01&&Math.abs(c.getBlue()-expected.getBlue())<.01,"Icon color mismatch");samples++;}}require(samples>5,"No opaque icon pixels");return null;});pass("icon-"+id,"Original silhouette tinted to live accent");}
    public static void main(String[] args)throws Exception{
        output=Files.createDirectories(Path.of(args[0]).toAbsolutePath());Platform.startup(()->Platform.setImplicitExit(false));
        try{
            app=fx(()->{var s=new Stage();var a=new MainApplication();a.start(s);var m=MainApplication.class.getDeclaredMethod("controller");m.setAccessible(true);controller=(MainWindowController)m.invoke(a);return s;});
            if(args.length>1){require(AccentTheme.hex(AccentTheme.color()).equals(args[1]),"Restart lost accent");icon("#home-compress");capture("restart");pass("restart","Color restored in fresh JVM");return;}
            capture("01-default-home");
            Stage settings=fx(()->{((Button)app.getScene().lookup("#home-settings")).fire();var s=(Stage)Window.getWindows().stream().filter(w->w!=app&&w.getScene()!=null&&w.getScene().lookup("#settings-personalize")!=null).findFirst().orElseThrow();((Button)s.getScene().lookup("#settings-personalize")).fire();return s;});
            ColorPicker picker=fx(()->(ColorPicker)settings.getScene().lookup("#settings-accent-color"));
            input=Files.createDirectories(output.resolve("source"));Files.writeString(input.resolve("主题.txt"),"Theme regression\n".repeat(100));fx(()->{controller.model().addSources(List.of(input));return null;});
            for(String hex:List.of("#8764b8","#008272","#ffb900","#ffffff")){
                fx(()->{picker.setValue(Color.web(hex));picker.fireEvent(new ActionEvent());require(AccentTheme.hex(AccentTheme.color()).equals(hex),"Picker did not save");settings.hide();return null;});
                for(boolean dark:new boolean[]{false,true}){
                    fx(()->{ThemeManager.setDark(dark);controller.model().pageProperty().set(MainWindowModel.Page.HOME);return null;});icon("#home-compress");capture(hex.substring(1)+"-"+dark+"-home");
                    fx(()->{controller.model().pageProperty().set(MainWindowModel.Page.COMPRESS);return null;});
                    require(AccentTheme.hex(fill("#compression-start")).equals(AccentTheme.hex(AccentTheme.primary())),"Primary button accent mismatch");contrast(hex+dark,capture(hex.substring(1)+"-"+dark+"-compress"));
                }
                pass("picker-"+hex,"Live main window, light/dark switch and primary button");
            }
            fx(()->{picker.setValue(Color.web("#8764b8"));picker.fireEvent(new ActionEvent());ThemeManager.setDark(true);BackgroundAppearance.configure(probeImage(),true,true);BackgroundAppearance.setControlsTransparency(50);return null;});
            fx(()->{settings.show();settings.getScene().getRoot().applyCss();settings.getScene().getRoot().layout();var thumb=(javafx.scene.layout.Region)settings.getScene().lookup(".toggle-switch:selected .thumb-area");require(thumb!=null,"No selected settings switch");require(AccentTheme.hex((Color)thumb.getBackground().getFills().getLast().getFill()).equals(AccentTheme.hex(AccentTheme.primary())),"Settings switch stayed blue");var rendered=settings.getScene().snapshot(null);int w=(int)rendered.getWidth(),h=(int)rendered.getHeight();int[] pixels=new int[w*h];rendered.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),pixels,0,w);var bitmap=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);bitmap.setRGB(0,0,w,h,pixels,0,w);javax.imageio.ImageIO.write(bitmap,"png",output.resolve("theme-settings.png").toFile());settings.hide();return null;});pass("settings-toggle","Settings switch uses selected accent");
            capture("purple-wallpaper-compress");Path archive=compress("purple","ZIP");extract("purple",archive);icon("#archive-open");capture("purple-wallpaper-extract");
            fx(()->{BackgroundAppearance.setControlsTransparency(100);return null;});require(fill("#compression-start").getOpacity()==0,"Accent broke full transparency");capture("purple-100-extract");
            var child=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Donezip.settings.dir="+System.getProperty("onezip.settings.dir"),"-Donezip.settings.legacy-dir="+System.getProperty("onezip.settings.legacy-dir"),"-cp",System.getProperty("java.class.path"),"AccentThemeProbe",output.resolve("fresh-jvm").toString(),"#8764b8"));pass("fresh-jvm",command(child).strip());
            fx(()->{((Button)settings.getScene().lookup("#settings-accent-reset")).fire();require(AccentTheme.color().equals(AccentTheme.DEFAULT),"Reset failed");return null;});pass("reset","Actual reset control restores default blue");
        }catch(Throwable e){results.add("FAIL accent: "+e);throw e;}
        finally{report();fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
        if(results.stream().anyMatch(r->r.startsWith("FAIL ")))throw new AssertionError("See results.txt");
    }
}
