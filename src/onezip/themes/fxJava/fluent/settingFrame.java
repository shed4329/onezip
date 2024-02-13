package onezip.themes.fxJava.fluent;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.ImageCursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;
import onezip.FX.setting.FX_GUISetting;
import onezip.setting.NormalSetting;
import org.controlsfx.control.ToggleSwitch;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static onezip.themes.fxJava.fluent.normalFrame.*;


public class settingFrame extends Application {
    FX_GUISetting fxGuiSetting = new FX_GUISetting();
    public static void main(String[] args) {
        launch(args);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void start(Stage stage) {
        AnchorPane anchorPane = new AnchorPane();


        VBox vBox = new VBox();
        Text text = new Text("OneZip设置");
        text.setFont(Font.font("Microsoft YaHei", FontWeight.LIGHT,20));
        TextField textField = new TextField();
        textField.setMaxWidth(330);


        vBox.setAlignment(Pos.TOP_CENTER);

        FlowPane flowPane = new FlowPane();


        Button buttonUniversal = new Button("通用\n压缩偏好设置，预览偏好设置    ");
        Image universalImg = new Image("onezip/themes/fxJava/fluent/img/universal.png");
        ImageView imageView = new ImageView(universalImg);
        //给按钮设置图标
        buttonUniversal.setGraphic(imageView);


        Button buttonLanguage = new Button("语言\n简体中文，繁體中文，English   ");
        Image languageImg = new Image("onezip/themes/fxJava/fluent/img/language.png");
        ImageView imageView2 = new ImageView(languageImg);
        //给按钮设置图标
        buttonLanguage.setGraphic(imageView2);


        Button buttonPersonalize = new Button("个性化\n应用内鼠标偏好设置，背景设置");
        Image personalizeImg = new Image("onezip/themes/fxJava/fluent/img/personalize.png");
        ImageView imageView3 = new ImageView(personalizeImg);
        //给按钮设置图标
        buttonPersonalize.setGraphic(imageView3);

        Button buttonAbout = new Button("关于\n关于OneZip,鸣谢                      ");
        Image aboutImg = new Image("onezip/themes/fxJava/fluent/img/about.png");
        ImageView imageView4 = new ImageView(aboutImg);
        //给按钮设置图标
        buttonAbout.setGraphic(imageView4);
        if (isDarkMode){
            buttonAbout.setStyle("-fx-background-color:#333333");
            buttonLanguage.setStyle("-fx-background-color:#333333");
            buttonPersonalize.setStyle("-fx-background-color:#333333");
            buttonAbout.setStyle("-fx-background-color:#333333");
            anchorPane.setStyle("-fx-background-color:#333333");
        }else{
            buttonUniversal.setStyle("-fx-background-color:#ffffff");
            buttonLanguage.setStyle("-fx-background-color:#ffffff");
            buttonPersonalize.setStyle("-fx-background-color:#ffffff");
            buttonAbout.setStyle("-fx-background-color:#ffffff");
        }
        flowPane.setAlignment(Pos.TOP_CENTER);
        flowPane.getChildren().addAll(buttonUniversal,buttonPersonalize,buttonLanguage,buttonAbout);



        vBox.getChildren().addAll(text,textField,flowPane);
        anchorPane.getChildren().add(vBox);
        vBox.prefWidthProperty().bind(anchorPane.widthProperty());
        Scene scene = new Scene(anchorPane);
        if (isCursorAble()){
            Image cursorImage = new Image("file:"+cursorPath);
            scene.setCursor(new ImageCursor(cursorImage));
        }
        JMetro jMetro = new JMetro(Style.LIGHT);
        if (isDarkMode){//读取normalFrame是否启用了夜间模式，设置界面一定由normalFrame进入，normalFrame一定确定了是否使用暗黑模式
            jMetro.setStyle(Style.DARK);
        }
        jMetro.setScene(scene);


        stage.setScene(scene);
        stage.initStyle(StageStyle.UNIFIED);
        stage.setWidth(750);
        stage.setHeight(400);
        stage.setTitle("设置");
        stage.getIcons().add(new Image("onezip/themes/fxJava/fluent/img/setting.png"));
        stage.show();

        Button home = new Button("主页            ");
        home.setStyle("-fx-background-color:transparent");
        home.setOnAction(actionEvent1 -> {
            anchorPane.getChildren().clear();
            anchorPane.getChildren().add(vBox);
            stage.setOpacity(1);
        });
        buttonAbout.setOnAction(actionEvent -> {
            anchorPane.getChildren().clear();
            VBox vBox1 = new VBox();


            Text text1 = new Text("\n关于");
            text1.setFont(Font.font("Microsoft YaHei",18));
            Button aboutOneZip = new Button("关于OneZip");
            Button thanks = new Button("鸣谢            ");
            aboutOneZip.setStyle("-fx-background-color:transparent");
            thanks.setStyle("-fx-background-color:transparent");
            vBox1.getChildren().addAll(home,text1,aboutOneZip,thanks);
            anchorPane.getChildren().addAll(vBox1);
            stage.setOpacity(0.85);

            AtomicBoolean everAbout= new AtomicBoolean(false);
            AtomicBoolean everThank= new AtomicBoolean(false);
            aboutOneZip.setOnAction(actionEvent1 -> {
                VBox vBox2 = new VBox();
                vBox2.getChildren().clear();
                aboutOneZip.setStyle("-fx-background-color:#afd0ec");
                thanks.setStyle("-fx-background-color:transparent");
                ImageView imageView1 = new ImageView("onezip/themes/fxJava/fluent/img/zip.png");
                Text text2 = new Text("Project OneZip");
                text2.setFont(Font.font("Microsoft YaHei",22));
                Button githubLink = new Button("see it on Github",new ImageView("onezip/themes/fxJava/fluent/img/github.png"));
                vBox2.getChildren().addAll(imageView1, text2, githubLink);
                vBox2.setAlignment(Pos.TOP_CENTER);
                if (everAbout.get()||everThank.get()) {
                    anchorPane.getChildren().remove(1);
                }
                anchorPane.getChildren().add(vBox2);

                vBox2.setLayoutX(125);
                vBox2.prefWidthProperty().bind(anchorPane.widthProperty().subtract(125));
                everAbout.set(true);
                githubLink.setOnAction(actionEvent2 -> {
                    HostServices host = getHostServices();
                    host.showDocument("https://github.com/shed4329/onezip_test");
                });
            });
            thanks.setOnAction(actionEvent1 -> {
                aboutOneZip.setStyle("-fx-background-color:transparent");
                thanks.setStyle("-fx-background-color:#afd0ec");
                VBox vBox2 = new VBox();
                vBox2.setLayoutX(125);
                vBox2.prefWidthProperty().bind(anchorPane.widthProperty().subtract(125));
                if (everThank.get()||everAbout.get()) {
                    anchorPane.getChildren().remove(1);
                }
                anchorPane.getChildren().add(vBox2);

                Hyperlink hyperlink1 = new Hyperlink("Zip4j - a java library for zip files");
                Hyperlink hyperlink2 = new Hyperlink("Welcome to 7-Zip-JBinding project");
                Hyperlink hyperlink3 = new Hyperlink("JavaFX中文官方网站");
                Hyperlink hyperlink4 = new Hyperlink("JavaFX");
                Hyperlink hyperlink5 = new Hyperlink("JMetro-A modern theme for JavaFX applications with light and dark style");
                Hyperlink hyperlink6 = new Hyperlink("unrar5-A uncompress and compress tool(unCompress rar,rar5,zip)");
                Hyperlink hyperlink7 = new Hyperlink("IntelliJ IDEA");
                Hyperlink hyperlink8 = new Hyperlink("iconfont");
                vBox2.getChildren().addAll(hyperlink1,hyperlink2,hyperlink3,hyperlink4,hyperlink5,hyperlink6,hyperlink7,hyperlink8);
                HostServices host = getHostServices();
                hyperlink1.setOnAction(actionEvent2 -> host.showDocument("http://www.lingala.net/zip4j.html"));
                hyperlink2.setOnAction(actionEvent2 -> host.showDocument("http://sevenzipjbind.sourceforge.net"));
                hyperlink3.setOnAction(actionEvent2 -> host.showDocument("https://openjfx.cn"));
                hyperlink4.setOnAction(actionEvent2 -> host.showDocument("https://openjfx.io"));
                hyperlink5.setOnAction(actionEvent2 -> host.showDocument("https://pixelduke.com/java-javafx-theme-jmetro/"));
                hyperlink6.setOnAction(actionEvent2 -> host.showDocument("https://github.com/sucat1997/unrar5#a-uncompress-and-compress-tooluncompress-rarrar5zip"));
                hyperlink7.setOnAction(actionEvent2 -> host.showDocument("https://www.jetbrains.com/idea/"));
                hyperlink8.setOnAction(actionEvent2 -> host.showDocument("https://www.iconfont.cn/"));
            });

        });
        buttonLanguage.setOnAction(actionEvent -> {
            anchorPane.getChildren().clear();
            VBox vBox1 = new VBox();
            Text text1 = new Text("\n语言");
            text1.setFont(Font.font("Microsoft YaHei",18));
            Button setYourLanguage = new Button("设置语言");
            setYourLanguage.setStyle("-fx-background-color:transparent");
            vBox1.getChildren().addAll(home,text1,setYourLanguage);
            anchorPane.getChildren().addAll(vBox1);
            stage.setOpacity(0.85);
            setYourLanguage.setOnAction(actionEvent1 -> {
                setYourLanguage.setStyle("-fx-background-color:#afd0ec");
                VBox vBox2 = new VBox();
                vBox2.setLayoutX(125);
                vBox2.prefWidthProperty().bind(anchorPane.widthProperty().subtract(125));
                Text text2 = new Text("设置应用内语言");
                ChoiceBox choiceBox = new ChoiceBox(FXCollections.observableArrayList("简体中文", "繁體中文", "English"));
                vBox2.getChildren().addAll(text2,choiceBox);
                anchorPane.getChildren().add(vBox2);
            });

        });
        buttonPersonalize.setOnAction(actionEvent -> {
            anchorPane.getChildren().clear();
            VBox vBox1 = new VBox();
            Text text1 = new Text("\n个性化");
            text1.setFont(Font.font("Microsoft YaHei",18));
            Button setYourBackground = new Button("设置背景   ");
            setYourBackground.setStyle("-fx-background-color:transparent");
            Button setYourUIMode = new Button("设置UI风格");
            setYourUIMode.setStyle("-fx-background-color:transparent");
            vBox1.getChildren().addAll(home,text1,setYourBackground,setYourUIMode);
            anchorPane.getChildren().addAll(vBox1);
            stage.setOpacity(0.85);
            AtomicBoolean everBackGround= new AtomicBoolean(false);
            AtomicBoolean everUIMode= new AtomicBoolean(false);
            setYourBackground.setOnAction(actionEvent1 -> {
                setYourUIMode.setStyle("-fx-background-color:transparent");
                setYourBackground.setStyle("-fx-background-color:#afd0ec");
                if (everBackGround.get()||everUIMode.get()){
                    anchorPane.getChildren().remove(1);
                }
                VBox vBox2 = new VBox();
                vBox2.setLayoutX(125);
                vBox2.prefWidthProperty().bind(anchorPane.widthProperty().subtract(125));
                Text text2 = new Text("选择鼠标图片");
                HBox hBox = new HBox();
                ImageView imageView5;
                if (cursorAble){
                     imageView5= new ImageView("file:"+cursorPath);
                     imageView5.setFitWidth(32);
                     imageView5.setFitHeight(32);
                }else {
                    imageView5 = new ImageView("onezip/themes/fxJava/fluent/img/Cursor1.png");
                }
                ImageView imageView6 = new ImageView("onezip/themes/fxJava/fluent/img/macos default.png");
                ImageView imageView7 = new ImageView("onezip/themes/fxJava/fluent/img/Windows11 lightPointer.png");
                ImageView imageView8 = new ImageView("onezip/themes/fxJava/fluent/img/light Arrow.png");
                Button cursor1 = new Button("",imageView5);
                Button cursor2 = new Button("",imageView6);
                Button cursor3 = new Button("",imageView7);
                Button cursor4 = new Button("",imageView8);
                hBox.getChildren().addAll(cursor1,cursor2,cursor3,cursor4);
                Button explore = new Button("浏览");
                Text text3 = new Text("夜间模式");
                ToggleSwitch toggleSwitch0 = new ToggleSwitch();
                toggleSwitch0.setSelected(isDarkMode);
                vBox2.getChildren().addAll(text2,hBox,explore,text3,toggleSwitch0);
                vBox2.setSpacing(10);
                anchorPane.getChildren().add(vBox2);
                everBackGround.set(true);
                explore.setOnAction(actionEvent2 -> {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("选择图片");
                    fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("便携式网络图形","*.png"),new FileChooser.ExtensionFilter("位图图像","*.bmp"),new FileChooser.ExtensionFilter("JPEG","*.jpeg"));
                    File file = fileChooser.showOpenDialog(stage);
                    if (file.exists()&&file.isFile()){
                        System.out.println(file.getPath());
                        /*Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("OK");
                        alert.setHeaderText("set cursor ok");
                        alert.setContentText("cursor image path:"+file.getPath());
                        alert.showAndWait();
                         */
                        try {
                            fxGuiSetting.setCursorPath(file.getPath());
                            Notification notification = new Notification();
                            notification.displayTray("OK","已设置图片"+file.getName());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                toggleSwitch0.selectedProperty().addListener(new ChangeListener<Boolean>() {
                    @Override
                    public void changed(ObservableValue<? extends Boolean> observableValue, Boolean aBoolean, Boolean t1) {
                        try {
                            if (isDarkMode){
                                FX_GUISetting.setDarkMode("");
                            }else{
                                FX_GUISetting.setDarkMode("true");
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        onezip.themes.fxJava.fluent.component.oneFluentAlert.alertInfo("夜间模式","您的改动将在重启应用后生效");
                    }
                });
            });
            setYourUIMode.setOnAction(actionEvent1 -> {
                setYourBackground.setStyle("-fx-background-color:transparent");
                setYourUIMode.setStyle("-fx-background-color:#afd0ec");
                if (everBackGround.get()||everUIMode.get()){
                    anchorPane.getChildren().remove(1);
                }
                Text text2 = new Text("设置UI启动模式");
                ChoiceBox choiceBox = new ChoiceBox(FXCollections.observableArrayList("JavaFX","Swing"));
                Text text3 = new Text("设置UI主题");
                ChoiceBox choiceBox2 = new ChoiceBox();
                VBox vBox2 = new VBox();
                vBox2.setLayoutX(125);
                vBox2.prefWidthProperty().bind(anchorPane.widthProperty().subtract(125));
                vBox2.getChildren().addAll(text2,choiceBox,text3,choiceBox2);
                anchorPane.getChildren().add(vBox2);
                choiceBox.getSelectionModel().selectedIndexProperty().addListener((observableValue, number, t1) -> {
                    String str = t1.toString();
                    System.out.println(str);
                    if (str.equals("0")){
                        choiceBox2.setItems(FXCollections.observableArrayList("normal","fluent","simple"));
                    }else {
                        choiceBox2.setItems(FXCollections.observableArrayList("metal","radius","system","color"));
                    }
                });
                choiceBox2.getSelectionModel().selectedIndexProperty().addListener((observableValue, number, t1) -> {
                    if (choiceBox.getSelectionModel().getSelectedIndex()==0&&t1.intValue()==0){
                        try {
                            fxGuiSetting.setBootTheme("normal");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                everUIMode.set(true);
            });

        });
        buttonUniversal.setOnAction(actionEvent -> {
            anchorPane.getChildren().clear();
            VBox vBox1 = new VBox();


            Text text1 = new Text("\n通用");
            text1.setFont(Font.font("Microsoft YaHei",18));

            Button compressSetting = new Button("压缩偏好设置");
            compressSetting.setStyle("-fx-background-color:transparent");
            Button extractSetting = new Button("解压偏好设置");
            compressSetting.setStyle("-fx-background-color:transparent");
            Button viewSetting = new Button("预览偏好设置");
            viewSetting.setStyle("-fx-background-color:transparent");
            vBox1.getChildren().addAll(home,text1,compressSetting,extractSetting,viewSetting);
            anchorPane.getChildren().addAll(vBox1);
            stage.setOpacity(0.85);
            /*AtomicBoolean everCompress= new AtomicBoolean(false);
            AtomicBoolean everExtract= new AtomicBoolean(false);
            AtomicBoolean everView= new AtomicBoolean(false);//以后再用吧
             */
            compressSetting.setOnAction(actionEvent1 -> {
                compressSetting.setStyle("-fx-background-color:#afd0ec");
                extractSetting.setStyle("-fx-background-color:transparent");
                viewSetting.setStyle("-fx-background-color:transparent");
            });
            extractSetting.setOnAction(actionEvent1 -> {
                extractSetting.setStyle("-fx-background-color:#afd0ec");
                compressSetting.setStyle("-fx-background-color:transparent");
                viewSetting.setStyle("-fx-background-color:transparent");
            });
            viewSetting.setOnAction(actionEvent1 -> {
                viewSetting.setStyle("-fx-background-color:#afd0ec");
                compressSetting.setStyle("-fx-background-color:transparent");
                extractSetting.setStyle("-fx-background-color:transparent");
                Text text2 = new Text("为节约资源关闭预览");
                ToggleSwitch viewAbleSwitch = new ToggleSwitch();
                viewAbleSwitch.setSelected(NormalSetting.getViewSwitch());
                VBox vBox2 = new VBox();
                vBox2.setLayoutX(125);
                vBox2.prefWidthProperty().bind(anchorPane.widthProperty().subtract(125));
                vBox2.getChildren().addAll(text2,viewAbleSwitch);
                anchorPane.getChildren().add(vBox2);
                viewAbleSwitch.selectedProperty().addListener((observableValue, aBoolean, t1) -> NormalSetting.setViewSwitch(t1));
            });
        });
        stage.setOnCloseRequest(event -> normalFrame.setSettingTime(0));
    }
}
