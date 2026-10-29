package cn.shed4329.onezip.themes.fxJava.fluent;

import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;

/** Keeps action captions readable and exposes their full text to pointer and keyboard users. */
final class ControlHints {
    private ControlHints() {}

    static void install(Parent parent) {
        // Control content is not in the scene-graph children until its skin is created.
        if (parent instanceof javafx.scene.control.ScrollPane scroll
                && scroll.getContent() instanceof Parent content) install(content);
        if (parent instanceof javafx.scene.control.TitledPane titled
                && titled.getContent() instanceof Parent content) install(content);
        for (var child : parent.getChildrenUnmodifiable()) {
            if (child instanceof ButtonBase button
                    && (button instanceof Button || button instanceof MenuButton)
                    && !button.getText().isBlank()) {
                button.setMinWidth(Region.USE_PREF_SIZE);
                if (button.getTooltip() == null) {
                    Tooltip tooltip = new Tooltip();
                    tooltip.textProperty().bind(button.textProperty());
                    tooltip.setWrapText(true);
                    tooltip.setMaxWidth(420);
                    button.setTooltip(tooltip);
                }
            }
            if (child instanceof Parent nested) install(nested);
        }
    }
}
