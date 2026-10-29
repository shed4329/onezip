import cn.shed4329.onezip.themes.fxJava.fluent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import javafx.scene.layout.StackPane;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
class NativeBackdropMatrix extends AppearanceProbe {
    static String call(String mode)throws Exception{
        String title=fx(()->app.getTitle());var p=new ProcessBuilder(Path.of(System.getenv("SystemRoot"),"System32/WindowsPowerShell/v1.0/powershell.exe").toString(),"-NoProfile","-NonInteractive","-ExecutionPolicy","Bypass","-File",Path.of("scripts/ProbeWindowsAccent.ps1").toAbsolutePath().toString(),"-Title",title,"-PeerPid",Long.toString(ProcessHandle.current().pid()),"-Mode",mode).redirectErrorStream(true).start();
        if(!p.waitFor(25,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("Probe timeout");}String result=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);Files.writeString(output.resolve("call-"+mode+".json"),result);check(p.exitValue()==0,result);return result;
    }
    static BufferedImage captureOwned(String name)throws Exception{
        for(int attempt=0;attempt<3;attempt++){
            fx(()->{app.toFront();app.requestFocus();return null;});
            var point=fx(()->new int[]{(int)(app.getX()+app.getWidth()/2),(int)app.getY()+15});var mouse=new java.awt.Robot();mouse.mouseMove(point[0],point[1]);mouse.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);mouse.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);Thread.sleep(150);
            try{return guardedDesktopCapture(name);}catch(AssertionError lost){if(attempt==2)throw lost;}
        }throw new AssertionError("No owned screenshot");
    }
    public static void main(String[] args)throws Exception{
        Path base=Files.createDirectories(Path.of(args[0]).toAbsolutePath());var results=new ArrayList<String>();Platform.startup(()->Platform.setImplicitExit(false));
        results.add("UNIFIED_WINDOW supported="+fx(()->Platform.isSupported(javafx.application.ConditionalFeature.UNIFIED_WINDOW)));
        try{
            for(StageStyle style:List.of(StageStyle.TRANSPARENT,StageStyle.UNIFIED)){
                output=Files.createDirectories(base.resolve(style.toString()));Canvas canvas=new Canvas(1500,1000);Stage backing=fx(()->{pattern(canvas,false);var s=new Stage(StageStyle.UNDECORATED);s.setScene(new Scene(new StackPane(canvas),1500,1000));s.setX(0);s.setY(0);s.setAlwaysOnTop(true);s.show();return s;});
                app=fx(()->{var s=new Stage(style);var a=new MainApplication();a.start(s);ThemeManager.setDark(true);BackgroundAppearance.configure("",false,false);s.setTitle("OneZip Matrix "+style+" "+ProcessHandle.current().pid());s.setWidth(1180);s.setHeight(760);s.setX(40);s.setY(40);s.setAlwaysOnTop(true);s.getScene().setFill(Color.TRANSPARENT);s.getScene().getRoot().setStyle("-onezip-surface:transparent;-fx-background-color:rgba(12,12,12,.12);");return s;});
                try{
                    for(String mode:(style==StageStyle.TRANSPARENT?List.of("None"):List.of("Acrylic","AccentBlur","AccentAcrylic"))){
                        call("None");String api=call(mode);fx(()->{pattern(canvas,false);return null;});var a=captureOwned(mode+"-a");fx(()->{pattern(canvas,true);return null;});var b=captureOwned(mode+"-b");
                        double response=difference(a,b,40,100,140,90),edges=variation(a,40,100,140,90),contrast=nativeTitleContrast(b);
                        String line=style+" "+mode+" response="+response+" edges="+edges+" titleContrast="+contrast+" stageOpacity="+fx(()->app.getOpacity())+" API="+api.strip();results.add(line);System.out.println(line);
                        call("None");
                    }
                }finally{try{call("None");}finally{fx(()->{app.hide();backing.hide();return null;});}}
            }
        }finally{Files.write(base.resolve("results.txt"),results);fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
    }
}
