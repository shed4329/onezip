package cn.shed4329.onezip.themes.fxJava.fluent;

import static cn.shed4329.onezip.i18n.I18n.t;

import cn.shed4329.onezip.i18n.I18n;
import cn.shed4329.onezip.setting.AppearancePreferences;
import cn.shed4329.onezip.setting.ExtractionPreferences;
import cn.shed4329.onezip.setting.SettingsStore;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import jfxtras.styles.jmetro.*;

import org.controlsfx.control.ToggleSwitch;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Working preferences only, with one replaceable scrollable page. */
public class SettingsWindow extends Application {
    private Stage stage;
    private final ScrollPane content = new ScrollPane();
    private final Label status = new Label();
    private final Map<String, Button> tabs = new LinkedHashMap<>();
    private Runnable flushAppearance = () -> {};

    @FunctionalInterface
    private interface Save {
        void run() throws IOException;
    }

    @FunctionalInterface
    private interface ToggleSave {
        void run(boolean enabled) throws IOException;
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        BorderPane root = new BorderPane();
        root.getStyleClass().add("onezip-settings");
        root.setMinSize(0, 0);
        Label title = new Label(t("settings.title"));
        title.getStyleClass().add("settings-heading");
        HBox navigation = new HBox(6);
        navigation.getStyleClass().add("settings-tabs");
        tab(
                navigation,
                "settings-general",
                t("settings.generalSection").strip(),
                "universal.png",
                this::general);
        tab(
                navigation,
                "settings-personalize",
                t("settings.personalSection").strip(),
                "personalize.png",
                this::appearance);
        tab(
                navigation,
                "settings-language",
                t("settings.languageSection").strip(),
                "language.png",
                this::language);
        tab(navigation, "settings-about", t("action.about"), "about.png", this::about);
        content.setId("settings-content-scroll");
        content.setFitToWidth(true);
        content.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(content, Priority.ALWAYS);
        status.setId("settings-status");
        status.setWrapText(true);
        status.getStyleClass().add("settings-hint");
        status.setMinHeight(20);
        VBox workspace = new VBox(18, title, navigation, content, status);
        workspace.setPadding(new Insets(42, 24, 16, 24));
        workspace.setMinSize(0, 0);
        root.setCenter(workspace);
        Scene scene = new Scene(root, 780, 630);
        ThemeManager.register(scene, new JMetro(ThemeManager.isDark() ? Style.DARK : Style.LIGHT));
        root.getStylesheets()
                .add(
                        Objects.requireNonNull(getClass().getResource("settings-theme.css"))
                                .toExternalForm());
        CursorImages.apply(scene, AppearancePreferences.cursorPath());
        stage.setTitle(t("settings.title"));
        stage.setScene(scene);
        stage.setMinWidth(680);
        stage.setMinHeight(480);
        stage.getIcons().add(resourceImage("zip.png"));
        select("settings-general", general());
        stage.setOnHidden(
                e -> {
                    flushAppearance.run();
                    MainApplication.setSettingTime(0);
                });
        stage.show();
    }

    private void tab(
            HBox bar,
            String id,
            String text,
            String image,
            java.util.function.Supplier<Node> page) {
        Button b = new Button(text);
        b.setId(id);
        b.getStyleClass().add("settings-tab");
        ImageView icon = new ImageView(resourceImage(image));
        icon.setFitWidth(20);
        icon.setFitHeight(20);
        icon.setPreserveRatio(true);
        icon.getStyleClass().add("accent-icon");
        b.setGraphic(icon);
        b.setOnAction(
                e -> {
                    flushAppearance.run();
                    flushAppearance = () -> {};
                    select(id, page.get());
                });
        tabs.put(id, b);
        bar.getChildren().add(b);
    }

    private static Image resourceImage(String name) {
        return new Image(
                Objects.requireNonNull(SettingsWindow.class.getResource("img/" + name))
                        .toExternalForm());
    }

    private void select(String id, Node page) {
        tabs.forEach(
                (key, button) ->
                        button.pseudoClassStateChanged(
                                javafx.css.PseudoClass.getPseudoClass("selected"), key.equals(id)));
        content.setContent(page);
        content.setVvalue(0);
        status.setText(SettingsStore.shared().warning());
        AccentTheme.apply(stage.getScene());
    }

    private Label hint(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("settings-hint");
        return label;
    }

    private VBox page(Node... nodes) {
        return new VBox(18, nodes);
    }

    private VBox group(String heading, Node... nodes) {
        Label label = new Label(heading);
        label.getStyleClass().add("settings-section-title");
        VBox group = new VBox(12, label);
        group.getStyleClass().add("settings-group");
        group.getChildren().addAll(nodes);
        return group;
    }

    private boolean save(Save action, String message) {
        try {
            action.run();
            status.setText(message);
            return true;
        } catch (IOException | IllegalArgumentException e) {
            status.setText(t("settings.saveErrorPrefix") + e.getMessage());
            return false;
        }
    }

    private void toggle(ToggleSwitch control, ToggleSave action) {
        AtomicBoolean reverting = new AtomicBoolean();
        control.selectedProperty()
                .addListener(
                        (o, old, value) -> {
                            if (reverting.get()) return;
                            if (!save(() -> action.run(value), t("settings.saved"))) {
                                reverting.set(true);
                                control.setSelected(old);
                                reverting.set(false);
                            }
                        });
    }

    private Node general() {
        ToggleSwitch smart = new ToggleSwitch(t("extraction.smart"));
        smart.setId("settings-smart-extract");
        smart.setSelected(ExtractionPreferences.smart());
        toggle(smart, ExtractionPreferences::setSmart);
        return page(
                group(
                        t("settings.extractPreferences"),
                        smart,
                        hint(t("extraction.smartHint")),
                        hint(t("extraction.defaultHint")),
                        hint(t("extract.noOverwrite"))));
    }

    private Node language() {
        ChoiceBox<String> choice = new ChoiceBox<>(FXCollections.observableArrayList(I18n.NAMES));
        choice.setId("settings-language-choice");
        choice.getSelectionModel().select(I18n.TAGS.indexOf(I18n.savedTag()));
        choice.setPrefWidth(220);
        Label result = hint("");
        result.setId("settings-language-status");
        Button apply = new Button(t("action.apply"));
        apply.setId("settings-language-apply");
        apply.disableProperty().bind(choice.valueProperty().isNull());
        apply.setOnAction(
                e -> {
                    if (save(
                            () ->
                                    I18n.saveLanguage(
                                            I18n.TAGS.get(
                                                    choice.getSelectionModel().getSelectedIndex())),
                            t("language.saved"))) result.setText(t("language.saved"));
                });
        return page(
                group(
                        t("settings.languageHeading"),
                        choice,
                        hint(t("language.restartHint")),
                        apply,
                        result));
    }

    private Node appearance() {
        ToggleSwitch dark = new ToggleSwitch(t("settings.dark"));
        dark.setId("settings-dark-mode");
        dark.setSelected(ThemeManager.isDark());
        toggle(
                dark,
                value -> {
                    AppearancePreferences.setDark(value);
                    ThemeManager.setDark(value);
                });
        ColorPicker color = new ColorPicker(AccentTheme.color());
        color.setId("settings-accent-color");
        color.setOnAction(
                e -> {
                    if (!save(() -> AccentTheme.set(color.getValue()), t("accent.saved")))
                        color.setValue(AccentTheme.color());
                });
        Button reset = new Button(t("accent.reset"));
        reset.setId("settings-accent-reset");
        reset.setOnAction(
                e -> {
                    if (save(() -> AccentTheme.set(AccentTheme.DEFAULT), t("accent.resetDone")))
                        color.setValue(AccentTheme.color());
                });
        TextField path = new TextField(BackgroundAppearance.path());
        path.setId("settings-background-path");
        path.setPromptText(t("background.empty"));
        ToggleSwitch enabled = new ToggleSwitch(t("background.enabled")),
                blur = new ToggleSwitch(t("background.blur"));
        enabled.setId("settings-background-enabled");
        blur.setId("settings-background-blur");
        enabled.setSelected(BackgroundAppearance.enabled());
        blur.setSelected(BackgroundAppearance.blurred());
        blur.disableProperty().bind(enabled.selectedProperty().not());
        Slider blurRadius = new Slider(0, 63, BackgroundAppearance.blurRadius());
        blurRadius.setId("settings-background-blur-radius");
        blurRadius.setAccessibleText(t("background.blurRadius"));
        blurRadius.setBlockIncrement(1);
        blurRadius.setMajorTickUnit(15);
        blurRadius.setShowTickMarks(true);
        blurRadius.setMaxWidth(340);
        blurRadius
                .disableProperty()
                .bind(enabled.selectedProperty().not().or(blur.selectedProperty().not()));
        Tooltip blurHelp = new Tooltip(t("background.blurRadiusHint"));
        blurHelp.setWrapText(true);
        blurHelp.setMaxWidth(420);
        blurRadius.setTooltip(blurHelp);
        blur.setTooltip(new Tooltip(t("background.blurHint")));
        Label radiusValue = new Label();
        radiusValue.setId("settings-background-blur-value");
        radiusValue.textProperty().bind(blurRadius.valueProperty().asString("%.0f px"));
        Slider transparency = new Slider(0, 100, BackgroundAppearance.controlsTransparency());
        transparency.setId("settings-controls-transparency");
        transparency.setBlockIncrement(5);
        transparency.setMajorTickUnit(25);
        transparency.setShowTickMarks(true);
        transparency.setMaxWidth(340);
        transparency.disableProperty().bind(enabled.selectedProperty().not());
        Label value = new Label();
        value.textProperty().bind(transparency.valueProperty().asString("%.0f%%"));
        AtomicBoolean syncing = new AtomicBoolean();
        AtomicBoolean revertingRadius = new AtomicBoolean();
        AtomicBoolean radiusDirty = new AtomicBoolean();
        Runnable saveRadius =
                () -> {
                    if (!radiusDirty.getAndSet(false)) return;
                    int radius = (int) Math.round(blurRadius.getValue());
                    if (radius != BackgroundAppearance.blurRadius()
                            && !save(
                                    () -> BackgroundAppearance.setBlurRadius(radius),
                                    t("settings.saved"))) {
                        revertingRadius.set(true);
                        blurRadius.setValue(BackgroundAppearance.blurRadius());
                        revertingRadius.set(false);
                        BackgroundAppearance.previewBlurRadius(BackgroundAppearance.blurRadius());
                    }
                };
        javafx.animation.PauseTransition radiusDebounce =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
        radiusDebounce.setOnFinished(e -> saveRadius.run());
        blurRadius
                .valueProperty()
                .addListener(
                        (o, a, b) -> {
                            if (revertingRadius.get()) return;
                            radiusDirty.set(true);
                            BackgroundAppearance.previewBlurRadius(
                                    (int) Math.round(b.doubleValue()));
                            radiusDebounce.playFromStart();
                        });
        Runnable refresh =
                () -> {
                    syncing.set(true);
                    path.setText(BackgroundAppearance.path());
                    enabled.setSelected(BackgroundAppearance.enabled());
                    blur.setSelected(BackgroundAppearance.blurred());
                    syncing.set(false);
                };
        Button choose = new Button(t("background.choose")),
                apply = new Button(t("action.apply")),
                clear = new Button(t("action.clear"));
        choose.setId("settings-background-choose");
        apply.setId("settings-background-apply");
        clear.setId("settings-background-clear");
        apply.setOnAction(
                e -> {
                    if (save(
                            () -> {
                                if (path.getText().isBlank())
                                    throw new IOException(t("background.enterPath"));
                                BackgroundAppearance.configure(
                                        path.getText(), blur.isSelected(), true);
                            },
                            t("background.applied"))) refresh.run();
                });
        choose.setOnAction(
                e -> {
                    FileChooser c = new FileChooser();
                    c.setTitle(t("background.chooseTitle"));
                    c.getExtensionFilters()
                            .add(
                                    new FileChooser.ExtensionFilter(
                                            t("file.images"), "*.jpg", "*.jpeg", "*.png", "*.bmp"));
                    File f = c.showOpenDialog(stage);
                    if (f != null
                            && save(
                                    () ->
                                            BackgroundAppearance.configure(
                                                    f.getAbsolutePath(), blur.isSelected(), true),
                                    t("background.applied"))) refresh.run();
                });
        clear.setOnAction(
                e -> {
                    if (save(
                            () -> BackgroundAppearance.configure("", blur.isSelected(), false),
                            t("background.themeRestored"))) refresh.run();
                });
        enabled.selectedProperty()
                .addListener(
                        (o, a, b) -> {
                            if (!syncing.get()) {
                                save(
                                        () -> {
                                            if (b && BackgroundAppearance.path().isBlank())
                                                throw new IOException(t("background.chooseFirst"));
                                            BackgroundAppearance.configure(
                                                    BackgroundAppearance.path(),
                                                    blur.isSelected(),
                                                    b);
                                        },
                                        t("settings.saved"));
                                refresh.run();
                            }
                        });
        blur.selectedProperty()
                .addListener(
                        (o, a, b) -> {
                            if (!syncing.get()) {
                                save(
                                        () ->
                                                BackgroundAppearance.configure(
                                                        BackgroundAppearance.path(),
                                                        b,
                                                        enabled.isSelected()),
                                        t("settings.saved"));
                                refresh.run();
                            }
                        });
        javafx.animation.PauseTransition debounce =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
        debounce.setOnFinished(
                e -> {
                    if (!save(
                            () ->
                                    BackgroundAppearance.setControlsTransparency(
                                            (int) Math.round(transparency.getValue())),
                            t("appearance.transparencySaved"))) {
                        transparency.setValue(BackgroundAppearance.controlsTransparency());
                        BackgroundAppearance.previewControlsTransparency(
                                BackgroundAppearance.controlsTransparency());
                    }
                });
        transparency
                .valueProperty()
                .addListener(
                        (o, a, b) -> {
                            BackgroundAppearance.previewControlsTransparency(
                                    (int) Math.round(b.doubleValue()));
                            debounce.playFromStart();
                        });
        flushAppearance =
                () -> {
                    radiusDebounce.stop();
                    saveRadius.run();
                    debounce.stop();
                    int percent = (int) Math.round(transparency.getValue());
                    if (percent != BackgroundAppearance.controlsTransparency())
                        save(
                                () -> BackgroundAppearance.setControlsTransparency(percent),
                                t("appearance.transparencySaved"));
                };
        Button cursor = new Button(t("background.choose")),
                systemCursor = new Button(t("cursor.reset"));
        cursor.setOnAction(
                e -> {
                    FileChooser c = new FileChooser();
                    c.setTitle(t("image.chooseTitle"));
                    c.getExtensionFilters()
                            .add(
                                    new FileChooser.ExtensionFilter(
                                            t("file.images"), "*.png", "*.jpg", "*.jpeg", "*.bmp"));
                    File f = c.showOpenDialog(stage);
                    if (f != null)
                        save(
                                () -> {
                                    if (CursorImages.load(f.getPath()).isEmpty())
                                        throw new IOException(t("cursor.unreadable"));
                                    AppearancePreferences.setCursorPath(f.getPath());
                                },
                                t("settings.restartSaved"));
                });
        systemCursor.setOnAction(
                e ->
                        save(
                                () -> AppearancePreferences.setCursorPath(""),
                                t("settings.restartSaved")));
        return page(
                group(
                        t("appearance.theme"),
                        dark,
                        new HBox(12, new Label(t("accent.label")), color, reset),
                        hint(t("accent.hint"))),
                group(
                        t("background.label"),
                        enabled,
                        path,
                        new HBox(8, choose, apply, clear),
                        blur,
                        new Label(t("background.blurRadius")),
                        new HBox(12, blurRadius, radiusValue),
                        new Label(t("appearance.transparency")),
                        new HBox(12, transparency, value),
                        hint(t("appearance.transparencyHint"))),
                group(
                        t("settings.cursorImage"),
                        new HBox(8, cursor, systemCursor),
                        hint(t("settings.restartSaved"))));
    }

    private Node about() {
        Hyperlink project = new Hyperlink(t("about.project"));
        project.setId("settings-about-onezip");
        project.setOnAction(
                e -> getHostServices().showDocument("https://github.com/shed4329/onezip_test"));
        Hyperlink icons = new Hyperlink("Fluent UI System Icons · MIT");
        icons.setOnAction(
                e ->
                        getHostServices()
                                .showDocument(
                                        "https://github.com/microsoft/fluentui-system-icons"));
        return page(
                group(
                        t("about.onezip"),
                        new Label("OneZip · " + cn.shed4329.onezip.AppVersion.VERSION),
                        hint(t("about.engine")),
                        project),
                group(t("about.thanks"), new Label("JavaFX · JMetro"), icons));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
