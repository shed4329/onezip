package onezip;

import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class ProcessFrame {
    ProgressBar progressBar;
    Text percentText;
    Stage stage;
    public void newProgressFrame(){
        progressBar= new ProgressBar();
        percentText = new Text("当前进度:");
        VBox vBox = new VBox();
        vBox.getChildren().addAll(progressBar,percentText);
        Scene scene = new Scene(vBox);
        stage=new Stage();
        stage.setScene(scene);
        stage.setTitle("压缩");
        stage.show();
    }
    public void setProgress(int Percent){
        progressBar.setProgress(Percent*0.01);
        percentText.setText("当前进度:"+Percent+"%");
    }
    public void close(){
        stage.close();
    }
}
