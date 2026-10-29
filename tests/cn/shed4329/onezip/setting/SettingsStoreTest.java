package cn.shed4329.onezip.setting;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;

class SettingsStoreTest {
    @Test
    void corruptedExistingFileCannotBeSilentlyOverwritten() throws Exception {
        var current = store();
        Files.createDirectories(current.path().getParent());
        String broken = "appearance.dark=\\uZZZZ\n";
        Files.writeString(current.path(), broken);
        assertFalse(current.flag("appearance.dark"));
        assertThrows(IOException.class, () -> current.put("appearance.dark", "true"));
        assertEquals(broken, Files.readString(current.path()));
    }

    @Test
    void deterministicUtf8FilePreservesPropertyEscapes() throws Exception {
        var current = store();
        String special = "  文本\\路径\n\t\r\f\u0001=:#!";
        current.put("z", special);
        current.put("a =:#!", "value");
        String first = Files.readString(current.path());
        current.put("z", special);
        assertEquals(first, Files.readString(current.path()));
        assertTrue(first.indexOf("a\\ ") < first.indexOf("z="));
        assertEquals(special, store().get("z", ""));
        assertEquals("value", store().get("a =:#!", ""));
    }

    @TempDir Path root;

    private SettingsStore store() {
        return new SettingsStore(root.resolve("local/OneZip"), root.resolve("legacy"));
    }

    @Test
    void disabledBackgroundKeepsPathAndBlurForReenable() throws Exception {
        var saved = store();
        saved.putAll(
                java.util.Map.of(
                        "appearance.background.path",
                        "test-background.jpg",
                        "appearance.background.blur",
                        "true",
                        "appearance.background.enabled",
                        "false"));
        var reloaded = store();
        assertFalse(reloaded.flag("appearance.background.enabled"));
        assertTrue(reloaded.flag("appearance.background.blur"));
        assertEquals(
                "test-background.jpg", reloaded.get("appearance.background.path", ""));
        reloaded.put("appearance.background.enabled", "true");
        assertTrue(store().flag("appearance.background.enabled"));
        assertTrue(store().flag("appearance.background.blur"));
        assertEquals(
                reloaded.get("appearance.background.path", ""),
                store().get("appearance.background.path", ""));
    }

    @Test
    void savesBackgroundPreferencesTogether() throws Exception {
        var current = store();
        current.putAll(
                java.util.Map.of(
                        "appearance.background.path",
                        "D:\\图库\\背景.jpg",
                        "appearance.background.blur",
                        "true"));
        var reloaded = store();
        assertEquals("D:\\图库\\背景.jpg", reloaded.get("appearance.background.path", ""));
        assertTrue(reloaded.flag("appearance.background.blur"));
        var invalid = new java.util.HashMap<String, String>();
        invalid.put("appearance.background.path", "changed");
        invalid.put("appearance.background.blur", null);
        assertThrows(NullPointerException.class, () -> current.putAll(invalid));
        assertEquals("D:\\图库\\背景.jpg", store().get("appearance.background.path", ""));
    }

    @Test
    void savesUtf8AndKeepsOtherKeysAcrossReloads() throws Exception {
        var store = store();
        assertEquals("", store.get("cursor.path", ""));
        store.put("cursor.path", "C:\\图片 #1\\光标.png");
        store.put("appearance.dark", "true");
        var reloaded = store();
        assertEquals("C:\\图片 #1\\光标.png", reloaded.get("cursor.path", ""));
        assertTrue(reloaded.flag("appearance.dark"));
        reloaded.put("appearance.dark", "false");
        assertFalse(store().flag("appearance.dark"));
        assertEquals("C:\\图片 #1\\光标.png", store().get("cursor.path", ""));
        try (var files = Files.list(store.path().getParent())) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void migratesBothLegacyFilesOnceWithoutChangingThem() throws Exception {
        Path legacy = Files.createDirectories(root.resolve("legacy"));
        String fx = "C:\\光标.png\njavafx\nfluent\ntrue", normal = "true\nGBK";
        Files.writeString(legacy.resolve("FX_GUISetting.txt"), fx);
        Files.writeString(legacy.resolve("NormalSetting.txt"), normal);
        var store = store();
        assertTrue(store.flag("appearance.dark"));
        assertTrue(store.flag("preview.disabled"));
        assertEquals("GBK", store.get("comment.charset", "UTF-8"));
        assertEquals(fx, Files.readString(legacy.resolve("FX_GUISetting.txt")));
        assertEquals(normal, Files.readString(legacy.resolve("NormalSetting.txt")));
        store.put("appearance.dark", "false");
        assertFalse(store().flag("appearance.dark"));
    }

    @Test
    void partialAndMissingLegacyFilesHaveDefaults() throws Exception {
        Files.createDirectories(root.resolve("legacy"));
        Files.writeString(root.resolve("legacy/FX_GUISetting.txt"), "\njavafx");
        assertFalse(store().flag("appearance.dark"));
        assertEquals("UTF-8", store().get("comment.charset", "UTF-8"));
    }

    @Test
    void malformedConfigDoesNotCrashOrOverwriteItOnRead() throws Exception {
        var store = store();
        Files.createDirectories(store.path().getParent());
        String broken = "bad=\\uQQQQ";
        Files.writeString(store.path(), broken);
        assertFalse(store.flag("appearance.dark"));
        assertFalse(store.warning().isBlank());
        assertEquals(broken, Files.readString(store.path()));
    }

    @Test
    void failedSaveLeavesMemoryAndPreviousFileIntact() throws Exception {
        var store = store();
        store.put("appearance.dark", "false");
        Path original = store.path(), saved = original.resolveSibling("saved.properties");
        Files.move(original, saved);
        Files.createDirectory(original);
        Files.writeString(original.resolve("block"), "x");
        assertThrows(IOException.class, () -> store.put("appearance.dark", "true"));
        assertFalse(store.flag("appearance.dark"));
        assertTrue(Files.readString(saved).contains("appearance.dark=false"));
        try (var files = Files.list(original.getParent())) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }
}
