import cn.shed4329.onezip.themes.fxJava.fluent.*;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
class OneZipNativeCarrierProbe extends WinRtBackdropProbe {
 static MainWindowController controller;
 static java.awt.image.BufferedImage screen(String id)throws Exception{
  fx(()->{app.toFront();app.requestFocus();app.getScene().getRoot().applyCss();app.getScene().getRoot().layout();return null;});Thread.sleep(180);
  String state="";long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
  while(System.nanoTime()<end){state=state();if(state.isEmpty()){Thread.sleep(30);continue;}if(state.contains("\"foreground\":true"))break;
   check(state.contains("\"ownFooter\":true"),"Main window footer occluded; refusing input");var p=fx(()->new int[]{(int)app.getX()+16,(int)(app.getY()+app.getHeight())-13});var robot=new java.awt.Robot();robot.mouseMove(p[0],p[1]);robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);Thread.sleep(150);
  }
  check(state.contains(nonce)&&number(state,"pid")==child.pid()&&state.contains("\"foreground\":true"),"Exact OneZip foreground window lost");
  var rect=fx(()->new java.awt.Rectangle((int)app.getX(),(int)app.getY(),(int)app.getWidth(),(int)app.getHeight()));var bitmap=new java.awt.Robot().createScreenCapture(rect);javax.imageio.ImageIO.write(bitmap,"png",output.resolve(id+".png").toFile());return bitmap;
 }
 static void photo(Path file)throws Exception{fx(()->{Image image=new Image(file.toUri().toString());var g=canvas.getGraphicsContext2D();double scale=Math.max(canvas.getWidth()/image.getWidth(),canvas.getHeight()/image.getHeight());g.drawImage(image,(canvas.getWidth()-image.getWidth()*scale)/2,(canvas.getHeight()-image.getHeight()*scale)/2,image.getWidth()*scale,image.getHeight()*scale);return null;});}
 public static void main(String[] args)throws Exception{
  output=Files.createDirectories(Path.of(args[1]).toAbsolutePath());control=Files.createDirectories(output.resolve("control"));var results=new ArrayList<String>();Platform.startup(()->Platform.setImplicitExit(false));
  try{
   canvas=new Canvas(1500,900);fx(()->{pattern(canvas,false);var s=new Stage(StageStyle.UNDECORATED);s.setScene(new Scene(new StackPane(canvas),1500,900));s.setX(0);s.setY(0);s.show();return null;});
   app=fx(()->{var s=new Stage(StageStyle.TRANSPARENT);var application=new MainApplication();application.start(s);var m=MainApplication.class.getDeclaredMethod("controller");m.setAccessible(true);controller=(MainWindowController)m.invoke(application);ThemeManager.setDark(true);BackgroundAppearance.configure("",false,false);BackgroundAppearance.previewControlsTransparency(100);var root=s.getScene().getRoot();root.pseudoClassStateChanged(PseudoClass.getPseudoClass("wallpaper"),true);root.setStyle(root.getStyle()+"-onezip-surface:transparent;-fx-background-color:rgba(12,12,12,.60);");s.getScene().setFill(Color.TRANSPARENT);s.setTitle("OneZip Native Composition Lab "+ProcessHandle.current().pid());s.setWidth(1180);s.setHeight(760);s.setX(20);s.setY(20);s.setAlwaysOnTop(true);s.toFront();return s;});
   String title=fx(()->app.getTitle());
   child=new ProcessBuilder(Path.of(System.getenv("SystemRoot"),"System32/WindowsPowerShell/v1.0/powershell.exe").toString(),"-NoProfile","-NonInteractive","-WindowStyle","Hidden","-ExecutionPolicy","Bypass","-File",Path.of("scripts/WinRtBackdropExperiment.ps1").toAbsolutePath().toString(),"-Directory",control.toString(),"-ParentPid",Long.toString(ProcessHandle.current().pid()),"-Nonce",nonce,"-TargetTitle",title).redirectErrorStream(true).redirectOutput(control.resolve("process.log").toFile()).start();
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);while(state().isEmpty()){if(!child.isAlive()||System.nanoTime()>deadline)throw new AssertionError("Carrier startup "+Files.readString(control.resolve("process.log")));Thread.sleep(100);}results.add("carrier initialized: "+state());
   for(String mode:List.of("clear","blur","clear")){
    fx(()->{pattern(canvas,false);app.toFront();app.requestFocus();return null;});command(mode);String id=mode+"-"+sequence;var a=screen(id+"-a");fx(()->{pattern(canvas,true);return null;});var b=screen(id+"-b");double response=difference(a,b,40,110,140,90),edge=variation(a,40,110,140,90);check(response>8,"Backdrop no longer follows independent content");check(mode.equals("blur")?edge<3:edge>4,"Blur/raw edge check failed");String line="PASS "+mode+" independent response="+response+" edges="+edge;results.add(line);System.out.println(line);
   }
   photo(Path.of(args[0]));command("clear");screen("photo-before-home");command("blur");var home=screen("photo-after-home");
   UiAcceptance.app=app;UiAcceptance.controller=controller;UiAcceptance.output=output;UiAcceptance.input=Files.createDirectories(output.resolve("source"));Files.writeString(UiAcceptance.input.resolve("说明.txt"),"OneZip real native backdrop archive workflow\n".repeat(200));
   UiAcceptance.contrast("native-home",home);
   fx(()->{controller.model().addSources(List.of(UiAcceptance.input));controller.model().pageProperty().set(MainWindowModel.Page.COMPRESS);return null;});UiAcceptance.contrast("native-compress",screen("photo-after-compress"));
   Path archive=UiAcceptance.compress("native-ui","ZIP");UiAcceptance.extract("native-ui",archive);results.addAll(UiAcceptance.results);UiAcceptance.contrast("native-extract",screen("photo-after-extract"));
   fx(()->{app.setX(70);app.setY(50);app.setWidth(1100);app.setHeight(720);return null;});Thread.sleep(250);screen("moved-resized-extract");String current=state();double scale=java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform().getScaleX();check(Math.abs(number(current,"width")/scale-fx(()->app.getWidth()))<2,"Carrier width did not follow");results.add("PASS native carrier follows resized parent");
   fx(()->{check(app.getOpacity()==1,"Whole window opacity changed");check(!app.getScene().lookup("#application-background").isVisible(),"Image wallpaper simulated system backdrop");return null;});results.add("PASS no application wallpaper/GaussianBlur used; stageOpacity=1");
  }catch(Throwable error){results.add("FAIL "+error);throw error;}
  finally{
   if(child!=null&&child.isAlive()){Files.writeString(control.resolve("command.json"),"{\"seq\":"+(++sequence)+",\"nonce\":\""+nonce+"\",\"mode\":\"stop\"}");if(!child.waitFor(8,TimeUnit.SECONDS))child.destroyForcibly();}
   results.add("nativeChildEnded="+(child==null||!child.isAlive())+" normalCleanup="+Files.exists(control.resolve("closed.txt")));
   if(app!=null){fx(()->{app.getScene().getRoot().setStyle("");app.getScene().getRoot().pseudoClassStateChanged(PseudoClass.getPseudoClass("wallpaper"),false);app.getScene().setFill(Color.WHITE);return null;});var restored=fx(()->app.getScene().snapshot(null));int w=(int)restored.getWidth(),h=(int)restored.getHeight();int[] pixels=new int[w*h];restored.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);var bitmap=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);bitmap.setRGB(0,0,w,h,pixels,0,w);javax.imageio.ImageIO.write(bitmap,"png",output.resolve("safe-normal-fallback.png").toFile());}
   Files.write(output.resolve("results.txt"),results);Files.write(output.resolve("contrast.csv"),UiAcceptance.contrasts);fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();
  }
 }
}
