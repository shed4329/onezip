package cn.shed4329.onezip.themes.fxJava.fluent;

import static cn.shed4329.onezip.i18n.I18n.t;

import cn.shed4329.onezip.engine.ArchiveEngine;
import cn.shed4329.onezip.engine.ZipComments;
import cn.shed4329.onezip.themes.fxJava.fluent.component.ArchiveDialogs;
import cn.shed4329.onezip.themes.fxJava.fluent.component.CompressionSourceTable;
import cn.shed4329.onezip.themes.fxJava.fluent.component.ZipEntryPreview;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** FXML event wiring; codecs and long-running work remain in ArchiveEngine/ArchiveTask. */
public final class MainWindowController implements AutoCloseable {
    private final MainWindowModel model = new MainWindowModel();
    private boolean darkMode;
    @FXML private BorderPane root;
    @FXML private VBox homePane, compressionPane, extractionPane;
    @FXML private ScrollPane compressionScroll;
    @FXML private ScrollPane compressionToolbarScroll, archiveToolbarScroll;
    @FXML private CompressionSourceTable sourceList;
    @FXML private Label sourceCount, sourceSize, compressionValidation;
    @FXML private Button removeSource, clearSource, compressStart;
    @FXML private Button moveSourceUp, moveSourceDown;
    @FXML private ToggleButton formatZip, formatSevenZip, formatTar, formatTarGzip;
    @FXML private ChoiceBox<String> compressionFormat, compressionLevel;
    @FXML private Spinner<Integer> compressionThreads;
    @FXML private PasswordField compressionPassword;
    @FXML private TextArea compressionComment;
    @FXML private TextField compressionName, compressionLocation;
    @FXML private Label compressionExtension;
    @FXML private GridPane compressionPanels;
    @FXML private VBox compressionSettings;
    @FXML private TitledPane compressionAdvanced;
    @FXML private ContextMenu archiveActions;
    @FXML private Button extractAll, comment, deleteEntries;
    @FXML private MenuButton addEntries;
    @FXML private MenuItem testArchive;
    @FXML private MenuItem archivePassword, archiveReload;
    private final javafx.beans.property.BooleanProperty editing =
            new javafx.beans.property.SimpleBooleanProperty();
    @FXML private ZipEntryPreview zipPreview;
    private Stage settingsStage;

    @FXML
    private void initialize() {
        for (ScrollPane toolbar : List.of(compressionToolbarScroll, archiveToolbarScroll)) {
            toolbar.viewportBoundsProperty()
                    .addListener(
                            (o, before, after) ->
                                    javafx.application.Platform.runLater(
                                            () -> {
                                                toolbar.setHvalue(0);
                                                toolbar.setVvalue(0);
                                            }));
        }
        root.widthProperty()
                .addListener((o, old, width) -> layoutCompressionPanels(width.doubleValue()));
        layoutCompressionPanels(root.getWidth());
        var compress = model.pageProperty().isEqualTo(MainWindowModel.Page.COMPRESS);
        var home = model.pageProperty().isEqualTo(MainWindowModel.Page.HOME);
        var extract = model.pageProperty().isEqualTo(MainWindowModel.Page.EXTRACT);
        homePane.visibleProperty().bind(home);
        homePane.managedProperty().bind(home);
        compressionPane.visibleProperty().bind(compress);
        compressionPane.managedProperty().bind(compress);
        compressionScroll.visibleProperty().bind(compress);
        compressionScroll.managedProperty().bind(compress);
        extractionPane.visibleProperty().bind(extract);
        extractionPane.managedProperty().bind(extract);
        model.pageProperty().addListener((o, old, value) -> updatePageStyle(value));
        updatePageStyle(model.pageProperty().get());
        sourceList.setSources(model.sources());
        sourceCount
                .textProperty()
                .bind(Bindings.size(model.sources()).asString(t("sources.selected")));
        sourceSize.textProperty().bind(sourceList.totalSizeProperty());
        sourceSize.setTooltip(new Tooltip(t("sources.sizeHint")));
        removeSource
                .disableProperty()
                .bind(Bindings.isEmpty(sourceList.getSelectionModel().getSelectedItems()));
        moveSourceUp.disableProperty().bind(removeSource.disableProperty());
        moveSourceDown.disableProperty().bind(removeSource.disableProperty());
        clearSource.disableProperty().bind(Bindings.isEmpty(model.sources()));
        compressStart.disableProperty().bind(Bindings.isEmpty(model.sources()));
        compressionFormat
                .getItems()
                .setAll(
                        "ZIP", "7z", "TAR", "GZIP", "TAR.GZ", "LZ4", "TAR.LZ4", "XZ", "TAR.XZ",
                        "BZIP2", "TAR.BZ2");
        compressionThreads.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 64, 0));
        compressionThreads.setTooltip(new Tooltip(t("threads.hint")));
        compressionLocation.setText(System.getProperty("user.home"));
        compressionFormat
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((o, old, value) -> updateCompressionOptions());
        compressionFormat.setValue("ZIP");
        compressionValidation
                .managedProperty()
                .bind(compressionValidation.textProperty().isNotEmpty());
        compressionValidation.visibleProperty().bind(compressionValidation.managedProperty());
        zipPreview.setArchiveActions(archiveActions);
        extractAll.disableProperty().bind(model.archiveProperty().isNull());
        testArchive.disableProperty().bind(model.archiveProperty().isNull());
        var notZip =
                Bindings.createBooleanBinding(
                        () ->
                                model.archive() == null
                                        || !model.archive()
                                                .getFileName()
                                                .toString()
                                                .toLowerCase(Locale.ROOT)
                                                .endsWith(".zip"),
                        model.archiveProperty());
        comment.disableProperty().bind(notZip.or(editing));
        addEntries.disableProperty().bind(notZip.or(editing));
        deleteEntries
                .disableProperty()
                .bind(notZip.or(editing).or(zipPreview.selectionCountProperty().isEqualTo(0)));
        addEntries.setTooltip(new Tooltip(t("zip.editHint")));
        deleteEntries.setTooltip(new Tooltip(t("zip.deleteHint")));
        extractAll.setTooltip(new Tooltip(t("extract.allHint")));
        archivePassword.disableProperty().bind(model.archiveProperty().isNull());
        archiveReload.disableProperty().bind(model.archiveProperty().isNull());
        model.archiveProperty().addListener((o, old, value) -> zipPreview.showArchive(value));
    }

    private void layoutCompressionPanels(double width) {
        boolean stacked = width < 1000;
        compressionPanels.getColumnConstraints().get(0).setPercentWidth(stacked ? 100 : 66);
        compressionPanels.getColumnConstraints().get(1).setPercentWidth(stacked ? 0 : 34);
        GridPane.setColumnIndex(compressionAdvanced, stacked ? 0 : 1);
        GridPane.setRowIndex(compressionAdvanced, stacked ? 1 : 0);
        compressionAdvanced.setMinWidth(0);
        compressionSettings.setMinWidth(0);
    }

    private void updatePageStyle(MainWindowModel.Page page) {
        root.pseudoClassStateChanged(
                javafx.css.PseudoClass.getPseudoClass("home-mode"),
                page == MainWindowModel.Page.HOME);
        root.pseudoClassStateChanged(
                javafx.css.PseudoClass.getPseudoClass("archive-mode"),
                page == MainWindowModel.Page.EXTRACT);
    }

    @FXML
    private void goHome() {
        model.pageProperty().set(MainWindowModel.Page.HOME);
    }

    public MainWindowModel model() {
        return model;
    }

    void setDarkMode(boolean value) {
        darkMode = value;
        archiveActions.getStyleClass().removeAll("dark", "light");
        archiveActions.getStyleClass().add(value ? "dark" : "light");
        for (var node : root.lookupAll(".home-tile"))
            if (node instanceof Button tile
                    && tile.getGraphic() instanceof javafx.scene.image.ImageView icon)
                icon.setEffect(value ? new javafx.scene.effect.ColorAdjust(0, 0, .35, 0) : null);
    }

    private Stage owner() {
        return (Stage) root.getScene().getWindow();
    }

    private void updateCompressionOptions() {
        String format = compressionFormat.getValue();
        String old = compressionLevel.getValue();
        for (ToggleButton button : List.of(formatZip, formatSevenZip, formatTar, formatTarGzip))
            button.setSelected(format.equals(button.getText()));
        compressionLevel
                .getItems()
                .setAll(t("level.standard"), t("level.fast"), t("level.fastest"), t("level.ultra"));
        if (!format.contains("BZIP2") && !format.equals("TAR.BZ2"))
            compressionLevel.getItems().add(t("level.store"));
        compressionLevel.setValue(
                compressionLevel.getItems().contains(old)
                        ? old
                        : compressionLevel.getItems().getFirst());
        compressionLevel.setDisable(format.equals("TAR"));
        compressionPassword.setDisable(!format.equals("ZIP"));
        if (compressionPassword.isDisabled()) compressionPassword.clear();
        compressionValidation.setText("");
        compressionComment.setDisable(!format.equals("ZIP"));
        compressionExtension.setText(compressionSuffix());
    }

    private String compressionSuffix() {
        return switch (compressionFormat.getValue()) {
            case "7z" -> ".7z";
            case "TAR" -> ".tar";
            case "GZIP" -> ".gz";
            case "TAR.GZ" -> ".tar.gz";
            case "LZ4" -> ".lz4";
            case "TAR.LZ4" -> ".tar.lz4";
            case "XZ" -> ".xz";
            case "TAR.XZ" -> ".tar.xz";
            case "BZIP2" -> ".bz2";
            case "TAR.BZ2" -> ".tar.bz2";
            default -> ".zip";
        };
    }

    @FXML
    private void browseCompressionLocation() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(t("compress.location"));
        try {
            Path current = Path.of(compressionLocation.getText());
            if (Files.isDirectory(current)) chooser.setInitialDirectory(current.toFile());
        } catch (java.nio.file.InvalidPathException ignored) {
        }
        File directory = chooser.showDialog(owner());
        if (directory != null) compressionLocation.setText(directory.getAbsolutePath());
    }

    @FXML
    private void compressAll() {
        startConfiguredCompression();
    }

    Stage startConfiguredCompression() {
        compressionValidation.setText("");
        String name = compressionName.getText().strip();
        if (name.isEmpty()
                || name.equals(".")
                || name.equals("..")
                || name.matches(".*[<>:\"/\\\\|?*].*")
                || name.chars().anyMatch(c -> c < 32)
                || name.endsWith(".")
                || name.endsWith(" ")) {
            compressionValidation.setText(t("validation.archiveName"));
            return null;
        }
        if (name.split("\\.", 2)[0]
                .stripTrailing()
                .matches("(?i)CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³]")) {
            compressionValidation.setText(t("validation.reservedName"));
            return null;
        }
        try {
            if (compressionLocation.getText().isBlank()) {
                compressionValidation.setText(t("validation.chooseLocation"));
                return null;
            }
            Path directory = Path.of(compressionLocation.getText()).toAbsolutePath().normalize();
            if (!Files.isDirectory(directory)) {
                compressionValidation.setText(t("validation.locationMissing"));
                return null;
            }
            String suffix = compressionSuffix();
            if (!name.toLowerCase(Locale.ROOT).endsWith(suffix)) name += suffix;
            else if (name.length() > suffix.length())
                compressionName.setText(name.substring(0, name.length() - suffix.length()));
            return startCompression(directory.resolve(name));
        } catch (java.nio.file.InvalidPathException e) {
            compressionValidation.setText(t("validation.locationInvalid"));
            return null;
        }
    }

    Stage startCompression(Path output) {
        compressionValidation.setText("");
        var sources = List.copyOf(sourceList.getItems());
        ArchiveEngine.Format format = ArchiveEngine.outputFormat("archive" + compressionSuffix());
        if (sources.isEmpty()) {
            compressionValidation.setText(t("validation.addSources"));
            return null;
        }
        if (ArchiveEngine.singleStreamFormat(format)
                && (sources.size() != 1
                        || !Files.isRegularFile(
                                sources.getFirst(), java.nio.file.LinkOption.NOFOLLOW_LINKS))) {
            compressionValidation.setText(t("validation.singleFile"));
            return null;
        }
        if (Files.exists(output, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            compressionValidation.setText(t("validation.outputExists"));
            return null;
        }
        String archiveComment = compressionComment.isDisabled() ? "" : compressionComment.getText();
        if (archiveComment.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65535) {
            compressionValidation.setText(t("validation.zipDescription"));
            return null;
        }
        int level =
                new int[] {5, 3, 1, 9, 0}[compressionLevel.getSelectionModel().getSelectedIndex()];
        char[] password =
                compressionPassword.isDisabled()
                        ? null
                        : compressionPassword.getText().toCharArray();
        try {
            var task =
                    cn.shed4329.onezip.application.ArchiveTask.compress(
                            sources,
                            output,
                            format,
                            level,
                            password,
                            compressionThreads.getValue(),
                            archiveComment);
            compressionPassword.clear();
            return ArchiveDialogs.showProgress(
                    owner(), task, t("task.compressPrefix") + output.getFileName(), darkMode);
        } finally {
            if (password != null) java.util.Arrays.fill(password, '\0');
        }
    }

    @FXML
    private void removeSources() {
        model.removeSources(List.copyOf(sourceList.getSelectionModel().getSelectedItems()));
    }

    @FXML
    private void addSourceFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(t("sources.add"));
        var files = chooser.showOpenMultipleDialog(owner());
        if (files != null) model.addSources(files.stream().map(File::toPath).toList());
    }

    @FXML
    private void addSourceFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(t("sources.add"));
        File folder = chooser.showDialog(owner());
        if (folder != null) model.addSources(List.of(folder.toPath()));
    }

    @FXML
    private void selectCompressionFormat(javafx.event.ActionEvent event) {
        var button = (ToggleButton) event.getSource();
        compressionFormat.setValue(button.getText());
        button.setSelected(true);
    }

    @FXML
    private void moveSourcesUp() {
        moveSources(-1);
    }

    @FXML
    private void moveSourcesDown() {
        moveSources(1);
    }

    private void moveSources(int direction) {
        var selected = new java.util.HashSet<>(sourceList.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;
        var order = new java.util.ArrayList<>(sourceList.getItems());
        for (int i = direction < 0 ? 1 : order.size() - 2;
                direction < 0 ? i < order.size() : i >= 0;
                i -= direction) {
            int target = i + direction;
            if (selected.contains(order.get(i)) && !selected.contains(order.get(target)))
                java.util.Collections.swap(order, i, target);
        }
        sourceList.getSortOrder().clear();
        model.reorderSources(order);
        sourceList.getSelectionModel().clearSelection();
        for (int i = 0; i < sourceList.getItems().size(); i++)
            if (selected.contains(sourceList.getItems().get(i)))
                sourceList.getSelectionModel().select(i);
    }

    @FXML
    private void clearSources() {
        model.clearSources();
    }

    @FXML
    private void sortSources(javafx.event.ActionEvent event) {
        String id = ((MenuItem) event.getSource()).getUserData().toString();
        var column =
                sourceList.getColumns().stream()
                        .filter(c -> id.equals(c.getId()))
                        .findFirst()
                        .orElseThrow();
        column.setSortType(
                id.equals("source-modified")
                        ? TableColumn.SortType.DESCENDING
                        : TableColumn.SortType.ASCENDING);
        sourceList.getSortOrder().setAll(java.util.List.of(column));
        sourceList.sort();
    }

    @FXML
    private void openArchive() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(t("archive.openTitle"));
        chooser.getExtensionFilters()
                .add(
                        new FileChooser.ExtensionFilter(
                                t("file.archive"),
                                "*.zip",
                                "*.7z",
                                "*.rar",
                                "*.rar5",
                                "*.tar",
                                "*.gz",
                                "*.gzip",
                                "*.tgz",
                                "*.lz4",
                                "*.xz",
                                "*.txz",
                                "*.bz2",
                                "*.bzip2",
                                "*.tbz",
                                "*.tbz2"));
        File file = chooser.showOpenDialog(owner());
        if (file != null) selectArchive(file.toPath());
    }

    public void selectArchive(Path path) {
        model.pageProperty().set(MainWindowModel.Page.EXTRACT);
        Path before = model.archive();
        model.selectArchive(path);
        if (model.archive().equals(before)) zipPreview.showArchive(model.archive());
    }

    /** Opens one Explorer-supplied path after the window and its owner are ready. */
    public void openShellPath(String argument) {
        try {
            Path path = Path.of(argument).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path))
                throw new IllegalArgumentException(t("archive.missingFile") + "\n" + path);
            if (!ArchiveEngine.readableName(path.getFileName().toString()))
                throw new IllegalArgumentException(t("extract.formatsHint") + "\n" + path);
            selectArchive(path);
        } catch (RuntimeException failure) {
            showError(t("archive.openTitle"), failure);
        }
    }

    @FXML
    private void extractAll() {
        if (model.archive() != null)
            ArchiveDialogs.showExtraction(
                    owner(), model.archive(), darkMode, zipPreview.filenameCharset());
    }

    @FXML
    private void newArchive() {
        model.pageProperty().set(MainWindowModel.Page.COMPRESS);
    }

    @FXML
    private void testArchive() {
        if (model.archive() != null) ArchiveDialogs.showTest(owner(), model.archive(), darkMode);
    }

    @FXML
    private void enterArchivePassword() {
        zipPreview.promptPassword();
    }

    @FXML
    private void reloadArchive() {
        zipPreview.reloadCurrentFolder();
    }

    @FXML
    private void changeEncoding(javafx.event.ActionEvent event) {
        var item = (RadioMenuItem) event.getSource();
        zipPreview.setFilenameEncoding(item.getUserData().toString());
    }

    @FXML
    private void addArchiveFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(t("zip.addTitle"));
        var files = chooser.showOpenMultipleDialog(owner());
        if (files != null) confirmAdd(files.stream().map(File::toPath).toList());
    }

    @FXML
    private void addArchiveFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(t("zip.addFolderTitle"));
        File folder = chooser.showDialog(owner());
        if (folder != null) confirmAdd(List.of(folder.toPath()));
    }

    private void confirmAdd(List<Path> sources) {
        Alert prompt =
                new Alert(
                        Alert.AlertType.CONFIRMATION,
                        t("zip.addNoticePrefix")
                                + zipPreview.currentPath()
                                + t("zip.addNoticeSuffix"),
                        ButtonType.OK,
                        ButtonType.CANCEL);
        prompt.initOwner(owner());
        prompt.setHeaderText(
                t("zip.addQuestionPrefix") + sources.size() + t("zip.addQuestionSuffix"));
        if (WindowChrome.showAndWait(prompt).filter(ButtonType.OK::equals).isPresent())
            editZip(sources, List.of());
    }

    @FXML
    private void deleteArchiveEntries() {
        var selected = zipPreview.selectedItems();
        if (selected.isEmpty()) return;
        Alert prompt =
                new Alert(
                        Alert.AlertType.CONFIRMATION,
                        t("zip.deleteNotice"),
                        ButtonType.OK,
                        ButtonType.CANCEL);
        prompt.initOwner(owner());
        prompt.setHeaderText(
                t("zip.deleteQuestionPrefix") + selected.size() + t("zip.deleteQuestionSuffix"));
        if (WindowChrome.showAndWait(prompt).filter(ButtonType.OK::equals).isPresent())
            editZip(List.of(), selected);
    }

    private void editZip(List<Path> additions, List<ArchiveEngine.ArchiveItem> removals) {
        Path archive = model.archive();
        String prefix = zipPreview.currentPath();
        var charset = zipPreview.filenameCharset();
        editing.set(true);
        javafx.concurrent.Task<Path> task =
                new javafx.concurrent.Task<>() {
                    @Override
                    protected Path call() throws Exception {
                        updateMessage(t("zip.writing"));
                        cn.shed4329.onezip.engine.ZipEditor.update(
                                archive, additions, prefix, removals, charset, this::isCancelled);
                        return archive;
                    }
                };
        task.addEventHandler(
                javafx.concurrent.WorkerStateEvent.WORKER_STATE_SUCCEEDED,
                e -> {
                    editing.set(false);
                    if (archive.equals(model.archive())) zipPreview.reloadCurrentFolder();
                });
        task.addEventHandler(
                javafx.concurrent.WorkerStateEvent.WORKER_STATE_FAILED, e -> editing.set(false));
        task.addEventHandler(
                javafx.concurrent.WorkerStateEvent.WORKER_STATE_CANCELLED, e -> editing.set(false));
        ArchiveDialogs.showProgress(owner(), task, t("zip.edit"), darkMode);
    }

    @FXML
    private void editComment() {
        if (comment.isDisabled()) return;
        try {
            Dialog<String> dialog = new Dialog<>();
            dialog.initOwner(owner());
            dialog.setTitle(t("zip.descriptionTitle"));
            dialog.setHeaderText(t("zip.editDescription"));
            TextArea editor = new TextArea(ZipComments.read(model.archive()));
            editor.setId("zip-comment-editor");
            editor.setWrapText(true);
            editor.setPrefRowCount(8);
            editor.setMinHeight(160);
            editor.setPrefWidth(520);
            Label validation = new Label();
            validation.setWrapText(true);
            dialog.getDialogPane().setContent(new VBox(8, editor, validation));
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            dialog.getDialogPane()
                    .lookupButton(ButtonType.OK)
                    .addEventFilter(
                            javafx.event.ActionEvent.ACTION,
                            e -> {
                                if (editor.getText()
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                                .length
                                        > 65535) {
                                    validation.setText(t("validation.descriptionLimit"));
                                    e.consume();
                                }
                            });
            dialog.setResultConverter(button -> button == ButtonType.OK ? editor.getText() : null);
            var result = WindowChrome.showAndWait(dialog);
            if (result.isPresent()) {
                ZipComments.write(model.archive(), result.get());
                zipPreview.reloadCurrentFolder();
            }
        } catch (Exception e) {
            showError(t("zip.descriptionError"), e);
        }
    }

    @FXML
    private void openSettings() {
        showSettings(false);
    }

    @FXML
    private void openAbout() {
        showSettings(true);
    }

    private void showSettings(boolean about) {
        try {
            if (settingsStage == null || !settingsStage.isShowing()) {
                MainApplication.settingTimes = 1;
                settingsStage = new Stage();
                settingsStage.initOwner(owner());
                new SettingsWindow().start(settingsStage);
            }
            if (about) {
                ((Button) settingsStage.getScene().lookup("#settings-about")).fire();
                settingsStage.setTitle(t("about.window"));
            } else if (settingsStage.getTitle().equals(t("about.window"))) {
                ((Button) settingsStage.getScene().lookup("#settings-general")).fire();
                settingsStage.setTitle(t("settings.title"));
            }
            settingsStage.setIconified(false);
            settingsStage.show();
            settingsStage.toFront();
            settingsStage.requestFocus();
            javafx.application.Platform.runLater(
                    () -> {
                        if (settingsStage.isShowing()) {
                            settingsStage.toFront();
                            settingsStage.requestFocus();
                        }
                    });
        } catch (Exception e) {
            MainApplication.setSettingTime(0);
            showError(t("settings.openError"), e);
        }
    }

    @FXML
    private void homeDrop(DragEvent event) {
        var files = event.getDragboard().getFiles();
        if (files.size() == 1
                && files.getFirst().isFile()
                && ArchiveEngine.readableName(files.getFirst().getName()))
            selectArchive(files.getFirst().toPath());
        else if (!files.isEmpty()) {
            model.addSources(files.stream().map(File::toPath).toList());
            newArchive();
        }
        event.setDropCompleted(!files.isEmpty());
        event.consume();
    }

    private void showError(String title, Exception error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner());
        alert.setHeaderText(title);
        alert.setContentText(error.getMessage());
        WindowChrome.showAndWait(alert);
    }

    @FXML
    private void sourceDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) event.acceptTransferModes(TransferMode.COPY);
        event.consume();
    }

    @FXML
    private void sourceDrop(DragEvent event) {
        List<File> files = event.getDragboard().getFiles();
        model.addSources(files.stream().map(File::toPath).toList());
        event.setDropCompleted(!files.isEmpty());
        event.consume();
    }

    private boolean readableDrop(DragEvent event) {
        var files = event.getDragboard().getFiles();
        return files.size() == 1
                && files.getFirst().isFile()
                && ArchiveEngine.readableName(files.getFirst().getName());
    }

    @FXML
    private void archiveDragOver(DragEvent event) {
        if (readableDrop(event)) event.acceptTransferModes(TransferMode.COPY);
        event.consume();
    }

    @FXML
    private void archiveDrop(DragEvent event) {
        boolean accepted = readableDrop(event);
        if (accepted) selectArchive(event.getDragboard().getFiles().getFirst().toPath());
        event.setDropCompleted(accepted);
        event.consume();
    }

    @Override
    public void close() {
        zipPreview.close();
        sourceList.close();
    }
}
