package cn.shed4329.onezip.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class AppearancePreferencesTest {
    @Test void blurRadiusBoundsAndDamagedValuesHaveSafeFallbacks() {
        assertEquals(0, AppearancePreferences.parseBlurRadius("-12"));
        assertEquals(0, AppearancePreferences.parseBlurRadius("0"));
        assertEquals(24, AppearancePreferences.parseBlurRadius("24"));
        assertEquals(63, AppearancePreferences.parseBlurRadius("999"));
        assertEquals(45, AppearancePreferences.parseBlurRadius(" 45 "));
        assertEquals(24, AppearancePreferences.parseBlurRadius("broken"));
        assertEquals(24, AppearancePreferences.parseBlurRadius("1e300"));
    }
}
