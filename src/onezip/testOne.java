package onezip;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.ImageCursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import onezip.CompressUtils.zip.zipUtils;
import onezip.CompressUtils.SevenZip.AddOrDeleteUtils;
import onezip.CompressUtils.SevenZip.viewUtils;
import onezip.CompressUtils.SevenZip.ExtractUtils;
import onezip.FX.setting.FX_GUISetting;
import onezip.Service.SevenZipCompressService;
import onezip.Service.SevenZipExtractService;
import onezip.setting.NormalSetting;
import onezip.Service.ZipScheduledService;
import onezip.component.RARPane;

import net.lingala.zip4j.exception.ZipException;


import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import onezip.component.oneAlert;
import onezip.tool.tool;

import static onezip.component.oneAlert.textInputDialog;


public class testOne extends Application {
    File zipTo;
    File compressTo;
    String password="";
    int level=0;
    String viewPath="";
    boolean parent=false;
    ArrayList<File> compressedFiles=new ArrayList<>();
    ArrayList<File> compressedFolders=new ArrayList<>();
    ArrayList<String> listFiles=new ArrayList<>();
    int compressFormatType=999;//0=zip,1=7z, 7z不支持设置压缩级别,空文件夹不会被添加
    String extractPassword = null;
    ListView compressListView = new ListView();

     String cursorPath="";//ui自定义
    FX_GUISetting fx_guiSetting= new FX_GUISetting();
    static Image cursorImage;
    static boolean cursorAble=false;
    static boolean deleteModel=false;
    ArrayList<String> arrayList1 = new ArrayList();
    NormalSetting normalSetting1 = new NormalSetting();
    ArrayList<String> nameList =new ArrayList<>();//文件列表，预览使用
    static int argType;
    static String bootArg;
    static String charsetNameForDecoding;

    ObservableList<String> fileItems;
    public static void main(String[] args) {
        FX_GUISetting fxGuiSetting = new FX_GUISetting();
        String string;
        try {
            string = fxGuiSetting.getBootTheme();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        //if user drag a file to the application shortcut,Windows will boot the file with a parameter,the file's absolute path,so check the arg to choose the initial pane
        if (args.length==1){
            argType = tool.isFileAnArchive(args[0]);
            bootArg = args[0];
        }
        if (string!=null&&!string.isEmpty()&&string.equals("Fluent")){
            onezip.themes.fxJava.fluent.normalFrame.main(new String[]{});
        }else {
            System.out.println("here");
            launch(args);
        }
    }

    public void start(Stage stage) throws Exception {
        if (argType != 0){
            if (argType == tool.FILE_IS_ARCHIVE){
                extractPane(new File(bootArg));
            } else if (argType == tool.FILE_IS_NOT_ARCHIVE) {
                compressPane();
            }
        }else {
            Button compress = new Button("\n压缩");
            compress.setStyle("-fx-background-color:#ffffff");
            Image compressImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("img/new.png")));
            compress.setGraphic(new ImageView(compressImage));
            compress.setPrefSize(135, 135);
            Button extract = new Button("解压");
            Image extractImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("img/open.png")));
            extract.setGraphic(new ImageView(extractImage));
            extract.setStyle("-fx-background-color:#ffffff");
            extract.setPrefSize(135, 135);
            Button setting = new Button("设置");
            Image settingImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("img/setting.png")));
            setting.setGraphic(new ImageView(settingImage));
            setting.setStyle("-fx-background-color:#ffffff");
            setting.setPrefSize(135, 135);
            Button about = new Button("关于");
            Image aboutImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("img/about.png")));
            about.setGraphic(new ImageView(aboutImage));
            about.setStyle("-fx-background-color:#ffffff");
            about.setPrefSize(135, 135);


            GridPane gridPane = new GridPane();

            gridPane.add(compress, 0, 0);
            gridPane.add(extract, 1, 0);
            gridPane.add(setting, 0, 1);
            gridPane.add(about, 1, 1);

            gridPane.setHgap(25);
            gridPane.setVgap(25);
            gridPane.setAlignment(Pos.CENTER);
            gridPane.styleProperty().set("-fx-background-color:#1f88db");

            Scene scene = new Scene(gridPane);
            if (!fx_guiSetting.getCursorPath().isEmpty()) {
                cursorImage = new Image("file:" + fx_guiSetting.getCursorPath());
                scene.setCursor(new ImageCursor(cursorImage));
                cursorAble = true;
            }
            stage.setScene(scene);
            stage.setWidth(700);
            stage.setHeight(500);
            stage.setTitle("OneZip version 0.06 channel1(only for test)");
            stage.getIcons().add(new Image("img/ZIP.png"));
            stage.show();

            compress.setOnAction(actionEvent -> compressPane());
            extract.setOnAction(actionEvent -> {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("选择压缩包");
                fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("压缩文件", "*.zip", "*.7z", "*.rar"));
                File file = fileChooser.showOpenDialog(stage);
                if (file == null) {
                    
                    oneAlert.alert("文件不能为空");
                } else {
                    extractPane(file);
                }
            });
            about.setOnAction(actionEvent -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.titleProperty().set("关于");
                alert.headerTextProperty().set("OneZip\n版本号：0.06 channel1\n仅供测试");
                alert.showAndWait();
            });
            setting.setOnAction(actionEvent -> {
                Group group = new Group();

                VBox vBox = new VBox();

                group.getChildren().add(vBox);

                Button normalSetting = new Button("常规");
                normalSetting.setPrefSize(125, 35);
                normalSetting.setStyle("-fx-background-color:#4a9fe1");
                Separator separator1 = new Separator();
                Button uiSetting = new Button("外观");
                uiSetting.setPrefSize(125, 35);
                uiSetting.setStyle("-fx-background-color:#4a9fe1");
                Separator separator2 = new Separator();
                Button languageSetting = new Button("语言");
                languageSetting.setPrefSize(125, 35);
                languageSetting.setStyle("-fx-background-color:#4a9fe1");

                Button background = new Button();
                background.setPrefSize(125, 2000);
                background.setStyle("-fx-background-color:#4a9fe1");
                vBox.getChildren().addAll(normalSetting, separator1, uiSetting, separator2, languageSetting, background);

                ScrollPane scrollPane = new ScrollPane();
                VBox vBoxPaneForContent = new VBox();
                vBoxPaneForContent.setSpacing(10.0);
                scrollPane.getStyleClass().clear();
                group.getChildren().add(scrollPane);
                scrollPane.setContent(vBoxPaneForContent);
                scrollPane.setLayoutX(135);

                Scene settingScene = new Scene(group);
                if (cursorAble) {
                    settingScene.setCursor(new ImageCursor(cursorImage));
                }
                Stage settingStage = new Stage();
                settingStage.setScene(settingScene);
                settingStage.setTitle("设置");
                settingStage.getIcons().add(new Image("img/ZIP.png"));
                settingStage.setWidth(700);
                settingStage.setHeight(500);
                settingStage.show();
                uiSetting.setOnAction(e -> {
                    Text cursorText = new Text("鼠标设置");
                    TextField pictureTextField = new TextField();
                    try {
                        pictureTextField.setText(fx_guiSetting.getCursorPath());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    pictureTextField.setEditable(false);
                    pictureTextField.setPrefWidth(500);
                    Group group1 = new Group();
                    Button explore = new Button("浏览");
                    Text themesText = new Text("主题");
                    ChoiceBox choiceBox = new ChoiceBox();
                    choiceBox.setItems(FXCollections.observableArrayList("normal", "fluent"));
                    group1.getChildren().addAll(pictureTextField, explore, cursorText, themesText, choiceBox);
                    themesText.setLayoutY(80);
                    choiceBox.setLayoutY(90);
                    explore.setLayoutY(15);
                    explore.setLayoutX(510);
                    pictureTextField.setLayoutY(15);


                    Button apply = new Button("应用");

                    vBoxPaneForContent.getChildren().clear();
                    vBoxPaneForContent.getChildren().addAll(group1, apply);
                    //.setTopAnchor(group1, 10.0);
                    vBoxPaneForContent.setPrefSize(550, 450);
                    AnchorPane.setBottomAnchor(apply, 10.0);
                    choiceBox.getSelectionModel().selectedIndexProperty().addListener((observableValue, number, t1) -> {
                        if (t1.intValue() == 1) {//0->normal 1->fluent
                            try {
                                fx_guiSetting.setBootTheme("Fluent");
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    });
                    explore.setOnAction(event -> {
                        FileChooser fileChooser = new FileChooser();//选择文件
                        fileChooser.setTitle("选择图片");
                        fileChooser.getExtensionFilters().addAll(
                                new FileChooser.ExtensionFilter("PNG", "*.png"),
                                new FileChooser.ExtensionFilter("JPEG", "*.jpeg"),
                                new FileChooser.ExtensionFilter("BMP", "*.bmp")
                        );
                        File pictures = fileChooser.showOpenDialog(settingStage);

                        if (pictures == null) {
                            oneAlert.alert("图片不能为空");
                        } else {
                            cursorPath = pictures.getAbsoluteFile().toString();
                            pictureTextField.setText(cursorPath);
                        }
                        try {
                            fx_guiSetting.setCursorPath(cursorPath);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    });
                    apply.setOnAction(actionEvent1 -> {
                        if (!cursorPath.isEmpty()) {
                            oneAlert.alertSuccess();
                            try {
                                cursorImage = new Image("file:" + fx_guiSetting.getCursorPath());
                                scene.setCursor(new ImageCursor(cursorImage));
                                cursorAble = true;
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                            settingStage.close();
                        }
                    });
                });
                normalSetting.setOnAction(e -> {
                    Text text = new Text("解压设置");
                    CheckBox checkBox = new CheckBox("解压界面关闭‘预览’以节约资源");
                    Text text2 = new Text("注释编码");
                    ChoiceBox choiceBoxForLang = new ChoiceBox();
                    choiceBoxForLang.setItems(FXCollections.observableArrayList("UTF-8", "GBK","ASCII","BIG5","Shift_JIS","EUC_KR","KOI8-R","iso-8859-1","iso-8859-2","iso-8859-5"));
                    Button applyLangDecoding = new Button("应用对字符串的修改");
                    try {
                        checkBox.setSelected(NormalSetting.getViewSwitch());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        checkBox.setSelected(false);
                    }
                    checkBox.selectedProperty().addListener(ex -> {

                        if (checkBox.isSelected()) {
                            try {
                                NormalSetting.setViewSwitch(true);
                            } catch (Exception exc) {
                                exc.printStackTrace();
                            }
                        } else {
                            try {
                                NormalSetting.setViewSwitch(false);
                            } catch (Exception exc) {
                                exc.printStackTrace();
                            }
                        }
                    });
                    vBoxPaneForContent.getChildren().clear();
                    vBoxPaneForContent.getChildren().addAll(text,checkBox,text2,choiceBoxForLang,applyLangDecoding);
                    vBoxPaneForContent.setPrefSize(550, 450);
                    applyLangDecoding.setOnAction(new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent actionEvent) {
                            String string = (String) choiceBoxForLang.getValue();
                            if (string != null) {
                                NormalSetting.setCommentDecodingType((String) choiceBoxForLang.getValue());
                            }else{
                                oneAlert.alert("本次未作出更改");
                            }
                        }
                    });
                });

            });
            compress.setOnDragOver(event -> {
                if (event.getGestureSource() != compress) {
                    event.acceptTransferModes(TransferMode.ANY);
                }
            });
            compress.setOnDragDropped(dragEvent -> {
                Dragboard dragboard = dragEvent.getDragboard();
                List<File> files = dragboard.getFiles();
                System.out.println(files);
                for (File file : files) {
                    if (file.isDirectory()) {
                        compressedFolders.add(file);
                    } else {
                        compressedFiles.add(file);
                    }
                    listFiles.add(file.getPath());
                    compressListView.setItems(FXCollections.observableArrayList(listFiles));
                }
                compressPane();

            });
            extract.setOnDragOver(event -> {
                if (event.getGestureSource() != extract) {
                    event.acceptTransferModes(TransferMode.ANY);
                }
            });
            extract.setOnDragDropped(dragEvent -> {
                Dragboard dragboard = dragEvent.getDragboard();
                List<File> files = dragboard.getFiles();
                if (files.size() == 1) {
                    if (files.get(0).getName().contains(".zip") || files.get(0).getName().contains(".7z") || files.get(0).getName().contains(".rar")) {
                        extractPane(files.get(0));
                    } else {
                        oneAlert.alert("（ErrorType：想多了你）只能处理zip,7z,rar压缩包");
                    }
                } else if (files.size() >= 2) {
                    oneAlert.alert("不能贪多哟，一次只能添加一个压缩包");
                } else {
                    oneAlert.alert("没有选中文件");
                }
            });
        }
    }
    private void compressPane(){

        Group group0 = new Group();
        Text addTextHeader = new Text("添加文件到压缩文件");

        compressListView.setPrefSize(800,200);
        compressListView.setLayoutY(10);
        Button exploreFolder = new Button("浏览文件夹");
        Button exploreFile = new Button("浏览文件");
        group0.getChildren().addAll(addTextHeader,compressListView,exploreFolder,exploreFile);
        exploreFile.setLayoutY(215);
        exploreFolder.setLayoutY(215);
        exploreFolder.setLayoutX(70);

        Group group1 = new Group();
        Text header = new Text("压缩文件设置");
        header.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD,14));
        group1.getChildren().add(header);

        Group group2 = new Group();
        Text fileHeader = new Text("文件名");
        fileHeader.setLayoutY(15);
        TextField fileTextField = new TextField();
        fileTextField.setEditable(false);
        fileTextField.setPrefWidth(500);
        Button explore = new Button("浏览");


        group2.getChildren().addAll(fileHeader,fileTextField,explore);
        fileTextField.setLayoutX(120);
        explore.setLayoutX(640);


        Button setPassword = new Button("设置密码(P)");



        Group group3 = new Group();
        Text zipSetting = new Text("压缩级别");
        zipSetting.setLayoutY(15);
        ChoiceBox<String> compressLevelChoiceBox = new ChoiceBox<>();
        compressLevelChoiceBox.setItems(FXCollections.observableArrayList("1","3","5","7","9"));
        group3.getChildren().addAll(zipSetting,compressLevelChoiceBox);
        compressLevelChoiceBox.setLayoutX(120);


        Group group4 = new Group();
        Text formatSetting = new Text("压缩文件格式");
        formatSetting.setLayoutY(15);
        ChoiceBox<String> formatChoiceBox = new ChoiceBox<>();
        formatChoiceBox.setItems(FXCollections.observableArrayList("zip","7z"));
        group4.getChildren().addAll(formatSetting,formatChoiceBox);
        formatChoiceBox.setLayoutX(120);

        Group group5 = new Group();
        Button start = new Button("开始(s)");
        Button cancel = new Button("取消");
        group5.getChildren().addAll(start,cancel);
        cancel.setLayoutX(70);


        VBox box = new VBox();
        box.getChildren().addAll(group0,group1,group2,setPassword,group3,group4,group5);
        box.setSpacing(10.0);

        Scene compressScene = new Scene(box);
        if (cursorAble){//自定义鼠标
            compressScene.setCursor(new ImageCursor(cursorImage));
        }
        if (argType==tool.FILE_IS_NOT_ARCHIVE){//如果启动参数进入设置文本框
            fileTextField.setText(bootArg);
            File file = new File(bootArg);
            if (file.isDirectory()){
                compressedFolders.add(file);
            }else{
                compressedFiles.add(file);
            }

        }

        Stage compressFrame = new Stage();
        compressFrame.setScene(compressScene);
        compressFrame.setTitle("新建压缩文件");
        compressFrame.setHeight(490);
        compressFrame.setWidth(800);
        compressFrame.initModality(Modality.WINDOW_MODAL);
        compressFrame.getIcons().add(new Image("img/ZIP.png"));
        compressFrame.show();

        exploreFile.setOnAction(actionEvent1 -> {
            FileChooser fileChooser1 = new FileChooser();
            fileChooser1.setTitle("选择文件");
            File compressedFile = fileChooser1.showOpenDialog(compressFrame);
            System.out.println(compressedFile);
            if (compressedFile!=null&compressedFile.exists()){
                compressedFiles.add(compressedFile);
                listFiles.add(compressedFile.getPath());
                compressListView.setItems(FXCollections.observableArrayList(listFiles));
            }
        });
        exploreFolder.setOnAction(actionEvent1 -> {

            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("选择文件夹");
            File compressedFolder = directoryChooser.showDialog(compressFrame);
            System.out.println(compressedFolder);
            if (compressedFolder!=null&compressedFolder.exists()){
                compressedFolders.add(compressedFolder);
                listFiles.add(compressedFolder.getPath());
                compressListView.setItems(FXCollections.observableArrayList(listFiles));


            }

        });
        explore.setOnAction(actionEvent13 -> {
            FileChooser fileChooser1 = new FileChooser();
            fileChooser1.setTitle("保存为");
            File savedFile = fileChooser1.showSaveDialog(compressFrame);
            if (savedFile==null) {
                oneAlert.alert("文件不能为空");
            }else{
                String path = savedFile.getAbsoluteFile().toString();
                if (!path.contains(".")) {
                    fileTextField.setText(path);
                } else {
                    String value = path.substring(0, path.lastIndexOf("."));
                    fileTextField.setText(value);
                }
            }
        });
        setPassword.setOnAction(actionEvent1 -> {
            Text input1 = new Text("请输入密码");
            PasswordField passwordField1 = new PasswordField();
            Text input2 = new Text("请再次输入密码");
            PasswordField passwordField2 = new PasswordField();

            Group group = new Group();
            Button apply = new Button("确定");
            Button cancel1 = new Button("取消");
            group.getChildren().addAll(apply, cancel1);
            cancel1.setLayoutX(50);

            VBox vBox = new VBox();
            vBox.getChildren().addAll(input1,passwordField1,input2,passwordField2,group);

            Scene passwordScene = new Scene(vBox);
            if (cursorAble){
                passwordScene.setCursor(new ImageCursor(cursorImage));
            }

            Stage passwordSetStage = new Stage();
            passwordSetStage.setScene(passwordScene);
            passwordSetStage.setTitle("输入密码");
            passwordSetStage.initOwner(compressFrame);
            passwordSetStage.getIcons().add(new Image("img/ZIP.png"));
            passwordSetStage.initModality(Modality.WINDOW_MODAL);
            passwordSetStage.show();
            apply.setOnAction(actionEvent11 -> {
                String password1 = passwordField1.getText();
                String password2 = passwordField2.getText();
                if(!password1.isEmpty()&&!password2.isEmpty()){
                    if (password1.equals(password2)){
                        System.out.println(password1);
                        password=password1;
                        oneAlert.alertInfo("成功","密码设置成功");
                        passwordSetStage.close();
                    }else{
                        oneAlert.alert("密码不一致");
                    }
                }else{
                    oneAlert.alert("密码不能为空");
                }
            });
            cancel1.setOnAction(actionEvent112 -> passwordSetStage.close());
        });
        compressLevelChoiceBox.getSelectionModel().selectedIndexProperty().addListener((observableValue, number, t1) -> {
            level = t1.intValue();
            System.out.println(level);
        });
        formatChoiceBox.getSelectionModel().selectedIndexProperty().addListener(((observableValue, number, t1) -> {
            compressFormatType=t1.intValue();
            System.out.println(compressFormatType);
            if (compressFormatType==0){
                String str=fileTextField.getText();
                if (str.contains(".zip")||str.contains(".7z")){
                    str=str.substring(0,str.lastIndexOf("."));
                }
                fileTextField.setText(str+".zip");
                zipTo = new File(str+ ".zip");
            }else if (compressFormatType==1){
                String str=fileTextField.getText();
                if (str.contains(".zip")||str.contains(".7z")){
                    str=str.substring(0,str.lastIndexOf("."));
                }
                fileTextField.setText(str+".7z");
                compressTo = new File(str+ ".7z");
            }
        }));

        start.setOnAction(actionEvent12 -> {
            System.out.println(compressedFiles);
            System.out.println(compressedFolders);
            if (compressFormatType==999){
                oneAlert.alert("请选择文件格式");
            } else if (compressFormatType==0) {
                if (zipTo==null){
                    oneAlert.alert("还没选择保存路径");
                }else{

                    if(password.isEmpty()){
                        try {
                            ZipScheduledService zipScheduledService = new ZipScheduledService(1,level,0,compressedFiles,compressedFolders,zipTo,false,password);
                            zipScheduledService.start();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }else{
                        try {
                            ZipScheduledService zipScheduledService = new ZipScheduledService(1,level,0,compressedFiles,compressedFolders,zipTo,true,password);
                            zipScheduledService.start();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }else if (compressFormatType==1){
                if (compressTo==null){
                    oneAlert.alert("还没选择保存路径");
                }else{
                    ArrayList<File> arrayList = new ArrayList<>();
                    arrayList.addAll(compressedFiles);
                    arrayList.addAll(compressedFolders);
                    if(password.isEmpty()){
                        try {
                            System.out.println(arrayList);
                            SevenZipCompressService sevenZipCompressService = new SevenZipCompressService(arrayList,compressTo.getPath());
                            sevenZipCompressService.start();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }else{
                        try {
                            SevenZipCompressService sevenZipCompressService = new SevenZipCompressService(arrayList,compressTo.getPath(),password);
                            sevenZipCompressService.start();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

        });
        compressListView.setOnDragOver(event -> {
            if (event.getGestureSource() != compressListView) {
                event.acceptTransferModes(TransferMode.ANY);
            }
        });
        compressListView.setOnDragDropped(dragEvent -> {
            Dragboard dragboard = dragEvent.getDragboard();
            List<File> files = dragboard.getFiles();
            System.out.println(files);
            for (File file:files){
                if (!listFiles.contains(file.getPath())) {
                    if (file.isDirectory()) {
                        compressedFolders.add(file);
                    } else {
                        compressedFiles.add(file);
                    }
                    listFiles.add(file.getPath());
                    compressListView.setItems(FXCollections.observableArrayList(listFiles));
                }
            }
        });
        cancel.setOnAction(actionEvent14 -> compressFrame.close());
    }
    private void extractPane(File file) {
        if (file.getName().contains(".zip")) {
            try {
                if (!zipUtils.isValid(file)) {
                    oneAlert.alert("无效的文件");
                } else {
                    HBox northPane = new HBox();
                    Button viewPaneExtract = new Button("解压文件");
                    Image unzipImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("img/unzip.png")));
                    viewPaneExtract.setGraphic(new ImageView(unzipImage));
                    viewPaneExtract.setStyle("-fx-background-color:#4b9fe2");
                    Button viewPaneAdd = new Button("添加文件");
                    Image addImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("img/fileAdd.png")));
                    viewPaneAdd.setGraphic(new ImageView(addImage));
                    viewPaneAdd.setStyle("-fx-background-color:#4b9fe2");
                    Button viewPaneDelete = new Button("删除文件");
                    Image deleteImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("img/delete.png")));
                    viewPaneDelete.setGraphic(new ImageView(deleteImage));
                    viewPaneDelete.setStyle("-fx-background-color:#4b9fe2");
                    Button viewPaneSetComment = new Button("编辑注释");
                    Image commentImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("img/comments.png")));
                    viewPaneSetComment.setGraphic(new ImageView(commentImage));
                    viewPaneSetComment.setStyle("-fx-background-color:#4b9fe2");
                    northPane.getChildren().addAll(viewPaneExtract, viewPaneAdd, viewPaneDelete, viewPaneSetComment);
                    viewPaneExtract.setPrefSize(100, 50);
                    viewPaneAdd.setPrefSize(100, 50);
                    viewPaneDelete.setPrefSize(100, 50);
                    viewPaneSetComment.setPrefSize(100, 50);


                    AnchorPane southPane = new AnchorPane();
                    TextArea commentArea = new TextArea();
                    try {
                        String comment = zipUtils.getComment(file);
                        if (!comment.isEmpty()) {
                            System.out.println("s-" + NormalSetting.getCommentDecodingType());
                            String decodedComment = new String(comment.getBytes(), NormalSetting.getCommentDecodingType());
                            commentArea.setText(decodedComment);
                            commentArea.setPrefWidth(800);
                            commentArea.setEditable(false);
                            southPane.getChildren().add(commentArea);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    AnchorPane middlePane = new AnchorPane();

                    ListView<String> listView = new ListView<>();//文件列表
                    listView.setPrefWidth(800);
                    boolean normalSet = true;

                    try {
                        normalSet = (!normalSetting1.getViewSwitch());
                        if (normalSet) {
                            System.out.println(normalSetting1.getViewSwitch());
                            list(file);
                        } else {
                            fileItems = FXCollections.observableArrayList("预览已关闭");
                            System.out.println("预览已关闭");
                        }
                        listView.setItems(fileItems);
                    } catch (ZipException e) {
                        e.printStackTrace();
                        fileItems = FXCollections.observableArrayList("预览失败");
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                        try {
                            list(file);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                    middlePane.getChildren().add(listView);

                    AnchorPane westPane = new AnchorPane();
                    System.out.println("Name：" + file.getName());

                    BorderPane borderPane = new BorderPane();
                    borderPane.setTop(northPane);
                    borderPane.setLeft(westPane);
                    borderPane.setBottom(southPane);
                    borderPane.setCenter(middlePane);

                    Scene viewScene = new Scene(borderPane);
                    if (cursorAble) {//自定义鼠标
                        viewScene.setCursor(new ImageCursor(cursorImage));
                    }
                    Stage viewStage = new Stage();
                    viewStage.setScene(viewScene);
                    viewStage.setWidth(800);
                    viewStage.setHeight(600);
                    viewStage.getIcons().add(new Image("img/ZIP.png"));
                    viewStage.setTitle(file.getName());
                    //stage.close();
                    viewStage.show();

                    boolean finalNormalSet = normalSet;
                    listView.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseEvent -> {
                        if (mouseEvent.getClickCount() == 2) {

                            if (finalNormalSet && !deleteModel) {
                                String newValue = listView.getSelectionModel().getSelectedItem();
                                //System.out.println("old value:"+oldValue);
                                System.out.println("new value:" + newValue);
                                System.out.println("\033[45m" + "viewPath:" + viewPath + "");//紫色标注
                                if (newValue.equals("..")) {
                                    int temp = viewPath.substring(0, viewPath.length() - 2).lastIndexOf("/");//查找最后出现的位置(最末的”/“不算)
                                    if (temp == -1) {//没有”/“
                                        viewPath = "";
                                    } else {
                                        viewPath = viewPath.substring(0, temp);
                                    }
                                    System.out.println("viewPath.." + viewPath);
                                    fileItems = FXCollections.observableArrayList(zipUtils.viewInPath(nameList, viewPath));
                                    System.out.println("items:" + fileItems);
                                    parent = true;
                                } else {
                                    if (parent) {//如果按了..,
                                        if (!viewPath.isEmpty() && !viewPath.endsWith("/")) {//空的话在根目录,用短路与防止判断第二个条件时报错，如果在最后一个字符为“/”
                                            viewPath = viewPath + "/";//..会导致路径末尾的”/“丢失
                                        }
                                        parent = false;
                                    }

                                    viewPath = viewPath + newValue;
                                    System.out.println("\033[44m" + "viewPath:" + viewPath + "");

                                    fileItems = FXCollections.observableArrayList(zipUtils.viewInPath(nameList, viewPath.substring(0, viewPath.length() - 1)));//去掉viewPath里最后的"/"
                                }

                                System.out.println(nameList.get(0));

                                listView.setItems(fileItems);


                            }
                        }
                    });


                    viewPaneExtract.setOnAction(actionEvent15 -> {
                        String extractPassword = null;
                        DirectoryChooser directoryChooser = new DirectoryChooser();
                        directoryChooser.setTitle("解压到");
                        File extractTo = directoryChooser.showDialog(viewStage);
                        try {
                            if (zipUtils.isEncrypted(file)) {

                                Alert alert = new Alert(Alert.AlertType.WARNING);
                                alert.setTitle("警告");
                                alert.setHeaderText("可能出现的问题");
                                alert.setContentText("本文件已加密，若文件解压密码输入错误，将只会解压文件夹，并不会提示错误，而是提示‘成功’");

                                alert.showAndWait();

                                extractPassword = textInputDialog();

                            }
                        } catch (ZipException e) {
                            throw new RuntimeException(e);
                        }
                        if (extractTo == null) {
                            oneAlert.alert("请选择解压位置");
                        } else {
                            try {

                                if (extractPassword == null || extractPassword.isEmpty()) {
                                    ZipScheduledService zipScheduledService = new ZipScheduledService(2, file, extractTo, Charset.forName("gbk"));
                                    zipScheduledService.start();
                                } else {
                                    ZipScheduledService zipScheduledService = new ZipScheduledService(2, file, extractTo, Charset.forName("gbk"), extractPassword);
                                    zipScheduledService.start();
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    viewPaneSetComment.setOnAction(actionEvent16 -> {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("警告");
                        alert.setHeaderText("可能导致的乱码");
                        alert.setContentText("编辑注释可能会导致在其他解压软件（如Bandizip）打开文件时文件名乱码");

                        alert.showAndWait();

                        TextInputDialog dialog = new TextInputDialog();
                        dialog.setTitle("注释");
                        dialog.setHeaderText("编辑压缩文件的注释");
                        dialog.setContentText("注释:");
                        Optional<String> result = dialog.showAndWait();
                        result.ifPresent(s -> {
                            System.out.println("comment: " + s);
                            zipUtils.setComment(s, file);
                            oneAlert.alertSuccess();
                        });

                    });
                    viewPaneAdd.setOnAction(actionEvent17 -> {
                        FileChooser fileChooser1 = new FileChooser();//选择文件
                        fileChooser1.setTitle("要添加的文件");

                        File toAdd = fileChooser1.showOpenDialog(viewStage);
                        AddOrDeleteScheduledService addOrDeleteScheduledService = new AddOrDeleteScheduledService(toAdd, file);
                        addOrDeleteScheduledService.start();
                    });
                    viewPaneDelete.setOnAction(actionEvent18 -> {
                        deleteModel = !deleteModel;
                            /*System.out.println(listView.getSelectionModel().getSelectedItem());
                            System.out.println(listView.getSelectionModel().getSelectedItem().substring(0,listView.getSelectionModel().getSelectedItem().length()-2));
                            System.out.println(viewPath);
                            System.out.println(viewPath.substring(0,viewPath.length()-2));

                             */
                        String listItemName = listView.getSelectionModel().getSelectedItem();
                        if (listItemName.contains(".")) {
                            Alert alert = new Alert(Alert.AlertType.WARNING);
                            alert.setTitle("警告");
                            alert.setHeaderText("可能出现的问题");
                            alert.setContentText("1.这可能会导致其他解压软件打开该压缩文件时中文乱码\n2.由于本功能存在一定问题，即使提示‘成功’也有可能未删除文件（诈骗功能）");

                            alert.showAndWait();
                            String str = (viewPath.replace("\\", "/") + listItemName.substring(0, listItemName.length()));
                            System.out.println("str:" + str);
                            AddOrDeleteScheduledService addOrDeleteScheduledService = new AddOrDeleteScheduledService(str, file);
                            addOrDeleteScheduledService.start();
                        }


                    });
                }
            } catch (ZipException e) {
                e.printStackTrace();
            }
        }else if (file.getName().contains(".7z")) {
            HBox northPane = new HBox();
            Button viewPaneExtract = new Button("解压文件");
            Image unzipImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("img/unzip.png")));
            viewPaneExtract.setGraphic(new ImageView(unzipImage));
            viewPaneExtract.setStyle("-fx-background-color:#4b9fe2");
            Button viewPaneAdd = new Button("添加文件");
            Image addImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("img/fileAdd.png")));
            viewPaneAdd.setGraphic(new ImageView(addImage));
            viewPaneAdd.setStyle("-fx-background-color:#4b9fe2");
            Button viewPaneDelete = new Button("删除文件");
            Image deleteImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("img/delete.png")));
            viewPaneDelete.setGraphic(new ImageView(deleteImage));
            viewPaneDelete.setStyle("-fx-background-color:#4b9fe2");

            northPane.getChildren().addAll(viewPaneExtract, viewPaneAdd, viewPaneDelete);
            viewPaneExtract.setPrefSize(100, 50);
            viewPaneAdd.setPrefSize(100, 50);
            viewPaneDelete.setPrefSize(100, 50);
            AnchorPane westPane = new AnchorPane();
            System.out.println("Name：" + file.getName());
            AnchorPane middlePane = new AnchorPane();

            ListView<String> listView = new ListView<>();//文件列表
            listView.setPrefWidth(800);
            boolean normalSet = true;

            try {
                normalSet = (!normalSetting1.getViewSwitch());
                if (normalSet) {
                    System.out.println(normalSetting1.getViewSwitch());
                    if (ExtractUtils.isEncrypted(file.getPath())){
                        extractPassword = textInputDialog();
                        sevenZipList(file, extractPassword);
                    }else {
                        sevenZipList(file);
                    }
                } else {
                    fileItems = FXCollections.observableArrayList("预览已关闭");
                    System.out.println("预览已关闭");
                }
                listView.setItems(fileItems);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("3");
                sevenZipList(file);
            }


            middlePane.getChildren().add(listView);
            BorderPane borderPane = new BorderPane();
            borderPane.setTop(northPane);
            borderPane.setLeft(westPane);

            borderPane.setCenter(middlePane);

            Scene viewScene = new Scene(borderPane);
            if (cursorAble) {//自定义鼠标
                viewScene.setCursor(new ImageCursor(cursorImage));
            }

            Stage viewStage = new Stage();
            viewStage.setScene(viewScene);
            viewStage.setWidth(800);
            viewStage.setHeight(600);
            viewStage.getIcons().add(new Image("img/ZIP.png"));
            viewStage.setTitle(file.getName());
            //stage.close();
            viewStage.show();
            boolean finalNormalSet = normalSet;

            listView.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseEvent -> {
                if(mouseEvent.getClickCount()==2) {

                    if (finalNormalSet && !deleteModel) {
                        String newValue = listView.getSelectionModel().getSelectedItem();
                        //System.out.println("old value:"+oldValue);
                        System.out.println("new value:" + newValue);
                        System.out.println("\033[45m" + "viewPath:" + viewPath + "");//紫色标注
                        if (viewPath == null || viewPath.isEmpty()) {
                            System.out.println(newValue);
                            viewPath = newValue;
                            System.out.println(viewPath);
                            arrayList1 = viewUtils.getFileNameInPath(nameList, viewPath);
                            System.out.println(arrayList1);
                            fileItems = FXCollections.observableArrayList(arrayList1);
                        } else {
                            if (newValue.equals("..")) {
                                if (viewPath.contains("\\")) {
                                    int i = viewPath.lastIndexOf("\\");
                                    viewPath = viewPath.substring(0, i);

                                } else {
                                    viewPath = "";
                                }
                                arrayList1 = viewUtils.getFileNameInPath(nameList, viewPath);
                                fileItems = FXCollections.observableArrayList(arrayList1);
                            } else {
                                viewPath = viewPath + newValue.replace(viewPath, "");
                                System.out.println(newValue);
                                arrayList1 = viewUtils.getFileNameInPath(nameList, viewPath);
                                fileItems = FXCollections.observableArrayList(arrayList1);
                            }
                        }
                        listView.setItems(fileItems);
                        arrayList1.clear();
                    }
                }
            });
            viewPaneExtract.setOnAction(actionEvent -> {

                DirectoryChooser directoryChooser = new DirectoryChooser();
                directoryChooser.setTitle("解压到");
                File extractTo = directoryChooser.showDialog(viewStage);
                if (onezip.CompressUtils.SevenZip.ExtractUtils.isEncrypted(file.getPath())) {
                    extractPassword = textInputDialog();
                }
                if (extractTo == null) {
                    oneAlert.alert("请选择解压位置");
                } else {
                    try {

                        if (extractPassword == null || extractPassword.isEmpty()) {
                            SevenZipExtractService sevenZipExtractService = new SevenZipExtractService(file.getPath(), extractTo.getPath());
                            sevenZipExtractService.start();
                        } else {
                            SevenZipExtractService sevenZipExtractService = new SevenZipExtractService(file.getPath(), extractTo.getPath(), extractPassword);
                            sevenZipExtractService.start();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            viewPaneAdd.setOnAction(actionEvent -> {
                oneAlert.alertWarning("您添加的文件可能会替代某些文件（似乎是第一个），并且有可能导致部分文件预览不可见，请尽量不要使用");
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("选择要添加的文件");
                File toAdd = fileChooser.showOpenDialog(viewStage);

                if (extractPassword==null||extractPassword.isEmpty()) {
                    extractPassword = textInputDialog();
                }
                try {

                    if (extractPassword == null || extractPassword.isEmpty()) {
                        SevenZipAddOrDeleteService sevenZipAddOrDeleteService = new SevenZipAddOrDeleteService(file.getPath(), toAdd);
                        sevenZipAddOrDeleteService.start();
                    } else {
                        SevenZipAddOrDeleteService sevenZipAddOrDeleteService = new SevenZipAddOrDeleteService(file.getPath(), toAdd, extractPassword);
                        sevenZipAddOrDeleteService.start();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            viewPaneDelete.setOnAction(actionEvent -> {
                oneAlert.alertWarning("由于技术问题，删除文件的功能已被禁用");
                /*String filePathIn7z = listView.getSelectionModel().getSelectedItem();
                System.out.println(filePathIn7z);
                boolean temp = ExtractUtils.isEncrypted(file.getPath());
                if (temp) {
                    extractPassword = textInputDialog();
                }
                try {

                    if (extractPassword == null || extractPassword.isEmpty()) {
                        SevenZipAddOrDeleteService sevenZipAddOrDeleteService = new SevenZipAddOrDeleteService(file.getPath(),filePathIn7z);
                        sevenZipAddOrDeleteService.start();
                    } else {
                        SevenZipAddOrDeleteService sevenZipAddOrDeleteService = new SevenZipAddOrDeleteService(file.getPath(), filePathIn7z, extractPassword);
                        sevenZipAddOrDeleteService.start();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

                 */
            });
        } else if (file.getName().contains(".rar")) {
            RARPane.main(new String[]{file.getPath()});
        }
    }
    private void list(File file) throws IOException {
        zipUtils.list(file,nameList);

        fileItems = FXCollections.observableArrayList(zipUtils.viewInPath(nameList,""));
        System.out.println(fileItems);

    }
    private void sevenZipList(File file){
        viewUtils.fileView(file.getPath(),nameList);
        System.out.println(nameList);
        fileItems = FXCollections.observableArrayList(onezip.CompressUtils.SevenZip.viewUtils.getFileNameInPath(nameList,""));
        System.out.println(fileItems);
    }
    private void sevenZipList(File file, String password){
        viewUtils.fileView(file.getPath(),nameList,password);
        System.out.println(nameList);
        fileItems = FXCollections.observableArrayList(onezip.CompressUtils.SevenZip.viewUtils.getFileNameInPath(nameList,""));
        System.out.println(fileItems);
    }

    public static boolean isCursorAble() {
        return cursorAble;
    }

    public static Image getCursorImage() {
        return cursorImage;
    }

    
}


class AddOrDeleteScheduledService extends ScheduledService{
    File toAdd;
    File zip;
    String deleteFilePathInZip;
    int type=0;
    public AddOrDeleteScheduledService(File toAdd,File zip){
        this.toAdd = toAdd;
        this.zip = zip;
        type=1;
    }
    public AddOrDeleteScheduledService(String deleteFilePathInZip,File zip){
        this.deleteFilePathInZip=deleteFilePathInZip;
        this.zip =zip;
        type=2;
    }
    @Override
    protected Task createTask() {
        return new Task<Integer>() {

            @Override
            protected Integer call() throws Exception {
                System.out.println(Thread.currentThread().getName());
                if (type==1){
                    zipUtils.add(toAdd,zip);
                    return 1;
                }else if (type==2){
                    int temp=zipUtils.deleteFile(deleteFilePathInZip,zip);
                    return temp;
                }
                return 0;
            }

            @Override
            protected void updateValue(Integer value) {
                if (value==0){
                    oneAlert.alert("error:no model selected");
                }else if (value==1||value==2){
                    oneAlert.alertSuccess();
                }
                AddOrDeleteScheduledService.this.cancel();
            }
        };
    }

}

class SevenZipAddOrDeleteService extends ScheduledService{
    String sevenZipFile;
    File fileToAdd;
    String fileIn7zPath;
    String password;
    int type;//1->添加文件 2->删除文件

    public SevenZipAddOrDeleteService(String sevenZipFile,File fileToAdd){
        this.sevenZipFile=sevenZipFile;
        this.fileToAdd=fileToAdd;
        type=1;
    }
    public SevenZipAddOrDeleteService(String sevenZipFile,File fileToAdd,String password){
        this.sevenZipFile=sevenZipFile;
        this.fileToAdd=fileToAdd;
        type=1;
    }
    /*public SevenZipAddOrDeleteService(String sevenZipFile,String fileIn7zPath){
        this.sevenZipFile=sevenZipFile;
        this.fileIn7zPath=fileIn7zPath;
        type=2;
    }
    public SevenZipAddOrDeleteService(String sevenZipFile,String fileIn7zPath,String password){
        this.sevenZipFile=sevenZipFile;
        this.fileIn7zPath=fileIn7zPath;
        type=2;
    }
     */
    @Override
    protected Task createTask() {
        return new Task<Integer>() {

            @Override
            protected Integer call() throws Exception {
                System.out.println(Thread.currentThread().getName());
                if (type==1){
                    if (password==null||password.isEmpty()){
                        String[] argument={sevenZipFile,sevenZipFile,fileToAdd.getName(),new String(toByteArray(fileToAdd.getPath()))};
                        AddOrDeleteUtils.main(argument);
                    }else{
                        String[] argument={sevenZipFile,sevenZipFile,fileToAdd.getName(),new String(toByteArray(fileToAdd.getPath())),password};
                        AddOrDeleteUtils.encryptUtilsLoader(argument);
                    }
                    return 1;
                }else if (type==2){
                    if (password==null||password.isEmpty()){
                        String[] argument={sevenZipFile,sevenZipFile,fileIn7zPath,"It's sorry to create sorry.txt in your 7z file due to some technological limits","sorry.txt"};
                        AddOrDeleteUtils.main(argument);
                    }else{
                        String[] argument={sevenZipFile,sevenZipFile,fileIn7zPath,"It's sorry to create sorry.txt in your 7z file due to some technological limits","sorry.txt",password};
                        AddOrDeleteUtils.encryptUtilsLoader(argument);
                    }
                    return 2;
                }
                return 0;

            }

            @Override
            protected void updateValue(Integer value) {
                if (value==0){
                    oneAlert.alert("error:no model selected");
                }else if (value==1||value==2){
                    oneAlert.alertSuccess();
                }
                SevenZipAddOrDeleteService.this.cancel();
            }
        };
    }

    private static byte[] toByteArray(String filename) throws IOException {

        FileChannel fc = null;
        try {
            fc = new RandomAccessFile(filename, "r").getChannel();
            MappedByteBuffer byteBuffer = fc.map(FileChannel.MapMode.READ_ONLY, 0,
                    fc.size()).load();
            System.out.println(byteBuffer.isLoaded());
            byte[] result = new byte[(int) fc.size()];
            if (byteBuffer.remaining() > 0) {
                // System.out.println("remain");
                byteBuffer.get(result, 0, byteBuffer.remaining());
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        } finally {
            try {
                fc.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}