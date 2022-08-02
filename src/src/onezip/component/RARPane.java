package onezip.component;

import javafx.application.Application;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import onezip.CompressUtils.RAR.UnCompressUtil;
import onezip.testOne;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;


public class RARPane extends Application {
    static String RARFile;
    public static void main(String[] args) {
        if (args.length==1){
            RARFile=args[0];
        }else if (args.length==2){
            RARFile=args[0];
        }else{
            return;
        }
        //launch(args);//Application launch must not be called more than once
       myStart(new Stage());
    }

    @Override
    public void start(Stage stage) throws Exception {
        //Application launch must not be called more than once
    }
    public static void myStart(Stage stage){
        Text rarName= new Text("\n"+RARFile.substring(RARFile.lastIndexOf("\\")+1));
        rarName.setFont(new Font(20));
        Text rarPath = new Text(RARFile);
        Group group = new Group();
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        group.getChildren().add(progressBar);
        Text status = new Text("状态:还未开始解压");
        HBox hBox = new HBox();
        Button extract = new Button("解压至");
        Button cancel = new Button("取消");
        hBox.getChildren().addAll(extract,cancel);
        VBox vBox = new VBox();
        vBox.getChildren().addAll(rarName,rarPath,group,status,hBox);
        vBox.setSpacing(10.0);
        Scene scene = new Scene(vBox);
        stage.setScene(scene);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setWidth(350);
        stage.setHeight(200);
        stage.show();

        extract.setOnAction(actionEvent -> {
            String password="";
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("解压至");
            File directory = directoryChooser.showDialog(stage);
            status.setText("服务加载中");
            group.getChildren().clear();
            ProgressBar progressBar1 = new ProgressBar();
            progressBar1.setPrefWidth(300);
            group.getChildren().add(progressBar1);
            File[] files = directory.listFiles();
                ArrayList<File> arrayList = new ArrayList<>(Arrays.asList(files));
                RARExtractService rarExtractService = new RARExtractService(RARFile,directory.getPath());
                status.setText("正在解压");
                rarExtractService.start();
            try {
                Thread.sleep(3000);//3000ms等待服务
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            File[] files2 = directory.listFiles();
            System.out.println(files.length+" "+files2.length);
            if (files.length==files2.length) {//好像加密RAR没有密码打开的话不会报错，什么也不做，只有曲线救国
                testOne.alertWarning("如果您认为该压缩包没有加密，请点击取消");
                password=testOne.textInputDialog();
                RARExtractService rarExtractService2 = new RARExtractService(RARFile, directory.getPath(), password);
                rarExtractService2.start();
            }
            try {
                Thread.sleep(100);//3000ms等待服务
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            File[] files3 = directory.listFiles();
            ArrayList<File> arrayList3 = new ArrayList<>(Arrays.asList(files3));
            if (UnCompressUtil.isIsSuccessful()) {
                System.out.println(files);
                System.out.println(files3);
                progressBar1.setProgress(1);
                status.setText("解压成功");
                testOne.alertSuccess();
            }else{
                progressBar1.setStyle("-fx-accent:#FF4500");


                progressBar1.setProgress(0.5);
                status.setText("解压失败");
            }


        });
        cancel.setOnAction(actionEvent -> stage.close());
    }

}
class RARExtractService extends ScheduledService {
    String RARFile;
    String outputPath;
    String password;

    public RARExtractService(String RARFile, String outputPath) {
        this.RARFile = RARFile;
        this.outputPath = outputPath;
    }

    public RARExtractService(String RARFile, String outputPath, String password) {
        this.RARFile = RARFile;
        this.outputPath = outputPath;
        this.password = password;
    }

    @Override
    protected Task createTask() {
        return new Task<Integer>() {

            @Override
            protected Integer call() throws Exception {
                System.out.println(Thread.currentThread().getName());

                if (password == null || password.isEmpty()) {
                    System.out.println(RARFile+" "+outputPath);
                    UnCompressUtil.unRar5AndOver5(RARFile, outputPath);
                } else {
                    UnCompressUtil.unRar5AndOver5(RARFile, outputPath,password);
                }
                return 1;

            }

            @Override
            protected void updateValue(Integer value) {
                if (value == 0) {
                    testOne.alert("error:no model selected");
                }
                RARExtractService.this.cancel();
            }
        };
    }
}
