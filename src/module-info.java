/**
 * OneZip desktop and command-line application with independent Java codecs.
 *
 * <p>The engine exposes no JavaFX API. UI controllers are opened only to the JavaFX modules that
 * construct them; codec internals remain encapsulated.
 */
@SuppressWarnings("module") // Preserve the user-selected cn.shed4329.onezip namespace.
module cn.shed4329.onezip {
    requires java.desktop;
    requires javafx.controls;
    requires javafx.fxml;
    requires org.jfxtras.styles.jmetro;
    requires org.controlsfx.controls;

    exports cn.shed4329.onezip.engine;
    exports cn.shed4329.onezip.engine.lzma;
    exports cn.shed4329.onezip.cli;
    exports cn.shed4329.onezip.setting;

    opens cn.shed4329.onezip.themes.fxJava.fluent to
            javafx.fxml,
            javafx.graphics;
    opens cn.shed4329.onezip.themes.fxJava.fluent.component to
            javafx.fxml;
}
