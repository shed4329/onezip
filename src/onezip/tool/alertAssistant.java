package onezip.tool;

public class alertAssistant {
    public static void alertSuccess(){
        if (themeAssistant.getThemeType()==ThemeType.Fluent){
            onezip.themes.fxJava.fluent.component.oneFluentAlert.alertSuccess();
        }else{
            onezip.component.oneAlert.alertSuccess();
        }
    }
}
