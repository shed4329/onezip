package onezip.component;

import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

public class oneAlert {
    public static void alert(String text){
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.titleProperty().set("错误");
        alert.headerTextProperty().set(text);
        alert.showAndWait();
    }
    public static void alertSuccess(){
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.titleProperty().set("成功");
        alert.headerTextProperty().set("成功");
        alert.showAndWait();
    }
    public static void alertException(Exception ex){
        Scene scene = new Scene(new AnchorPane());
        Stage stage = new Stage();
        stage.setScene(scene);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Exception Dialog");
        alert.setHeaderText("发生异常");
        alert.setContentText(ex.getMessage());



// Create expandable Exception.
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        String exceptionText = sw.toString();

        Label label = new Label("The exception stacktrace was:");

        TextArea textArea = new TextArea(exceptionText);
        textArea.setEditable(false);
        textArea.setWrapText(true);

        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane expContent = new GridPane();
        expContent.setMaxWidth(Double.MAX_VALUE);
        expContent.add(label, 0, 0);
        expContent.add(textArea, 0, 1);

// Set expandable Exception into the dialog pane.
        alert.getDialogPane().setExpandableContent(expContent);
        alert.initOwner(stage);
        alert.showAndWait();
    }
    public static void alertWarning(String text){
        Alert alert = new Alert(Alert.AlertType.WARNING,text);
        alert.showAndWait();
    }
    public static String textInputDialog(){
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("密码");
        dialog.setHeaderText("该文件已加密，你需要提供该文件密码才能解压");
        dialog.setContentText("密码:");

        Optional<String> result = dialog.showAndWait();
        final String[] str = new String[1];
        result.ifPresent(s -> {
            str[0] =s;
        });
        System.out.println(str[0]);
        return str[0];
    }
    public static void alertInfo(String title,String text){
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.titleProperty().set(title);
        alert.headerTextProperty().set(text);
        alert.showAndWait();
    }
}
