import cn.shed4329.onezip.themes.fxJava.fluent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.*;
import org.controlsfx.control.ToggleSwitch;
import java.nio.file.*;
import java.util.*;

/** Focused packaged-app regression. Uses isolated settings; never enables native Acrylic. */
class PostRestartAppearanceProbe extends UiAcceptance {
    static Stage settings;
    static ToggleSwitch toggle(String id){return (ToggleSwitch)settings.getScene().lookup(id);}
    static void state(boolean enabled,boolean blur,int opacity)throws Exception{
        fx(()->{require(BackgroundAppearance.enabled()==enabled,"enabled lost");require(BackgroundAppearance.blurred()==blur,"blur lost");require(BackgroundAppearance.controlsTransparency()==opacity,"transparency lost");require(BackgroundAppearance.path().equals(image.toString()),"path lost");require(app.getScene().lookup("#application-background").isVisible()==enabled,"visibility disagrees");require(app.getOpacity()==1,"Whole-window opacity changed");return null;});
    }
    static void restart(boolean enabled,boolean blur,int transparency)throws Exception{
        var args=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Dfile.encoding=UTF-8","-Donezip.settings.dir="+System.getProperty("onezip.settings.dir"),"-Donezip.settings.legacy-dir="+System.getProperty("onezip.settings.legacy-dir"),"-cp",System.getProperty("java.class.path"),"PostRestartAppearanceProbe",image.toString(),output.resolve("restart-"+enabled+"-"+blur+"-"+transparency).toString(),"restore",Boolean.toString(enabled),Boolean.toString(blur),Integer.toString(transparency)));
        pass("restart-"+enabled+"-"+blur+"-"+transparency,command(args).strip());
    }
    public static void main(String[] args)throws Exception{
        image=Path.of(args[0]).toAbsolutePath().normalize();output=Files.createDirectories(Path.of(args[1]).toAbsolutePath());
        Platform.startup(()->Platform.setImplicitExit(false));
        try{
            app=fx(()->{var s=new Stage();var a=new MainApplication();a.start(s);var m=MainApplication.class.getDeclaredMethod("controller");m.setAccessible(true);controller=(MainWindowController)m.invoke(a);return s;});
            if(args.length>2&&args[2].equals("restore")){state(Boolean.parseBoolean(args[3]),Boolean.parseBoolean(args[4]),Integer.parseInt(args[5]));capture("restored");pass("fresh-jvm","Stored path, switches, transparency and visible layer agree");return;}
            fx(()->{ThemeManager.setDark(true);return null;});capture("01-before-solid");
            settings=fx(()->{((Button)app.getScene().lookup("#home-settings")).fire();var s=(Stage)Window.getWindows().stream().filter(w->w!=app&&w.getScene()!=null&&w.getScene().lookup("#settings-personalize")!=null).findFirst().orElseThrow();((Button)s.getScene().lookup("#settings-personalize")).fire();return s;});
            fx(()->{((TextField)settings.getScene().lookup("#settings-background-path")).setText(image.toString());((Button)settings.getScene().lookup("#settings-background-apply")).fire();return null;});
            if(args.length<3){
            String prior=fx(()->BackgroundAppearance.path()+"|"+BackgroundAppearance.enabled()+"|"+BackgroundAppearance.blurred()+"|"+BackgroundAppearance.controlsTransparency());
            Platform.runLater(()->((Button)settings.getScene().lookup("#settings-background-choose")).fire());
            try{pass("native-picker-cancel-command",powershell("scripts/ChooseBackgroundFile.ps1","-PeerPid",Long.toString(ProcessHandle.current().pid()),"-DialogTitle","选择背景图片","-Cancel").strip());fx(()->{require(prior.equals(BackgroundAppearance.path()+"|"+BackgroundAppearance.enabled()+"|"+BackgroundAppearance.blurred()+"|"+BackgroundAppearance.controlsTransparency()),"Cancel changed settings");return null;});pass("native-picker-cancel","Actual native dialog cancelled; saved appearance unchanged");}
            catch(AssertionError e){results.add("NOT_TESTED native-picker-cancel: "+e.getMessage());}
            Platform.runLater(()->((Button)settings.getScene().lookup("#settings-background-choose")).fire());
            try{pass("native-picker-selection",powershell("scripts/ChooseBackgroundFile.ps1","-PeerPid",Long.toString(ProcessHandle.current().pid()),"-DialogTitle","选择背景图片","-ImagePath",image.toString(),"-FileNameLabel","File name|文件名|檔案名稱|檔名").strip());fx(()->{require(BackgroundAppearance.path().equals(image.toString())&&BackgroundAppearance.enabled(),"Selection not applied");return null;});}
            catch(AssertionError e){results.add("NOT_TESTED native-picker-selection: bounded UIAutomation attempt unavailable; verified cleanup requested. "+e.getMessage());}
            }else results.add("Native picker: see run1; deliberately not retried");
            fx(()->{var p=(TextField)settings.getScene().lookup("#settings-background-path");p.setText(output.resolve("missing.jpg").toString());((Button)settings.getScene().lookup("#settings-background-apply")).fire();require(BackgroundAppearance.path().equals(image.toString()),"Invalid path replaced valid preference");p.setText(image.toString());return null;});pass("invalid-path","Invalid draft does not replace saved background");
            input=Files.createDirectories(output.resolve("source"));Files.writeString(input.resolve("说明.txt"),"背景回归 pure Java\n".repeat(400));fx(()->{controller.model().addSources(List.of(input));return null;});
            for(boolean blur:new boolean[]{false,true}){
                fx(()->{toggle("#settings-background-blur").setSelected(blur);return null;});
                for(int t:new int[]{0,50,100}){
                    fx(()->{((Slider)settings.getScene().lookup("#settings-controls-transparency")).setValue(t);return null;});Thread.sleep(400);state(true,blur,t);
                    fx(()->{settings.hide();return null;});
                    String id=(blur?"blur":"image")+"-"+t;
                    fx(()->{controller.model().pageProperty().set(MainWindowModel.Page.COMPRESS);return null;});contrast(id+"-compress",capture(id+"-compress"));
                    Path archive=compress(id,"ZIP");extract(id,archive);contrast(id+"-extract",capture(id+"-extract"));
                    restart(true,blur,t);
                    pass("switch-"+id,"Actual controls, ZIP create/extract and byte comparison");
                }
                restart(true,blur,100);
            }
            fx(()->{toggle("#settings-background-enabled").setSelected(false);return null;});state(false,true,100);capture("02-after-disabled");restart(false,true,100);
            fx(()->{toggle("#settings-background-enabled").setSelected(true);return null;});state(true,true,100);restart(true,true,100);capture("03-after-reenabled");
            pass("disabled-reenabled","Path, blur and transparency retained; solid fallback restored while off");
            fx(()->{ThemeManager.setDark(false);controller.model().pageProperty().set(MainWindowModel.Page.COMPRESS);return null;});contrast("light-blur-compress",capture("light-blur-compress"));
            Path seven=compress("light-blur","7z");extract("light-blur",seven);contrast("light-blur-extract",capture("light-blur-extract"));
            results.add("NOT_TESTED native-Acrylic-visual: previously failed; not retried or enabled in this run");
            results.add("NOT_TESTED native-Acrylic-disable-recovery: prior research evidence only; not newly exercised");
            results.add("NOT_TESTED Windows10 physical machine: unavailable");
        }catch(Throwable e){results.add("FAIL regression: "+e);throw e;}
        finally{report();fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
        if(results.stream().anyMatch(r->r.startsWith("FAIL ")))throw new AssertionError("Regression failed; see results.txt");
    }
}
