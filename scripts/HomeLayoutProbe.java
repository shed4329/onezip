import cn.shed4329.onezip.i18n.I18n;
import cn.shed4329.onezip.setting.SettingsStore;
import cn.shed4329.onezip.themes.fxJava.fluent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
class HomeLayoutProbe extends UiAcceptance {
    static void alignment()throws Exception{fx(()->{layout();require(app.getScene().lookup(".home-title")==null,"Brand heading remains");require(app.getScene().lookup("#window-brand").isVisible(),"Small top-left brand missing");require(app.getScene().getRoot().lookupAll(".home-tile").size()==4,"Missing home action");for(String id:List.of("home-compress","home-extract","home-settings","home-about")){var b=(Button)app.getScene().lookup("#"+id);require(b.getContentDisplay()==ContentDisplay.TOP&&b.getOnAction()!=null,"Wrong action layout");var icon=(ImageView)b.getGraphic();var text=b.lookup(".text");var i=icon.localToScene(icon.getBoundsInLocal());var t=text.localToScene(text.getBoundsInLocal());require(t.getMinY()-i.getMaxY()>=10,"Icon/text gap too small");require(Math.abs(i.getCenterX()-t.getCenterX())<2,"Icon/text not centered");require(icon.getFitWidth()==48&&b.getFont().getSize()==14,"Wrong icon or label size");}return null;});pass("alignment","Four wired actions, vertical48px icon/14px label, shared centerline and no heading");}
    public static void main(String[] args)throws Exception{
        output=Files.createDirectories(Path.of(args[1]).toAbsolutePath());SettingsStore.shared().put("appearance.language",args[0]);I18n.initialize();Platform.startup(()->Platform.setImplicitExit(false));
        try{
            app=fx(()->{var s=new Stage();var a=new MainApplication();a.start(s);var m=MainApplication.class.getDeclaredMethod("controller");m.setAccessible(true);controller=(MainWindowController)m.invoke(a);return s;});
            for(boolean dark:new boolean[]{false,true}){fx(()->{ThemeManager.setDark(dark);return null;});alignment();capture((dark?"dark":"light")+"-home");}
            fx(()->{BackgroundAppearance.configure("test-background.jpg",true,true);BackgroundAppearance.setControlsTransparency(50);return null;});alignment();capture("image-home");
            fx(()->{BackgroundAppearance.setControlsTransparency(100);return null;});capture("clear-home");
            fx(()->{((Button)app.getScene().lookup("#home-compress")).fire();require(controller.model().pageProperty().get()==MainWindowModel.Page.COMPRESS,"Compress action failed");require(!app.getScene().lookup("#window-brand").isVisible(),"Brand overlaps workspace heading");controller.model().pageProperty().set(MainWindowModel.Page.HOME);((Button)app.getScene().lookup("#home-settings")).fire();return null;});
            fx(()->{var s=Window.getWindows().stream().filter(w->w!=app&&w.isShowing()&&w.getScene()!=null&&w.getScene().lookup("#settings-language")!=null).findFirst().orElseThrow();s.hide();((Button)app.getScene().lookup("#home-about")).fire();require(Window.getWindows().stream().anyMatch(w->w!=app&&w.isShowing()&&w.getScene()!=null&&w.getScene().lookup("#settings-about-onezip")!=null),"About action failed");return null;});pass("navigation","Actual Compress, Settings and About buttons work; Extract handler retained, native picker not opened");
        }catch(Throwable e){results.add("FAIL home-layout: "+e);throw e;}
        finally{report();fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
    }
}
