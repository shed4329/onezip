package cn.shed4329.onezip.themes.fxJava.fluent;

import cn.shed4329.onezip.setting.SettingsStore;

import javafx.scene.*;
import javafx.scene.control.Labeled;
import javafx.scene.image.*;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.util.*;

/** User accent; keeps the existing icon silhouettes and original image resources. */
public final class AccentTheme {
    private static final String KEY = "appearance.accent",
            ORIGINAL = AccentTheme.class.getName() + ".original";
    public static final Color DEFAULT = Color.web("#0078d7");

    private AccentTheme() {}

    public static Color color() {
        try {
            return Color.web(SettingsStore.shared().get(KEY, "#0078d7"));
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }

    public static String hex(Color c) {
        return String.format(
                Locale.ROOT,
                "#%02x%02x%02x",
                Math.round(c.getRed() * 255),
                Math.round(c.getGreen() * 255),
                Math.round(c.getBlue() * 255));
    }

    public static void set(Color color) throws IOException {
        Objects.requireNonNull(color);
        SettingsStore.shared().put(KEY, hex(color));
        ThemeManager.refreshAccent();
    }

    private static double linear(double c) {
        return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4);
    }

    private static double luminance(Color c) {
        return .2126 * linear(c.getRed())
                + .7152 * linear(c.getGreen())
                + .0722 * linear(c.getBlue());
    }

    /** White labels on accent buttons must remain readable even for pale custom colors. */
    public static Color primary() {
        Color c = color();
        while (1.05 / (luminance(c) + .05) < 4.5) c = c.interpolate(Color.BLACK, .06);
        return c;
    }

    public static Color foreground() {
        Color c = color();
        if (ThemeManager.isDark()) {
            while ((luminance(c) + .05) / (.04 + .05) < 4.5) c = c.interpolate(Color.WHITE, .06);
        } else {
            while (1.05 / (luminance(c) + .05) < 4.5) c = c.interpolate(Color.BLACK, .06);
        }
        return c;
    }

    static void apply(Scene scene) {
        Color primary = primary(), accent = foreground();
        var root = scene.getRoot();
        String clean =
                root.getStyle()
                        .replaceAll(
                                "(?:-onezip-(?:accent|primary|primary-hover|selected)|-fx-accent|-fx-focus-color|-fx-faint-focus-color|accent_color):[^;]*;",
                                "");
        Color selected =
                (ThemeManager.isDark() ? Color.web("#252525") : Color.WHITE)
                        .interpolate(accent, .20);
        root.setStyle(
                clean
                        + "-onezip-accent:"
                        + hex(accent)
                        + ";-onezip-selected:"
                        + hex(selected)
                        + ";-onezip-primary:"
                        + hex(primary)
                        + ";-onezip-primary-hover:"
                        + hex(primary.interpolate(Color.BLACK, .14))
                        + ";-fx-accent:"
                        + hex(primary)
                        + ";-fx-focus-color:"
                        + hex(accent)
                        + ";-fx-faint-focus-color:transparent;accent_color:"
                        + hex(primary)
                        + ";");
        tint(root, false, accent, new IdentityHashMap<>());
    }

    private static void tint(Node node, boolean actions, Color color, Map<Image, Image> images) {
        actions |=
                node.getStyleClass().contains("home-tile")
                        || node.getStyleClass().contains("archive-commandbar")
                        || node.getStyleClass().contains("accent-icon");
        if (actions && node instanceof ImageView view && view.getImage() != null) {
            Image original =
                    (Image) view.getProperties().computeIfAbsent(ORIGINAL, k -> view.getImage());
            if (original.getPixelReader() != null) {
                Image tinted =
                        images.computeIfAbsent(
                                original,
                                src -> {
                                    int w = (int) src.getWidth(), h = (int) src.getHeight();
                                    var dst = new WritableImage(w, h);
                                    int rgb =
                                            (int)
                                                    (Math.round(color.getRed() * 255) << 16
                                                            | Math.round(color.getGreen() * 255)
                                                                    << 8
                                                            | Math.round(color.getBlue() * 255));
                                    for (int y = 0; y < h; y++)
                                        for (int x = 0; x < w; x++)
                                            dst.getPixelWriter()
                                                    .setArgb(
                                                            x,
                                                            y,
                                                            (src.getPixelReader().getArgb(x, y)
                                                                            & 0xff000000)
                                                                    | rgb);
                                    return dst;
                                });
                view.setEffect(null);
                view.setImage(tinted);
            }
        }
        if (node instanceof Labeled labeled && labeled.getGraphic() != null)
            tint(labeled.getGraphic(), actions, color, images);
        if (node instanceof Parent parent)
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (node instanceof Labeled labeled && child == labeled.getGraphic()) continue;
                tint(child, actions, color, images);
            }
    }
}
