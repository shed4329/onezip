package cn.shed4329.onezip.i18n;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

class I18nBundlesTest {
    private Properties read(String suffix) throws IOException {
        Properties p = new Properties();
        try (var in =
                getClass()
                        .getResourceAsStream(
                                "/cn/shed4329/onezip/i18n/Messages" + suffix + ".properties")) {
            assertNotNull(in);
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    @Test
    void everyLocaleHasAllKeysAndCompatibleFormatting() throws Exception {
        Properties base = read("");
        var printf = Pattern.compile("%(?:[0-9]+\\$)?(?:0?[0-9]+)?(?:\\.[0-9]+)?[dsf]");
        for (String suffix : List.of("_zh_CN", "_zh_TW", "_ja", "_en")) {
            Properties p = read(suffix);
            assertEquals(base.keySet(), p.keySet(), suffix);
            for (String key : base.stringPropertyNames()) {
                String text = p.getProperty(key);
                assertFalse(text.isBlank(), suffix + ":" + key);
                var expected =
                        printf.matcher(base.getProperty(key))
                                .results()
                                .map(m -> m.group())
                                .toList();
                var actual = printf.matcher(text).results().map(m -> m.group()).toList();
                assertEquals(expected, actual, suffix + ":" + key);
                if (!actual.isEmpty()) {
                    Object[] args =
                            actual.stream()
                                    .map(
                                            f ->
                                                    f.endsWith("d")
                                                            ? (Object) 3
                                                            : f.endsWith("f") ? 1.5 : "value")
                                    .toArray();
                    assertDoesNotThrow(
                            () -> String.format(Locale.ROOT, text, args), suffix + ":" + key);
                }
            }
        }
    }

    @Test
    void fxmlResourceReferencesResolveAndBundlesAreNotLocaleFallbacks() throws Exception {
        String fxml;
        try (var in =
                getClass()
                        .getResourceAsStream(
                                "/cn/shed4329/onezip/themes/fxJava/fluent/main-window.fxml")) {
            fxml = new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        }
        var refs =
                Pattern.compile("(?:text|promptText)=\"%([^\"]+)\"")
                        .matcher(fxml)
                        .results()
                        .map(m -> m.group(1))
                        .toList();
        assertTrue(refs.size() > 30);
        for (String tag : I18n.TAGS) {
            var b = I18n.bundle(Locale.forLanguageTag(tag));
            assertEquals(Locale.forLanguageTag(tag), b.getLocale());
            for (String key : refs) assertTrue(b.containsKey(key), tag + ":" + key);
        }
        assertEquals("Extract", I18n.bundle(Locale.ENGLISH).getString("action.extract"));
        assertEquals("展開", I18n.bundle(Locale.JAPANESE).getString("action.extract"));
        assertEquals("解壓縮", I18n.bundle(Locale.TRADITIONAL_CHINESE).getString("action.extract"));
    }

    @Test
    void localeVariantsResolveIndependentlyOfHostDefault() {
        assertEquals(Locale.ENGLISH, I18n.bundle(Locale.US).getLocale());
        assertEquals(Locale.JAPANESE, I18n.bundle(Locale.JAPAN).getLocale());
        assertEquals(
                Locale.TRADITIONAL_CHINESE,
                I18n.bundle(Locale.forLanguageTag("zh-Hant")).getLocale());
        assertEquals(
                Locale.TRADITIONAL_CHINESE,
                I18n.bundle(Locale.forLanguageTag("zh-HK")).getLocale());
        assertEquals(Locale.SIMPLIFIED_CHINESE, I18n.bundle(Locale.GERMAN).getLocale());
    }

    @Test
    void unsupportedLanguageCannotBeSaved() {
        assertThrows(IllegalArgumentException.class, () -> I18n.saveLanguage("unsupported"));
    }
}
