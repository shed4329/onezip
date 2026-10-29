package cn.shed4329.onezip;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LauncherTest {
    @Test void shellPathsPreserveSpacesUnicodeAndEveryArgument() {
        String[] paths = {"C:\\檔案 空間\\測試.zip", "D:\\other.7z"};
        assertArrayEquals(paths, Launcher.desktopArguments(paths));
        assertNotSame(paths, Launcher.desktopArguments(paths));
    }
    @Test void positionalNamesAlwaysBelongToTheDesktop() {
        assertArrayEquals(new String[]{"c"}, Launcher.desktopArguments(new String[]{"c"}));
    }
    @Test void explicitCommandsStayHeadless() {
        for (String command : new String[]{"a", "c", "compress", "x", "extract", "l", "list", "t", "test", "--help", "--version"})
            assertNull(Launcher.desktopArguments(new String[]{"--cli", command, "archive.zip"}));
    }
    @Test void explicitDesktopSupportsAmbiguousNamesAndNoFile() {
        assertArrayEquals(new String[0], Launcher.desktopArguments(new String[0]));
        assertArrayEquals(new String[]{"c"}, Launcher.desktopArguments(new String[]{"--open", "c"}));
        assertArrayEquals(new String[]{"archive.zip"}, Launcher.desktopArguments(new String[]{"gui", "archive.zip"}));
    }
}
