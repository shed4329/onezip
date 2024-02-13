package onezip.themes.fxJava.fluent.component;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;

/**
 * An alternative to alert built in JavaFX and providing a fluent appearance
 */
public class FluentAlert{
    private double x = 0.0;
    private double y = 0.0;

    private VBox vBox=new VBox();
    private double vBoxSpacing=15;
    private StackPane stackPane=new StackPane(vBox);
    private Stage stage;
    private Scene scene;
    private double stageWidth=300;
    private double stageHeight=100;
    private String HeaderText;
    private Text Header = new Text();
    private String ContentText;
    private Text Content = new Text();
    private HBox hBox=new HBox();
    private Node extentionalNode;
    private Alert.AlertType alertType= Alert.AlertType.NONE;
    private String fontName="Microsoft YaHei";
    private boolean isDarkMode =false;//适配暗黑模式
    private boolean isLoadedComponents = false;
    public FluentAlert(){

    }
    public FluentAlert(Alert.AlertType alertType){
        this.alertType = alertType;

    }
    public void showAndWait(){

        addComponents();


        //stackPane.setPrefSize(500,500);

        stage.show();
    }
    private void addComponents(){//加载已经初始化的组件
        Header.setText(HeaderText);
        Header.setFont(Font.font(fontName, FontWeight.BOLD, 22));
        Content.setText(ContentText);
        Content.setFont(Font.font(fontName, 14));
        if (isDarkMode){
            Header.setFill(Color.WHITE);
            Content.setFill(Color.WHITE);
        }
        if (alertType != Alert.AlertType.NONE) initAlertType:{
            VBox vBox1 = new VBox();
            vBox1.setSpacing(vBoxSpacing);
            vBox1.getChildren().addAll(Header, Content);
            hBox.setSpacing(100);
            Image image = null;
            if (alertType == Alert.AlertType.INFORMATION){
                image = new Image("onezip/themes/fxJava/fluent/img/infoAlert.png");
            } else if (alertType == Alert.AlertType.WARNING) {
                image = new Image("onezip/themes/fxJava/fluent/img/warningAlert.png");
            }else if (alertType == Alert.AlertType.ERROR){
                image = new Image("onezip/themes/fxJava/fluent/img/errorAlert.png");
            } else if (alertType == Alert.AlertType.CONFIRMATION) {
                image = new Image("onezip/themes/fxJava/fluent/img/confirmAlert.png");
            }
            Button button = new Button("OK");
            ImageView imageView = new ImageView(image);
            button.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent actionEvent) {
                    stage.close();
                }
            });
            if (hBox.getChildren().size()!=0){//之前已经加载过了
                hBox.getChildren().remove(1);//不能使用removeAll，不会删除原节点的应用，再次添加时抛出Exception in JavaFX thread java.lang.IllegalArgumentException: Children: duplicate children added
                hBox.getChildren().remove(0);
                hBox.getChildren().addAll(vBox1,imageView);
                vBox.getChildren().remove(1);
                vBox.getChildren().remove(0);
                vBox.getChildren().add(hBox);
                vBox.getChildren().add(button);
                break initAlertType;
            }
            hBox.getChildren().addAll(vBox1);
            hBox.getChildren().add(imageView);
            vBox.getChildren().add(hBox);
            vBox.getChildren().add(button);
        }else {

            vBox.setSpacing(vBoxSpacing);
            vBox.getChildren().addAll(Header, Content);
        }
        //设置阴影
        StackPane stackPane = new StackPane();//据说只有用StackPane才可以设置阴影
        StackPane.setMargin(vBox,new Insets(50,50,50,65));
        if (isDarkMode){
            stackPane.setStyle( "-fx-background-color: rgba(32, 32, 32, 1);" +
                    "-fx-effect: dropshadow(gaussian, black, 30, 0, 0, 0);" +
                    "-fx-background-insets: 50;"
            );//css
        }else{
            stackPane.setStyle( "-fx-background-color: rgba(255, 255, 255, 1);" +
                    "-fx-effect: dropshadow(gaussian, black, 30, 0, 0, 0);" +
                    "-fx-background-insets: 50;"
            );//css
        }

        stackPane.setPrefSize(stageWidth+100,stageHeight+100);//100的空间被阴影站掉了
        JMetro jMetro = new JMetro();
        if (isDarkMode){
            jMetro.setStyle(Style.DARK);
        }
        jMetro.setParent(vBox);
        scene = new Scene(stackPane);
        scene.setFill(Color.TRANSPARENT);//面板要设置成透明，不然阴影外又有一圈白色
        //加载组件
        stackPane.getChildren().add(vBox);
        stage = new Stage();
        stage.setScene(scene);
        stage.initStyle(StageStyle.TRANSPARENT);//解除操作平台修饰
        //拖拽事件
        stackPane.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                x = mouseEvent.getSceneX();
                y = mouseEvent.getSceneY();
                //x,y是拖拽点相对Pane左上方的距离
            }
        });
        stackPane.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                stage.setX(mouseEvent.getScreenX()-x);
                stage.setY(mouseEvent.getScreenY()-y);
                //get的坐标是拖拽后鼠标相对屏幕的位置，由于set设置的是左上角坐标，则要减去x，y
            }
        });
    }

    public void setHeaderText(String headerText) {
        HeaderText = headerText;
    }

    public void setContentText(String contentText) {
        ContentText = contentText;
    }

    public void setAlertType(Alert.AlertType alertType) {
        this.alertType = alertType;
    }

    public void setDarkMode(boolean darkMode) {
        isDarkMode = darkMode;
    }

    /**
     * use other font to replace the default font,Microsoft YaHei
     * @param fontName
     */

    public void setFontName(String fontName) {
        this.fontName = fontName;
    }

    public double getStageWidth() {
        return stageWidth;
    }

    public void setStageWidth(double stageWidth) {
        this.stageWidth = stageWidth;
    }

    public double getStageHeight() {
        return stageHeight;
    }

    public void setStageHeight(double stageHeight) {
        this.stageHeight = stageHeight;
    }

}
