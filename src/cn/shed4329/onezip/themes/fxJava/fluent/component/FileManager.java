package cn.shed4329.onezip.themes.fxJava.fluent.component;

import static cn.shed4329.onezip.i18n.I18n.t;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;

/** UI-only shell integration. Never executes an archive or invokes an archive engine. */
public final class FileManager {
    private FileManager() {}

    public static void reveal(Path target) throws IOException {
        Path path = target.toAbsolutePath().normalize();
        if (!Files.exists(path)) throw new IOException(t("reveal.missingPrefix") + path);
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            String systemRoot = System.getenv("SystemRoot");
            if (systemRoot == null) throw new IOException(t("reveal.windowsMissing"));
            Path explorer = Path.of(systemRoot, "explorer.exe");
            // Separate arguments: no shell command concatenation, even for spaces or Unicode.
            new ProcessBuilder(explorer.toString(), "/select,", path.toString()).start();
        } else if (Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
            Desktop.getDesktop().browseFileDirectory(path.toFile());
        } else throw new IOException(t("reveal.unsupported"));
    }
}
