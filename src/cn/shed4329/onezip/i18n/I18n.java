package cn.shed4329.onezip.i18n;

import cn.shed4329.onezip.setting.SettingsStore;

import java.io.IOException;
import java.util.*;

/** ResourceBundle locale is fixed for a session so an active task never changes language midway. */
public final class I18n {
    public static final List<String> TAGS = List.of("zh-CN", "zh-TW", "ja", "en");
    public static final List<String> NAMES = List.of("简体中文", "繁體中文", "日本語", "English");
    private static final String KEY = "appearance.language";
    private static final Locale LOCALE = Locale.forLanguageTag(savedTag());
    private static final ResourceBundle BUNDLE = bundle(LOCALE);

    private I18n() {}

    public static String savedTag() {
        String value = SettingsStore.shared().get(KEY, "zh-CN");
        return TAGS.contains(value) ? value : "zh-CN";
    }

    public static void saveLanguage(String tag) throws IOException {
        if (!TAGS.contains(tag)) throw new IllegalArgumentException("Unsupported language: " + tag);
        SettingsStore.shared().put(KEY, tag);
    }

    public static Locale locale() {
        return LOCALE;
    }

    public static void initialize() {
        Locale.setDefault(LOCALE);
    }

    public static ResourceBundle bundle() {
        return BUNDLE;
    }

    public static ResourceBundle bundle(Locale locale) {
        Locale supported =
                switch (locale.getLanguage()) {
                    case "en" -> Locale.ENGLISH;
                    case "ja" -> Locale.JAPANESE;
                    case "zh" ->
                            (locale.getCountry().equals("TW")
                                            || locale.getCountry().equals("HK")
                                            || locale.getScript().equals("Hant"))
                                    ? Locale.TRADITIONAL_CHINESE
                                    : Locale.SIMPLIFIED_CHINESE;
                    default -> Locale.SIMPLIFIED_CHINESE;
                };
        return ResourceBundle.getBundle(
                "cn.shed4329.onezip.i18n.Messages", supported, I18n.class.getModule());
    }

    public static String t(String key) {
        return BUNDLE.getString(key);
    }

    public static String format(String key, Object... args) {
        return String.format(LOCALE, t(key), args);
    }
}
