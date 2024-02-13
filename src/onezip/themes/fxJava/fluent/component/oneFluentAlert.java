package onezip.themes.fxJava.fluent.component;

import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class oneFluentAlert {
    private static FluentAlert fluentAlert = new FluentAlert();
    public static void alertError(String text){
        fluentAlert.setAlertType(Alert.AlertType.ERROR);
        fluentAlert.setHeaderText("错误");
        fluentAlert.setContentText(text);
        fluentAlert.showAndWait();
    }
    public static void alertSuccess(){
        fluentAlert.setAlertType(Alert.AlertType.INFORMATION);
        fluentAlert.setHeaderText("成功");
        fluentAlert.setContentText("成功");
        fluentAlert.showAndWait();
    }
    public static void alertWarning(String text){
        fluentAlert.setAlertType(Alert.AlertType.WARNING);
        fluentAlert.setHeaderText("警告");
        fluentAlert.setContentText(text);
        fluentAlert.showAndWait();
    }
    public static void alertInfo(String title,String text){
        fluentAlert.setAlertType(Alert.AlertType.INFORMATION);
        fluentAlert.setHeaderText(title);
        fluentAlert.setContentText(text);
        fluentAlert.showAndWait();
    }

    public static void setIsDarkMode(boolean isDarkMode) {
        fluentAlert.setDarkMode(isDarkMode);
    }
}
