package cn.shed4329.onezip.setting;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/**
 * Single-file UTF-8 user settings with lazy legacy migration and atomic replacement.
 *
 * <p>Access is serialized within an instance. Updates preserve unrelated keys and replace the
 * in-memory snapshot only after the replacement file has been written successfully. This class does
 * not coordinate concurrent application processes; the last completed writer wins. Existing
 * unreadable files are preserved and cannot be overwritten through this instance; repair them and
 * restart. Output uses stable key ordering with no changing timestamp.
 */
public final class SettingsStore {
    private final Path file, legacy;
    private Properties values;
    private String warning = "";
    private boolean unreadableExistingFile;

    /**
     * Creates an independent store; no filesystem access occurs until a value is requested.
     *
     * @param directory directory containing {@code settings.properties}
     * @param legacyDirectory read-only source for the two former text preference files
     */
    public SettingsStore(Path directory, Path legacyDirectory) {
        file = directory.toAbsolutePath().resolve("settings.properties");
        legacy = legacyDirectory;
    }

    private static final class Shared {
        static final SettingsStore INSTANCE =
                new SettingsStore(
                        defaultDirectory(),
                        Path.of(
                                System.getProperty(
                                        "onezip.settings.legacy-dir",
                                        Path.of(System.getProperty("java.io.tmpdir"), "OneZip")
                                                .toString())));
    }

    /** Returns the process-wide store under the current user's local application-data directory. */
    public static SettingsStore shared() {
        return Shared.INSTANCE;
    }

    private static Path defaultDirectory() {
        String override = System.getProperty("onezip.settings.dir");
        if (override != null && !override.isBlank()) return Path.of(override);
        String local = System.getenv("LOCALAPPDATA");
        return (local == null || local.isBlank()
                        ? Path.of(System.getProperty("user.home"), "AppData", "Local")
                        : Path.of(local))
                .resolve("OneZip");
    }

    /** Returns the absolute settings file location, including a configured test override. */
    public Path path() {
        return file;
    }

    /**
     * Returns the latest load/migration warning, or an empty string when no warning was recorded.
     */
    public synchronized String warning() {
        load();
        return warning;
    }

    /** Reads a string from the cached snapshot, returning the supplied fallback if absent. */
    public synchronized String get(String key, String fallback) {
        load();
        return values.getProperty(key, fallback);
    }

    /** Reads a boolean; missing or unrecognized values are false. */
    public synchronized boolean flag(String key) {
        return Boolean.parseBoolean(get(key, "false"));
    }

    /** Persists one value while preserving the other keys. */
    public synchronized void put(String key, String value) throws IOException {
        putAll(Map.of(key, value));
    }

    /** Persists a set of related values in one replacement operation. */
    public synchronized void putAll(Map<String, String> changes) throws IOException {
        load();
        if (unreadableExistingFile) {
            throw new IOException(
                    "Existing settings could not be read; repair the file and restart: " + file);
        }
        Properties next = new Properties();
        next.putAll(values);
        changes.forEach(
                (key, value) ->
                        next.setProperty(
                                Objects.requireNonNull(key), Objects.requireNonNull(value)));
        write(next);
        values = next;
        warning = "";
    }

    private void load() {
        if (values != null) return;
        values = new Properties();
        // Unknown accessibility is not proof of absence: never migrate over an unreadable file.
        boolean existing = !Files.notExists(file, LinkOption.NOFOLLOW_LINKS);
        try {
            if (existing) {
                Properties loaded = new Properties();
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    loaded.load(reader);
                }
                values = loaded;
            } else {
                boolean found =
                        migrate(
                                "FX_GUISetting.txt",
                                new String[] {
                                    "cursor.path",
                                    "legacy.bootMode",
                                    "legacy.bootTheme",
                                    "appearance.dark"
                                });
                found |=
                        migrate(
                                "NormalSetting.txt",
                                new String[] {"preview.disabled", "comment.charset"});
                if (found) write(values);
            }
        } catch (IOException | IllegalArgumentException e) {
            unreadableExistingFile = existing;
            warning = "无法读取或迁移设定：" + e.getMessage();
        }
    }

    private boolean migrate(String name, String[] keys) throws IOException {
        Path source = legacy.resolve(name);
        if (!Files.isRegularFile(source)) return false;
        List<String> lines;
        try {
            lines = Files.readAllLines(source, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            lines = Files.readAllLines(source, Charset.defaultCharset());
        }
        for (int i = 0; i < Math.min(keys.length, lines.size()); i++)
            values.setProperty(keys[i], lines.get(i));
        return true;
    }

    private void write(Properties next) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "settings-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write("# OneZip user settings (UTF-8)\n");
                for (String key : next.stringPropertyNames().stream().sorted().toList()) {
                    writer.write(escape(key, true));
                    writer.write('=');
                    writer.write(escape(next.getProperty(key), false));
                    writer.write('\n');
                }
            }
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String escape(String text, boolean key) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else {
                        if ((key && " =:#!".indexOf(c) >= 0) || (i == 0 && c == ' '))
                            out.append('\\');
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
