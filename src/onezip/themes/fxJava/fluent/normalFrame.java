package onezip.themes.fxJava.fluent;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class normalFrame extends Application {
    ArrayList<File> fileArrayList = new ArrayList<>();
    ArrayList<File> folderArrayList = new ArrayList<>();
    File archive;
    static boolean compressPaneStatus = true;//false->解压面板，ture->压缩面板
    ListView<String> compressListView = new ListView();
    ObservableList<String> compressFileNameList=FXCollections.observableArrayList();
    ListView<String> extractListView = new ListView();
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
        //绑定，初始化列表
        compressListView.prefWidthProperty().bind(stage.widthProperty());
        compressListView.prefHeightProperty().bind(stage.heightProperty().subtract(42));
        extractListView.prefWidthProperty().bind(stage.widthProperty());
        extractListView.prefHeightProperty().bind(stage.heightProperty().subtract(42));
        compressListView.setItems(FXCollections.observableArrayList("现在，您可以拖拽文件至此"));
        extractListView.setItems(FXCollections.observableArrayList("现在，您可以拖拽文件至此"));
        contentPane.getChildren().addAll(hBox,compressListView);
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
            compressPaneStatus=true;
            hBox.getChildren().clear();
            contentPane.getChildren().remove(1);
            contentPane.getChildren().add(compressListView);

            Button addFile = new Button("添加文件",new ImageView("onezip/themes/fxJava/fluent/img/addFile.png"));
            addFile.setStyle("-fx-background-color:#ffffff");
            Button addFolder = new Button("添加文件夹",new ImageView("onezip/themes/fxJava/fluent/img/addFolder.png"));
            addFolder.setStyle("-fx-background-color:#ffffff");
            Button compressAll = new Button("全部压缩",new ImageView("onezip/themes/fxJava/fluent/img/compressLine.png"));
            compressAll.setStyle("-fx-background-color:#ffffff");

            hBox.getChildren().addAll(addFile,addFolder,compressAll);
            compress.setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/compressSelect.png"));
            extract.setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/extractNormal.png"));
            addFile.setOnAction(actionEvent1 -> {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("添加文件");
                File file =fileChooser.showOpenDialog(stage);
                if (file!=null) {//非空检验
                    fileArrayList.add(file);
                    compressFileNameList.add(file.getName());

                    compressListView.setItems(compressFileNameList);
                    System.out.println(compressFileNameList);
                    compressListView.setCellFactory((ListView<String> l) -> new ColorRectCell());
                }
            });
            addFolder.setOnAction(actionEvent1 -> {
                DirectoryChooser directoryChooser = new DirectoryChooser();
                directoryChooser.setTitle("添加文件夹");
                File folder =directoryChooser.showDialog(stage);
                if (folder!=null) {
                    folderArrayList.add(folder);
                    compressFileNameList.add(folder.getName()+"\\");

                    compressListView.setItems(compressFileNameList);
                    System.out.println(compressFileNameList);
                    compressListView.setCellFactory((ListView<String> l) -> new ColorRectCell());
                }
            });
        });
        extract.setOnAction(actionEvent -> {
            compressPaneStatus=false;
            hBox.getChildren().clear();
            contentPane.getChildren().remove(1);
            contentPane.getChildren().add(extractListView);

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
            addArchive.setOnAction(actionEvent1 -> {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("打开压缩文件");
                fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("压缩文件","*.zip","*.7z","*.rar"));
                File file=fileChooser.showOpenDialog(stage);
                if (file!=null){
                    archive=file;
                }
            });
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
        compressListView.setOnDragOver(dragEvent -> {
            if (dragEvent.getGestureSource() != compressListView) {
                dragEvent.acceptTransferModes(TransferMode.ANY);
            }
        });
        compressListView.setOnDragDropped(dragEvent -> {
            Dragboard dragboard = dragEvent.getDragboard();
            List<File> files = dragboard.getFiles();
            fileArrayList.addAll(files);
            for (File f:files){
                if (f.isDirectory()){
                    compressFileNameList.add(f.getName()+"\\");
                }else{
                    compressFileNameList.add(f.getName());
                }
                compressListView.setItems(compressFileNameList);
                System.out.println(compressFileNameList);
                compressListView.setCellFactory((ListView<String> l) -> new ColorRectCell());
            }
        });
        compress.fire();//要添加监听器之后使用才有效
    }
    static class ColorRectCell extends ListCell<String> {//自定义一个ListView组件外观选择项目组件
        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);//更新listView对象的选项组件内容
            //Rectangle rect = new Rectangle(100, 20);//创建一个矩形
            if (item != null) {
                System.out.println(item);
                //rect.setFill(Color.web(item));//填充矩形对象颜色
                if (item.contains("\\")){
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/folder.png"));
                }else if (item.contains(".exe")) {
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/exe.png"));
                } else if (item.contains(".doc") || item.contains(".docx") || item.contains("dot") || item.contains(".docm") || item.contains(".dotx") || item.contains(".dotm")) {
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/doc.png"));//设置ListCell对象的图形外观
                } else if (item.contains(".ppt") || item.contains(".pptx") || item.contains(".pptm") || item.contains(".pps") || item.contains(".ppsx") || item.contains(".potx")) {
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/ppt.png"));//设置ListCell对象的图形外观
                } else if (item.contains(".xls") || item.contains(".xlsb") || item.contains(".xlsm") || item.contains(".xlsx") || item.contains(".xlt") || item.contains(".xltm") || item.contains(".xltx")) {
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/xls.png"));
                } else if (item.contains(".mp4") || item.contains(".mkv") || item.contains(".mpeg") || item.contains(".flv") || item.contains(".mov") || item.contains(".avi")) {
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/mp4.png"));
                } else if (item.contains(".mp3") || item.contains(".m3u8") || item.contains(".mid") || item.contains(".midi") || item.contains(".m4a") || item.contains(".flac") || item.contains(".mp2")) {
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/mp3.png"));
                } else if (item.contains(".png") || item.contains(".bmp") || item.contains(".jpeg") || item.contains(".jpg") || item.contains(".gif") || item.contains(".webp")) {
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/png.png"));
                } else if (item.contains(".txt") || item.contains(".rtf")) {
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/txt.png"));
                } else if (item.contains(".pdf")) {
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/pdf.png"));
                } else if(item.contains(".zip")||item.contains(".7z")||item.contains(".rar")||item.contains(".tar")||item.contains(".gz")||item.contains(".bz2")||item.contains(".lz4")){
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/zip.png"));
                }else if (item.contains(".java") || item.contains(".jar")) {
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/java.png"));
                } else if (item.contains(".md") || item.contains(".markdown")) {
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/markdown.png"));
                }else if (item.contains(".html")||item.contains(".py")||item.contains(".h")||item.contains(".c")||item.contains(".php")||item.contains(".cc")||item.contains(".cpp")||item.contains(".R")||item.contains(".go")||item.contains(".cxx")||item.contains(".py3")||item.contains(".vb")||item.contains(".js")||item.contains(".asm")||item.contains(".s")||item.contains(".S")){
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/code.png"));
                }else{
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/unknown.png"));
                }
                setText(item.toString());
            }else{
                setGraphic(null);
            }
        }
    }
}
