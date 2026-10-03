package app.spicetify.extension.spotify.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ThemeResolverTest {
    private static Map<String, Integer> scheme(Object... pairs) {
        Map<String, Integer> scheme = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) scheme.put((String) pairs[i], (Integer) pairs[i + 1]);
        return scheme;
    }

    @Test
    public void derivesMissingSurfacesFromMain() {
        Map<String, Integer> colors = ThemeResolver.resolve(scheme("main", 0xFF121212, "text", 0xFFFFFFFF), "button").colors;
        assertEquals(Integer.valueOf(0xFF202020), colors.get("main-elevated"));
        assertEquals(Integer.valueOf(0xFF1B1B1B), colors.get("highlight"));
        assertEquals(Integer.valueOf(0xFF2A2A2A), colors.get("highlight-elevated"));
    }

    @Test
    public void keepsSurfacesTheSchemeSets() {
        Map<String, Integer> colors = ThemeResolver.resolve(scheme("main", 0xFF1E1E2E, "main-elevated", 0xFF313244), "button").colors;
        assertEquals(Integer.valueOf(0xFF313244), colors.get("main-elevated"));
    }

    @Test
    public void derivesThePressedAccentWhenMissingOrEqual() {
        assertEquals(Integer.valueOf(0xFF1ABC54), ThemeResolver.resolve(scheme("button", 0xFF1ED760), "button").colors.get("button-active"));
        assertEquals(Integer.valueOf(0xFF1ABC54), ThemeResolver.resolve(
                scheme("button", 0xFF1ED760, "button-active", 0xFF1ED760), "button").colors.get("button-active"));
        assertEquals(Integer.valueOf(0xFF123456), ThemeResolver.resolve(
                scheme("button", 0xFF1ED760, "button-active", 0xFF123456), "button").colors.get("button-active"));
    }

    @Test
    public void aCustomAccentKeyReplacesTheAccentAndItsPressedColor() {
        ThemeResolver.Result theme = ThemeResolver.resolve(
                scheme("button", 0xFF7F849C, "button-active", 0xFF9399B2, "mauve", 0xFFCBA6F7), "mauve");
        assertEquals(Integer.valueOf(0xFFCBA6F7), theme.colors.get("button"));
        assertEquals(Integer.valueOf(ArgbColors.mix(0xFFCBA6F7, 0xFF000000, 0.125)), theme.colors.get("button-active"));
    }

    @Test
    public void anUnknownAccentKeyFails() {
        try {
            ThemeResolver.resolve(scheme("main", 0xFF000000), "peach");
            fail();
        } catch (ThemeException expected) {
            assertEquals("Accent key \"peach\" is not in this color scheme.", expected.getMessage());
        }
    }

    @Test
    public void iconsOnTheAccentAreBlackOrWhiteByContrast() {
        assertEquals(Integer.valueOf(0xFF000000), ThemeResolver.resolve(scheme("button", 0xFF1ED760), "button").colors.get("on-button"));
        assertEquals(Integer.valueOf(0xFFFFFFFF), ThemeResolver.resolve(scheme("button", 0xFF1E3A8A), "button").colors.get("on-button"));
    }

    @Test
    public void unsetKeysKeepSpotifysColors() {
        ThemeResolver.Result theme = ThemeResolver.resolve(
                scheme("main", 0xFF000000, "sidebar", 0xFF111111, "misc", 0xFF222222, "equalizer", 0xFF333333), "button");
        assertFalse(theme.colors.containsKey("text"));
        assertFalse(theme.colors.containsKey("button"));
        assertFalse(theme.colors.containsKey("sidebar"));
    }

    @Test
    public void warnsAboutUnreadableTextAndLightBackgrounds() {
        List<String> warnings = ThemeResolver.warnings(ThemeResolver.resolve(
                scheme("main", 0xFFF5F7FA, "text", 0xFFFFFFFF, "misc", 0xFF000000), "button").colors);
        assertTrue(warnings.get(0).startsWith("Text on the background has a contrast of"));
        assertTrue(warnings.contains("The background is light. Spotify draws some text in white, which will be hard to read."));
        assertEquals(3, warnings.size());
        assertTrue(ThemeResolver.warnings(ThemeResolver.resolve(scheme("main", 0xFF000000), "button").colors).isEmpty());
        // Secondary text the theme leaves alone is Spotify's #B3B3B3, which white text would hide.
        assertEquals(Collections.singletonList("Secondary text on the background has a contrast of 2.4:1, below 3:1."),
                ThemeResolver.warnings(ThemeResolver.resolve(scheme("main", 0xFF6E6E6E), "button").colors));
    }
}
