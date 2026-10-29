package cn.shed4329.onezip.themes.fxJava.fluent.component;

import static cn.shed4329.onezip.i18n.I18n.t;

import cn.shed4329.onezip.application.ArchiveTask;
import cn.shed4329.onezip.engine.ArchiveEngine;
import cn.shed4329.onezip.themes.fxJava.fluent.WindowChrome;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;

import java.io.File;
import java.nio.file.*;
import java.util.List;

/** Compression and extraction dialogs wired to the Java engine, not native services. */
public final class ArchiveDialogs {
    private ArchiveDialogs() {}

    public static void showCompression(Window owner, List<Path> sources, boolean dark) {
        if (sources.isEmpty()) {
            error(owner, t("validation.sourcesEmpty"));
            return;
        }
        Stage dialog = window(owner, t("compress.options"));
        TextField directory = new TextField(System.getProperty("user.home"));
        directory.setEditable(false);
        directory.setId("archive-directory");
        Button browse = new Button(t("action.choose"));
        browse.setOnAction(
                e -> {
                    DirectoryChooser c = new DirectoryChooser();
                    File f = c.showDialog(dialog);
                    if (f != null) directory.setText(f.getPath());
                });
        HBox location = new HBox(8, directory, browse);
        HBox.setHgrow(directory, Priority.ALWAYS);
        TextField name =
                new TextField(
                        sources.size() == 1 ? sources.get(0).getFileName().toString() : "Archive");
        name.setId("archive-name");
        ChoiceBox<String> format =
                new ChoiceBox<>(
                        FXCollections.observableArrayList(
                                "ZIP", "7z", "TAR", "GZIP", "TAR.GZ", "LZ4", "TAR.LZ4", "XZ",
                                "TAR.XZ", "BZIP2", "TAR.BZ2"));
        format.setValue("ZIP");
        format.setId("archive-format");
        ChoiceBox<String> level =
                new ChoiceBox<>(
                        FXCollections.observableArrayList(
                                t("level.normalLegacy"),
                                t("level.fast"),
                                t("level.fastest"),
                                t("level.ultraLegacy"),
                                t("level.storeLegacy")));
        level.getSelectionModel().selectFirst();
        level.setId("archive-level");
        Spinner<Integer> threads = threads();
        CheckBox encrypted = new CheckBox(t("compress.encryption"));
        encrypted.setId("archive-encryption");
        PasswordField password = new PasswordField(), confirm = new PasswordField();
        password.setId("archive-password");
        confirm.setId("archive-password-confirm");
        password.setPromptText(t("password.labelLegacy"));
        confirm.setPromptText(t("password.confirm"));
        HBox passwords = new HBox(8, password, confirm);
        passwords.visibleProperty().bind(encrypted.selectedProperty());
        passwords.managedProperty().bind(encrypted.selectedProperty());
        Label note = new Label("ZIP：DEFLATE　　7z：LZMA2");
        note.setWrapText(true);
        format.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, a, b) -> {
                            encrypted.setSelected(false);
                            encrypted.setDisable(!"ZIP".equals(b));
                            level.setDisable("TAR".equals(b));
                            boolean bzip = "BZIP2".equals(b) || "TAR.BZ2".equals(b);
                            level.setItems(
                                    FXCollections.observableArrayList(
                                            bzip
                                                    ? List.of(
                                                            t("level.normalLegacy"),
                                                            t("level.smallBlock"),
                                                            t("level.smallestBlock"),
                                                            t("level.largestBlock"))
                                                    : List.of(
                                                            t("level.normalLegacy"),
                                                            t("level.fast"),
                                                            t("level.fastest"),
                                                            t("level.ultraLegacy"),
                                                            t("level.storeLegacy"))));
                            level.getSelectionModel().selectFirst();
                            note.setText(
                                    switch (b) {
                                        case "7z" -> t("format.7zHint");
                                        case "TAR" -> t("format.tarHint");
                                        case "GZIP" -> t("format.gzipHint");
                                        case "TAR.GZ" -> t("format.tarGzipHint");
                                        case "LZ4" -> t("format.lz4Hint");
                                        case "TAR.LZ4" -> t("format.tarLz4Hint");
                                        case "XZ" -> t("format.xzHint");
                                        case "TAR.XZ" -> t("format.tarXzHint");
                                        case "BZIP2" -> t("format.bzip2Hint");
                                        case "TAR.BZ2" -> t("format.tarBzip2Hint");
                                        default -> t("format.zipHint");
                                    });
                        });
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.addRow(0, new Label(t("compress.locationLegacy")), location);
        grid.addRow(1, new Label(t("compress.filenameLegacy")), name);
        grid.addRow(2, new Label(t("compress.formatLegacy")), format);
        grid.addRow(3, new Label(t("compress.levelLegacy")), level);
        grid.addRow(
                4,
                new Label(t("threads.labelLegacy")),
                new HBox(8, threads, new Label(t("threads.shortHint"))));
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(new ColumnConstraints(90), fields);
        Label validation = new Label();
        validation.setWrapText(true);
        validation.setStyle("-fx-text-fill: #c43b31;");
        validation.setId("archive-validation");
        Button start = new Button(t("compress.startLegacy")),
                cancel = new Button(t("action.cancel"));
        start.setDefaultButton(true);
        start.setId("archive-start");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> dialog.close());
        HBox buttons = new HBox(10, start, cancel);
        VBox content =
                new VBox(
                        16,
                        new Label(
                                t("task.compressPrefixLegacy")
                                        + sources.size()
                                        + t("compress.selectedSuffix")),
                        grid,
                        encrypted,
                        passwords,
                        note,
                        validation,
                        buttons);
        content.setPadding(new Insets(24));
        Scene scene = new Scene(content, 590, 490);
        cn.shed4329.onezip.themes.fxJava.fluent.ThemeManager.register(
                scene, new JMetro(dark ? Style.DARK : Style.LIGHT));
        dialog.setScene(scene);
        start.setOnAction(
                e -> {
                    try {
                        String filename = name.getText().trim();
                        if (filename.isEmpty()
                                || filename.equals(".")
                                || filename.equals("..")
                                || filename.matches(".*[\\\\/:*?\"<>|].*"))
                            throw new IllegalArgumentException(t("validation.filenameLegacy"));
                        if (encrypted.isSelected()
                                && (password.getText().isEmpty()
                                        || !password.getText().equals(confirm.getText())))
                            throw new IllegalArgumentException(t("validation.passwordMismatch"));
                        String suffix =
                                switch (format.getValue()) {
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
                        ArchiveEngine.Format selectedFormat =
                                ArchiveEngine.outputFormat("archive" + suffix);
                        if (ArchiveEngine.singleStreamFormat(selectedFormat)
                                && (sources.size() != 1
                                        || !Files.isRegularFile(
                                                sources.getFirst(), LinkOption.NOFOLLOW_LINKS)))
                            throw new IllegalArgumentException(t("validation.singleFileLegacy"));
                        if (!filename.toLowerCase(java.util.Locale.ROOT).endsWith(suffix))
                            filename += suffix;
                        Path output = Path.of(directory.getText()).resolve(filename);
                        if (Files.exists(output))
                            throw new IllegalArgumentException(t("validation.existsLegacy"));
                        int selected =
                                new int[] {5, 3, 1, 9, 0}
                                        [level.getSelectionModel().getSelectedIndex()];
                        ArchiveTask task =
                                ArchiveTask.compress(
                                        sources,
                                        output,
                                        selectedFormat,
                                        selected,
                                        encrypted.isSelected()
                                                ? password.getText().toCharArray()
                                                : null,
                                        threads.getValue());
                        password.clear();
                        confirm.clear();
                        dialog.close();
                        showProgress(owner, task, t("task.compressPrefixLegacy") + filename, dark);
                    } catch (Exception ex) {
                        validation.setText(ex.getMessage());
                    }
                });
        dialog.show();
    }

    public static void showExtraction(Window owner, Path archive, boolean dark) {
        showExtraction(owner, archive, dark, java.nio.charset.Charset.forName("IBM437"));
    }

    public static void showExtraction(
            Window owner, Path archive, boolean dark, java.nio.charset.Charset charset) {
        String lower = archive.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (!ArchiveEngine.readableName(lower)) {
            error(owner, t("extract.formatsHint"));
            return;
        }
        Dialog<ButtonType> prompt = new Dialog<>();
        prompt.initOwner(owner);
        prompt.setTitle(t("extract.title"));
        prompt.setHeaderText(archive.getFileName().toString());
        String base = cn.shed4329.onezip.engine.ExtractionLayout.folderName(archive);
        TextField destination =
                new TextField(
                        archive.toAbsolutePath()
                                .getParent()
                                .resolve(base.isBlank() ? t("extract.title") : base)
                                .toString());
        destination.setId("extract-destination");
        CheckBox smart = new CheckBox(t("extraction.smart"));
        smart.setId("extract-smart");
        smart.setSelected(cn.shed4329.onezip.setting.ExtractionPreferences.smart());
        String direct = archive.toAbsolutePath().getParent().toString(),
                nested = destination.getText();
        if (smart.isSelected()) destination.setText(direct);
        smart.selectedProperty()
                .addListener(
                        (o, a, b) -> {
                            if (destination.getText().equals(a ? direct : nested))
                                destination.setText(b ? direct : nested);
                        });
        Tooltip smartHint =
                new Tooltip(t("extraction.smartHint") + "\n" + t("extraction.defaultHint"));
        smartHint.setWrapText(true);
        smartHint.setMaxWidth(420);
        smart.setTooltip(smartHint);
        destination.setTooltip(new Tooltip(t("extract.noOverwrite")));
        Button browse = new Button(t("action.browseEllipsis"));
        browse.setOnAction(
                e -> {
                    DirectoryChooser chooser = new DirectoryChooser();
                    chooser.setTitle(t("extract.chooseDirectory"));
                    File selected = chooser.showDialog(owner);
                    if (selected != null) destination.setText(selected.getAbsolutePath());
                });
        PasswordField password = new PasswordField();
        password.setId("extract-password");
        password.setPromptText(t("password.optional"));
        Spinner<Integer> threads = threads();
        Label validation = new Label();
        validation.setId("extract-validation");
        validation.setWrapText(true);
        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(12);
        fields.addRow(0, new Label(t("extract.destination")), destination, browse);
        fields.addRow(1, new Label(t("password.label")), password);
        fields.add(new Label(t("threads.label")), 0, 2);
        threads.setMaxWidth(120);
        threads.setTooltip(new Tooltip(t("threads.shortHint")));
        fields.add(threads, 1, 2, 2, 1);
        GridPane.setHgrow(destination, Priority.ALWAYS);
        GridPane.setHgrow(password, Priority.ALWAYS);
        prompt.getDialogPane().setContent(new VBox(14, fields, smart, validation));
        prompt.getDialogPane().setPrefWidth(620);
        ButtonType start = new ButtonType(t("extract.start"), ButtonBar.ButtonData.OK_DONE);
        prompt.getDialogPane().getButtonTypes().setAll(start, ButtonType.CANCEL);
        Button launch = (Button) prompt.getDialogPane().lookupButton(start);
        launch.setId("extract-start");
        launch.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                e -> {
                    e.consume();
                    try {
                        if (destination.getText().isBlank())
                            throw new IllegalArgumentException(t("validation.extractDirectory"));
                        Path target = Path.of(destination.getText()).toAbsolutePath().normalize();
                        if (Files.isRegularFile(target))
                            throw new IllegalArgumentException(t("validation.extractIsFile"));
                        var task =
                                smart.isSelected()
                                        ? ArchiveTask.extractSmart(
                                                archive,
                                                target,
                                                password.getText().toCharArray(),
                                                threads.getValue(),
                                                charset)
                                        : ArchiveTask.extract(
                                                archive,
                                                target,
                                                password.getText().toCharArray(),
                                                threads.getValue(),
                                                charset);
                        password.clear();
                        prompt.close();
                        showProgress(
                                owner, task, t("task.extractPrefix") + archive.getFileName(), dark);
                    } catch (Exception failure) {
                        validation.setText(failure.getMessage());
                    }
                });
        prompt.setOnHidden(e -> password.clear());
        WindowChrome.show(prompt);
        cn.shed4329.onezip.themes.fxJava.fluent.ThemeManager.register(
                prompt.getDialogPane().getScene(), new JMetro(dark ? Style.DARK : Style.LIGHT));
        prompt.getDialogPane().getStyleClass().add("extraction-dialog");
        prompt.getDialogPane()
                .getScene()
                .getStylesheets()
                .add(ArchiveDialogs.class.getResource("archive-dialog.css").toExternalForm());
    }

    public static void showTest(Window owner, Path archive, boolean dark) {
        Dialog<ButtonType> prompt = new Dialog<>();
        prompt.initOwner(owner);
        prompt.setTitle(t("test.title"));
        prompt.setHeaderText(t("test.hint"));
        PasswordField password = new PasswordField();
        password.setId("archive-test-password");
        prompt.getDialogPane().setContent(password);
        prompt.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        WindowChrome.showAndWait(prompt)
                .filter(ButtonType.OK::equals)
                .ifPresent(
                        b ->
                                showProgress(
                                        owner,
                                        ArchiveTask.test(archive, password.getText().toCharArray()),
                                        t("task.testPrefix") + archive.getFileName(),
                                        dark));
        password.clear();
    }

    private static Spinner<Integer> threads() {
        Spinner<Integer> value = new Spinner<>(0, 64, 0);
        value.setId("archive-threads");
        value.setPrefWidth(105);
        return value;
    }

    public static Stage showProgress(
            Window owner, javafx.concurrent.Task<Path> task, String title, boolean dark) {
        var workerFinished = new javafx.beans.property.SimpleBooleanProperty(false);
        Stage dialog = window(owner, title);
        dialog.setTitle(title);
        Label state = new Label(t("progress.preparing"));
        state.setId("archive-status");
        state.getStyleClass().add("progress-heading");
        Label archiveName =
                new Label(
                        task instanceof ArchiveTask a
                                ? a.archivePath().getFileName().toString()
                                : title);
        archiveName.setId("archive-output-name");
        archiveName.getStyleClass().add("progress-archive");
        archiveName.setMaxWidth(Double.MAX_VALUE);
        archiveName.setTooltip(
                new Tooltip(task instanceof ArchiveTask a ? a.archivePath().toString() : title));
        Label current = new Label();
        current.textProperty().bind(task.messageProperty());
        current.setWrapText(true);
        current.setMinWidth(0);
        current.setMaxWidth(Double.MAX_VALUE);
        current.setId("archive-current-file");
        ProgressBar bar = new ProgressBar();
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.progressProperty().bind(task.progressProperty());
        bar.setId("archive-progress");
        Label percentage = new Label(t("progress.calculating"));
        percentage.setId("archive-percentage");
        Label speed = new Label(task instanceof ArchiveTask ? "0 kB/s" : "—");
        speed.setId("archive-speed");
        speed.setTooltip(new Tooltip(t("progress.speedHint")));
        Label remaining = new Label(t("progress.etaUnknown"));
        remaining.setId("archive-remaining");
        HBox progressRow = new HBox(18, bar, percentage);
        progressRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(bar, Priority.ALWAYS);
        percentage.setMinWidth(65);
        HBox metrics = new HBox(18, speed, new Region(), remaining);
        HBox.setHgrow(metrics.getChildren().get(1), Priority.ALWAYS);
        Label elapsed = new Label(t("progress.elapsedZero"));
        elapsed.setId("archive-elapsed");
        Label processed = new Label(t("progress.processedZero"));
        processed.setId("archive-processed");
        Label filePosition = new Label(t("progress.scanning"));
        filePosition.setId("archive-file-position");
        HBox details = new HBox(18, filePosition, new Region(), processed);
        HBox.setHgrow(details.getChildren().get(1), Priority.ALWAYS);
        long[] started = {System.nanoTime()};
        long[] sample = {started[0], 0};
        Runnable refresh =
                () -> {
                    long seconds = (System.nanoTime() - started[0]) / 1_000_000_000L;
                    elapsed.setText(
                            String.format(
                                    java.util.Locale.ROOT,
                                    t("progress.elapsed"),
                                    seconds / 3600,
                                    seconds / 60 % 60,
                                    seconds % 60));
                    double value = task.getProgress();
                    String text =
                            value < 0
                                    ? t("progress.calculating")
                                    : String.format(
                                            java.util.Locale.ROOT,
                                            "%.1f%%",
                                            Math.min(100, value * 100));
                    percentage.setText(text);
                    dialog.setTitle(value < 0 ? title : text + " · " + title);
                    if (task instanceof ArchiveTask archiveTask) {
                        long now = System.nanoTime(), bytes = archiveTask.processedBytes();
                        processed.setText(
                                String.format(
                                        java.util.Locale.ROOT,
                                        t("progress.processed"),
                                        bytes / 1_000_000.0,
                                        archiveTask.totalBytes() / 1_000_000.0));
                        filePosition.setText(
                                t("progress.filePrefix")
                                        + archiveTask.fileIndex()
                                        + " / "
                                        + archiveTask.fileCount());
                        if (now - sample[0] >= 750_000_000L) {
                            double rate =
                                    Math.max(0, bytes - sample[1])
                                            * 1_000_000_000.0
                                            / (now - sample[0]);
                            speed.setText(speed(rate));
                            sample[0] = now;
                            sample[1] = bytes;
                            long left = Math.max(0, archiveTask.totalBytes() - bytes);
                            remaining.setText(
                                    value >= .99
                                            ? t("progress.finishing")
                                            : rate > 0 && value > 0
                                                    ? cn.shed4329.onezip.i18n.I18n.format(
                                                            "progress.eta", duration(left / rate))
                                                    : t("progress.etaUnknown"));
                        }
                    }
                };
        javafx.animation.Timeline timer =
                new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(
                                javafx.util.Duration.millis(250), e -> refresh.run()));
        timer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        Runnable stop =
                () -> {
                    timer.stop();
                    refresh.run();
                    bar.progressProperty().unbind();
                    bar.setProgress(Math.max(0, task.getProgress()));
                    bar.getStyleClass().add("stopped");
                    speed.setText("—");
                    remaining.setText("—");
                };
        Button action = new Button(t("action.cancel"));
        action.setId("archive-cancel");
        Runnable cancel =
                () -> {
                    if (!task.isDone()) {
                        state.setText(t("progress.cancelling"));
                        action.setDisable(true);
                        stop.run();
                        task.cancel();
                    }
                };
        action.setOnAction(
                e -> {
                    if (!task.isDone()) cancel.run();
                    else dialog.close();
                });
        ToggleButton openFolder = new ToggleButton(t("progress.revealShort"));
        openFolder.setTooltip(new Tooltip(t("progress.reveal")));
        openFolder.setMinWidth(Region.USE_PREF_SIZE);
        action.setMinWidth(Region.USE_PREF_SIZE);
        openFolder.setId("archive-open-folder");
        openFolder.setGraphic(FileIcons.view("folder", true));
        java.util.function.Supplier<Path> outputTarget =
                () -> task instanceof ArchiveTask a ? a.revealTarget() : null;
        Path target = outputTarget.get();
        openFolder.setDisable(target == null);
        openFolder.setOnAction(
                e -> {
                    if (openFolder.isSelected()
                            && task.getState() == javafx.concurrent.Worker.State.SUCCEEDED)
                        openDirectory(dialog, outputTarget.get());
                });
        HBox actions = new HBox(16, openFolder, new Region(), action);
        actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(actions.getChildren().get(1), Priority.ALWAYS);
        actions.getStyleClass().add("progress-actions");
        VBox box =
                new VBox(
                        14,
                        state,
                        archiveName,
                        progressRow,
                        current,
                        details,
                        metrics,
                        elapsed,
                        new Region(),
                        actions);
        VBox.setVgrow(box.getChildren().get(7), Priority.ALWAYS);
        box.getStyleClass().add("progress-dialog");
        box.setPadding(new Insets(28));
        Scene scene = new Scene(box, 760, 410);
        dialog.setMinWidth(680);
        dialog.setMinHeight(410);
        cn.shed4329.onezip.themes.fxJava.fluent.ThemeManager.register(
                scene, new JMetro(dark ? Style.DARK : Style.LIGHT));
        scene.getStylesheets()
                .add(ArchiveDialogs.class.getResource("progress-dialog.css").toExternalForm());
        dialog.setScene(scene);
        task.setOnRunning(
                e -> {
                    started[0] = System.nanoTime();
                    sample[0] = started[0];
                    state.setText(
                            task instanceof ArchiveTask a
                                    ? a.operationLabel() + "…"
                                    : t("progress.processing"));
                    timer.play();
                });
        task.setOnSucceeded(
                e -> {
                    timer.stop();
                    refresh.run();
                    bar.getStyleClass().add("completed");
                    percentage.setText("100.0%");
                    dialog.setTitle("100.0% · " + title);
                    bar.progressProperty().unbind();
                    bar.setProgress(1);
                    if (task instanceof ArchiveTask archiveTask)
                        speed.setText(
                                t("progress.averagePrefix")
                                        + speed(
                                                archiveTask.processedBytes()
                                                        * 1_000_000_000.0
                                                        / Math.max(
                                                                1,
                                                                System.nanoTime() - started[0])));
                    current.textProperty().unbind();
                    current.setText(task.getValue().toString());
                    remaining.setText(t("progress.completed"));
                    state.setText(t("progress.completed"));
                    action.setText(t("action.closeLegacy"));
                    action.setDisable(false);
                    if (openFolder.isSelected()) openDirectory(dialog, outputTarget.get());
                });
        task.setOnFailed(
                e -> {
                    stop.run();
                    openFolder.setDisable(true);
                    current.textProperty().unbind();
                    current.setText(task.getException().getMessage());
                    state.setText(t("progress.failed"));
                    dialog.setTitle(t("progress.failedPrefix") + title);
                    action.setText(t("action.closeLegacy"));
                    action.setDisable(false);
                });
        task.setOnCancelled(
                e -> {
                    stop.run();
                    openFolder.setDisable(true);
                    current.textProperty().unbind();
                    current.setText(t("progress.cancelNotice"));
                    state.setText(
                            t(workerFinished.get() ? "progress.cancelled" : "progress.cancelling"));
                    dialog.setTitle(t("progress.cancelledPrefix") + title);
                    action.setText(t("action.closeLegacy"));
                    action.setDisable(!workerFinished.get());
                });
        workerFinished.addListener(
                (o, old, finished) -> {
                    if (finished && task.isCancelled()) {
                        state.setText(t("progress.cancelled"));
                        action.setText(t("action.closeLegacy"));
                        action.setDisable(false);
                    }
                });
        dialog.setOnCloseRequest(
                e -> {
                    if (!workerFinished.get()) {
                        cancel.run();
                        e.consume();
                    }
                });
        dialog.addEventHandler(
                WindowEvent.WINDOW_HIDDEN,
                e -> {
                    timer.stop();
                    if (!workerFinished.get()) task.cancel();
                });
        dialog.show();
        Thread worker =
                new Thread(
                        () -> {
                            try {
                                task.run();
                            } finally {
                                javafx.application.Platform.runLater(
                                        () -> workerFinished.set(true));
                            }
                        },
                        "onezip-archive");
        // A cancelled JavaFX Task is 'done' before its call()/rollback has returned.
        // Keep the JVM alive and the progress window open until that cleanup finishes.
        worker.setDaemon(false);
        worker.start();
        return dialog;
    }

    private static String duration(double seconds) {
        long value = (long) Math.ceil(Math.min(seconds, Long.MAX_VALUE));
        if (value < 60) return value + t("time.seconds");
        if (value < 3600)
            return value / 60 + t("time.minutesBetween") + value % 60 + t("time.seconds");
        return value / 3600 + t("time.hours") + value % 3600 / 60 + t("time.minutes");
    }

    private static void openDirectory(Window owner, Path directory) {
        if (directory == null) return;
        Thread opener =
                new Thread(
                        () -> {
                            try {
                                FileManager.reveal(directory);
                            } catch (Exception e) {
                                javafx.application.Platform.runLater(
                                        () ->
                                                error(
                                                        owner,
                                                        t("reveal.errorPrefix") + e.getMessage()));
                            }
                        },
                        "onezip-open-folder");
        opener.setDaemon(true);
        opener.start();
    }

    private static String speed(double bytesPerSecond) {
        return String.format(
                java.util.Locale.ROOT,
                "%.1f %s",
                bytesPerSecond >= 1_000_000 ? bytesPerSecond / 1_000_000 : bytesPerSecond / 1000,
                bytesPerSecond >= 1_000_000 ? "MB/s" : "kB/s");
    }

    private static Stage window(Window owner, String title) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        stage.setTitle(title);
        return stage;
    }

    private static void error(Window owner, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        if (owner != null) alert.initOwner(owner);
        WindowChrome.showAndWait(alert);
    }
}
