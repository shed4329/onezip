package cn.shed4329.onezip.themes.fxJava.fluent;

import cn.shed4329.onezip.setting.AppearancePreferences;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;

import java.util.Objects;

/** JavaFX lifecycle and theme setup; layout and events live in FXML and MainWindowController. */
public class MainApplication extends Application {
    // Theme/cursor state of the current running window; saved preferences apply on restart.
    static boolean isDarkMode;
    static boolean cursorAble;
    static String cursorPath;
    static int settingTimes;
    private MainWindowController controller;

    public static void main(String[] args) {
        launch(args);
    }

    public static boolean isCursorAble() {
        return cursorAble;
    }

    public static void setSettingTime(int value) {
        settingTimes = value;
    }

    MainWindowController controller() {
        return controller;
    }

    @Override
    public void start(Stage stage) throws Exception {
        java.util.List<String> paths =
                getParameters() == null ? java.util.List.of() : getParameters().getRaw();
        startWindow(stage);
        if (!paths.isEmpty()) {
            controller.openShellPath(paths.getFirst());
            for (String path : paths.subList(1, paths.size())) {
                MainApplication additional = new MainApplication();
                additional.startWindow(new Stage());
                additional.controller.openShellPath(path);
            }
        }
    }

    private void startWindow(Stage stage) throws Exception {
        cn.shed4329.onezip.i18n.I18n.initialize();
        isDarkMode = AppearancePreferences.dark();
        ThemeManager.setDark(isDarkMode);
        cursorPath = AppearancePreferences.cursorPath();
        cursorAble = cursorPath != null && !cursorPath.isEmpty();
        FXMLLoader loader =
                new FXMLLoader(Objects.requireNonNull(getClass().getResource("main-window.fxml")));
        loader.setResources(cn.shed4329.onezip.i18n.I18n.bundle());
        Parent root = loader.load();
        controller = loader.getController();
        controller.setDarkMode(isDarkMode);
        Scene scene = new Scene(root);
        ThemeManager.register(
                scene, new JMetro(isDarkMode ? Style.DARK : Style.LIGHT), controller::setDarkMode);
        root.getStylesheets()
                .add(
                        Objects.requireNonNull(getClass().getResource("main-window.css"))
                                .toExternalForm());
        BackgroundAppearance.install((javafx.scene.layout.BorderPane) root);
        cursorAble = CursorImages.apply(scene, cursorPath);
        stage.setTitle("OneZip");
        stage.getIcons()
                .add(
                        new Image(
                                Objects.requireNonNull(getClass().getResource("img/zip.png"))
                                        .toExternalForm()));
        stage.setScene(scene);
        stage.setWidth(680);
        stage.setHeight(560);
        controller
                .model()
                .pageProperty()
                .addListener(
                        new javafx.beans.value.ChangeListener<MainWindowModel.Page>() {
                            private boolean openedWorkspace;

                            @Override
                            public void changed(
                                    javafx.beans.value.ObservableValue<
                                                    ? extends MainWindowModel.Page>
                                            property,
                                    MainWindowModel.Page old,
                                    MainWindowModel.Page page) {
                                if (!openedWorkspace && page != MainWindowModel.Page.HOME) {
                                    openedWorkspace = true;
                                    if (!stage.isMaximized() && stage.getWidth() < 900) {
                                        stage.setWidth(1180);
                                        stage.setHeight(760);
                                    }
                                }
                            }
                        });
        stage.setMinWidth(680);
        stage.setMinHeight(420);
        stage.setOnHidden(e -> controller.close());
        stage.show();
    }

    @Override
    public void stop() {
        if (controller != null) controller.close();
    }
}
