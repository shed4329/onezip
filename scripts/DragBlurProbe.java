import cn.shed4329.onezip.engine.ArchiveEngine;
import cn.shed4329.onezip.setting.*;
import cn.shed4329.onezip.themes.fxJava.fluent.*;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.robot.Robot;
import javafx.stage.*;
import org.controlsfx.control.ToggleSwitch;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Actual mouse input and fresh-process image blur acceptance, using isolated preferences. */
public class DragBlurProbe {
    static Stage app,settings; static MainWindowController controller; static Robot robot;
    static Path output; static List<String> results=new ArrayList<>();
    static <T>T fx(Callable<T> action)throws Exception{var task=new FutureTask<T>(action);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    static void pass(String text){results.add("PASS "+text);System.out.println("PASS "+text);}
    static void shot(Stage stage,String name)throws Exception{fx(()->{
        stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();
        var image=stage.getScene().snapshot(null);int w=(int)image.getWidth(),h=(int)image.getHeight();
        int[] data=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),data,0,w);
        BufferedImage bitmap=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);bitmap.setRGB(0,0,w,h,data,0,w);
        ImageIO.write(bitmap,"png",output.resolve(name+".png").toFile());return null;});}
    static ImageView image(){return (ImageView)((Parent)app.getScene().lookup("#application-background")).getChildrenUnmodifiable().getFirst();}
    static double radius(){return image().getEffect() instanceof GaussianBlur blur?blur.getRadius():0;}
    static Point2D blank() {return blank(".archive-commandbar");}
    static Point2D blank(String selector) {var toolbar=(HBox)app.getScene().lookup(selector);var space=toolbar.getChildren().stream().filter(n->n.getClass()==Region.class).findFirst().orElseThrow();check(space.getBoundsInLocal().getWidth()>40,"No blank drag surface");return space.localToScreen(space.getBoundsInLocal().getWidth()/2,space.getBoundsInLocal().getHeight()/2);}
    static Point2D center(Node node){return node.localToScreen(node.getBoundsInLocal().getWidth()/2,node.getBoundsInLocal().getHeight()/2);}
    static void drag(Point2D point,double dx,double dy)throws Exception{
        fx(()->{robot.mouseMove(point);robot.mousePress(MouseButton.PRIMARY);return null;});Thread.sleep(100);
        for(int i=1;i<=5;i++){final int step=i;fx(()->{robot.mouseMove(point.getX()+dx*step/5,point.getY()+dy*step/5);return null;});Thread.sleep(35);}
        fx(()->{robot.mouseRelease(MouseButton.PRIMARY);return null;});Thread.sleep(250);
    }
    static void click(Point2D point)throws Exception{fx(()->{robot.mouseMove(point);robot.mousePress(MouseButton.PRIMARY);robot.mouseRelease(MouseButton.PRIMARY);return null;});Thread.sleep(130);}
    static void waitRadius(int expected)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(BackgroundAppearance.blurRadius()!=expected&&System.nanoTime()<deadline)Thread.sleep(30);check(BackgroundAppearance.blurRadius()==expected,"Radius not persisted: "+expected);}
    public static void main(String[] args)throws Exception{
        output=Files.createDirectories(Path.of(args[0]));boolean restore=args.length>1&&args[1].equals("restore");
        if(!restore)SettingsStore.shared().put("appearance.language","zh-TW");
        Platform.startup(()->Platform.setImplicitExit(false));
        try{
            Path a=output.resolve("alpha.txt"),b=output.resolve("beta.txt"),archive=output.resolve("sample.zip");
            if(!restore){Files.writeString(a,"alpha\n".repeat(200));Files.writeString(b,"beta\n".repeat(200));new ArchiveEngine().compress(List.of(a,b),archive,ArchiveEngine.Format.ZIP,1,null,null,null,1);}
            app=fx(()->{if(!restore){BackgroundAppearance.configure(Objects.requireNonNull(System.getProperty("onezip.probe.image"), "Set -Donezip.probe.image to a test image"),true,true);BackgroundAppearance.setControlsTransparency(65);}var s=new Stage();var application=new MainApplication();application.start(s);var getter=MainApplication.class.getDeclaredMethod("controller");getter.setAccessible(true);controller=(MainWindowController)getter.invoke(application);robot=new Robot();s.setAlwaysOnTop(true);s.toFront();s.requestFocus();if(!restore)controller.selectArchive(archive);return s;});
            Thread.sleep(500);
            if(!restore){
                fx(()->{var screen=Screen.getPrimary().getVisualBounds();app.setWidth(Math.min(1100,screen.getWidth()-100));app.setHeight(Math.min(700,screen.getHeight()-100));app.setX(screen.getMinX()+35);app.setY(screen.getMinY()+35);return null;});Thread.sleep(250);
                Point2D original=fx(()->new Point2D(app.getX(),app.getY()));drag(fx(DragBlurProbe::blank),55,30);
                fx(()->{check(Math.abs(app.getX()-original.getX()-55)<5&&Math.abs(app.getY()-original.getY()-30)<5,"Blank toolbar drag did not move window");return null;});pass("native-mouse toolbar blank moves window with ScrollPane ancestor");
                Point2D before=fx(()->new Point2D(app.getX(),app.getY()));Point2D button=fx(()->center(app.getScene().lookup("#archive-open")));drag(button,90,25);final Point2D stable=before;
                fx(()->{check(Math.abs(app.getX()-stable.getX())<2&&Math.abs(app.getY()-stable.getY())<2,"Button drag moved window");return null;});pass("interactive command drag does not move window");
                Point2D point=fx(DragBlurProbe::blank);click(point);click(point);Thread.sleep(250);check(fx(app::isMaximized),"Double click did not maximize");
                drag(fx(DragBlurProbe::blank),35,50);check(!fx(app::isMaximized),"Dragging maximized window did not restore");pass("toolbar double-click maximizes; dragging restores and moves");
                fx(()->{var bounds=Screen.getPrimary().getVisualBounds();app.setX(bounds.getMinX()+25);app.setY(bounds.getMinY()+25);return null;});Thread.sleep(200);
                var rows=fx(()->app.getScene().getRoot().lookupAll(".table-row-cell").stream().filter(n->n instanceof TableRow<?> row&&!row.isEmpty()).sorted(Comparator.comparingInt(n->((TableRow<?>)n).getIndex())).toList());check(rows.size()>=2,"Fixture rows missing");
                click(fx(()->center(rows.get(0))));fx(()->{robot.keyPress(KeyCode.CONTROL);return null;});try{click(fx(()->center(rows.get(1))));}finally{fx(()->{robot.keyRelease(KeyCode.CONTROL);return null;});}
                fx(()->{check(((TableView<?>)app.getScene().lookup("#zip-entry-table")).getSelectionModel().getSelectedItems().size()==2,"Ctrl multi-select broken");return null;});pass("native-mouse Ctrl multi-selection preserved");
            }
            fx(()->{((Button)app.getScene().lookup(restore?"#home-settings":"#archive-settings")).fire();return null;});settings=fx(()->(Stage)Window.getWindows().stream().filter(w->w!=app&&w.isShowing()&&w.getScene().lookup("#settings-personalize")!=null).findFirst().orElseThrow());
            fx(()->{((Button)settings.getScene().lookup("#settings-personalize")).fire();return null;});
            Slider slider=fx(()->(Slider)settings.getScene().lookup("#settings-background-blur-radius"));
            if(restore){fx(()->{check(slider.getValue()==37&&radius()==37&&BackgroundAppearance.blurred()&&BackgroundAppearance.enabled(),"Fresh JVM did not restore radius / flags");return null;});shot(app,"restart-radius-37");pass("fresh JVM restores blur radius 37 and rendered effect");return;}
            for(int value:new int[]{0,24,63}){
                fx(()->{slider.setValue(value);check(radius()==value,"Live preview differs");for(var node:app.getScene().getRoot().lookupAll(".label"))check(node.getEffect()==null,"Foreground text was blurred");return null;});waitRadius(value);shot(app,"blur-"+value);pass("radius "+value+" live preview, persisted value, foreground text stays sharp");
            }
            fx(()->{slider.setValue(37);((Button)settings.getScene().lookup("#settings-general")).fire();check(BackgroundAppearance.blurRadius()==37,"Page switch lost pending radius");((Button)settings.getScene().lookup("#settings-personalize")).fire();return null;});pass("immediate page switch flushes pending radius");
            fx(()->{var toggle=(ToggleSwitch)settings.getScene().lookup("#settings-background-blur");toggle.setSelected(false);check(radius()==0&&settings.getScene().lookup("#settings-background-blur-radius").isDisabled(),"Off did not clear effect/disable slider");toggle.setSelected(true);check(radius()==37,"Re-enable lost radius");var enabled=(ToggleSwitch)settings.getScene().lookup("#settings-background-enabled");enabled.setSelected(false);check(settings.getScene().lookup("#settings-background-blur-radius").isDisabled(),"Disabled image still has active radius");enabled.setSelected(true);check(radius()==37,"Image toggle lost radius");((ScrollPane)settings.getScene().lookup("#settings-content-scroll")).setVvalue(.55);return null;});Thread.sleep(180);shot(settings,"settings-blur-slider");pass("blur / image switches disable slider and restore chosen radius");
            fx(()->{settings.setAlwaysOnTop(true);settings.toFront();settings.requestFocus();return null;});Thread.sleep(150);
            Point2D settingsBefore=fx(()->new Point2D(settings.getX(),settings.getY()));drag(fx(()->center(settings.getScene().lookup(".settings-heading"))),35,20);
            fx(()->{check(Math.abs(settings.getX()-settingsBefore.getX()-35)<5&&Math.abs(settings.getY()-settingsBefore.getY()-20)<5,"Settings heading drag failed");settings.hide();app.toFront();app.requestFocus();controller.model().pageProperty().set(MainWindowModel.Page.COMPRESS);return null;});Thread.sleep(200);pass("settings heading moves its window");
            Point2D compressBefore=fx(()->new Point2D(app.getX(),app.getY()));drag(fx(()->blank(".compression-toolbar")),35,20);
            fx(()->{check(Math.abs(app.getX()-compressBefore.getX()-35)<5&&Math.abs(app.getY()-compressBefore.getY()-20)<5,"Compression toolbar drag failed");controller.model().pageProperty().set(MainWindowModel.Page.HOME);return null;});Thread.sleep(150);pass("compression toolbar blank moves window");
            Point2D homeBefore=fx(()->new Point2D(app.getX(),app.getY()));drag(fx(()->app.getScene().getRoot().localToScreen(200,15)),30,20);
            fx(()->{check(Math.abs(app.getX()-homeBefore.getX()-30)<5&&Math.abs(app.getY()-homeBefore.getY()-20)<5,"Home title area drag failed");return null;});pass("home title area moves window");
        }catch(Throwable failure){results.add("FAIL "+failure);throw failure;}
        finally{Files.write(output.resolve("results.txt"),results);fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
    }
}
