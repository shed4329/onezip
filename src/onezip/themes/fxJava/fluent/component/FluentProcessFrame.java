package onezip.themes.fxJava.fluent.component;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import jfxtras.styles.jmetro.JMetro;

public class FluentProcessFrame {
    private ProgressBar progressBar=new ProgressBar();
    private Text percentageText=new Text();
    private int percentage;
    private Text timeText=new Text();
    private String time;
    private Text currentFileText = new Text();
    private String currentFile;
   // private Text zipPathText;
   // private String zipPath;
   // private Text unzipPathText=new Text();
    //private String unzipPath;
    private Text summaryText=new Text();
    private String summary;
    private TextArea textArea=new TextArea();
    private Stage stage = new Stage();
    public void newProgressFrame(){

        BorderPane infoBar = new BorderPane();
        infoBar.setLeft(percentageText);
        infoBar.setRight(timeText);
        ProgressBar progressBar = new ProgressBar();
        BorderPane actionBar = new BorderPane();
        VBox vBox = new VBox();
        vBox.setSpacing(15);
        vBox.getChildren().addAll(infoBar,progressBar,currentFileText,textArea,actionBar);
        vBox.setPadding(new Insets(10,10,10,10));
        Button button =new Button();
        Image image =new Image("onezip/themes/fxJava/fluent/img/openInExplorer.png");
        ImageView openInExplorer = new ImageView(image);
        button.setGraphic(openInExplorer);
        button.setStyle("-fx-background-color:#ffffff");
        Button close = new Button("关闭");
        actionBar.setLeft(button);
        actionBar.setRight(close);
        textArea.setEditable(false);

        progressBar.prefWidthProperty().bind(vBox.widthProperty().subtract(20));
        vBox.prefWidthProperty().bind(stage.widthProperty());


        Scene scene =new Scene(vBox);
        stage.setScene(scene);
        JMetro jMetro = new JMetro();
        jMetro.setScene(scene);
        stage.setTitle("正在压缩 新建压缩.zip");
        stage.setWidth(600);
        stage.setHeight(400);
        stage.show();
    }

    public void setProgress(int percent) {
        double percentage = Double.valueOf(percent);
        System.out.println(percentage*0.01);
        progressBar.setProgress(percentage);
        percentageText.setText(percentage+"%");
    }

    public void setCurrentFile(String currentFile) {
        currentFileText.setText(currentFile);
    }
    public void setSummary(String summary) {
        this.summary = summary;
    }
    public void close(){
        stage.close();
    }
}
