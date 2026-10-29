import cn.shed4329.onezip.themes.fxJava.fluent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.paint.Color;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
class SettingsChromeProbe extends UiAcceptance {
    static Stage settings;
    static void shot(String id)throws Exception{fx(()->{settings.getScene().getRoot().applyCss();settings.getScene().getRoot().layout();var rendered=settings.getScene().snapshot(null);int w=(int)rendered.getWidth(),h=(int)rendered.getHeight();int[] pixels=new int[w*h];rendered.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),pixels,0,w);var bitmap=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);bitmap.setRGB(0,0,w,h,pixels,0,w);javax.imageio.ImageIO.write(bitmap,"png",output.resolve(id+".png").toFile());return null;});}
    static void icons()throws Exception{fx(()->{for(String id:List.of("settings-general","settings-personalize","settings-language","settings-about")){var view=(ImageView)((Button)settings.getScene().lookup("#"+id)).getGraphic();var image=view.getImage();int count=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){Color c=image.getPixelReader().getColor(x,y);if(c.getOpacity()>.95){require(AccentTheme.hex(c).equals(AccentTheme.hex(AccentTheme.foreground())),"Stale blue icon: "+id);count++;}}require(count>5,"No icon pixels: "+id);}return null;});pass("category-icons","Four settings category icons match active theme");}
    public static void main(String[] args)throws Exception{
        output=Files.createDirectories(Path.of(args[0]).toAbsolutePath());Platform.startup(()->Platform.setImplicitExit(false));
        try{
            fx(()->{ThemeManager.setDark(true);AccentTheme.set(Color.web("#8764b8"));settings=new Stage();new SettingsWindow().start(settings);return null;});
            require(fx(()->settings.getStyle()==StageStyle.UNDECORATED),"System decoration still present");icons();shot("01-purple-settings-home");
            fx(()->{((Button)settings.getScene().lookup("#settings-personalize")).fire();ColorPicker picker=(ColorPicker)settings.getScene().lookup("#settings-accent-color");picker.setValue(Color.web("#008272"));picker.fireEvent(new javafx.event.ActionEvent());((Button)settings.getScene().lookup("#settings-general")).fire();return null;});icons();shot("02-green-after-navigation");
            fx(()->{ThemeManager.setDark(false);return null;});icons();shot("03-light-settings-home");
            fx(()->{((Button)settings.getScene().lookup("#window-maximize")).fire();return null;});Thread.sleep(250);require(fx(()->settings.isMaximized()),"Maximize failed");
            fx(()->{((Button)settings.getScene().lookup("#window-maximize")).fire();return null;});Thread.sleep(250);require(fx(()->!settings.isMaximized()),"Restore failed");pass("maximize-restore","Actual caption button toggles state");
            fx(()->{((Button)settings.getScene().lookup("#window-minimize")).fire();return null;});Thread.sleep(250);require(fx(()->settings.isIconified()),"Minimize failed");fx(()->{settings.setIconified(false);settings.toFront();return null;});Thread.sleep(250);pass("minimize-restore","Minimize and restore work");
            fx(()->{settings.setAlwaysOnTop(true);settings.setIconified(false);settings.setX(60);settings.setY(60);settings.toFront();settings.requestFocus();return null;});Thread.sleep(600);
            require(fx(()->settings.isFocused()&&!settings.isIconified()&&!settings.isMaximized()),"Test window not ready for native mouse input");
            double[] pos=fx(()->new double[]{settings.getX(),settings.getY()});
            javafx.scene.robot.Robot mouse=fx(()->new javafx.scene.robot.Robot());
            fx(()->{mouse.mouseMove(pos[0]+220,pos[1]+20);mouse.mousePress(javafx.scene.input.MouseButton.PRIMARY);return null;});Thread.sleep(120);
            fx(()->{mouse.mouseMove(pos[0]+260,pos[1]+50);return null;});Thread.sleep(120);fx(()->{mouse.mouseRelease(javafx.scene.input.MouseButton.PRIMARY);return null;});Thread.sleep(150);
            require(fx(()->Math.abs(settings.getX()-pos[0])>15&&Math.abs(settings.getY()-pos[1])>10),"Caption drag did not move");pass("drag","Real JavaFX Robot caption drag moves window");
            fx(()->{settings.setX(60);settings.setY(60);return null;});Thread.sleep(150);
            double[] size=fx(()->{var grip=settings.getScene().lookup("#window-resize");var b=grip.localToScreen(grip.getBoundsInLocal());return new double[]{settings.getWidth(),settings.getHeight(),b.getCenterX(),b.getCenterY()};});
            require(fx(()->size[2]<settings.getX()+settings.getWidth()&&size[3]<settings.getY()+settings.getHeight()),"Resize grip outside restored window");
            fx(()->{mouse.mouseMove(size[2],size[3]);mouse.mousePress(javafx.scene.input.MouseButton.PRIMARY);return null;});Thread.sleep(100);fx(()->{mouse.mouseMove(size[2]+40,size[3]+30);return null;});Thread.sleep(120);fx(()->{mouse.mouseRelease(javafx.scene.input.MouseButton.PRIMARY);return null;});Thread.sleep(120);
            require(fx(()->settings.getWidth()>size[0]+15&&settings.getHeight()>size[1]+10),"Resize grip failed");pass("resize","Real JavaFX Robot grip drag resizes window");shot("04-after-drag-resize");
            fx(()->{((Button)settings.getScene().lookup("#window-close")).fire();return null;});require(fx(()->!settings.isShowing()),"Close failed");pass("close","Actual caption close button closes settings");
        }catch(Throwable e){results.add("FAIL settings-chrome: "+e);throw e;}
        finally{report();fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
    }
}
