package cn.shed4329.onezip.themes.fxJava.fluent;

import cn.shed4329.onezip.setting.AppearancePreferences;

import javafx.beans.property.*;
import javafx.scene.Scene;
import javafx.stage.*;

import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;

import java.util.ArrayList;
import java.util.function.Consumer;

/** Live appearance state. Registrations belong to their scenes, not a static window registry. */
public final class ThemeManager {
    private static final BooleanProperty DARK =
            new SimpleBooleanProperty(AppearancePreferences.dark());
    private static final String METRO = ThemeManager.class.getName() + ".metro",
            CALLBACK = ThemeManager.class.getName() + ".callback";

    private ThemeManager() {}

    public static boolean isDark() {
        return DARK.get();
    }

    public static ReadOnlyBooleanProperty darkProperty() {
        return DARK;
    }

    public static void register(Scene scene, JMetro metro) {
        register(scene, metro, null);
    }

    public static void register(Scene scene, JMetro metro, Consumer<Boolean> listener) {
        scene.getProperties().put(METRO, metro);
        if (listener != null) scene.getProperties().put(CALLBACK, listener);
        metro.setScene(scene);
        apply(scene);
        scene.windowProperty()
                .addListener(
                        (o, old, window) -> {
                            if (window != null) {
                                if (window instanceof Stage stage) WindowChrome.install(stage);
                                apply(scene);
                                window.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> apply(scene));
                            }
                        });
        if (scene.getWindow() instanceof Stage stage) {
            WindowChrome.install(stage);
            apply(scene);
        }
    }

    public static void setDark(boolean value) {
        DARK.set(value);
        MainApplication.isDarkMode = value;
        for (Window window : new ArrayList<>(Window.getWindows()))
            if (window.getScene() != null) apply(window.getScene());
    }

    public static void refreshAccent() {
        for (Window window : new ArrayList<>(Window.getWindows()))
            if (window.getScene() != null) apply(window.getScene());
        BackgroundAppearance.previewControlsTransparency(
                BackgroundAppearance.controlsTransparency());
    }

    @SuppressWarnings("unchecked")
    private static void apply(Scene scene) {
        if (!(scene.getProperties().get(METRO) instanceof JMetro metro)) return;
        Style style = isDark() ? Style.DARK : Style.LIGHT;
        if (metro.getStyle() != style) metro.setStyle(style);
        scene.getRoot().getStyleClass().removeAll("dark", "light");
        scene.getRoot().getStyleClass().add(isDark() ? "dark" : "light");
        if (scene.getProperties().get(CALLBACK) instanceof Consumer<?> listener)
            ((Consumer<Boolean>) listener).accept(isDark());
        AccentTheme.apply(scene);
        ControlHints.install(scene.getRoot());
    }
}
