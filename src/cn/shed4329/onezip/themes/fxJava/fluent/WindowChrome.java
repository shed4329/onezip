package cn.shed4329.onezip.themes.fxJava.fluent;

import static cn.shed4329.onezip.i18n.I18n.t;

import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.stage.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Shared application chrome. System file/folder pickers keep their native UI. */
public final class WindowChrome {
    private static final String KEY = WindowChrome.class.getName();

    private WindowChrome() {}

    private static Pane icon(String name) {
        try (var in = WindowChrome.class.getResourceAsStream("icons/fluent/" + name + ".svg")) {
            String svg =
                    new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
            var matcher = Pattern.compile("<path[^>]* d=\"([^\"]+)\"").matcher(svg);
            if (!matcher.find()) throw new IllegalStateException("Missing SVG path");
            SVGPath path = new SVGPath();
            path.setContent(matcher.group(1));
            path.getStyleClass().add("caption-glyph");
            path.setManaged(false);
            Pane pane = new Pane(path);
            pane.setMinSize(16, 16);
            pane.setPrefSize(16, 16);
            pane.setMaxSize(16, 16);
            pane.setMouseTransparent(true);
            pane.setUserData(name);
            return pane;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean command(Object target) {
        for (Node n = target instanceof Node p ? p : null; n != null; n = n.getParent())
            if (n instanceof Control
                    && !(n instanceof Label)
                    && !(n instanceof ScrollPane)
                    && !(n instanceof DialogPane)
                    && !(n instanceof ToolBar)) return true;
        return false;
    }

    private static boolean dragSurface(Object target, double sceneY) {
        if (command(target)) return false;
        if (sceneY >= 0 && sceneY < 38) return true;
        for (Node n = target instanceof Node node ? node : null; n != null; n = n.getParent())
            if (n.getStyleClass().stream()
                    .anyMatch(
                            style ->
                                    Set.of(
                                                    "archive-commandbar",
                                                    "compression-toolbar",
                                                    "page-title",
                                                    "settings-heading",
                                                    "progress-heading",
                                                    "dialog-heading")
                                            .contains(style))) return true;
        return false;
    }

    private static HBox titlebar(Stage stage, Runnable close, boolean full) {
        HBox bar = new HBox();
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        bar.getStyleClass().add("window-controls");
        bar.setId("window-controls");
        bar.setPrefSize(full ? 138 : 46, 38);
        bar.setManaged(false);
        if (full) {
            Button min = button("minimize", t("window.minimize")),
                    max = button("maximize", t("window.maximize"));
            min.setOnAction(e -> stage.setIconified(true));
            max.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
            stage.maximizedProperty()
                    .addListener(
                            (o, a, b) -> {
                                max.setGraphic(icon(b ? "restore" : "maximize"));
                                max.setAccessibleText(
                                        b ? t("window.restore") : t("window.maximize"));
                                max.setTooltip(
                                        new Tooltip(
                                                b ? t("window.restore") : t("window.maximize")));
                            });
            max.disableProperty().bind(stage.resizableProperty().not());
            bar.getChildren().addAll(min, max);
        }
        Button exit = button("close", t("window.close"));
        exit.setOnAction(e -> close.run());
        bar.getChildren().add(exit);
        double[] offset = new double[2];
        double[] normalSize = {680, 560}, fraction = {.5};
        stage.widthProperty()
                .addListener(
                        (o, a, b) -> {
                            if (!stage.isMaximized() && b.doubleValue() > 0)
                                normalSize[0] = b.doubleValue();
                        });
        stage.heightProperty()
                .addListener(
                        (o, a, b) -> {
                            if (!stage.isMaximized() && b.doubleValue() > 0)
                                normalSize[1] = b.doubleValue();
                        });
        boolean[] moving = {false};
        Parent root = stage.getScene().getRoot();
        root.addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                e -> {
                    moving[0] =
                            e.getButton() == javafx.scene.input.MouseButton.PRIMARY
                                    && dragSurface(e.getTarget(), e.getSceneY());
                    if (moving[0]) {
                        offset[0] = e.getScreenX() - stage.getX();
                        offset[1] = e.getScreenY() - stage.getY();
                        fraction[0] = Math.clamp(offset[0] / stage.getWidth(), 0, 1);
                    }
                });
        root.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> moving[0] = false);
        root.addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_DRAGGED,
                e -> {
                    if (moving[0] && e.isPrimaryButtonDown()) {
                        if (stage.isMaximized()) {
                            double width = normalSize[0], height = normalSize[1];
                            stage.setMaximized(false);
                            stage.setWidth(width);
                            stage.setHeight(height);
                            offset[0] = width * fraction[0];
                            offset[1] = Math.min(30, offset[1]);
                        }
                        stage.setX(e.getScreenX() - offset[0]);
                        stage.setY(e.getScreenY() - offset[1]);
                        e.consume();
                    }
                });
        root.addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                e -> {
                    if (full
                            && stage.isResizable()
                            && e.getButton() == javafx.scene.input.MouseButton.PRIMARY
                            && e.getClickCount() == 2
                            && dragSurface(e.getTarget(), e.getSceneY()))
                        stage.setMaximized(!stage.isMaximized());
                });
        return bar;
    }

    private static Button button(String name, String label) {
        Button b = new Button();
        b.setId("window-" + name);
        b.getStyleClass().add("caption-command");
        b.setGraphic(icon(name));
        b.setFocusTraversable(false);
        b.setAccessibleText(label);
        b.setTooltip(new Tooltip(label));
        return b;
    }

    private static void styles(Parent root) {
        root.getStyleClass().add("onezip-chrome");
        root.getStylesheets()
                .add(
                        Objects.requireNonNull(WindowChrome.class.getResource("window-chrome.css"))
                                .toExternalForm());
    }

    public static void install(Stage stage) {
        if (stage.isShowing()
                || stage.getScene() == null
                || stage.getProperties().containsKey(KEY)
                || stage.getScene().getRoot() instanceof DialogPane) return;
        stage.getProperties().put(KEY, true);
        stage.initStyle(StageStyle.UNDECORATED);
        Scene scene = stage.getScene();
        Parent content = scene.getRoot();
        BorderPane frame;
        if (content instanceof BorderPane border && border.getTop() == null) {
            frame = border;
        } else {
            frame = new BorderPane();
            scene.setRoot(frame);
            frame.getStyleClass().addAll(content.getStyleClass());
            content.getStyleClass().clear();
            frame.setCenter(content);
        }
        frame.setMinSize(0, 0);
        if (content instanceof Region r) r.setMinSize(0, 0);
        styles(frame);
        mount(frame, titlebar(stage, () -> requestClose(stage), true));
        if (frame.getStyleClass().contains("onezip-window")) {
            var logo =
                    new javafx.scene.image.ImageView(
                            new javafx.scene.image.Image(
                                    Objects.requireNonNull(
                                                    WindowChrome.class.getResource("img/zip.png"))
                                            .toExternalForm()));
            logo.setFitWidth(22);
            logo.setFitHeight(22);
            logo.setPreserveRatio(true);
            Label name = new Label("OneZip");
            name.setStyle("-fx-font-size:13px;-fx-font-weight:normal;");
            HBox brand = new HBox(8, logo, name);
            brand.setId("window-brand");
            brand.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            brand.setManaged(false);
            frame.getChildren().add(brand);
            brand.resizeRelocate(14, 0, 120, 38);
            brand.visibleProperty()
                    .bind(
                            javafx.beans.binding.Bindings.createBooleanBinding(
                                    () ->
                                            frame.getPseudoClassStates()
                                                    .contains(
                                                            javafx.css.PseudoClass.getPseudoClass(
                                                                    "home-mode")),
                                    frame.getPseudoClassStates()));
        }
        Region grip = new Region();
        grip.setId("window-resize");
        grip.setMinSize(16, 12);
        grip.setPrefSize(16, 12);
        grip.setPickOnBounds(true);
        grip.setCursor(javafx.scene.Cursor.SE_RESIZE);
        grip.setManaged(false);
        frame.getChildren().add(grip);
        Runnable place =
                () -> grip.resizeRelocate(frame.getWidth() - 16, frame.getHeight() - 12, 16, 12);
        frame.widthProperty().addListener((o, a, b) -> place.run());
        frame.heightProperty().addListener((o, a, b) -> place.run());
        place.run();
        grip.visibleProperty().bind(stage.resizableProperty().and(stage.maximizedProperty().not()));
        double[] resize = new double[4];
        grip.setOnMousePressed(
                e -> {
                    resize[0] = e.getScreenX();
                    resize[1] = e.getScreenY();
                    resize[2] = stage.getWidth();
                    resize[3] = stage.getHeight();
                    e.consume();
                });
        grip.setOnMouseDragged(
                e -> {
                    if (stage.isResizable() && !stage.isMaximized()) {
                        stage.setWidth(
                                Math.max(
                                        stage.getMinWidth(),
                                        resize[2] + e.getScreenX() - resize[0]));
                        stage.setHeight(
                                Math.max(
                                        stage.getMinHeight(),
                                        resize[3] + e.getScreenY() - resize[1]));
                    }
                    e.consume();
                });
    }

    private static void mount(Pane root, HBox controls) {
        root.getChildren().add(controls);
        Runnable place =
                () ->
                        controls.resizeRelocate(
                                Math.max(0, root.getWidth() - controls.getPrefWidth()),
                                0,
                                controls.getPrefWidth(),
                                38);
        root.widthProperty().addListener((o, a, b) -> place.run());
        place.run();
    }

    private static void requestClose(Stage stage) {
        WindowEvent event = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
        javafx.event.Event.fireEvent(stage, event);
        if (!event.isConsumed()) stage.close();
    }

    public static void prepare(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        if (pane.getProperties().containsKey(KEY)) return;
        pane.getProperties().put(KEY, true);
        dialog.initStyle(StageStyle.UNDECORATED);
        Stage stage = (Stage) pane.getScene().getWindow();
        String text = pane.getHeaderText();
        Node old = pane.getHeader();
        pane.setHeaderText(null);
        if (old != null) pane.setHeader(old);
        else if (text != null && !text.isBlank()) {
            Label heading = new Label(text);
            heading.setWrapText(true);
            heading.getStyleClass().add("dialog-heading");
            pane.setHeader(heading);
        }
        styles(pane);
        mount(pane, titlebar(stage, dialog::close, false));
        ThemeManager.register(
                pane.getScene(),
                new jfxtras.styles.jmetro.JMetro(
                        ThemeManager.isDark()
                                ? jfxtras.styles.jmetro.Style.DARK
                                : jfxtras.styles.jmetro.Style.LIGHT));
        for (ButtonType type : pane.getButtonTypes()) {
            String key =
                    type == ButtonType.OK
                            ? "action.ok"
                            : type == ButtonType.CANCEL
                                    ? "action.cancel"
                                    : type == ButtonType.CLOSE
                                            ? "window.close"
                                            : type == ButtonType.APPLY
                                                    ? "action.apply"
                                                    : type == ButtonType.YES
                                                            ? "action.yes"
                                                            : type == ButtonType.NO
                                                                    ? "action.no"
                                                                    : null;
            if (key != null && pane.lookupButton(type) instanceof Button button)
                button.setText(t(key));
        }
    }

    public static <T> Optional<T> showAndWait(Dialog<T> dialog) {
        prepare(dialog);
        return dialog.showAndWait();
    }

    public static void show(Dialog<?> dialog) {
        prepare(dialog);
        dialog.show();
    }
}
