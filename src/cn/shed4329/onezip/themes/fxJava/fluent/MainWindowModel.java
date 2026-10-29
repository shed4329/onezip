package cn.shed4329.onezip.themes.fxJava.fluent;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.nio.file.Path;
import java.util.Collection;

/** Window state only: no controls, dialogs or compression implementation. Use on the FX thread. */
public final class MainWindowModel {
    public enum Page {
        HOME,
        COMPRESS,
        EXTRACT
    }

    private final ObjectProperty<Page> page = new SimpleObjectProperty<>(Page.HOME);
    private final ObjectProperty<Path> archive = new SimpleObjectProperty<>();
    private final ObservableList<Path> sources = FXCollections.observableArrayList();
    private final ObservableList<Path> sourceView =
            FXCollections.unmodifiableObservableList(sources);

    public ObjectProperty<Page> pageProperty() {
        return page;
    }

    public ReadOnlyObjectProperty<Path> archiveProperty() {
        return archive;
    }

    public Path archive() {
        return archive.get();
    }

    public void selectArchive(Path path) {
        archive.set(path.toAbsolutePath().normalize());
    }

    public ObservableList<Path> sources() {
        return sourceView;
    }

    public void addSources(Collection<Path> paths) {
        var known = new java.util.HashSet<>(sources);
        var added = new java.util.ArrayList<Path>();
        for (Path path : paths) {
            Path normalized = path.toAbsolutePath().normalize();
            if (known.add(normalized)) added.add(normalized);
        }
        sources.addAll(added);
    }

    public void removeSources(Collection<Path> paths) {
        sources.removeAll(paths);
    }

    public void clearSources() {
        sources.clear();
    }

    public void reorderSources(java.util.List<Path> order) {
        if (order.size() != sources.size()
                || !new java.util.HashSet<>(order).equals(new java.util.HashSet<>(sources)))
            throw new IllegalArgumentException("Source order must preserve every item");
        sources.setAll(order);
    }
}
