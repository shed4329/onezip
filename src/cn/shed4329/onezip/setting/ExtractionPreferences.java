package cn.shed4329.onezip.setting;

import java.io.IOException;

public final class ExtractionPreferences {
    private ExtractionPreferences() {}

    public static boolean smart() {
        return SettingsStore.shared().flag("extraction.smart");
    }

    public static void setSmart(boolean enabled) throws IOException {
        SettingsStore.shared().put("extraction.smart", Boolean.toString(enabled));
    }
}
