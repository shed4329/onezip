package onezip.themes.fxJava.fluent;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.*;

import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;
import net.lingala.zip4j.exception.ZipException;

import onezip.CompressUtils.zip.zipUtils;
import onezip.Service.RARExtractService;
import onezip.Service.SevenZipCompressService;
import onezip.Service.SevenZipExtractService;
import onezip.Service.ZipScheduledService;
import onezip.testOne;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class normalFrame extends Application {
    ArrayList<File> fileArrayList = new ArrayList<>();
    ArrayList<File> folderArrayList = new ArrayList<>();
    File archive;
    static boolean compressPaneStatus = true;//false->解压面板，ture->压缩面板
    ListView<String> compressListView = new ListView();
    int compressFormatType = 0;//0=zip,1=7z
    boolean compressIsPasswordEncrypted = false;
    ObservableList<String> compressFileNameList=FXCollections.observableArrayList();
    ListView<String> extractListView = new ListView();
    static int settingTimes = 0;
    Button extractStart = new Button("确定");
    PasswordField extractPasswordField = new PasswordField();
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
            compressAll.setOnAction(actionEvent1 -> {
                if(compressFileNameList.size()!=0) {
                    VBox vBox0 = new VBox();
                    Text text = new Text("\n压缩选项");
                    text.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 22));

                    Text text2 = new Text("目标文件夹");
                    HBox hBox2 = new HBox();
                    hBox2.setSpacing(10);
                    TextField textField = new TextField();
                    textField.setPrefWidth(400);
                    textField.setText(System.getProperty("user.home"));
                    textField.setEditable(false);
                    Button button = new Button("...");
                    hBox2.getChildren().addAll(textField, button);

                    Text text3 = new Text("文件名称");
                    TextField textField1 = new TextField();
                    //textField1.setPrefWidth(400);//好像直接上组件就与面板宽度一致

                    Text text4 = new Text("压缩格式");
                    ChoiceBox choiceBox = new ChoiceBox(FXCollections.observableArrayList("zip", "7z"));
                    choiceBox.getSelectionModel().selectFirst();
                    choiceBox.setPrefWidth(430);

                    Button button1 = new Button("高级");

                    Text text5 = new Text("压缩级别");
                    ChoiceBox choiceBox2 = new ChoiceBox(FXCollections.observableArrayList("正常", "快速", "最快", "极限压缩"));
                    choiceBox2.getSelectionModel().selectFirst();

                    CheckBox checkBox = new CheckBox("使用密码加密");
                    Text text6 = new Text("密码");
                    PasswordField passwordField0 = new PasswordField();
                    Text text7 = new Text("确认密码");
                    PasswordField passwordField1 = new PasswordField();

                    vBox0.getChildren().addAll(text, text2, hBox2, text3, textField1, text4, choiceBox, button1, text5, choiceBox2, checkBox, text6, passwordField0, text7, passwordField1);
                    vBox0.setSpacing(10);

                    HBox hBox0 = new HBox();
                    Button start = new Button("确认");
                    Button cancel = new Button("取消");
                    start.setStyle("-fx-background-color:#87cefa");
                    start.setPrefWidth(200);
                    cancel.setPrefWidth(200);
                    hBox0.getChildren().addAll(start, cancel);
                    hBox0.setAlignment(Pos.BOTTOM_CENTER);
                    hBox0.setSpacing(10);
                    hBox0.setLayoutY(290);
                    Group group = new Group();
                    group.getChildren().addAll(vBox0, hBox0);

                    Scene compressChildScene = new Scene(group);
                    JMetro jMetro1 = new JMetro();
                    jMetro1.setScene(compressChildScene);
                    Stage compressChildStage = new Stage();
                    compressChildStage.setWidth(450);
                    compressChildStage.setHeight(317);
                    compressChildStage.initStyle(StageStyle.UNDECORATED);
                    compressChildStage.setScene(compressChildScene);
                    compressChildStage.show();
                    final boolean[] advanced = {false};
                    button1.setOnAction(actionEvent2 -> {
                        if (advanced[0]) {//已有高级页面
                            compressChildStage.setHeight(317);
                            hBox0.setLayoutY(290);
                            advanced[0] = false;
                        } else {//未进入
                            compressChildStage.setHeight(408);
                            hBox0.setLayoutY(380);
                            advanced[0] = true;
                            checkBox.setSelected(false);
                        }
                    });
                    checkBox.selectedProperty().addListener(new ChangeListener<Boolean>() {
                        @Override
                        public void changed(ObservableValue<? extends Boolean> observableValue, Boolean aBoolean, Boolean t1) {
                            if (t1) {//if selected
                                compressChildStage.setHeight(530);
                                hBox0.setLayoutY(500);
                                compressIsPasswordEncrypted = true;
                            } else {
                                compressChildStage.setHeight(408);
                                hBox0.setLayoutY(380);
                                compressIsPasswordEncrypted = false;
                            }
                        }
                    });
                    choiceBox.getSelectionModel().selectedIndexProperty().addListener(new ChangeListener<Number>() {
                        @Override
                        public void changed(ObservableValue<? extends Number> observableValue, Number number, Number t1) {
                            if (t1.intValue() == 1) {//0=zip,1=7z
                                choiceBox2.getSelectionModel().selectFirst();
                                choiceBox2.setDisable(true);
                                compressFormatType = 1;
                            } else {
                                choiceBox2.setDisable(false);
                                compressFormatType = 0;
                            }
                        }
                    });
                    button.setOnAction(actionEvent2 -> {
                        DirectoryChooser directoryChooser = new DirectoryChooser();
                        directoryChooser.setTitle("选择文件夹");
                        File folder = directoryChooser.showDialog(compressChildStage);
                        if (folder != null) {
                            textField.setText(folder.getPath());
                        }
                    });
                    cancel.setOnAction(actionEvent2 -> {
                        compressChildStage.close();
                    });
                    start.setOnAction(actionEvent2 -> {
                        //检验环节
                        String filePath = textField.getText();
                        if (filePath == null || filePath.isEmpty()) {
                            testOne.alert("还没有选择保存路径");
                        } else {
                            String fileName = textField1.getText();
                            if (fileName == null || fileName.isEmpty()) {
                                testOne.alert("请输入文件名");
                            } else {
                                int compressLevel = choiceBox2.getSelectionModel().getSelectedIndex();
                                if (compressLevel == 1) {
                                    compressLevel = 3;
                                } else if (compressLevel == 2) {
                                    compressLevel = 1;
                                } else if (compressLevel == 3) {
                                    compressLevel = 8;
                                }
                                if (compressIsPasswordEncrypted) {//这边是加密区
                                    String password0 = passwordField0.getText();
                                    String password1 = passwordField1.getText();
                                    if (password0 == null || password0.isEmpty()) {
                                        testOne.alert("密码不能为空");
                                    } else if (password1 == null || password1.isEmpty()) {
                                        testOne.alert("密码不能为空");
                                    } else if (password0.equals(password1)) {
                                        if (compressFormatType == 0) {//zip
                                            try {
                                                ZipScheduledService zipScheduledService = new ZipScheduledService(1, compressLevel, 0, fileArrayList, folderArrayList, new File(filePath + File.separator + fileName + ".zip"), true, password0);
                                                zipScheduledService.start();//服务异常
                                            } catch (Exception e) {
                                                throw new RuntimeException(e);
                                            }
                                        } else {
                                            ArrayList<File> arrayList = new ArrayList<>();
                                            arrayList.addAll(fileArrayList);
                                            arrayList.addAll(folderArrayList);
                                            SevenZipCompressService sevenZipCompressService = new SevenZipCompressService(arrayList, new File(filePath + File.separator + fileName + ".7z").getPath(), password0);
                                            sevenZipCompressService.start();
                                        }
                                    } else {
                                        testOne.alert("密码不一致");
                                    }
                                } else {
                                    if (compressFormatType == 0) {//zip
                                        try {
                                            ZipScheduledService zipScheduledService = new ZipScheduledService(1, compressLevel, 0, fileArrayList, folderArrayList, new File(filePath + File.separator + fileName + ".zip"), false, "");
                                            zipScheduledService.start();//服务异常
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    } else {
                                        ArrayList<File> arrayList = new ArrayList<>();
                                        arrayList.addAll(fileArrayList);
                                        arrayList.addAll(folderArrayList);
                                        SevenZipCompressService sevenZipCompressService = new SevenZipCompressService(arrayList, new File(filePath + File.separator + fileName + ".7z").getPath());
                                        sevenZipCompressService.start();
                                    }
                                }
                            }
                        }
                    });
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
                if (file!=null&&archive==null){
                    archive=file;
                    extractListView.setItems(FXCollections.observableArrayList(file.getName()));
                    extractListView.setCellFactory((ListView<String> l) -> new extractFormatIconCell());
                }
            });
            extractAll.setOnAction(actionEvent1 -> {
                if (archive!=null){
                    Notification notification = new Notification();
                    DirectoryChooser directoryChooser = new DirectoryChooser();
                    directoryChooser.setTitle("选择文件夹");
                    File directory = directoryChooser.showDialog(stage);
                    String format = archive.getName();
                    if (format.contains(".zip")){
                        try {
                            if (onezip.CompressUtils.zip.zipUtils.isEncrypted(archive)){
                                extractStart.setOnAction(actionEvent2 -> {
                                    String str = extractPasswordField.getText();
                                    if (str!=null&&str.isEmpty()==false) {
                                        try {
                                            ZipScheduledService zipScheduledService = new ZipScheduledService(2,archive,directory,Charset.forName("gbk"),str);
                                            zipScheduledService.start();
                                            notification.displayTray("完成",archive.getName()+"已成功解压到"+directory.getPath());
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                });
                                extractPasswordProtectedPane(stage);
                            }else{
                                zipUtils.unzip(archive,directory, Charset.forName("gbk"));
                                notification.displayTray("完成",archive.getName()+"已成功解压到"+directory.getPath());
                            }
                        } catch (ZipException e) {
                            throw new RuntimeException(e);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }else if (format.contains(".7z")){
                        System.out.println("here");
                        if (onezip.CompressUtils.SevenZip.ExtractUtils.isEncrypted(archive.getPath())) {
                            extractStart.setOnAction(actionEvent2 -> {
                                String str = extractPasswordField.getText();
                                if (str!=null&&str.isEmpty()==false) {
                                    SevenZipExtractService sevenZipExtractService = new SevenZipExtractService(archive.getPath(), directory.getPath(), str);
                                    sevenZipExtractService.setFluentNotice(true);
                                    sevenZipExtractService.start();
                                }
                            });
                            extractPasswordProtectedPane(stage);
                        }else{
                            SevenZipExtractService sevenZipExtractService = new SevenZipExtractService(archive.getPath(), directory.getPath());
                            sevenZipExtractService.setFluentNotice(true);
                            sevenZipExtractService.start();
                        }
                    }else{
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setHeaderText("该文件是否加密？");
                        alert.setContentText("由于技术原因，我们无法确认这个压缩包是否加密，如果您认为该压缩包已加密，请点击确定，若没有加密，请点击取消");
                        Optional<ButtonType> result = alert.showAndWait();
                        if (result.get()==ButtonType.OK){
                            extractStart.setOnAction(actionEvent2 -> {
                                String str = extractPasswordField.getText();
                                if (str!=null&&str.isEmpty()==false) {
                                    RARExtractService rarExtractService = new RARExtractService(archive.getPath(), directory.getPath(),str);
                                    rarExtractService.setFluentNotice(true);
                                    rarExtractService.start();
                                }
                            });
                            extractPasswordProtectedPane(stage);
                        }else{
                            RARExtractService rarExtractService = new RARExtractService(archive.getPath(), directory.getPath());
                            rarExtractService.setFluentNotice(true);
                            rarExtractService.start();
                        }
                    }
                }
            });
            addFile.setOnAction(actionEvent1 -> {
                testOne.alertWarning("sorry,该功能尚未开放");
            });
            deleteFile.setOnAction(actionEvent1 -> {
                testOne.alertWarning("sorry,该功能尚未开放");
            });
            comment.setOnAction(actionEvent1 -> {
                if (archive!=null&&archive.getName().contains(".zip")){
                    try {
                        String zipComment = zipUtils.getComment(archive);
                        String UTF_8Comment = new String(zipComment.getBytes(), "UTF-8");
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setHeaderText("是否编辑注释？");
                        alert.setContentText("文件注释: "+UTF_8Comment);
                        Optional<ButtonType> result = alert.showAndWait();
                        if (result.get()==ButtonType.OK){
                            Alert alert2 = new Alert(Alert.AlertType.WARNING);
                            alert2.setTitle("警告");
                            alert2.setHeaderText("可能导致的乱码");
                            alert2.setContentText("编辑注释可能会导致在其他解压软件（如Bandizip）打开文件时文件名乱码");
                            alert2.showAndWait();

                            String commentToAdd = commentInputDialog();
                            zipUtils.setComment(commentToAdd,archive);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

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
        extractListView.setOnDragOver(dragEvent -> {
            if (dragEvent.getGestureSource() != extractListView) {
                dragEvent.acceptTransferModes(TransferMode.ANY);
            }
        });
        extractListView.setOnDragDropped(dragEvent -> {
            Dragboard dragboard = dragEvent.getDragboard();
            List<File> files = dragboard.getFiles();
            if (files.size()==1&&archive==null){
                File file = files.get(0);
                String str =file.getName();
                if (str.contains(".zip")||str.contains(".7z")||str.contains(".rar"))
                archive=file;
                extractListView.setItems(FXCollections.observableArrayList(file.getName()));
                extractListView.setCellFactory((ListView<String> l) -> new extractFormatIconCell());
            }
        });
        compress.fire();//要添加监听器之后使用才有效
    }
    public void extractPasswordProtectedPane(Stage stage) {
        VBox vBox = new VBox();
        ImageView imageView = new ImageView("onezip/themes/fxJava/fluent/img/password.png");
        HBox hBox2 = new HBox();
        VBox vBox2 = new VBox();
        Text text = new Text("密码保护");
        text.setFont(Font.font("Microsoft YaHei",FontWeight.BOLD,18));
        Text text2 = new Text("请输入密码");
        vBox2.getChildren().addAll(text,text2,extractPasswordField);
        vBox2.setPrefWidth(300);
        vBox2.setSpacing(10);
        hBox2.getChildren().addAll(imageView,vBox2);
        hBox2.setSpacing(15);
        hBox2.setPadding(new Insets(10));

        HBox hBox3 = new HBox();

        Button cancel = new Button("取消");
        extractStart.setPrefWidth(180);
        cancel.setPrefWidth(180);
        extractStart.setStyle("-fx-background-color:#87cefa");
        hBox3.getChildren().addAll(extractStart,cancel);
        hBox3.setSpacing(20);
        hBox3.setAlignment(Pos.BOTTOM_CENTER);
        hBox3.setPadding(new Insets(10,0,10,0));

        vBox.getChildren().addAll(hBox2,hBox3);
        vBox.setSpacing(10);
        Scene extractChildrenScene = new Scene(vBox);
        JMetro jMetro2 = new JMetro();
        jMetro2.setScene(extractChildrenScene);
        Stage extractChildrenStage = new Stage();
        extractChildrenStage.setScene(extractChildrenScene);
        extractChildrenStage.initStyle(StageStyle.UNDECORATED);
        extractChildrenStage.setWidth(450);
        extractChildrenStage.setHeight(170);
        extractChildrenStage.initOwner(stage);
        extractChildrenStage.initModality(Modality.WINDOW_MODAL);
        extractChildrenStage.show();
        cancel.setOnAction(actionEvent2 -> {
            extractChildrenStage.close();
        });
    }
    public static String commentInputDialog(){
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("注释");
        dialog.setHeaderText("编辑文件注释");
        dialog.setContentText("注释:");

        Optional<String> result = dialog.showAndWait();
        final String[] str = new String[1];
        result.ifPresent(s -> {
            str[0] =s;
        });
        System.out.println(str[0]);
        return str[0];
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
    static class extractFormatIconCell extends ListCell<String> {//自定义一个ListView组件外观选择项目组件
        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);//更新listView对象的选项组件内容
            //Rectangle rect = new Rectangle(100, 20);//创建一个矩形
            if (item != null) {
                System.out.println(item);
                //rect.setFill(Color.web(item));//填充矩形对象颜色
                if (item.contains(".zip")){
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/zipFormat.png"));
                }else if(item.contains(".7z")){
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/7zFormat.png"));
                }else{
                    setGraphic(new ImageView("onezip/themes/fxJava/fluent/img/format/rarFormat.png"));
                }
                setText(item.toString());
            }else{
                setGraphic(null);
            }
        }
    }
}
