package onezip;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.ImageCursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import onezip.CompressUtils.zip.zipUtils;
import onezip.FX.setting.FX_GUISetting;

import net.lingala.zip4j.exception.ZipException;


import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Objects;


public class testOne extends Application {
    File zipTo;
    String password="";
    int level=0;
    String viewPath="";
    boolean parent=false;
    ProgressBar progressBar;
    ArrayList<File> compressedFiles=new ArrayList<File>();
    ArrayList<File> compressedFolders=new ArrayList<File>();
    ArrayList<String> listFiles=new ArrayList<String>();

    String cursorPath="";//ui自定义
    FX_GUISetting fx_guiSetting= new FX_GUISetting();
    Image cursorImage;
    boolean cursorAble=false;

    ProgressBar processBar;

    ArrayList<String> nameList =new ArrayList<>();//文件列表，预览使用
    ObservableList<String> fileItems;
    public static void main(String[] args) {
        launch(args);
    }
    public void start(Stage stage) throws Exception {
        Button compress = new Button("\n压缩");
        compress.setStyle("-fx-background-color:#ffffff");
        Image compressImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("img/new.png")));
        compress.setGraphic(new ImageView(compressImage));
        compress.setPrefSize(135,135);
        Button extract = new Button("解压");
        Image extractImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("img/open.png")));
        extract.setGraphic(new ImageView(extractImage));
        extract.setStyle("-fx-background-color:#ffffff");
        extract.setPrefSize(135,135);
        Button setting = new Button("设置");
        Image settingImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("img/setting.png")));
        setting.setGraphic(new ImageView(settingImage));
        setting.setStyle("-fx-background-color:#ffffff");
        setting.setPrefSize(135,135);
        Button about = new Button("关于");
        Image aboutImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("img/about.png")));
        about.setGraphic(new ImageView(aboutImage));
        about.setStyle("-fx-background-color:#ffffff");
        about.setPrefSize(135,135);


        GridPane gridPane = new GridPane();

        gridPane.add(compress,0,0);
        gridPane.add(extract,1,0);
        gridPane.add(setting,0,1);
        gridPane.add(about,1,1);

        gridPane.setHgap(25);
        gridPane.setVgap(25);
        gridPane.setAlignment(Pos.CENTER);
        gridPane.styleProperty().set("-fx-background-color:#1f88db");

        Scene scene = new Scene(gridPane);
        if (!fx_guiSetting.getCursorPath().isEmpty()){
            cursorImage = new Image("file:"+fx_guiSetting.getCursorPath());
            scene.setCursor(new ImageCursor(cursorImage));
            cursorAble=true;
        }

        stage.setScene(scene);
        stage.setWidth(700);
        stage.setHeight(500);
        stage.setTitle("OneZip version 0.03 channel(only for test)");
        stage.getIcons().add(new Image("img/ZIP.png"));
        stage.show();

        compress.setOnAction(actionEvent -> {

                Group group0 = new Group();
                Text addTextHeader = new Text("添加文件到压缩文件");
                ListView listView = new ListView();
                listView.setPrefSize(800,200);
                listView.setLayoutY(10);
                Button exploreFolder = new Button("浏览文件夹");
                Button exploreFile = new Button("浏览文件");
                group0.getChildren().addAll(addTextHeader,listView,exploreFolder,exploreFile);
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
                ChoiceBox<String> choiceBox = new ChoiceBox<>();
                choiceBox.setItems(FXCollections.observableArrayList("0","1","2","3","4","5","6","7","8"));
                group3.getChildren().addAll(zipSetting,choiceBox);
                choiceBox.setLayoutX(120);

                Group group4 = new Group();
                Button start = new Button("开始(s)");
                Button cancel = new Button("取消");
                group4.getChildren().addAll(start,cancel);
                cancel.setLayoutX(70);


                VBox box = new VBox();
                box.getChildren().addAll(group0,group1,group2,setPassword,group3,group4);
                box.setSpacing(10.0);

                Scene compressScene = new Scene(box);
                if (cursorAble){//自定义鼠标
                    compressScene.setCursor(new ImageCursor(cursorImage));
                }

                Stage compressFrame = new Stage();
                compressFrame.setScene(compressScene);
                compressFrame.setTitle("新建压缩文件");
                compressFrame.setHeight(460);
                compressFrame.setWidth(800);
                compressFrame.initOwner(stage);
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
                        listView.setItems(FXCollections.observableArrayList(listFiles));
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
                            listView.setItems(FXCollections.observableArrayList(listFiles));


                        }

                });
                explore.setOnAction(actionEvent13 -> {
                    FileChooser fileChooser1 = new FileChooser();
                    fileChooser1.setTitle("保存为");
                    File savedFile = fileChooser1.showSaveDialog(compressFrame);
                    if (savedFile==null) {
                        alert("文件不能为空");
                    }else{
                        String path = savedFile.getAbsoluteFile().toString();
                        if (!path.contains(".")) {
                            fileTextField.setText(path + ".zip");
                            zipTo = new File(path + ".zip");
                        } else {
                            String value = path.substring(0, path.indexOf(".")) + ".zip";
                            fileTextField.setText(value);
                            zipTo = new File(value);
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
                               Alert alert = new Alert(Alert.AlertType.INFORMATION);
                               alert.titleProperty().set("成功");
                               alert.headerTextProperty().set("密码设置成功");
                               alert.showAndWait();
                               passwordSetStage.close();
                           }else{
                               alert("密码不一致");
                           }
                        }else{
                            alert("密码不能为空");
                        }
                    });
                    cancel1.setOnAction(actionEvent112 -> passwordSetStage.close());
                });
                choiceBox.getSelectionModel().selectedIndexProperty().addListener((observableValue, number, t1) -> {
                    int level = t1.intValue();
                    System.out.println(level);
                });
                start.setOnAction(actionEvent12 -> {
                    System.out.println(compressedFiles);
                    System.out.println(compressedFolders);
                    if (zipTo==null){
                        alert("还没选择保存路径");
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
                });
                cancel.setOnAction(actionEvent14 -> compressFrame.close());
        });
        extract.setOnAction(actionEvent -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("选择压缩包");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("压缩文件", "*.zip"));
            File file =fileChooser.showOpenDialog(stage);
            if (file==null){
                alert("文件不能为空");
            }else{
                HBox northPane = new HBox();
                Button viewPaneExtract = new Button("解压文件");
                northPane.getChildren().addAll(viewPaneExtract);
                viewPaneExtract.setPrefSize(100,50);

                AnchorPane southPane = new AnchorPane();
                TextArea commentArea = new TextArea();
                try {
                    String comment = zipUtils.getComment(file);
                    String UTF_8Comment = new String(comment.getBytes(),"UTF-8");
                    commentArea.setText(UTF_8Comment);
                    commentArea.setPrefWidth(800);
                    commentArea.setEditable(false);
                    southPane.getChildren().add(commentArea);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                AnchorPane middlePane = new AnchorPane();

                ListView<String> listView = new ListView<>();//文件列表
                listView.setPrefWidth(800);
                try {


                    zipUtils.list(file,nameList);

                    fileItems = FXCollections.observableArrayList(zipUtils.viewInPath(nameList,""));
                    System.out.println(fileItems);
                    listView.setItems(fileItems);
                } catch (ZipException e) {
                    e.printStackTrace();
                    fileItems = FXCollections.observableArrayList("预览失败");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                middlePane.getChildren().add(listView);

                AnchorPane westPane = new AnchorPane();
                System.out.println("Name："+file.getName());

                BorderPane borderPane = new BorderPane();
                borderPane.setTop(northPane);
                borderPane.setLeft(westPane);
                borderPane.setBottom(southPane);
                borderPane.setCenter(middlePane);

                Scene viewScene = new Scene(borderPane);
                if (cursorAble){//自定义鼠标
                    viewScene.setCursor(new ImageCursor(cursorImage));
                }

                Stage viewStage = new Stage();
                viewStage.setScene(viewScene);
                viewStage.setWidth(800);
                viewStage.setHeight(600);
                viewStage.getIcons().add(new Image("img/ZIP.png"));
                viewStage.setTitle(file.getName());
                stage.close();
                viewStage.show();

                listView.addEventHandler(MouseEvent.MOUSE_CLICKED,e->{
                        String newValue = listView.getSelectionModel().getSelectedItem();
                        //System.out.println("old value:"+oldValue);
                        System.out.println("new value:"+newValue);
                        System.out.println("\033[45m"+"viewPath:"+viewPath+"");//紫色标注
                        if (newValue.equals("..")){
                            int temp = viewPath.substring(0,viewPath.length()-2).lastIndexOf("/");//查找最后出现的位置(最末的”/“不算)
                            if (temp==-1){//没有”/“
                                viewPath="";
                            }else{
                                viewPath=viewPath.substring(0,temp);
                            }
                            System.out.println("viewPath.."+viewPath);
                            fileItems =FXCollections.observableArrayList(zipUtils.viewInPath(nameList,viewPath));
                            System.out.println("items:"+fileItems);
                            parent=true;
                        }else{
                            if (parent){//如果按了..,
                                if (!viewPath.isEmpty()&&!viewPath.endsWith("/")){//空的话在根目录,用短路与防止判断第二个条件时报错，如果在最后一个字符为“/”
                                    viewPath=viewPath+"/";//..会导致路径末尾的”/“丢失
                                }
                                parent=false;
                            }

                            viewPath=viewPath+newValue;
                            System.out.println("\033[44m"+"viewPath:"+viewPath+"");

                            fileItems =FXCollections.observableArrayList(zipUtils.viewInPath(nameList,viewPath.substring(0,viewPath.length()-1)));//去掉viewPath里最后的"/"
                        }

                        System.out.println(nameList.get(0));

                        listView.setItems(fileItems);

                });



                viewPaneExtract.setOnAction(actionEvent15 -> {
                    DirectoryChooser directoryChooser = new DirectoryChooser();
                    directoryChooser.setTitle("解压到");
                    File extractTo =directoryChooser.showDialog(viewStage);
                    if (extractTo==null){
                        alert("请选择解压位置");
                    }else{
                        try {
                            ZipScheduledService zipScheduledService = new ZipScheduledService(2,file,extractTo,Charset.forName("gbk"));
                            zipScheduledService.start();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
        about.setOnAction(actionEvent -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.titleProperty().set("关于");
            alert.headerTextProperty().set("OneZip\n版本号：0.03 channel 2\n仅供测试");
            alert.showAndWait();
        });
        setting.setOnAction(actionEvent -> {
            Group group = new Group();

            VBox vBox = new VBox();

            group.getChildren().add(vBox);

            Button normalSetting = new Button("常规");
            normalSetting.setPrefSize(125,35);
            normalSetting.setStyle("-fx-background-color:#4a9fe1");
            Separator separator1 =new Separator();
            Button uiSetting = new Button("外观");
            uiSetting.setPrefSize(125,35);
            uiSetting.setStyle("-fx-background-color:#4a9fe1");
            Separator separator2 =new Separator();
            Button languageSetting = new Button("语言");
            languageSetting.setPrefSize(125,35);
            languageSetting.setStyle("-fx-background-color:#4a9fe1");

            Button background = new Button();
            background.setPrefSize(125,2000);
            background.setStyle("-fx-background-color:#4a9fe1");
            vBox.getChildren().addAll(normalSetting,separator1,uiSetting,separator2,languageSetting,background);

            AnchorPane anchorPane = new AnchorPane();
            group.getChildren().add(anchorPane);
            anchorPane.setLayoutX(135);

            Scene settingScene = new Scene(group);
            if (cursorAble){
                settingScene.setCursor(new ImageCursor(cursorImage));
            }
            Stage settingStage = new Stage();
            settingStage.setScene(settingScene);
            settingStage.setTitle("设置");
            settingStage.getIcons().add(new Image("img/ZIP.png"));
            settingStage.setWidth(700);
            settingStage.setHeight(500);
            settingStage.show();
            uiSetting.setOnAction(e->{
                Text cursorText= new Text("鼠标设置");
                TextField pictureTextField = new TextField();
                try {
                    pictureTextField.setText(fx_guiSetting.getCursorPath());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                pictureTextField.setEditable(false);
                pictureTextField.setPrefWidth(500);
                Group cursorSetting = new Group();
                Button explore = new Button("浏览");
                cursorSetting.getChildren().addAll(pictureTextField,explore,cursorText);
                explore.setLayoutY(15);
                explore.setLayoutX(510);
                pictureTextField.setLayoutY(15);

                Button apply = new Button("应用");
                anchorPane.getChildren().addAll(cursorSetting,apply);
                AnchorPane.setTopAnchor(cursorSetting,10.0);
                anchorPane.setPrefSize(550,450);
                AnchorPane.setBottomAnchor(apply,10.0);
                explore.setOnAction(event->{
                    FileChooser fileChooser = new FileChooser();//选择文件夹
                    fileChooser.setTitle("选择图片");
                    fileChooser.getExtensionFilters().addAll(
                            new FileChooser.ExtensionFilter("PNG","*.png"),
                            new FileChooser.ExtensionFilter("JPEG","*.jpeg"),
                            new FileChooser.ExtensionFilter("BMP","*.bmp")
                    );
                    File pictures = fileChooser.showOpenDialog(settingStage);

                    if (pictures==null) {
                        alert("图片不能为空");
                    }else{
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
                    if (!cursorPath.isEmpty()){
                        alertSuccess();
                        try {
                            cursorImage = new Image("file:"+fx_guiSetting.getCursorPath());
                            scene.setCursor(new ImageCursor(cursorImage));
                            cursorAble=true;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                        settingStage.close();
                    }
                });
            });
        });
    }
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
}

class ZipScheduledService extends ScheduledService {
    int type;
    int level;
    int method;
    File input;
    File output;
    File inputFile;
    ArrayList<File> inputFiles;
    ArrayList<File> inputFolders;
    boolean isEncrypt;
    String password;
    Charset charset;
    public ZipScheduledService(int type,int level,int method,ArrayList<File> inputFiles,ArrayList<File> inputFolders,File output,boolean isEncrypt,String password) throws Exception {//压缩文件调用
        System.out.println("zip service");
        this.type = type;
        this.level = level;
        this.method = method;
        this.inputFiles = inputFiles;
        this.inputFolders=inputFolders;
        this.output = output;
        this.isEncrypt = isEncrypt;
        this.password = password;
    }
    public ZipScheduledService(int type,File input, File output, Charset charset){//解压文件调用
        this.type = type;
        this.input=input;
        this.output=output;
        this.charset=charset;
    }
    @Override
    protected Task<Integer> createTask() {
        return new Task<Integer>() {
            @Override
            protected Integer call() throws Exception {//非UI线程处理,避免压缩包过大卡死
                System.out.println(Thread.currentThread().getName());
                System.out.println(inputFiles);
                System.out.println(inputFolders);
                if (type==1){//压缩
                    if (inputFiles!=null&&inputFiles.size()!=0){
                        System.out.println(inputFiles);
                        zipUtils.zipFiles(inputFiles,output,method,level,isEncrypt,password);
                        System.out.println(inputFiles);
                    }

                    if (inputFolders!=null&&inputFolders.size()!=0){
                        if (inputFolders.size()==1){
                            System.out.println("zipUtils.zipFolder();");
                            zipUtils.zipFolder(inputFolders.get(0),output,method,level,isEncrypt,password);
                        }else{
                            return 0;
                        }

                        System.out.println(inputFolders);
                    }

                }else  if(type ==2){//解压
                    zipUtils.unzip(input,output,charset);
                }

                return -1;
            }

            @Override
            protected void updateValue(Integer value) {
                if (value==0){
                    testOne.alert("错误！不能添加多个文件夹，稍后可以在【设置】->【通用】->【将多个文件夹复制到同一文件夹后添加到压缩文件中】");
                }else{
                    testOne.alertSuccess();
                }
                    ZipScheduledService.this.cancel();

            }
        };
    }
}