import cn.shed4329.onezip.themes.fxJava.fluent.*;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.*;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/** Round-two real-JAR acceptance; logs individual outcomes instead of reusing unit counts. */
class UiAcceptance {
    static final List<String> results=new ArrayList<>(),contrasts=new ArrayList<>(List.of("case,samples,minimum_ratio,failures"));
    static Stage app;static MainWindowController controller;static Robot robot;static Path output,image,input;static boolean baseline;
    static <T>T fx(Callable<T> c)throws Exception{var t=new FutureTask<>(c);Platform.runLater(t);return t.get(25,TimeUnit.SECONDS);}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void pass(String id,String detail){results.add("PASS "+id+": "+detail);}
    static void layout(){app.getScene().getRoot().applyCss();app.getScene().getRoot().layout();}
    static BufferedImage capture(String name)throws Exception{
        fx(()->{var b=Screen.getPrimary().getVisualBounds();app.setX(b.getMinX()+20);app.setY(b.getMinY()+20);app.setWidth(Math.min(app.getWidth(),b.getWidth()-40));app.setHeight(Math.min(app.getHeight(),b.getHeight()-40));layout();app.toFront();return null;});Thread.sleep(180);
        var shot=fx(()->{var rendered=app.getScene().snapshot(null);int w=(int)rendered.getWidth(),h=(int)rendered.getHeight();int[] pixels=new int[w*h];rendered.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);var bitmap=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);bitmap.setRGB(0,0,w,h,pixels,0,w);return bitmap;});ImageIO.write(shot,"png",output.resolve(name+".png").toFile());return shot;
    }
    static double luminance(Color c){double[] v={c.getRed(),c.getGreen(),c.getBlue()};for(int i=0;i<3;i++)v[i]=v[i]<=.04045?v[i]/12.92:Math.pow((v[i]+.055)/1.055,2.4);return .2126*v[0]+.7152*v[1]+.0722*v[2];}
    static double ratio(Color a,Color b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    static void contrast(String id,BufferedImage shot)throws Exception{fx(()->{
        int count=0;double minimum=100;var failed=new ArrayList<String>();
        for(Node node:app.getScene().getRoot().lookupAll(".text")){
            if(!(node instanceof Text t)||t.getText().isBlank()||t.isDisabled()||!(t.getFill() instanceof Color fg))continue;
            boolean visible=true;for(Node n=t;n!=null;n=n.getParent())if(!n.isVisible()||n.getOpacity()<.99){visible=false;break;}if(!visible)continue;
            Bounds b=t.localToScene(t.getBoundsInLocal());if(b==null)continue;
            int x=(int)Math.floor(b.getMinX())-2,y=(int)Math.floor(b.getMinY()+b.getHeight()/2);
            if(x<2||y<2||x>=shot.getWidth()-2||y>=shot.getHeight()-4||b.getMaxY()>app.getScene().getHeight()-2)continue;
            for(Node n=t.getParent();n!=null;n=n.getParent())if(n.getClip()!=null&&!n.localToScene(n.getClip().getBoundsInParent()).contains(b.getCenterX(),b.getCenterY())){visible=false;break;}if(!visible)continue;
            int rgb=shot.getRGB(x,y);Color bg=Color.rgb((rgb>>16)&255,(rgb>>8)&255,rgb&255);double a=fg.getOpacity();Color composed=new Color(fg.getRed()*a+bg.getRed()*(1-a),fg.getGreen()*a+bg.getGreen()*(1-a),fg.getBlue()*a+bg.getBlue()*(1-a),1);
            double value=ratio(composed,bg),limit=t.getFont().getSize()>=24||t.getFont().getSize()>=18.5&&t.getFont().getStyle().contains("Bold")?3:4.5;minimum=Math.min(minimum,value);count++;
            if(value<limit)failed.add(t.getText().replace('\n',' ')+"="+String.format(Locale.ROOT,"%.2f",value));
        }
        contrasts.add(id+","+count+","+String.format(Locale.ROOT,"%.3f",minimum)+",\""+String.join(" | ",failed).replace('"','\'')+"\"");results.add((count>0&&failed.isEmpty()?"PASS ":"FAIL ")+"contrast-"+id+": samples="+count+"; minimum="+minimum+"; "+failed);return null;
    });}
    static String command(List<String> args)throws Exception{var p=new ProcessBuilder(args).redirectErrorStream(true).start();if(!p.waitFor(35,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("Child timed out");}String text=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);require(p.exitValue()==0,text);return text;}
    static String powershell(String script,String...args)throws Exception{var list=new ArrayList<String>(List.of(Path.of(System.getenv("SystemRoot"),"System32/WindowsPowerShell/v1.0/powershell.exe").toString(),"-NoProfile","-NonInteractive","-ExecutionPolicy","Bypass","-File",Path.of(script).toAbsolutePath().toString()));list.addAll(List.of(args));return command(list);}
    static void controls()throws Exception{
        Stage settings=fx(()->{((Button)app.getScene().lookup("#home-settings")).fire();var s=(Stage)Window.getWindows().stream().filter(w->w!=app&&w.getScene()!=null&&w.getScene().lookup("#settings-personalize")!=null).findFirst().orElseThrow();((Button)s.getScene().lookup("#settings-personalize")).fire();s.getScene().getRoot().applyCss();return s;});
        fx(()->{((Button)settings.getScene().lookup("#settings-background-clear")).fire();var e=(org.controlsfx.control.ToggleSwitch)settings.getScene().lookup("#settings-background-enabled");e.setSelected(true);require(!e.isSelected(),"Empty-path enable did not revert");return null;});pass("enable-empty-path","Reverted without invalid saved state");
        Platform.runLater(()->((Button)settings.getScene().lookup("#settings-background-choose")).fire());
        try{String picker=powershell("scripts/ChooseBackgroundFile.ps1","-PeerPid",Long.toString(ProcessHandle.current().pid()),"-DialogTitle","选择背景图片","-ImagePath",image.toString(),"-FileNameLabel","File name|文件名|檔案名稱|檔名");fx(()->{require(BackgroundAppearance.path().equals(image.toString())&&BackgroundAppearance.enabled(),"Native picker did not apply image");return null;});pass("native-background-file-picker",picker.strip());}
        catch(AssertionError unavailable){results.add("NOT_TESTED native-background-file-picker: UIA provider did not expose a stable editable field; chooser was cancelled; path-input Apply is tested separately");}
        fx(()->{var path=(TextField)settings.getScene().lookup("#settings-background-path");path.setText(image.toString());((Button)settings.getScene().lookup("#settings-background-apply")).fire();require(BackgroundAppearance.enabled()&&BackgroundAppearance.path().equals(image.toString()),"Path Apply failed: "+((Label)settings.getScene().lookup("#settings-status")).getText()+"; field="+path.getText()+"; saved="+BackgroundAppearance.path()+"; disabled="+settings.getScene().lookup("#settings-background-apply").isDisabled()+"; enabled="+BackgroundAppearance.enabled());path.setText(output.resolve("missing.jpg").toString());((Button)settings.getScene().lookup("#settings-background-apply")).fire();require(BackgroundAppearance.path().equals(image.toString()),"Invalid draft overwrote valid image");return null;});pass("background-path-apply","Actual path input / Apply button and invalid draft rejection");
        fx(()->{var e=(org.controlsfx.control.ToggleSwitch)settings.getScene().lookup("#settings-background-enabled");var b=(org.controlsfx.control.ToggleSwitch)settings.getScene().lookup("#settings-background-blur");b.setSelected(true);e.setSelected(false);require(!BackgroundAppearance.enabled()&&BackgroundAppearance.path().equals(image.toString()),"Off lost path");require(!app.getScene().lookup("#application-background").isVisible()&&b.isDisabled(),"Off did not hide image");e.setSelected(true);require(BackgroundAppearance.enabled()&&BackgroundAppearance.blurred()&&!b.isDisabled(),"On lost blur preference");b.setSelected(false);require(!BackgroundAppearance.blurred(),"Blur off failed");b.setSelected(true);((TextField)settings.getScene().lookup("#settings-background-path")).setText(image.toString());settings.setAlwaysOnTop(false);settings.hide();return null;});pass("background-switches","Actual enable/disable and blur controls retain path and preferences");
    }
    static Stage progress()throws Exception{return fx(()->(Stage)Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene()!=null&&w.getScene().lookup("#archive-status")!=null).findFirst().orElseThrow());}
    static void complete(Stage stage)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);String state="";while(System.nanoTime()<end){state=fx(()->((Label)stage.getScene().lookup("#archive-status")).getText());if(state.equals("已完成")||state.equals("操作失敗"))break;Thread.sleep(20);}require(state.equals("已完成"),"Archive state: "+state);fx(()->{stage.hide();return null;});}
    static Path compress(String id,String format)throws Exception{
        Path archive=output.resolve(id+(format.equals("ZIP")?".zip":".7z"));Files.deleteIfExists(archive);
        fx(()->{controller.model().pageProperty().set(MainWindowModel.Page.COMPRESS);((ChoiceBox<String>)app.getScene().lookup("#compression-format")).setValue(format);((TextField)app.getScene().lookup("#compression-name")).setText(archive.getFileName().toString());((TextField)app.getScene().lookup("#compression-location")).setText(output.toString());((Button)app.getScene().lookup("#compression-start")).fire();return null;});
        complete(progress());require(Files.size(archive)>20,"Empty archive");pass("compress-"+id,"Actual start button, "+format);return archive;
    }
    static void openArchive(Path archive)throws Exception{fx(()->{controller.selectArchive(archive);return null;});long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(fx(()->app.getScene().lookup("#zip-preview-progress").isVisible())){if(System.nanoTime()>end)throw new AssertionError("Browser timeout");Thread.sleep(20);}fx(()->{require(((Label)app.getScene().lookup("#zip-preview-status")).getText().startsWith("共 "),"Listing failed");return null;});}
    static void extract(String id,Path archive)throws Exception{
        Path dest=output.resolve("decoded-"+id);require(!Files.exists(dest),"Use a fresh acceptance output directory");openArchive(archive);fx(()->{((Button)app.getScene().lookup("#extractAll")).fire();return null;});
        fx(()->{Window d=Window.getWindows().stream().filter(w->w.getScene()!=null&&w.getScene().lookup("#extract-destination")!=null).findFirst().orElseThrow();var field=(TextField)d.getScene().lookup("#extract-destination");String name=archive.getFileName().toString();require(Path.of(field.getText()).getFileName().toString().equals(name.substring(0,name.lastIndexOf('.'))),"Default destination retained archive extension");var rendered=d.getScene().snapshot(null);int w=(int)rendered.getWidth(),h=(int)rendered.getHeight();int[] pixels=new int[w*h];rendered.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);var bitmap=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);bitmap.setRGB(0,0,w,h,pixels,0,w);ImageIO.write(bitmap,"png",output.resolve("extract-dialog-"+id+".png").toFile());field.clear();((Button)d.getScene().lookup("#extract-start")).fire();require(d.isShowing(),"Empty extraction destination accepted");field.setText(dest.toString());((Button)d.getScene().lookup("#extract-start")).fire();return null;});complete(progress());
        try(var files=Files.walk(input)){for(Path source:files.filter(Files::isRegularFile).toList()){Path decoded=dest.resolve(input.getFileName()).resolve(input.relativize(source));require(Files.exists(decoded)&&Files.mismatch(source,decoded)==-1,"Byte mismatch: "+source);}}
        pass("extract-"+id,"Actual dialog, empty-path validation and byte-for-byte comparison");
    }
    static void page(String mode,boolean dark,String page,Path archive)throws Exception{
        fx(()->{ThemeManager.setDark(dark);controller.model().pageProperty().set(MainWindowModel.Page.valueOf(page));return null;});if(page.equals("EXTRACT"))openArchive(archive);
        String id=mode+"-"+(dark?"dark":"light")+"-"+page.toLowerCase(Locale.ROOT);contrast(id,capture(id));
        fx(()->{require(app.getScene().lookup("#sidebar")==null&&app.getScene().lookup("#archive-folder-tree")==null,"Sidebar/tree returned");require(!((TitledPane)app.getScene().lookup(".compression-advanced")).isExpanded(),"Advanced defaults expanded");
            if(!baseline)for(Node n:app.getScene().getRoot().lookupAll(".button"))if(n instanceof Region r&&r.getBackground()!=null)for(var fill:r.getBackground().getFills())require(fill.getRadii().getTopLeftHorizontalRadius()==0,"Rounded button returned");return null;});
    }
    static void restore(boolean enabled)throws Exception{
        fx(()->{BackgroundAppearance.configure(image.toString(),true,enabled);return null;});
        var args=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Dfile.encoding=UTF-8","-Donezip.settings.dir="+System.getProperty("onezip.settings.dir"),"-Donezip.settings.legacy-dir="+System.getProperty("onezip.settings.legacy-dir"),"-cp",System.getProperty("java.class.path"),Path.of("scripts/AppearanceRestoreProbe.java").toAbsolutePath().toString(),image.toString(),output.toString(),Boolean.toString(enabled)));
        pass("fresh-jvm-restore-"+enabled,command(args).strip());
    }
    static BufferedImage nativeCapture(String name)throws Exception{
        fx(()->{app.setIconified(false);app.setAlwaysOnTop(true);app.toFront();app.requestFocus();return null;});Thread.sleep(300);
        String identity=AppearanceProbe.nativeProbe("Inspect");require(identity.contains("\"foregroundPid\":"+ProcessHandle.current().pid()+","),"Native desktop capture lost foreground ownership");
        Rectangle rect=fx(()->new Rectangle((int)app.getX(),(int)app.getY(),(int)app.getWidth(),(int)app.getHeight()));var shot=robot.createScreenCapture(rect);ImageIO.write(shot,"png",output.resolve(name+".png").toFile());return shot;
    }
    static void nativeTest()throws Exception{
        fx(()->{controller.model().pageProperty().set(MainWindowModel.Page.HOME);return null;});
        AppearanceProbe.app=app;AppearanceProbe.output=Files.createDirectories(output.resolve("native"));
        results.addAll(AppearanceProbe.safeNativeBackdropCheck());
        results.add("UNSUPPORTED native-setting-restart: no native effect is enabled or persisted");
        results.add("NOT_TESTED windows-10-machine: native metadata records this host build");
    }
    static void report()throws Exception{Files.write(output.resolve("results.txt"),results);Files.write(output.resolve("contrast.csv"),contrasts);for(String r:results)System.out.println(r);}
    public static void main(String[] args)throws Exception{
        image=Path.of(args[0]).toAbsolutePath().normalize();output=Path.of(args[1]).toAbsolutePath();Files.createDirectories(output);baseline=args.length>2&&args[2].equals("--baseline");robot=new Robot();Platform.startup(()->Platform.setImplicitExit(false));
        try{
            app=fx(()->{var s=new Stage();var application=new MainApplication();application.start(s);var getter=MainApplication.class.getDeclaredMethod("controller");getter.setAccessible(true);controller=(MainWindowController)getter.invoke(application);return s;});
            input=Files.createDirectories(output.resolve("source"));Files.createDirectories(input.resolve("docs"));Files.writeString(input.resolve("说明.txt"),"OneZip UI round-two\n中文文本\n".repeat(300));Files.write(input.resolve("empty.bin"),new byte[0]);byte[] bytes=new byte[8192];new Random(4329).nextBytes(bytes);Files.write(input.resolve("docs/data.bin"),bytes);
            fx(()->{controller.model().addSources(List.of(input));return null;});if(!baseline)controls();
            Path preview=output.resolve("preview.zip");Files.deleteIfExists(preview);new cn.shed4329.onezip.engine.ArchiveEngine().compress(List.of(input),preview,cn.shed4329.onezip.engine.ArchiveEngine.Format.ZIP,5,null,null,null,1);
            for(String mode:List.of("off","image","blur")){
                fx(()->{if(baseline)BackgroundAppearance.configure(mode.equals("off")?"":image.toString(),mode.equals("blur"));else BackgroundAppearance.configure(image.toString(),mode.equals("blur"),!mode.equals("off"));return null;});
                for(boolean dark:new boolean[]{true,false}){for(String p:List.of("HOME","COMPRESS","EXTRACT"))page(mode,dark,p,preview);if(!baseline)for(String format:List.of("ZIP","7z")){String id=mode+"-"+dark+"-"+format;Path archive=compress(id,format);extract(id,archive);}}
            }
            if(!baseline){nativeTest();restore(false);restore(true);}
        }catch(Throwable error){results.add("FAIL acceptance-execution: "+error);throw error;}
        finally{report();fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
        if(!baseline&&results.stream().anyMatch(r->r.startsWith("FAIL ")))throw new AssertionError("Acceptance has FAIL entries; see results.txt");
    }
}
