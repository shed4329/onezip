import cn.shed4329.onezip.themes.fxJava.fluent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.control.Label;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
class SingleDwmProbe extends AppearanceProbe {
 static native int flags(String title,int flags);
 static native int set(String title,int material,boolean dark);
 static native long[] inspect(String title);
 static String title;static Canvas canvas;
 static java.awt.image.BufferedImage shot(String id)throws Exception{
  fx(()->{app.toFront();app.requestFocus();return null;});Thread.sleep(180);long[] v=inspect(title);
  if(v[0]!=v[1]){check(v[7]==1,"Own title not visible");var p=fx(()->new int[]{(int)(app.getX()+app.getWidth()/2),(int)app.getY()+15});var r=new java.awt.Robot();r.mouseMove(p[0],p[1]);r.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);r.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);Thread.sleep(150);v=inspect(title);}
  check(v[0]!=0&&v[0]==v[1],"Exact HWND not foreground");var bounds=fx(()->new java.awt.Rectangle((int)app.getX(),(int)app.getY(),(int)app.getWidth(),(int)app.getHeight()));var image=new java.awt.Robot().createScreenCapture(bounds);javax.imageio.ImageIO.write(image,"png",output.resolve(id+".png").toFile());return image;
 }
 public static void main(String[] args)throws Exception{
  output=Files.createDirectories(Path.of(args[0]).toAbsolutePath());boolean onezip=args.length>1;System.load(Path.of("target/native-research/jni/single_dwm.dll").toAbsolutePath().toString());Platform.startup(()->Platform.setImplicitExit(false));var results=new ArrayList<String>();
  try{
   canvas=new Canvas(1500,900);fx(()->{pattern(canvas,false);var b=new Stage(StageStyle.UNDECORATED);b.setScene(new Scene(new StackPane(canvas),1500,900));b.setAlwaysOnTop(true);b.show();return null;});
   title="OneZip Single DWM Lab "+ProcessHandle.current().pid();
   app=fx(()->{var s=new Stage(StageStyle.UNIFIED);if(onezip){new MainApplication().start(s);ThemeManager.setDark(true);BackgroundAppearance.configure("",false,false);s.getScene().getRoot().setStyle("-onezip-surface:transparent;-fx-background-color:transparent;");s.getScene().setFill(Color.TRANSPARENT);}else{var root=new StackPane(new Label("Native DWM minimal control"));root.setStyle("-fx-background-color:transparent;");var scene=new Scene(root,900,650,Color.TRANSPARENT);s.setScene(scene);}s.setTitle(title);s.setWidth(1000);s.setHeight(700);s.setX(20);s.setY(20);s.setAlwaysOnTop(true);s.show();return s;});
   results.add("nativeFlags="+Integer.getInteger("onezip.probe.nativeFlags",0)+" hr="+fx(()->flags(title,Integer.getInteger("onezip.probe.nativeFlags",0))));
   results.add("runtime="+System.getProperty("javafx.runtime.version")+" unifiedSupported="+fx(()->Platform.isSupported(javafx.application.ConditionalFeature.UNIFIED_WINDOW)));
   for(int material:new int[]{1,3,1}){
    int hr=fx(()->{int code=set(title,material,true);app.setWidth(material==3?1001:1000);app.getScene().getRoot().requestLayout();return code;});Thread.sleep(200);fx(()->{pattern(canvas,false);return null;});var a=shot("material-"+material+"-a");fx(()->{pattern(canvas,true);return null;});var b=shot("material-"+material+"-b");double response=difference(a,b,70,120,140,90),edges=variation(a,70,120,140,90);String line="material="+material+" hr="+hr+" native="+Arrays.toString(inspect(title))+" response="+response+" edges="+edges;System.out.println(line);results.add(line);
   }
  }finally{if(app!=null)fx(()->set(title,1,true));Files.write(output.resolve("results.txt"),results);fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
 }
}
