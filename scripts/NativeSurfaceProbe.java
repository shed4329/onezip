import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.StackPane;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
class NativeSurfaceProbe extends AppearanceProbe {
 static native boolean start(int options);
 static native int mode(int kind);
 static native int[] info();
 static native void stop();
 static java.awt.image.BufferedImage shot(String id)throws Exception{
  Thread.sleep(250);int[] v=info();
  if(v[4]!=1){check(v[7]==1,"Native title bar occluded; refusing mouse input");var robot=new java.awt.Robot();robot.mouseMove(v[0]+450,v[1]+16);robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);Thread.sleep(150);v=info();}
  check(v[4]==1&&v[6]==1,"Pure native surface not foreground/visible: "+Arrays.toString(v));var image=new java.awt.Robot().createScreenCapture(new java.awt.Rectangle(v[0],v[1],v[2],v[3]));ImageIO.write(image,"png",output.resolve(id+".png").toFile());return image;
 }
 public static void main(String[] args)throws Exception{
  output=Files.createDirectories(Path.of(args[0]).toAbsolutePath());System.load(Path.of("target/native-research/jni/surface_probe.dll").toAbsolutePath().toString());var results=new ArrayList<String>();Platform.startup(()->Platform.setImplicitExit(false));Canvas canvas=new Canvas(1500,1000);
  try{
   fx(()->{pattern(canvas,false);var s=new Stage(StageStyle.UNDECORATED);s.setScene(new Scene(new StackPane(canvas),1500,1000));s.setAlwaysOnTop(true);s.setX(0);s.setY(0);s.show();return null;});
   for(int options:new int[]{0}){
    check(start(options),"Native creation failed");
    try{for(int kind:new int[]{1,4,1}){
     String id="surface-"+options+"-material-"+kind;int hr=mode(kind);fx(()->{pattern(canvas,false);return null;});var a=shot(id+"-a");fx(()->{pattern(canvas,true);return null;});var b=shot(id+"-b");
     double response=difference(a,b,120,140,160,90),edge=variation(a,120,140,160,90);boolean visual=response>8&&edge<5;String line=id+" API="+hr+" foreground="+Arrays.toString(info())+" response="+response+" edges="+edge+" blurred="+visual;results.add(line);System.out.println(line);
    }}finally{stop();}
   }
  }finally{stop();Files.write(output.resolve("results.txt"),results);fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
 }
}
