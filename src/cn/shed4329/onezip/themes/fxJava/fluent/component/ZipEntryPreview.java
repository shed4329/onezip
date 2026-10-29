package cn.shed4329.onezip.themes.fxJava.fluent.component;

import static cn.shed4329.onezip.i18n.I18n.t;

import cn.shed4329.onezip.engine.ArchiveEngine;
import cn.shed4329.onezip.themes.fxJava.fluent.WindowChrome;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Archive browser; background listing uses the existing pure-Java readers. ZIP reads only its
 * directory.
 */
public final class ZipEntryPreview extends VBox implements AutoCloseable {
    private final ArchiveEngine engine = new ArchiveEngine();
    private final TableView<ArchiveEngine.ArchiveItem> table = new TableView<>();
    private final Label status = new Label();
    private final Label placeholder = new Label(t("archive.dropHint"));
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Button back = new Button("←"), forward = new Button("→"), up = new Button("↑");
    private final HBox breadcrumbs = new HBox(2);
    private final SplitPane browser = new SplitPane();
    private final TextArea archiveComment = new TextArea();

    private record Listing(ArchiveDirectory directory, String comment) {}

    private final javafx.beans.property.ReadOnlyIntegerWrapper selectionCount =
            new javafx.beans.property.ReadOnlyIntegerWrapper();
    private java.nio.charset.Charset filenameCharset = java.nio.charset.Charset.forName("IBM437");
    private char[] sessionPassword = new char[0];
    private String restorePath = "";
    private final Label selection = new Label();
    private final Deque<ArchiveDirectory.Folder> past = new ArrayDeque<>(),
            future = new ArrayDeque<>();
    private ArchiveDirectory directory;
    private ArchiveDirectory.Folder folder;
    private final ThreadPoolExecutor loader =
            new ThreadPoolExecutor(
                    1,
                    1,
                    0,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(1),
                    r -> {
                        Thread t = new Thread(r, "onezip-zip-preview");
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.DiscardOldestPolicy());
    private Task<Listing> current;
    private Path selected;
    private boolean closed;

    public ZipEntryPreview() {
        setSpacing(0);
        setPadding(Insets.EMPTY);
        setId("zip-entry-preview");
        getStyleClass().add("archive-browser");
        table.setId("zip-entry-table");
        status.setId("zip-preview-status");
        progress.setId("zip-preview-progress");
        TableColumn<ArchiveEngine.ArchiveItem, ArchiveEngine.ArchiveItem> name =
                new TableColumn<>(t("column.name"));
        name.setId("archive-name");
        name.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue()));
        name.setComparator(
                Comparator.comparing(
                        v -> visible(ArchiveDirectory.fileName(v)), String.CASE_INSENSITIVE_ORDER));
        name.setPrefWidth(480);
        name.setMinWidth(120);
        name.setCellFactory(
                column ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(
                                    ArchiveEngine.ArchiveItem value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(null);
                                setGraphic(null);
                                setTooltip(null);
                                if (empty || value == null) return;
                                String text = visible(ArchiveDirectory.fileName(value));
                                setText(text);
                                setTooltip(new Tooltip(visible(value.name())));
                                setGraphic(FileIcons.view(value.name(), value.directory()));
                                setGraphicTextGap(8);
                            }
                        });
        TableColumn<ArchiveEngine.ArchiveItem, String> type = new TableColumn<>(t("column.type"));
        type.setCellValueFactory(
                v ->
                        new ReadOnlyStringWrapper(
                                FileIcons.type(v.getValue().name(), v.getValue().directory())));
        type.setPrefWidth(110);
        type.setMinWidth(85);
        TableColumn<ArchiveEngine.ArchiveItem, ArchiveEngine.ArchiveItem> size =
                new TableColumn<>(t("column.originalSize"));
        size.setId("archive-original-size");
        size.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue()));
        size.setComparator(Comparator.comparingLong(ArchiveEngine.ArchiveItem::size));
        size.setPrefWidth(110);
        size.setMinWidth(110);
        size.setCellFactory(
                column ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(
                                    ArchiveEngine.ArchiveItem value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(
                                        empty || value == null
                                                ? null
                                                : value.directory() ? "—" : size(value.size()));
                                setTooltip(
                                        empty || value == null || value.directory()
                                                ? null
                                                : new Tooltip(value.size() + " bytes"));
                            }
                        });
        table.getColumns().add(name);
        table.getColumns().add(type);
        table.getColumns().add(size);
        TableColumn<ArchiveEngine.ArchiveItem, Number> packed =
                new TableColumn<>(t("column.compressedSize"));
        packed.setId("archive-packed-size");
        packed.setPrefWidth(110);
        packed.setMinWidth(100);
        packed.setCellValueFactory(
                v -> {
                    var d = directory == null ? null : directory.details(v.getValue());
                    return new javafx.beans.property.ReadOnlyLongWrapper(
                            d == null ? -1 : d.packedSize());
                });
        packed.setCellFactory(
                c ->
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
        table.getColumns().setAll(List.of(name, packed, size, type));
        addDetailColumn(
                t("column.modified"),
                155,
                d ->
                        d.modified() == null
                                ? "—"
                                : d.modified()
                                        .format(
                                                java.time.format.DateTimeFormatter.ofPattern(
                                                        "yyyy/MM/dd HH:mm:ss")));
        addDetailColumn(t("column.method"), 95, ArchiveEngine.EntryDetails::method);
        addDetailColumn(
                t("column.encryption"),
                95,
                d -> "无".equals(d.encryption()) ? t("encryption.none") : d.encryption());
        addDetailColumn("CRC32", 105, ArchiveEngine.EntryDetails::crc);

        table.setSortPolicy(
                view -> {
                    Comparator<ArchiveEngine.ArchiveItem> order =
                            Comparator.comparing(item -> !item.directory());
                    Comparator<ArchiveEngine.ArchiveItem> selected = view.getComparator();
                    if (selected == null) selected = name.getComparator();
                    FXCollections.sort(view.getItems(), order.thenComparing(selected));
                    return true;
                });
        table.setRowFactory(
                view -> {
                    TableRow<ArchiveEngine.ArchiveItem> row = new TableRow<>();
                    row.setOnMouseClicked(
                            e -> {
                                if (e.getButton() == MouseButton.PRIMARY
                                        && e.getClickCount() == 2
                                        && !row.isEmpty()) open(row.getItem());
                            });
                    return row;
                });
        table.setOnKeyPressed(
                e -> {
                    if (e.getCode() == KeyCode.ENTER) {
                        open(table.getSelectionModel().getSelectedItem());
                        e.consume();
                    } else if (e.getCode() == KeyCode.BACK_SPACE) {
                        up.fire();
                        e.consume();
                    } else if (e.isAltDown() && e.getCode() == KeyCode.LEFT) {
                        back.fire();
                        e.consume();
                    } else if (e.isAltDown() && e.getCode() == KeyCode.RIGHT) {
                        forward.fire();
                        e.consume();
                    }
                });
        name.setPrefWidth(260);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(placeholder);
        for (var column : table.getColumns()) {
            var measure = new javafx.scene.text.Text(column.getText());
            measure.setFont(
                    javafx.scene.text.Font.font(
                            "Microsoft YaHei", javafx.scene.text.FontWeight.BOLD, 13));
            double headerWidth = Math.ceil(measure.getLayoutBounds().getWidth()) + 64;
            column.setMinWidth(Math.max(column.getMinWidth(), headerWidth));
            column.setPrefWidth(Math.max(column.getPrefWidth(), headerWidth));
        }
        table.setFixedCellSize(32);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getSelectionModel()
                .getSelectedItems()
                .addListener(
                        (javafx.collections.ListChangeListener<ArchiveEngine.ArchiveItem>)
                                change -> updateSelection());
        progress.setPrefSize(18, 18);
        progress.setMaxSize(18, 18);
        busy(false);
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        HBox header = new HBox(8, progress, status, selection);
        HBox.setHgrow(status, Priority.ALWAYS);
        header.getStyleClass().add("archive-statusbar");
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.visibleProperty()
                .bind(status.textProperty().isNotEmpty().or(progress.visibleProperty()));
        header.managedProperty().bind(header.visibleProperty());
        back.setId("zip-nav-back");
        forward.setId("zip-nav-forward");
        up.setId("zip-nav-up");
        breadcrumbs.setId("zip-breadcrumbs");
        back.setTooltip(new Tooltip(t("navigation.backHint")));
        forward.setTooltip(new Tooltip(t("navigation.forwardHint")));
        up.setTooltip(new Tooltip(t("navigation.upHint")));
        back.setAccessibleText(t("navigation.back"));
        forward.setAccessibleText(t("navigation.forward"));
        up.setAccessibleText(t("navigation.up"));
        back.setOnAction(
                e -> {
                    if (!past.isEmpty()) {
                        future.push(folder);
                        display(past.pop());
                    }
                });
        forward.setOnAction(
                e -> {
                    if (!future.isEmpty()) {
                        past.push(folder);
                        display(future.pop());
                    }
                });
        up.setOnAction(
                e -> {
                    if (folder != null && folder.parent() != null) navigate(folder.parent());
                });
        ScrollPane address = new ScrollPane(breadcrumbs);
        address.setFitToHeight(true);
        address.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        address.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        address.setMinWidth(0);
        HBox navigation = new HBox(4, back, forward, up, address);
        HBox.setHgrow(address, Priority.ALWAYS);
        navigation.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        navigation.getStyleClass().add("archive-addressbar");
        browser.setId("archive-browser-split");
        browser.setOrientation(javafx.geometry.Orientation.VERTICAL);
        archiveComment.setId("archive-comment-text");
        archiveComment.setEditable(false);
        archiveComment.setWrapText(true);
        archiveComment.setPromptText(t("archive.noComment"));
        VBox commentPane = new VBox(archiveComment);
        commentPane.setId("archive-comment-pane");
        commentPane.getStyleClass().add("archive-comment-pane");
        commentPane.setMinHeight(60);
        commentPane.setPrefHeight(120);
        VBox.setVgrow(archiveComment, Priority.ALWAYS);
        browser.getItems().addAll(table, commentPane);
        browser.setDividerPositions(.78);
        browser.setMinHeight(0);
        table.setMinHeight(0);
        resetNavigation();
        getChildren().addAll(navigation, browser, header);
        VBox.setVgrow(browser, Priority.ALWAYS);
    }

    public void setArchiveActions(ContextMenu actions) {
        table.setContextMenu(actions);
    }

    public void promptPassword() {
        if (selected == null) return;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle(t("archive.passwordTitle"));
        PasswordField field = new PasswordField();
        field.setPromptText(t("archive.passwordPrompt"));
        field.setId("archive-directory-password");
        dialog.getDialogPane().setContent(field);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        if (WindowChrome.showAndWait(dialog).filter(ButtonType.OK::equals).isPresent()) {
            char[] password = field.getText().toCharArray();
            field.clear();
            loadArchive(selected, password);
        } else field.clear();
    }

    private void addDetailColumn(
            String title,
            double width,
            java.util.function.Function<ArchiveEngine.EntryDetails, String> value) {
        TableColumn<ArchiveEngine.ArchiveItem, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(
                v -> {
                    var d = directory == null ? null : directory.details(v.getValue());
                    String text = d == null ? "" : value.apply(d);
                    return new ReadOnlyStringWrapper(
                            text == null || text.isEmpty() ? "—" : visible(text));
                });
        table.getColumns().add(column);
    }

    public javafx.beans.property.ReadOnlyIntegerProperty selectionCountProperty() {
        return selectionCount.getReadOnlyProperty();
    }

    public List<ArchiveEngine.ArchiveItem> selectedItems() {
        return List.copyOf(table.getSelectionModel().getSelectedItems());
    }

    public String currentPath() {
        return directory == null || folder == null ? "" : directory.path(folder);
    }

    public java.nio.charset.Charset filenameCharset() {
        return filenameCharset;
    }

    public void reloadCurrentFolder() {
        if (selected == null) return;
        String path = currentPath();
        loadArchive(selected, sessionPassword.clone());
        restorePath = path;
    }

    public void setFilenameEncoding(String name) {
        filenameCharset = java.nio.charset.Charset.forName(name);
        if (selected != null) loadArchive(selected, sessionPassword.clone());
    }

    private void updateRows() {
        table.getSelectionModel().clearSelection();
        table.getItems().setAll(folder.children());
        table.sort();
        long folders =
                table.getItems().stream().filter(ArchiveEngine.ArchiveItem::directory).count();
        status.setText(
                cn.shed4329.onezip.i18n.I18n.format(
                        "archive.summary",
                        table.getItems().size(),
                        table.getItems().size() - folders,
                        folders));
    }

    private void updateSelection() {
        int count = table.getSelectionModel().getSelectedItems().size();
        selectionCount.set(count);
        selection.setText(
                count == 0 ? "" : cn.shed4329.onezip.i18n.I18n.format("archive.selection", count));
    }

    /** Called on the FX thread by both the file chooser and drag/drop selection. */
    public void showArchive(Path archive) {
        loadArchive(archive, new char[0]);
    }

    private void loadArchive(Path archive, char[] password) {
        if (closed) {
            Arrays.fill(password, '\0');
            return;
        }
        Arrays.fill(sessionPassword, '\0');
        sessionPassword = password.clone();
        restorePath = "";
        if (current != null) current.cancel(true);
        current = null;
        loader.getQueue().clear();
        busy(false);
        table.getItems().clear();
        resetNavigation();
        selected = archive;
        boolean readable =
                archive != null
                        && archive.getFileName() != null
                        && ArchiveEngine.readableName(archive.getFileName().toString());
        if (!readable) {
            status.setText("");
            placeholder.setText(t("archive.dropHint"));
            Arrays.fill(password, '\0');
            return;
        }
        status.setText(t("archive.readingDirectory"));
        placeholder.setText(t("archive.reading"));
        busy(true);
        Path source = archive;
        Task<Listing> task =
                new Task<>() {
                    @Override
                    protected Listing call() throws Exception {
                        try {
                            var result =
                                    engine.listArchive(
                                            source, password, this::isCancelled, filenameCharset);
                            return new Listing(
                                    ArchiveDirectory.fromDetails(
                                            result.entries(), this::isCancelled),
                                    result.comment());
                        } finally {
                            Arrays.fill(password, '\0');
                        }
                    }
                };
        current = task;
        task.setOnSucceeded(
                e -> {
                    if (closed || current != task) return;
                    busy(false);
                    directory = task.getValue().directory();
                    archiveComment.setText(task.getValue().comment());
                    var target = directory.root();
                    for (String part : restorePath.split("/")) {
                        if (part.isEmpty()) continue;
                        ArchiveDirectory.Folder match = null;
                        for (var child : target.folders())
                            if (child.label().equals(part)) {
                                match = child;
                                break;
                            }
                        if (match == null) break;
                        target = match;
                    }
                    display(target);
                    restorePath = "";
                });
        task.setOnFailed(
                e -> {
                    if (closed || current != task) return;
                    busy(false);
                    Throwable error = task.getException();
                    String message = error.getMessage();
                    status.setText(
                            (source.toString().toLowerCase(Locale.ROOT).endsWith(".zip")
                                            ? t("archive.zipReadErrorPrefix")
                                            : t("archive.readErrorPrefix"))
                                    + visible(
                                            message == null
                                                    ? error.getClass().getSimpleName()
                                                    : message));
                    placeholder.setText(t("archive.passwordHint"));
                });
        loader.execute(task);
    }

    private void resetNavigation() {
        directory = null;
        folder = null;
        past.clear();
        future.clear();
        breadcrumbs.getChildren().clear();
        archiveComment.clear();
        selectionCount.set(0);
        selection.setText("");
        status.setTooltip(null);
        back.setDisable(true);
        forward.setDisable(true);
        up.setDisable(true);
    }

    private void open(ArchiveEngine.ArchiveItem item) {
        if (directory != null && item != null) {
            var next = directory.folder(item);
            if (next != null) navigate(next);
        }
    }

    private void navigate(ArchiveDirectory.Folder next) {
        if (next == folder) return;
        if (folder != null) past.push(folder);
        future.clear();
        display(next);
    }

    private void display(ArchiveDirectory.Folder next) {
        folder = next;
        updateRows();
        table.scrollTo(0);

        placeholder.setText(t("archive.emptyFolder"));
        status.setTooltip(
                new Tooltip(
                        t("archive.recordsPrefix")
                                + directory.entryCount()
                                + t("archive.recordsSuffix")));
        back.setDisable(past.isEmpty());
        forward.setDisable(future.isEmpty());
        up.setDisable(next.parent() == null);
        breadcrumbs.getChildren().clear();
        Deque<ArchiveDirectory.Folder> chain = new ArrayDeque<>();
        for (var node = next; node != null; node = node.parent()) chain.push(node);
        while (!chain.isEmpty()) {
            var node = chain.pop();
            Button link =
                    new Button(
                            node.parent() == null ? t("navigation.root") : visible(node.label()));
            link.setOnAction(e -> navigate(node));
            link.setDisable(node == next);
            breadcrumbs.getChildren().add(link);
            if (!chain.isEmpty()) breadcrumbs.getChildren().add(new Label("›"));
        }
    }

    private void busy(boolean value) {
        progress.setVisible(value);
        progress.setManaged(value);
    }

    private static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};
        double n = bytes / 1024.0;
        int i = 0;
        while (n >= 1024 && i < units.length - 1) {
            n /= 1024;
            i++;
        }
        return String.format(Locale.ROOT, "%.1f %s", n, units[i]);
    }

    private static String visible(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (Character.isISOControl(c))
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (current != null) current.cancel(true);
        current = null;
        loader.shutdownNow();
        Arrays.fill(sessionPassword, '\0');
    }
}
