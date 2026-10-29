package cn.shed4329.onezip.setting;

import java.io.IOException;

/** Typed appearance values shared by windows; storage remains in the single SettingsStore. */
public final class AppearancePreferences {
    private AppearancePreferences() {}

    /** Returns the background-only Gaussian blur radius in logical pixels, between 0 and 63. */
    public static int blurRadius() {
        return parseBlurRadius(
                SettingsStore.shared().get("appearance.background.blurRadius", "24"));
    }

    static int parseBlurRadius(String value) {
        try {
            return Math.clamp(Integer.parseInt(value.strip()), 0, 63);
        } catch (NumberFormatException invalid) {
            return 24;
        }
    }

    /** Saves a bounded radius; the independent blur switch retains its current state. */
    public static void setBlurRadius(int radius) throws IOException {
        SettingsStore.shared()
                .put(
                        "appearance.background.blurRadius",
                        Integer.toString(Math.clamp(radius, 0, 63)));
    }

    /** Returns the persisted dark-theme preference; defaults to the light theme. */
    public static boolean dark() {
        return SettingsStore.shared().flag("appearance.dark");
    }

    /** Saves the preference; the UI applies it only after this write succeeds. */
    public static void setDark(boolean dark) throws IOException {
        SettingsStore.shared().put("appearance.dark", Boolean.toString(dark));
    }

    /** Returns the custom cursor path, or an empty string for the system cursor. */
    public static String cursorPath() {
        return SettingsStore.shared().get("cursor.path", "");
    }

    /** Saves a cursor path; an empty string selects the system cursor. */
    public static void setCursorPath(String path) throws IOException {
        SettingsStore.shared().put("cursor.path", path);
    }
}
