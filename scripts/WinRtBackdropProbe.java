import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.StackPane;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import javax.imageio.ImageIO;
class WinRtBackdropProbe extends AppearanceProbe {
 static Process child;static String nonce=UUID.randomUUID().toString();static int sequence;static Path control;static Canvas canvas;
 static long number(String json,String key){var m=Pattern.compile("\""+key+"\":(-?[0-9]+)").matcher(json);check(m.find(),"Missing "+key+" in "+json);return Long.parseLong(m.group(1));}
 static String state()throws Exception {if(Files.exists(control.resolve("error.txt")))throw new AssertionError(Files.readString(control.resolve("error.txt")));return Files.exists(control.resolve("state.json"))?readState():"";}
 static String readState(){try{return Files.readString(control.resolve("state.json"));}catch(java.io.IOException moving){return "";}}
 static void command(String mode)throws Exception{int seq=++sequence;Files.writeString(control.resolve("command.json"),"{\"seq\":"+seq+",\"nonce\":\""+nonce+"\",\"mode\":\""+mode+"\"}");if(mode.equals("stop"))return;long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(System.nanoTime()<end){String s=state();if(!s.isEmpty()&&number(s,"seq")==seq&&s.contains("\"foreground\":true"))return;Thread.sleep(50);}throw new AssertionError("WinRT command/foreground timeout: "+state());}
 static String focusState()throws Exception{
  long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(System.nanoTime()<end){String s=state();if(s.isEmpty()){Thread.sleep(30);continue;}check(s.contains(nonce)&&number(s,"pid")==child.pid(),"Foreign native target");if(s.contains("\"foreground\":true"))return s;
   check(s.contains("\"ownTitle\":true"),"Native title is occluded; refusing input");var scale=java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform();var r=new java.awt.Robot();r.mouseMove((int)((number(s,"x")+450)/scale.getScaleX()),(int)((number(s,"y")+16)/scale.getScaleY()));r.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);r.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);Thread.sleep(180);
  }throw new AssertionError("Native target focus not acquired");
 }
 static java.awt.image.BufferedImage captureHost(String id)throws Exception{
  Thread.sleep(250);String s=focusState();check(s.contains(nonce)&&number(s,"pid")==child.pid()&&s.contains("\"foreground\":true"),"Foreign or inactive native target");var scale=java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform();var r=new java.awt.Rectangle((int)Math.round(number(s,"x")/scale.getScaleX()),(int)Math.round(number(s,"y")/scale.getScaleY()),(int)Math.round(number(s,"width")/scale.getScaleX()),(int)Math.round(number(s,"height")/scale.getScaleY()));var image=new java.awt.Robot().createScreenCapture(r);ImageIO.write(image,"png",output.resolve(id+".png").toFile());return image;
 }
 public static void main(String[] args)throws Exception{
  output=Files.createDirectories(Path.of(args[0]).toAbsolutePath());control=Files.createDirectories(output.resolve("control"));Platform.startup(()->Platform.setImplicitExit(false));var results=new ArrayList<String>();
  try{
   canvas=new Canvas(1100,760);fx(()->{pattern(canvas,false);var s=new Stage(StageStyle.UNDECORATED);s.setScene(new Scene(new StackPane(canvas),1100,760));s.setAlwaysOnTop(false);s.setX(0);s.setY(0);s.show();return null;});
   child=new ProcessBuilder(Path.of(System.getenv("SystemRoot"),"System32/WindowsPowerShell/v1.0/powershell.exe").toString(),"-NoProfile","-NonInteractive","-WindowStyle","Hidden","-ExecutionPolicy","Bypass","-File",Path.of("scripts/WinRtBackdropExperiment.ps1").toAbsolutePath().toString(),"-Directory",control.toString(),"-ParentPid",Long.toString(ProcessHandle.current().pid()),"-Nonce",nonce).redirectErrorStream(true).redirectOutput(control.resolve("process.log").toFile()).start();
   long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);while(state().isEmpty()){if(!child.isAlive()||System.nanoTime()>end)throw new AssertionError("WinRT startup: "+Files.readString(control.resolve("process.log")));Thread.sleep(100);}
   results.add("WinRT initialized: "+state());
   for(String mode:List.of("clear","blur","plain","blur","off")){
    String id=mode+"-"+sequence;fx(()->{pattern(canvas,false);return null;});command(mode);var a=captureHost(id+"-a");fx(()->{pattern(canvas,true);return null;});var b=captureHost(id+"-b");double response=difference(a,b,80,100,160,90),edge=variation(a,80,100,160,90);String line=mode+" response="+response+" edges="+edge+" actualHostBlur="+(response>8&&edge<5);results.add(line);System.out.println(line);
   }
  }catch(Throwable e){results.add("FAIL "+e);throw e;}
  finally{if(child!=null&&child.isAlive()){command("stop");if(!child.waitFor(8,TimeUnit.SECONDS))child.destroyForcibly();}results.add("childEnded="+(child==null||!child.isAlive())+" closedMarker="+Files.exists(control.resolve("closed.txt")));Files.write(output.resolve("results.txt"),results);fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
 }
}
