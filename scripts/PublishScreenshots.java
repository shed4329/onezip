import cn.shed4329.onezip.engine.ArchiveEngine;
import cn.shed4329.onezip.i18n.I18n;
import cn.shed4329.onezip.setting.SettingsStore;
import cn.shed4329.onezip.themes.fxJava.fluent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.nio.file.*;
import java.util.List;

/** Captures real scenes with isolated settings and synthetic inputs for public documentation. */
class PublishScreenshots {
    static Path output;
    static Stage app;
    static MainWindowController controller;
    static <T> T fx(java.util.concurrent.Callable<T> operation) throws Exception {
        var result = new java.util.concurrent.FutureTask<T>(operation);
        Platform.runLater(result);
        return result.get(25, java.util.concurrent.TimeUnit.SECONDS);
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static void capture(String name) throws Exception {
        Thread.sleep(250);
        fx(() -> {
            app.getScene().getRoot().applyCss();
            app.getScene().getRoot().layout();
            var shot = app.getScene().snapshot(null);
            int width = (int) shot.getWidth(), height = (int) shot.getHeight();
            int[] pixels = new int[width * height];
            shot.getPixelReader().getPixels(0, 0, width, height,
                    javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
            var bitmap = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            bitmap.setRGB(0, 0, width, height, pixels, 0, width);
            javax.imageio.ImageIO.write(bitmap, "png", output.resolve(name + ".png").toFile());
            return null;
        });
    }
    public static void main(String[] args) throws Exception {
        output = Files.createDirectories(Path.of(args[0]).toAbsolutePath());
        Path fixtures = Files.createDirectories(Path.of(args[1]).toAbsolutePath());
        Path readme = fixtures.resolve("README.md");
        Path notes = fixtures.resolve("notes.txt");
        Path documents = Files.createDirectories(fixtures.resolve("Documents"));
        Files.writeString(readme, "# OneZip example\nSynthetic public demonstration files.\n");
        Files.writeString(notes, "Archive demo\n".repeat(200));
        Files.writeString(documents.resolve("example.txt"), "OneZip documentation sample.\n");
        Path config = fixtures.resolve("settings.json");
        Path css = fixtures.resolve("styles.css");
        Path javaSource = fixtures.resolve("Example.java");
        Path csv = fixtures.resolve("measurements.csv");
        Path preview = fixtures.resolve("preview.png");
        Files.writeString(config, "{\n  \"theme\": \"sage\",\n  \"language\": \"en\"\n}\n");
        Files.writeString(css, ".example { color: #526d4e; background: #f2f5eb; }\n".repeat(80));
        Files.writeString(javaSource, "public class Example { public static void main(String[] args) { System.out.println(\"Hello, OneZip!\"); } }\n");
        Files.writeString(csv, "sample,value\n" + "example,42\n".repeat(600));
        var picture = new java.awt.image.BufferedImage(320, 180, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = picture.createGraphics();
        graphics.setColor(new java.awt.Color(221, 232, 203));
        graphics.fillRect(0, 0, 320, 180);
        graphics.setColor(new java.awt.Color(87, 119, 76));
        graphics.fillRoundRect(80, 40, 160, 100, 20, 20);
        graphics.dispose();
        javax.imageio.ImageIO.write(picture, "png", preview.toFile());
        Path archive = fixtures.resolve("Example.zip");
        new ArchiveEngine().compress(List.of(readme, notes, documents, config, css, javaSource, csv, preview), archive,
                ArchiveEngine.Format.ZIP, 5, null, null, null, 2, "OneZip sample archive — demonstration files only.");
        SettingsStore.shared().put("appearance.language", "en");
        I18n.initialize();
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            app = fx(() -> {
                BackgroundAppearance.configure("", false, false);
                ThemeManager.setDark(true);
                Stage stage = new Stage();
                MainApplication application = new MainApplication();
                application.start(stage);
                var getter = MainApplication.class.getDeclaredMethod("controller");
                getter.setAccessible(true);
                controller = (MainWindowController) getter.invoke(application);
                stage.setWidth(1180); stage.setHeight(800);
                return stage;
            });
            if (args.length < 3) capture("home");
            fx(() -> {
                controller.model().pageProperty().set(MainWindowModel.Page.COMPRESS);
                ((TextField) app.getScene().lookup("#compression-name")).setText("Example");
                ((TextField) app.getScene().lookup("#compression-location")).setText("C:\\Archives");
                return null;
            });
            if (args.length < 3) capture("compress");
            fx(() -> { controller.selectArchive(archive); return null; });
            long deadline = System.nanoTime() + 15_000_000_000L;
            while (fx(() -> app.getScene().lookup("#zip-preview-progress").isVisible())) {
                if (System.nanoTime() > deadline) throw new AssertionError("Listing timeout");
                Thread.sleep(30);
            }
            fx(() -> {
                var table = (TableView<?>) app.getScene().lookup("#zip-entry-table");
                require(table.getItems().size() >= 3, "Demo entries missing");
                return null;
            });
            if (args.length >= 3) {
                fx(() -> {
                    ThemeManager.setDark(false);
                    AccentTheme.set(javafx.scene.paint.Color.web("#91643f"));
                    BackgroundAppearance.configure(args[2], true, true);
                    BackgroundAppearance.setBlurRadius(3);
                    BackgroundAppearance.setControlsTransparency(85);
                    return null;
                });
            }
            capture("extract");
            System.out.println("PASS real scene screenshots: home, compress, extract; isolated settings, synthetic files, no desktop capture");
        } finally {
            fx(() -> { for (var window : List.copyOf(javafx.stage.Window.getWindows())) window.hide(); return null; });
            Platform.exit();
        }
    }
}
