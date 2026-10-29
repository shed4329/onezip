package cn.shed4329.onezip.themes.fxJava.fluent;

import static cn.shed4329.onezip.i18n.I18n.t;

import cn.shed4329.onezip.setting.SettingsStore;

import javafx.beans.value.*;
import javafx.css.PseudoClass;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Local wallpaper only. GaussianBlur affects the image, never text or the OS backdrop. */
public final class BackgroundAppearance extends Region {
    private static final String KEY = BackgroundAppearance.class.getName();
    private static final PseudoClass WALLPAPER = PseudoClass.getPseudoClass("wallpaper");
    private static Image cached;
    private static String cachedPath = "";
    private final ImageView picture = new ImageView();
    private final GaussianBlur blurEffect = new GaussianBlur();
    private final Rectangle tint = new Rectangle(), clip = new Rectangle();
    private final BorderPane root;
    private final ChangeListener<Boolean> theme = (o, a, b) -> apply();

    private BackgroundAppearance(BorderPane root) {
        this.root = root;
        setId("application-background");
        setManaged(false);
        setMouseTransparent(true);
        getChildren().addAll(picture, tint);
        setClip(clip);
        ThemeManager.darkProperty().addListener(new WeakChangeListener<>(theme));
    }

    public static void install(BorderPane root) {
        var workspace = (StackPane) root.getCenter();
        var layer = new BackgroundAppearance(root);
        root.getProperties().put(KEY, layer);
        workspace.getChildren().addFirst(layer);
        workspace
                .widthProperty()
                .addListener(
                        (o, a, b) ->
                                layer.resizeRelocate(0, 0, b.doubleValue(), workspace.getHeight()));
        workspace
                .heightProperty()
                .addListener(
                        (o, a, b) ->
                                layer.resizeRelocate(0, 0, workspace.getWidth(), b.doubleValue()));
        layer.apply();
    }

    public static void configure(String path, boolean blur) throws IOException {
        configure(path, blur, !path.isBlank());
    }

    public static void configure(String path, boolean blur, boolean enabled) throws IOException {
        String normalized =
                path.isBlank() ? "" : Path.of(path).toAbsolutePath().normalize().toString();
        Image decoded = normalized.isEmpty() || !enabled ? null : decode(normalized);
        SettingsStore.shared()
                .putAll(
                        Map.of(
                                "appearance.background.path",
                                normalized,
                                "appearance.background.blur",
                                Boolean.toString(blur),
                                "appearance.background.enabled",
                                Boolean.toString(enabled && !normalized.isEmpty())));
        cachedPath = normalized;
        cached = decoded;
        for (Window window : new ArrayList<>(Window.getWindows()))
            if (window.getScene() != null
                    && window.getScene().getRoot().getProperties().get(KEY)
                            instanceof BackgroundAppearance layer) layer.apply();
    }

    public static String path() {
        return SettingsStore.shared().get("appearance.background.path", "");
    }

    public static boolean blurred() {
        return SettingsStore.shared().flag("appearance.background.blur");
    }

    /** Returns the saved image blur radius; existing settings retain the former 24px effect. */
    public static int blurRadius() {
        return cn.shed4329.onezip.setting.AppearancePreferences.blurRadius();
    }

    /** Previews a radius on open background images without saving or blurring foreground text. */
    public static void previewBlurRadius(int radius) {
        for (Window window : new ArrayList<>(Window.getWindows()))
            if (window.getScene() != null
                    && window.getScene().getRoot().getProperties().get(KEY)
                            instanceof BackgroundAppearance layer) layer.updateBlur(radius);
    }

    /** Persists a radius and applies it to open windows only after the write succeeds. */
    public static void setBlurRadius(int radius) throws IOException {
        cn.shed4329.onezip.setting.AppearancePreferences.setBlurRadius(radius);
        previewBlurRadius(blurRadius());
    }

    private void updateBlur(int radius) {
        int bounded = Math.clamp(radius, 0, 63);
        blurEffect.setRadius(bounded);
        picture.setEffect(blurred() && bounded > 0 ? blurEffect : null);
        requestLayout();
    }

    public static boolean enabled() {
        return Boolean.parseBoolean(
                SettingsStore.shared()
                        .get("appearance.background.enabled", Boolean.toString(!path().isEmpty())));
    }

    private static Image decode(String path) throws IOException {
        if (!Files.isRegularFile(Path.of(path))) throw new IOException(t("background.missing"));
        Image image = new Image(Path.of(path).toUri().toString(), 1920, 1080, true, true, false);
        if (image.isError() || image.getWidth() == 0)
            throw new IOException(t("background.unreadable"), image.getException());
        return image;
    }

    /** Transparency of workspace fills; labels, icons and window opacity stay unchanged. */
    public static int controlsTransparency() {
        try {
            return Math.clamp(
                    Integer.parseInt(
                            SettingsStore.shared().get("appearance.controls.transparency", "35")),
                    0,
                    100);
        } catch (NumberFormatException invalid) {
            return 35;
        }
    }

    public static void previewControlsTransparency(int percent) {
        int value = Math.clamp(percent, 0, 100);
        for (Window window : new ArrayList<>(Window.getWindows()))
            if (window.getScene() != null
                    && window.getScene().getRoot().getProperties().get(KEY)
                            instanceof BackgroundAppearance layer) layer.controlFills(value);
    }

    public static void setControlsTransparency(int percent) throws IOException {
        int value = Math.clamp(percent, 0, 100);
        SettingsStore.shared().put("appearance.controls.transparency", Integer.toString(value));
        previewControlsTransparency(value);
    }

    private void controlFills(int percent) {
        double alpha = 1 - percent / 100.0;
        boolean dark = ThemeManager.isDark();
        int neutral = dark ? 48 : 241, hover = dark ? 68 : 225;
        root.pseudoClassStateChanged(PseudoClass.getPseudoClass("clear-surfaces"), percent == 100);
        root.pseudoClassStateChanged(PseudoClass.getPseudoClass("clear-actions"), percent >= 60);
        String style = root.getStyle().replaceAll("-onezip-glass-[a-z]+:[^;]*;", "");
        Color primary = AccentTheme.primary();
        root.setStyle(
                style
                        + String.format(
                                Locale.ROOT,
                                "-onezip-glass-button:rgba(%d,%d,%d,%.3f);-onezip-glass-hover:rgba(%d,%d,%d,%.3f);-onezip-glass-pressed:rgba(%d,%d,%d,%.3f);-onezip-glass-primary:rgba(%d,%d,%d,%.3f);",
                                neutral,
                                neutral,
                                neutral,
                                alpha,
                                hover,
                                hover,
                                hover,
                                (alpha == 0 ? 0 : Math.min(1, alpha + .12)),
                                hover,
                                hover,
                                hover,
                                (alpha == 0 ? 0 : Math.min(1, alpha + .24)),
                                Math.round(primary.getRed() * 255),
                                Math.round(primary.getGreen() * 255),
                                Math.round(primary.getBlue() * 255),
                                Math.min(1, alpha / .65 * .90)));
        double factor = Math.min(1, alpha / .65);
        int panel = dark ? 34 : 247, sheet = dark ? 25 : 255;
        root.setStyle(
                root.getStyle()
                        + String.format(
                                Locale.ROOT,
                                "-onezip-glass-panel:rgba(%d,%d,%d,%.3f);-onezip-glass-sheet:rgba(%d,%d,%d,%.3f);-onezip-glass-input:rgba(%d,%d,%d,%.3f);-onezip-glass-stripe:rgba(%d,%d,%d,%.3f);",
                                panel,
                                panel,
                                panel,
                                .90 * factor,
                                sheet,
                                sheet,
                                sheet,
                                .52 * factor,
                                sheet,
                                sheet,
                                sheet,
                                .85 * factor,
                                dark ? 255 : 0,
                                dark ? 255 : 0,
                                dark ? 255 : 0,
                                .035 * factor));
    }

    private void apply() {
        String path = path();
        Image image = null;
        try {
            if (enabled() && !path.isEmpty()) {
                if (!cachedPath.equals(path) || cached == null) {
                    cached = decode(path);
                    cachedPath = path;
                }
                image = cached;
            }
        } catch (IOException | RuntimeException ignored) {
        }
        controlFills(controlsTransparency());
        picture.setImage(image);
        updateBlur(blurRadius());
        tint.setFill(
                ThemeManager.isDark() ? Color.rgb(12, 12, 12, .58) : Color.rgb(255, 255, 255, .56));
        setVisible(image != null);
        root.pseudoClassStateChanged(WALLPAPER, image != null);
        requestLayout();
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth(), h = getHeight();
        clip.setWidth(w);
        clip.setHeight(h);
        tint.setWidth(w);
        tint.setHeight(h);
        Image image = picture.getImage();
        if (image == null) return;
        double
                margin =
                        picture.getEffect() instanceof GaussianBlur effect
                                ? effect.getRadius() + 4
                                : 0,
                scale =
                        Math.max(
                                (w + 2 * margin) / image.getWidth(),
                                (h + 2 * margin) / image.getHeight());
        picture.setFitWidth(image.getWidth() * scale);
        picture.setFitHeight(image.getHeight() * scale);
        picture.relocate((w - picture.getFitWidth()) / 2, (h - picture.getFitHeight()) / 2);
    }
}
