package cn.shed4329.onezip.themes.fxJava.fluent.component;

import static cn.shed4329.onezip.i18n.I18n.t;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Bundled file-type icons, shared by physical files and archive entries. Call on the FX thread. */
public final class FileIcons {
    private static final Map<String, Image> CACHE = new HashMap<>();

    private FileIcons() {}

    public static String type(String name, boolean directory) {
        if (directory) return t("file.folder");
        return switch (resource(name, false)) {
            case "txt.png" -> t("file.text");
            case "markdown.png" -> t("file.markdown");
            case "pdf.png" -> t("file.pdf");
            case "doc.png" -> t("file.word");
            case "xls.png" -> t("file.spreadsheet");
            case "ppt.png" -> t("file.presentation");
            case "png.png" -> t("file.image");
            case "mp3.png" -> t("file.audio");
            case "mp4.png" -> t("file.video");
            case "zipFormat.png" -> t("file.archive");
            default -> {
                String leaf =
                        name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
                int dot = leaf.lastIndexOf('.');
                yield dot < 0
                        ? t("file.generic")
                        : leaf.substring(dot + 1).toUpperCase(Locale.ROOT)
                                + t("file.extensionSuffix");
            }
        };
    }

    public static ImageView view(String name, boolean directory) {
        String resource = resource(name, directory);
        Image image =
                CACHE.computeIfAbsent(
                        resource,
                        key ->
                                new Image(
                                        Objects.requireNonNull(
                                                        FileIcons.class.getResource(
                                                                "/cn/shed4329/onezip/themes/fxJava/fluent/img/format/"
                                                                        + key))
                                                .toExternalForm()));
        ImageView view = new ImageView(image);
        view.setFitWidth(20);
        view.setFitHeight(20);
        view.setPreserveRatio(true);
        view.setUserData(resource);
        return view;
    }

    private static String resource(String name, boolean directory) {
        if (directory) return "folder.png";
        // Only the last path component determines the type; parent names can contain dots.
        String leaf = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        int dot = leaf.lastIndexOf('.');
        String extension = dot < 0 ? "" : leaf.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "txt", "log", "csv", "ini", "cfg", "conf" -> "txt.png";
            case "md", "markdown" -> "markdown.png";
            case "java", "class", "jar" -> "java.png";
            case "json",
                    "xml",
                    "yaml",
                    "yml",
                    "toml",
                    "html",
                    "htm",
                    "css",
                    "js",
                    "ts",
                    "jsx",
                    "tsx",
                    "py",
                    "c",
                    "h",
                    "cpp",
                    "hpp",
                    "cs",
                    "go",
                    "rs",
                    "sh",
                    "bat",
                    "cmd",
                    "ps1",
                    "sql" ->
                    "code.png";
            case "pdf" -> "pdf.png";
            case "doc", "docx", "odt", "rtf" -> "doc.png";
            case "xls", "xlsx", "ods" -> "xls.png";
            case "ppt", "pptx", "odp" -> "ppt.png";
            case "png", "jpg", "jpeg", "gif", "bmp", "webp", "ico", "svg", "tif", "tiff" ->
                    "png.png";
            case "mp3", "wav", "flac", "ogg", "aac", "m4a" -> "mp3.png";
            case "mp4", "mkv", "avi", "mov", "wmv", "webm" -> "mp4.png";
            case "zip",
                    "7z",
                    "rar",
                    "rar5",
                    "tar",
                    "gz",
                    "gzip",
                    "tgz",
                    "lz4",
                    "xz",
                    "txz",
                    "bz2",
                    "bzip2",
                    "tbz",
                    "tbz2" ->
                    "zipFormat.png";
            case "exe", "msi", "dll" -> "exe.png";
            default -> "unknown.png";
        };
    }
}
