package onezip.themes.fxJava.fluent;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;


public class normalFrame extends Application {
    static int settingTimes = 0;
    public static void main(String[] args) {
        launch(args);
    }

    public static void setSettingTime(int settingTime) {
        settingTimes=settingTime;
    }

    @Override
    public void start(Stage stage) throws Exception {
        Button compress = new Button("压缩",new ImageView("onezip/themes/fxJava/fluent/img/compressNormal.png"));
        Button extract = new Button("解压",new ImageView("onezip/themes/fxJava/fluent/img/extractNormal.png"));
        Button space = new Button("");
        Button setting = new Button("设置",new ImageView("onezip/themes/fxJava/fluent/img/settingLightDark.png"));
        compress.setPrefWidth(100);
        extract.setPrefWidth(100);
        space.setPrefWidth(100);
        setting.setPrefWidth(100);
        space.prefHeightProperty().bind(stage.heightProperty().subtract(171));

        VBox bar = new VBox();
        bar.getChildren().addAll(compress,extract,space,setting);
        VBox contentPane = new VBox();
        HBox hBox = new HBox();
        Button drag = new Button("将文件拖拽至此");
        drag.setDisable(true);
        drag.prefWidthProperty().bind(stage.widthProperty());
        drag.prefHeightProperty().bind(stage.heightProperty().subtract(42));
        contentPane.getChildren().addAll(hBox,drag);
        contentPane.setLayoutX(100);

        AnchorPane anchorPane = new AnchorPane();
        anchorPane.getChildren().addAll(bar,contentPane);
        Scene scene = new Scene(anchorPane);
        JMetro jMetro = new JMetro(Style.LIGHT);
        jMetro.setScene(scene);
        stage.setHeight(600);
        stage.setWidth(900);
        stage.setTitle("OneZip");
        stage.getIcons().add(new Image("onezip/themes/fxJava/fluent/img/zip.png"));
        stage.setScene(scene);
        stage.show();
        compress.setOnAction(actionEvent -> {
            hBox.getChildren().clear();
            Button addFile = new Button("添加文件",new ImageView("onezip/themes/fxJava/fluent/img/addFile.png"));
            addFile.setStyle("-fx-background-color:#ffffff");
            Button addFolder = new Button("添加文件夹",new ImageView("onezip/themes/fxJava/fluent/img/addFolder.png"));
            addFolder.setStyle("-fx-background-color:#ffffff");
            Button compressAll = new Button("全部压缩",new ImageView("onezip/themes/fxJava/fluent/img/compressLine.png"));
            compressAll.setStyle("-fx-background-color:#ffffff");

            hBox.getChildren().addAll(addFile,addFolder,compressAll);
            compress.setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/compressSelect.png"));
            extract.setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/extractNormal.png"));
        });
        extract.setOnAction(actionEvent -> {
            hBox.getChildren().clear();
            Button addArchive = new Button("添加压缩文件",new ImageView("onezip/themes/fxJava/fluent/img/addFolder.png"));
            addArchive.setStyle("-fx-background-color:#ffffff");
            Button extractAll = new Button("全部解压",new ImageView("onezip/themes/fxJava/fluent/img/extractLine.png"));
            extractAll.setStyle("-fx-background-color:#ffffff");
            Button addFile = new Button("添加文件",new ImageView("onezip/themes/fxJava/fluent/img/addFile.png"));
            addFile.setStyle("-fx-background-color:#ffffff");
            Button deleteFile = new Button("删除文件",new ImageView("onezip/themes/fxJava/fluent/img/delete.png"));
            deleteFile.setStyle("-fx-background-color:#ffffff");
            Button comment = new Button("注释",new ImageView("onezip/themes/fxJava/fluent/img/comment.png"));
            comment.setStyle("-fx-background-color:#ffffff");

            hBox.getChildren().addAll(addArchive,extractAll,addFile,deleteFile,comment);
            extract.setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/extractSelect.png"));
            compress.setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/compressNormal.png"));
        });
        setting.setOnAction(actionEvent -> {
            if (settingTimes==0) {
                settingTimes=1;
                settingFrame testA = new settingFrame();
                try {
                    testA.start(new Stage());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        compress.fire();//要添加监听器之后使用才有效
    }
}
