import cn.shed4329.onezip.themes.fxJava.fluent.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
class FullTransparencyProbe extends UiAcceptance {
    static void checkFills(String page,String... selectors)throws Exception {
        fx(()->{layout();for(String selector:selectors){var nodes=app.getScene().getRoot().lookupAll(selector);require(!nodes.isEmpty(),"Missing component "+selector);for(Node node:nodes)if(node instanceof Region r&&r.getBackground()!=null)for(var f:r.getBackground().getFills())require(!(f.getFill() instanceof Color c)||c.getOpacity()<.001,"Opaque "+page+" "+selector+" "+f.getFill()+" style="+node.getStyle()+" parent="+node.getParent().getStyleClass());}return null;});pass("clear-"+page,"All checked component fills fully transparent");
    }
    static void audit(String page)throws Exception{fx(()->{for(Node n:app.getScene().getRoot().lookupAll("*")){boolean visible=true;for(Node p=n;p!=null;p=p.getParent())if(!p.isVisible()||p.getOpacity()==0){visible=false;break;}if(!visible||!(n instanceof Region r)||r.getBackground()==null)continue;for(var f:r.getBackground().getFills())if(f.getFill() instanceof Color c&&c.getOpacity()>.95)results.add("AUDIT "+page+" "+n.getClass().getSimpleName()+"#"+n.getId()+" "+n.getStyleClass()+"="+c);}return null;});}
    public static void main(String[] args)throws Exception{
        output=Files.createDirectories(Path.of(args[1]).toAbsolutePath());image=Path.of(args[0]);Platform.startup(()->Platform.setImplicitExit(false));
        try{
            app=fx(()->{var s=new Stage();var a=new MainApplication();a.start(s);var m=MainApplication.class.getDeclaredMethod("controller");m.setAccessible(true);controller=(MainWindowController)m.invoke(a);s.setWidth(1180);s.setHeight(760);BackgroundAppearance.configure(image.toString(),true,true);ThemeManager.setDark(true);return s;});
            boolean restore=args.length>2;if(restore){require(BackgroundAppearance.controlsTransparency()==100,"100% lost on restart");pass("restart","100% preserved");capture("restart-home-100");return;}else{
                fx(()->{var m=MainWindowController.class.getDeclaredMethod("openSettings");m.setAccessible(true);m.invoke(controller);return null;});
                Stage settings=fx(()->(Stage)Window.getWindows().stream().filter(w->w!=app&&w.getScene()!=null&&w.getScene().lookup("#settings-personalize")!=null).findFirst().orElseThrow());
                fx(()->{((Button)settings.getScene().lookup("#settings-personalize")).fire();var slider=(Slider)settings.getScene().lookup("#settings-controls-transparency");require(slider.getMax()==100,"Slider capped below100");slider.setValue(100);return null;});Thread.sleep(400);fx(()->{settings.hide();return null;});require(BackgroundAppearance.controlsTransparency()==100,"Actual slider not saved");pass("slider","100% available and saved from actual settings control");
            }
            input=Files.createDirectories(output.resolve("source"));Files.writeString(input.resolve("说明.txt"),"full transparency\n".repeat(20));Path archive=output.resolve("fixture.zip");new cn.shed4329.onezip.engine.ArchiveEngine().compress(List.of(input),archive,cn.shed4329.onezip.engine.ArchiveEngine.Format.ZIP,5,null,null,null,1);fx(()->{controller.model().addSources(List.of(input));return null;});
            for(boolean dark:new boolean[]{true,false}){
                fx(()->{ThemeManager.setDark(dark);controller.model().pageProperty().set(MainWindowModel.Page.COMPRESS);((TitledPane)app.getScene().lookup(".compression-advanced")).setExpanded(true);return null;});Thread.sleep(300);
                String id=(dark?"dark":"light");capture(id+"-compress-100");checkFills(id+"-compress","#source-clear",".compression-toolbar",".compression-settings",".compression-advanced > .title",".compression-advanced > .content",".source-table",".source-table > .virtual-flow",".source-table .column-header",".compression-settings .text-field",".compression-settings .button",".compression-start",".compression-advanced .text-area .content",".format-selector .toggle-button:selected",".spinner .text-field",".arrow-button");audit(id+"-compress");
                openArchive(archive);capture(id+"-extract-100");checkFills(id+"-extract",".archive-commandbar",".archive-commandbar .button",".archive-addressbar","#zip-entry-table","#zip-entry-table .column-header","#archive-comment-text .content");audit(id+"-extract");
            }
            fx(()->{ThemeManager.setDark(true);controller.model().pageProperty().set(MainWindowModel.Page.HOME);return null;});capture("home-100");checkFills("home",".home-tile");
            fx(()->{BackgroundAppearance.configure(image.toString(),true,false);return null;});contrast("wallpaper-off",capture("wallpaper-off"));pass("background-off","Normal solid theme restored at stored100%");
            fx(()->{BackgroundAppearance.configure(image.toString(),true,true);return null;});
        }catch(Throwable e){results.add("FAIL full-transparency: "+e);throw e;}
        finally{report();fx(()->{new ArrayList<>(Window.getWindows()).forEach(Window::hide);return null;});Platform.exit();}
    }
}
