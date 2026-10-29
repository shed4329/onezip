package cn.shed4329.onezip.themes.fxJava.fluent.component;

import static cn.shed4329.onezip.i18n.I18n.t;

import javafx.beans.property.*;
import javafx.collections.*;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.scene.control.*;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Source metadata is read once in the background, never recursively for directory sizes. */
public final class CompressionSourceTable extends TableView<Path> implements AutoCloseable {
    private record Info(boolean directory, long size, LocalDateTime modified, String error) {}

    private final Map<Path, Info> metadata = new HashMap<>();
    private final ReadOnlyStringWrapper totalSize =
            new ReadOnlyStringWrapper(t("sources.sizeZero"));
    private final ThreadPoolExecutor worker =
            new ThreadPoolExecutor(
                    1,
                    1,
                    0,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(1),
                    r -> {
                        Thread thread = new Thread(r, "onezip-source-metadata");
                        thread.setDaemon(true);
                        return thread;
                    },
                    new ThreadPoolExecutor.DiscardOldestPolicy());
    private Task<Map<Path, Info>> current;
    private ObservableList<Path> sources;
    private final ListChangeListener<Path> changed = change -> loadMetadata();

    public CompressionSourceTable() {
        setId("source-list");
        getStyleClass().add("source-table");
        setMinHeight(80);
        setFixedCellSize(34);
        getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setPlaceholder(new Label(t("sources.dropHint")));
        TableColumn<Path, Path> name = new TableColumn<>(t("column.name"));
        name.setId("source-name");
        name.setPrefWidth(400);
        name.setMinWidth(140);
        name.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue()));
        name.setComparator(
                Comparator.comparing(
                        p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        name.setPrefWidth(260);
        name.setCellFactory(
                column ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(Path path, boolean empty) {
                                super.updateItem(path, empty);
                                setText(null);
                                setGraphic(null);
                                setTooltip(null);
                                if (empty || path == null) return;
                                Info info = metadata.get(path);
                                setText(path.getFileName().toString());
                                setGraphic(
                                        FileIcons.view(
                                                path.toString(), info != null && info.directory));
                                setGraphicTextGap(8);
                                setTooltip(
                                        new Tooltip(
                                                path
                                                        + (info == null || info.error.isEmpty()
                                                                ? ""
                                                                : "\n" + info.error)));
                            }
                        });
        TableColumn<Path, Number> size = new TableColumn<>(t("column.size"));
        size.setId("source-size");
        size.setPrefWidth(125);
        size.setCellValueFactory(
                v ->
                        new ReadOnlyLongWrapper(
                                metadata.containsKey(v.getValue())
                                        ? metadata.get(v.getValue()).size
                                        : -1));
        size.setCellFactory(
                column ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(Number value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(
                                        empty || value == null
                                                ? null
                                                : value.longValue() < 0
                                                        ? "—"
                                                        : size(value.longValue()));
                            }
                        });
        TableColumn<Path, String> type = new TableColumn<>(t("column.type"));
        type.setId("source-type");
        type.setPrefWidth(150);
        type.setCellValueFactory(
                v -> {
                    Info info = metadata.get(v.getValue());
                    return new ReadOnlyStringWrapper(
                            info == null
                                    ? "—"
                                    : info.error.isEmpty()
                                            ? FileIcons.type(
                                                    v.getValue().toString(), info.directory)
                                            : t("file.unreadable"));
                });
        TableColumn<Path, String> modified = new TableColumn<>(t("column.modified"));
        modified.setId("source-modified");
        modified.setPrefWidth(180);
        modified.setCellValueFactory(
                v -> {
                    Info info = metadata.get(v.getValue());
                    return new ReadOnlyStringWrapper(
                            info == null || info.modified == null
                                    ? "—"
                                    : info.modified.format(
                                            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
                });
        TableColumn<Path, String> path = new TableColumn<>(t("column.path"));
        path.setId("source-path");
        path.setPrefWidth(320);
        path.setMinWidth(140);
        path.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().toString()));
        path.setCellFactory(
                column ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(String value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(empty ? null : value);
                                setTooltip(empty || value == null ? null : new Tooltip(value));
                            }
                        });
        getColumns().add(name);
        getColumns().add(size);
        getColumns().add(type);
        getColumns().add(modified);
        getColumns().add(path);
        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    public void setSources(ObservableList<Path> values) {
        if (sources != null) sources.removeListener(changed);
        sources = values;
        SortedList<Path> sorted = new SortedList<>(values);
        sorted.comparatorProperty().bind(comparatorProperty());
        setItems(sorted);
        values.addListener(changed);
        loadMetadata();
    }

    public ReadOnlyStringProperty totalSizeProperty() {
        return totalSize.getReadOnlyProperty();
    }

    private void loadMetadata() {
        if (current != null) current.cancel(true);
        current = null;
        worker.getQueue().clear();
        List<Path> paths = List.copyOf(sources);
        metadata.keySet().retainAll(new HashSet<>(paths));
        if (paths.isEmpty()) {
            metadata.clear();
            totalSize.set(t("sources.sizeZero"));
            refresh();
            return;
        }
        Map<Path, Info> cached = Map.copyOf(metadata);
        totalSize.set(t("sources.reading"));
        Task<Map<Path, Info>> task =
                new Task<>() {
                    @Override
                    protected Map<Path, Info> call() throws Exception {
                        Map<Path, Info> result = new HashMap<>();
                        for (Path path : paths) {
                            if (isCancelled()) return Map.of();
                            Info existing = cached.get(path);
                            if (existing != null) {
                                result.put(path, existing);
                                continue;
                            }
                            try {
                                var attrs =
                                        Files.readAttributes(
                                                path,
                                                BasicFileAttributes.class,
                                                LinkOption.NOFOLLOW_LINKS);
                                result.put(
                                        path,
                                        new Info(
                                                attrs.isDirectory(),
                                                attrs.isRegularFile() ? attrs.size() : -1,
                                                LocalDateTime.ofInstant(
                                                        attrs.lastModifiedTime().toInstant(),
                                                        ZoneId.systemDefault()),
                                                ""));
                            } catch (java.io.IOException | SecurityException e) {
                                result.put(
                                        path,
                                        new Info(
                                                false,
                                                -1,
                                                null,
                                                Objects.toString(
                                                        e.getMessage(), t("sources.readError"))));
                            }
                        }
                        return result;
                    }
                };
        current = task;
        task.setOnSucceeded(
                e -> {
                    if (current != task) return;
                    metadata.clear();
                    metadata.putAll(task.getValue());
                    long bytes = 0;
                    boolean incomplete = false;
                    for (Info info : metadata.values()) {
                        if (info.size < 0) incomplete = true;
                        else bytes = Math.min(Long.MAX_VALUE - info.size, bytes) + info.size;
                    }
                    totalSize.set(t("sources.sizePrefix") + (incomplete ? "≥" : "") + size(bytes));
                    refresh();
                    sort();
                });
        task.setOnFailed(
                e -> {
                    if (current == task) totalSize.set(t("sources.partialError"));
                });
        worker.execute(task);
    }

    private static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};
        double value = bytes;
        int index = -1;
        do {
            value /= 1024;
            index++;
        } while (value >= 1024 && index < units.length - 1);
        return String.format(Locale.ROOT, "%.1f %s", value, units[index]);
    }

    @Override
    public void close() {
        if (current != null) current.cancel(true);
        if (sources != null) sources.removeListener(changed);
        worker.shutdownNow();
    }
}
