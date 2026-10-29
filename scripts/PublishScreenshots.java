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
        Path archive = fixtures.resolve("Example.zip");
        new ArchiveEngine().compress(List.of(readme, notes, documents), archive,
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
            capture("home");
            fx(() -> {
                controller.model().pageProperty().set(MainWindowModel.Page.COMPRESS);
                ((TextField) app.getScene().lookup("#compression-name")).setText("Example");
                ((TextField) app.getScene().lookup("#compression-location")).setText("C:\\Archives");
                return null;
            });
            capture("compress");
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
            capture("extract");
            System.out.println("PASS real scene screenshots: home, compress, extract; isolated settings, synthetic files, no desktop capture");
        } finally {
            fx(() -> { for (var window : List.copyOf(javafx.stage.Window.getWindows())) window.hide(); return null; });
            Platform.exit();
        }
    }
}
