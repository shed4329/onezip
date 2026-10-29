import cn.shed4329.onezip.i18n.I18n;
import cn.shed4329.onezip.setting.*;
import cn.shed4329.onezip.engine.*;
import cn.shed4329.onezip.themes.fxJava.fluent.*;
import cn.shed4329.onezip.themes.fxJava.fluent.component.ArchiveDialogs;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.*;
import org.controlsfx.control.ToggleSwitch;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static cn.shed4329.onezip.i18n.I18n.t;

class SettingsSmartProbe extends UiAcceptance {
    static void cancellationWaitsForCleanup()throws Exception{
        var began=new CountDownLatch(1);var cleaning=new CountDownLatch(1);var release=new CountDownLatch(1);
        javafx.concurrent.Task<Path> task=new javafx.concurrent.Task<>(){
            protected Path call()throws Exception{
                began.countDown();
                try{while(!isCancelled())Thread.sleep(10);return output;}
                finally{cleaning.countDown();boolean done=false;while(!done)try{done=release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ignored){}}
            }
        };
        Stage dialog=fx(()->ArchiveDialogs.showProgress(app,task,"cleanup barrier",false));
        try{
            require(began.await(5,TimeUnit.SECONDS),"Task did not start");
            fx(()->{((Button)dialog.getScene().lookup("#archive-cancel")).fire();return null;});
            require(cleaning.await(5,TimeUnit.SECONDS),"Cleanup did not start");
            fx(()->{require(((Button)dialog.getScene().lookup("#archive-cancel")).isDisabled(),"Close enabled before rollback finished");dialog.fireEvent(new WindowEvent(dialog,WindowEvent.WINDOW_CLOSE_REQUEST));require(dialog.isShowing(),"Close bypassed rollback barrier");return null;});
        }finally{release.countDown();}
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<end&&fx(()->((Button)dialog.getScene().lookup("#archive-cancel")).isDisabled()))Thread.sleep(10);
        fx(()->{require(!((Button)dialog.getScene().lookup("#archive-cancel")).isDisabled(),"Close stayed disabled after cleanup");require(((Label)dialog.getScene().lookup("#archive-status")).getText().equals(t("progress.cancelled")),"Final cancellation state wrong");dialog.close();return null;});
        pass("cancellation-cleanup-barrier","Close waits for actual worker cleanup, then becomes available");
    }
    static void shot(Stage s,String id)throws Exception{fx(()->{s.getScene().getRoot().applyCss();s.getScene().getRoot().layout();var im=s.getScene().snapshot(null);int w=(int)im.getWidth(),h=(int)im.getHeight();int[] p=new int[w*h];im.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),p,0,w);var b=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);b.setRGB(0,0,w,h,p,0,w);javax.imageio.ImageIO.write(b,"png",output.resolve(id+".png").toFile());return null;});}
    static Path archive(String name,List<Path> sources,boolean seven,char[] password)throws Exception{Path p=output.resolve("archives").resolve(name+(seven?".7z":".zip"));new ArchiveEngine().compress(sources,p,seven?ArchiveEngine.Format.SEVEN_Z:ArchiveEngine.Format.ZIP,5,password,null,null,1);return p;}
    static void run(Path archive,Path base,boolean smart,String password,boolean failure,Path expected)throws Exception{
        fx(()->{ArchiveDialogs.showExtraction(app,archive,ThemeManager.isDark());return null;});Stage d=fx(()->(Stage)Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene()!=null&&w.getScene().lookup("#extract-destination")!=null).findFirst().orElseThrow());
        fx(()->{var box=(CheckBox)d.getScene().lookup("#extract-smart");require(box.isSelected()==ExtractionPreferences.smart(),"Global smart default not applied");box.setSelected(smart);((TextField)d.getScene().lookup("#extract-destination")).setText(base.toString());((PasswordField)d.getScene().lookup("#extract-password")).setText(password);return null;});shot(d,"dialog-"+archive.getFileName()+"-"+smart);
        fx(()->{((Button)d.getScene().lookup("#extract-start")).fire();return null;});Stage s=progress();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);String state="";while(System.nanoTime()<end){state=fx(()->((Label)s.getScene().lookup("#archive-status")).getText());if(state.equals(t("progress.completed"))||state.equals(t("progress.failed")))break;Thread.sleep(20);}require(state.equals(t(failure?"progress.failed":"progress.completed")),"Unexpected task result "+state);
        if(!failure)require(fx(()->((Label)s.getScene().lookup("#archive-current-file")).getText().equals(expected.toAbsolutePath().normalize().toString())),"Resolved destination not shown");shot(s,"result-"+archive.getFileName()+"-"+smart);fx(()->{s.hide();return null;});pass("extract-"+archive.getFileName()+"-"+smart,failure?"Expected safe failure":"Actual smart task resolved "+expected);
    }
    public static void main(String[] args)throws Exception{
        output=Files.createDirectories(Path.of(args[1]).toAbsolutePath());if(args.length<3)SettingsStore.shared().put("appearance.language",args[0]);I18n.initialize();Platform.startup(()->Platform.setImplicitExit(false));
        if(Boolean.getBoolean("onezip.probe.diagnostics"))Thread.ofPlatform().daemon().start(()->{try{Thread.sleep(8000);Platform.runLater(()->{for(var w:Window.getWindows())if(w.isShowing()&&w.getScene()!=null)for(var n:w.getScene().getRoot().lookupAll(".label"))if(n instanceof Label l)System.err.println("VISIBLE LABEL: "+l.getText());});}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        try{
            app=fx(()->{var s=new Stage();var a=new MainApplication();a.start(s);var m=MainApplication.class.getDeclaredMethod("controller");m.setAccessible(true);controller=(MainWindowController)m.invoke(a);((Button)s.getScene().lookup("#home-settings")).fire();return s;});Stage settings=fx(()->(Stage)Window.getWindows().stream().filter(w->w!=app&&w.isShowing()&&w.getScene()!=null&&w.getScene().lookup("#settings-general")!=null).findFirst().orElseThrow());
            if(args.length>2){require(ExtractionPreferences.smart(),"Smart default lost on restart");require(fx(()->((ToggleSwitch)settings.getScene().lookup("#settings-smart-extract")).isSelected()),"Restored toggle wrong");shot(settings,"restored");pass("restart","Smart preference and UI restored");return;}
            fx(()->{char[] supplied=new char[]{'t','e','s','t'};var task=cn.shed4329.onezip.application.ArchiveTask.extract(output.resolve("not-opened.zip"),output.resolve("cancelled-before-start"),supplied);task.cancel();var field=task.getClass().getDeclaredField("password");field.setAccessible(true);for(char c:(char[])field.get(task))require(c==0,"Cancelled queued task retained password");require(supplied[0]=='t',"Task modified caller password");java.util.Arrays.fill(supplied,'\0');return null;});pass("queued-cancel-password","Owned password cleared before task runs; caller array untouched");
            cancellationWaitsForCleanup();
            fx(()->{ThemeManager.setDark(true);((ToggleSwitch)settings.getScene().lookup("#settings-smart-extract")).setSelected(true);require(ExtractionPreferences.smart(),"Setting is not wired");return null;});shot(settings,"01-general-dark");
            fx(()->{((Button)settings.getScene().lookup("#settings-personalize")).fire();((ColorPicker)settings.getScene().lookup("#settings-accent-color")).setValue(javafx.scene.paint.Color.web("#008272"));((ColorPicker)settings.getScene().lookup("#settings-accent-color")).fireEvent(new javafx.event.ActionEvent());var field=(TextField)settings.getScene().lookup("#settings-background-path");field.setText(probeImage());((Button)settings.getScene().lookup("#settings-background-apply")).fire();require(BackgroundAppearance.enabled(),"Background apply failed");((ToggleSwitch)settings.getScene().lookup("#settings-background-blur")).setSelected(true);((Slider)settings.getScene().lookup("#settings-controls-transparency")).setValue(100);((Button)settings.getScene().lookup("#settings-general")).fire();require(BackgroundAppearance.controlsTransparency()==100,"Navigation lost pending transparency save");return null;});
            for(String id:List.of("settings-personalize","settings-language","settings-about","settings-general")){fx(()->{((Button)settings.getScene().lookup("#"+id)).fire();return null;});shot(settings,"page-"+id);}
            fx(()->{((Button)settings.getScene().lookup("#settings-personalize")).fire();((ToggleSwitch)settings.getScene().lookup("#settings-dark-mode")).setSelected(false);require(!ThemeManager.isDark(),"Dark switch not wired");return null;});shot(settings,"02-appearance-light");fx(()->{settings.hide();return null;});
            Path src=Files.createDirectories(output.resolve("source"));Path a=Files.writeString(src.resolve("a_日本語.txt"),"alpha"),b=Files.writeString(src.resolve("b_中文.txt"),"beta");Path folder=Files.createDirectories(src.resolve("project"));Files.writeString(folder.resolve("inside.txt"),"inside");
            Path one=archive("single",List.of(a),false,null),oneFolder=archive("folder",List.of(folder),false,null),mixed=archive("mixed",List.of(a,b),false,null),seven=archive("mixed7",List.of(a,b),true,null),encrypted=archive("encrypted",List.of(a,b),false,"secret".toCharArray());
            Path singleBase=output.resolve("decoded-single");run(one,singleBase,true,"",false,singleBase);require(Files.readString(singleBase.resolve(a.getFileName())).equals("alpha"),"Single file nested incorrectly");
            Path folderBase=output.resolve("decoded-folder");run(oneFolder,folderBase,true,"",false,folderBase);require(Files.readString(folderBase.resolve("project/inside.txt")).equals("inside"),"Folder nested incorrectly");
            for(Path p:List.of(mixed,seven,encrypted)){Path base=output.resolve("decoded-"+p.getFileName());Path target=base.resolve(ExtractionLayout.folderName(p));run(p,base,true,p.equals(encrypted)?"secret":"",false,target);require(Files.readString(target.resolve(a.getFileName())).equals("alpha")&&Files.readString(target.resolve(b.getFileName())).equals("beta"),"Mixed output wrong");}
            Path flat=output.resolve("decoded-normal");run(mixed,flat,false,"",false,flat);require(Files.exists(flat.resolve(a.getFileName()))&&!Files.exists(flat.resolve("mixed")),"Per-task ordinary override ignored");
            Path clash=output.resolve("decoded-clash");Files.createDirectories(clash.resolve("mixed"));Path existing=Files.writeString(clash.resolve("mixed").resolve(b.getFileName()),"keep");run(mixed,clash,true,"",true,null);require(Files.readString(existing).equals("keep")&&!Files.exists(clash.resolve("mixed").resolve(a.getFileName())),"Collision overwrote or leaked output");pass("no-overwrite","Existing file preserved and partial output removed");
            Path wrong=output.resolve("wrong-password");run(encrypted,wrong,true,"wrong",true,null);require(!Files.exists(wrong),"Wrong password left output behind");pass("wrong-password","Wrong password leaves no output after task cleanup");
            var child=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Dfile.encoding=UTF-8","-Donezip.settings.dir="+System.getProperty("onezip.settings.dir"),"-Donezip.settings.legacy-dir="+System.getProperty("onezip.settings.legacy-dir"),"-cp",System.getProperty("java.class.path"),"SettingsSmartProbe",args[0],output.resolve("restart").toString(),"restore"));
            String modulePath=System.getProperty("onezip.probe.module-path");
            if(modulePath!=null){
                var access=new ArrayList<String>(List.of("--module-path",modulePath,"--add-modules","cn.shed4329.onezip","-Donezip.probe.module-path="+modulePath));
                for(String pkg:List.of("i18n","application","themes.fxJava.fluent","themes.fxJava.fluent.component"))access.addAll(List.of("--add-exports","cn.shed4329.onezip/cn.shed4329.onezip."+pkg+"=ALL-UNNAMED"));
                access.addAll(List.of("--add-opens","cn.shed4329.onezip/cn.shed4329.onezip.themes.fxJava.fluent=ALL-UNNAMED","--add-opens","cn.shed4329.onezip/cn.shed4329.onezip.application=ALL-UNNAMED"));
                child.addAll(1,access);
            }
            pass("fresh-jvm",command(child).strip());
        }catch(Throwable e){results.add("FAIL settings-smart: "+e);throw e;}
        finally{report();fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
    }
}
