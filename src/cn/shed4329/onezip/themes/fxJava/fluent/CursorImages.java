package cn.shed4329.onezip.themes.fxJava.fluent;

import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.Scene;
import javafx.scene.image.Image;

import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;

/** A missing or damaged custom cursor must not prevent the application from starting. */
final class CursorImages {
    private CursorImages() {}

    static Optional<Image> load(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try (var input = Files.newInputStream(Path.of(value))) {
            Image image = new Image(input);
            return image.isError() || image.getWidth() <= 0 ? Optional.empty() : Optional.of(image);
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            return Optional.empty();
        }
    }

    static boolean apply(Scene scene, String value) {
        var image = load(value);
        scene.setCursor(image.<Cursor>map(ImageCursor::new).orElse(Cursor.DEFAULT));
        return image.isPresent();
    }
}
