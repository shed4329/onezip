import cn.shed4329.onezip.themes.fxJava.fluent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Focused regression evidence. No codec benchmark or previously passed broad matrix. */
class ExtractBackdropRecheck extends UiAcceptance {
    static double patch(BufferedImage a,BufferedImage b,String selector)throws Exception {
        return fx(()->{var n=app.getScene().lookup(selector);var r=n.localToScene(n.getBoundsInLocal());int x=(int)r.getMinX()+40,y=(int)(r.getMinY()+r.getHeight()*.65);return AppearanceProbe.difference(a,b,x,y,Math.min(160,(int)r.getWidth()-80),Math.min(35,(int)(r.getMaxY()-y)-5));});
    }
    public static void main(String[] args)throws Exception {
        image=Path.of(args[0]).toAbsolutePath();output=Files.createDirectories(Path.of(args[1]).toAbsolutePath());baseline=args.length>2&&args[2].equals("baseline");boolean restore=args.length>2&&args[2].equals("restore");
        Platform.startup(()->Platform.setImplicitExit(false));
        try {
            app=fx(()->{var s=new Stage();var a=new MainApplication();a.start(s);var m=MainApplication.class.getDeclaredMethod("controller");m.setAccessible(true);controller=(MainWindowController)m.invoke(a);s.setWidth(1180);s.setHeight(760);return s;});
            if(restore){fx(()->{require(BackgroundAppearance.controlsTransparency()==65,"Slider preference lost after JVM restart");require(BackgroundAppearance.enabled()&&BackgroundAppearance.blurred(),"Background state changed");return null;});pass("transparency-restart","65%, background enabled+blur retained in fresh JVM");contrast("restart-home",capture("restart-home"));
                Path source=Files.writeString(output.resolve("sample.txt"),"transparent controls");
                fx(()->{controller.model().addSources(List.of(source));controller.model().pageProperty().set(MainWindowModel.Page.COMPRESS);ThemeManager.setDark(false);return null;});
                for(int value:new int[]{0,85}){fx(()->{BackgroundAppearance.previewControlsTransparency(value);return null;});contrast("light-buttons-"+value,capture("light-buttons-"+value));}
                fx(()->{BackgroundAppearance.previewControlsTransparency(65);ThemeManager.setDark(true);return null;});
                openArchive(Path.of("target/extract-backdrop-fix-20261029/final-v3/fixture.zip").toAbsolutePath());contrast("latest-dark-extract",capture("latest-dark-extract"));
                require(BackgroundAppearance.controlsTransparency()==65,"Preview persisted unexpectedly");return;}
            if(args.length>2&&args[2].equals("native")){
                fx(()->{ThemeManager.setDark(true);BackgroundAppearance.configure(image.toString(),true,true);return null;});
                AppearanceProbe.app=app;AppearanceProbe.output=Files.createDirectories(output.resolve("native"));results.addAll(AppearanceProbe.safeNativeBackdropCheck());
                require(BackgroundAppearance.controlsTransparency()==65,"Native changed transparency preference");pass("native-preferences-restored","wallpaper, blur, transparency unchanged");return;
            }
            input=Files.createDirectories(output.resolve("source"));Files.writeString(input.resolve("说明.txt"),"Wallpaper focused test\n".repeat(20));Path archive=output.resolve("fixture.zip");new cn.shed4329.onezip.engine.ArchiveEngine().compress(List.of(input),archive,cn.shed4329.onezip.engine.ArchiveEngine.Format.ZIP,5,null,null,null,1);
            BufferedImage fixture=new BufferedImage(800,600,BufferedImage.TYPE_INT_RGB);for(int y=0;y<600;y++)for(int x=0;x<800;x++)fixture.setRGB(x,y,0xff169a35);Path alt=output.resolve("visibility-fixture.png");ImageIO.write(fixture,"png",alt.toFile());
            for(boolean dark:new boolean[]{true,false}){
                fx(()->{ThemeManager.setDark(dark);BackgroundAppearance.configure(image.toString(),true,true);return null;});openArchive(archive);String id=dark?"dark":"light";var a=capture(id+"-extract-image");contrast(id+"-extract",a);
                fx(()->{BackgroundAppearance.configure(alt.toString(),true,true);return null;});var b=capture(id+"-extract-alternate");double table=patch(a,b,"#zip-entry-table"),comment=patch(a,b,"#archive-comment-text");results.add((baseline?"BASELINE ":table>4&&comment>4?"PASS ":"FAIL ")+id+"-background-response: table="+table+", comment="+comment+" (same wallpaper pseudo-state)");
            }
            if(baseline)return;
            fx(()->{controller.model().addSources(List.of(input));return null;});
            fx(()->{ThemeManager.setDark(true);BackgroundAppearance.configure(image.toString(),true,true);var m=MainWindowController.class.getDeclaredMethod("openSettings");m.setAccessible(true);m.invoke(controller);return null;});
            Stage settings=fx(()->(Stage)Window.getWindows().stream().filter(w->w!=app&&w.getScene()!=null&&w.getScene().lookup("#settings-personalize")!=null).findFirst().orElseThrow());
            Slider slider=fx(()->{((Button)settings.getScene().lookup("#settings-personalize")).fire();((Button)settings.getScene().lookup("#settings-background")).fire();return (Slider)settings.getScene().lookup("#settings-controls-transparency");});
            for(int value:new int[]{0,65,85}){
                fx(()->{slider.setValue(value);return null;});Thread.sleep(350);
                fx(()->{require(BackgroundAppearance.controlsTransparency()==value,"Slider did not save");controller.model().pageProperty().set(MainWindowModel.Page.COMPRESS);layout();var button=(Region)app.getScene().lookup("#source-clear");Color fill=(Color)button.getBackground().getFills().getFirst().getFill();require(Math.abs(fill.getOpacity()-(1-value/100.0))<.015,"Button alpha not updated: "+fill);require(button.getOpacity()==1&&app.getOpacity()==1,"Text/window opacity changed");return null;});
                var shot=capture("buttons-"+value+"-compress");contrast("buttons-"+value,shot);pass("slider-"+value,"actual settings slider, saved alpha and opaque text/window");
            }
            fx(()->{slider.setValue(65);return null;});Thread.sleep(350);
            fx(()->{((ScrollPane)settings.getScene().lookup("#settings-personal-scroll")).setVvalue(1);return null;});Stage main=app;app=settings;capture("transparency-settings");app=main;
            fx(()->{settings.hide();BackgroundAppearance.configure(image.toString(),true,false);return null;});contrast("background-off",capture("background-off-compress"));
            fx(()->{BackgroundAppearance.configure(image.toString(),true,true);controller.model().pageProperty().set(MainWindowModel.Page.HOME);return null;});
            AppearanceProbe.app=app;AppearanceProbe.output=Files.createDirectories(output.resolve("native"));results.addAll(AppearanceProbe.safeNativeBackdropCheck());
            require(BackgroundAppearance.enabled()&&BackgroundAppearance.blurred()&&BackgroundAppearance.controlsTransparency()==65,"Native experiment changed preferences");pass("native-preferences-restored","image, blur and transparency preferences unchanged");
            openArchive(archive);capture("latest-dark-extract");
        }catch(Throwable e){results.add("FAIL focused-execution: "+e);throw e;}
        finally{report();fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
        if(!baseline&&results.stream().anyMatch(x->x.startsWith("FAIL ")))throw new AssertionError("Focused regression failed; see results");
    }
}
