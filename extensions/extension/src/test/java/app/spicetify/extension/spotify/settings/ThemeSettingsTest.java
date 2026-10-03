package app.spicetify.extension.spotify.settings;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import app.spicetify.extension.spotify.theme.ThemePresets;
import app.spicetify.extension.spotify.theme.ThemeRoleMap;
import app.spicetify.extension.spotify.theme.ThemeState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

// Android 13 takes the resource table, which works in Robolectric.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE, shadows = ThemeSettingsTest.Capabilities.class)
public class ThemeSettingsTest {
    @Implements(value = InstalledPatches.class, isInAndroidSdk = false)
    public static class Capabilities {
        @Implementation public static boolean themeColors() { return true; }
    }

    /** The role table the theme patch injects, cut down to the page background. */
    @Implements(value = ThemeRoleMap.class, isInAndroidSdk = false)
    public static class Patched {
        @Implementation protected static String encoded() { return "main:gray_7"; }
    }

    @Before public void initialize() {
        var application = RuntimeEnvironment.getApplication();
        application.deleteSharedPreferences("spicetify_patch_settings");
        application.deleteSharedPreferences("spicetify_theme");
        PatchSettings.initialize(application);
    }

    @Test public void parsesOpaqueAndTranslucentHex() {
        assertEquals(Integer.valueOf(0xFF112233), ThemeSettings.parse("#112233"));
        assertEquals(Integer.valueOf(0xFF112233), ThemeSettings.parse(" 112233 "));
        assertEquals(Integer.valueOf(0x80112233), ThemeSettings.parse("#80112233"));
        assertNull(ThemeSettings.parse("#1122"));
        assertNull(ThemeSettings.parse("green"));
    }

    @Test @Config(shadows = Patched.class)
    public void choosingAThemeAppliesItAndOffersARestart() {
        try (var controller = appearance()) {
            View root = ShadowDialog.getLatestDialog().getWindow().getDecorView();
            assertNotNull(row(root, "Spotify, selected"));
            assertNotNull(row(root, "Material You"));
            assertNull(row(root, "Background, #121212"));
            assertEquals(View.GONE, restartBar(root).getVisibility());
            row(root, "Midnight").performClick();
            Dialog prompt = ShadowDialog.getLatestDialog();
            assertTrue(hasText(prompt.getWindow().getDecorView(), "Restart Spotify to finish applying Midnight?"));
            button(prompt, "Later").performClick();
            assertFalse(prompt.isShowing());
            assertEquals(View.VISIBLE, restartBar(root).getVisibility());
            assertEquals("midnight", ThemeState.load(controller.get()).kind);
            // Spicetify's own screens follow the theme.
            assertEquals(0xFF0B1026, SpotifyStyle.background());
            assertEquals(0xFF1C2340, SpotifyStyle.surface());
            assertEquals(0xFF509BF5, SpotifyStyle.accent());
            assertNotNull(row(root, "Midnight, selected"));
            // Tapping the theme in use changes nothing.
            row(root, "Midnight, selected").performClick();
            assertSame(prompt, ShadowDialog.getLatestDialog());
            row(root, "Spotify").performClick();
            assertEquals(ThemePresets.STOCK, ThemeState.load(controller.get()).kind);
            assertEquals(0xFF121212, SpotifyStyle.background());
            assertEquals(0xFF282828, SpotifyStyle.surface());
            assertEquals(0xFF1ED760, SpotifyStyle.accent());
        }
    }

    @Test @Config(shadows = Patched.class)
    public void customStartsFromTheCurrentThemeAndEditsOneColor() {
        try (var controller = appearance()) {
            View root = ShadowDialog.getLatestDialog().getWindow().getDecorView();
            row(root, "Midnight").performClick();
            button(ShadowDialog.getLatestDialog(), "Later").performClick();
            row(root, "Custom").performClick();
            button(ShadowDialog.getLatestDialog(), "Later").performClick();
            ThemeState.Selection custom = ThemeState.load(controller.get());
            assertEquals(ThemeState.CUSTOM, custom.kind);
            assertEquals(Integer.valueOf(0xFF1C2340), custom.colors.get("card"));
            row(root, "Background, #0B1026").performClick();
            Dialog sheet = ShadowDialog.getLatestDialog();
            EditText hex = first(sheet.getWindow().getDecorView(), EditText.class);
            hex.setText("nope");
            button(sheet, "Save").performClick();
            assertTrue(sheet.isShowing());
            hex.setText("#000000");
            button(sheet, "Save").performClick();
            assertFalse(sheet.isShowing());
            custom = ThemeState.load(controller.get());
            assertEquals(Integer.valueOf(0xFF000000), custom.colors.get("main"));
            assertEquals(Integer.valueOf(0xFF1C2340), custom.colors.get("card"));
            assertEquals(Integer.valueOf(0xFF509BF5), custom.colors.get("button"));
            assertNotNull(row(root, "Surface, #1C2340"));
        }
    }

    @Test public void aThemeThatCannotApplyIsNotSaved() {
        // Without the patch's role table, no theme can take effect.
        try (var controller = appearance()) {
            View root = ShadowDialog.getLatestDialog().getWindow().getDecorView();
            row(root, "OLED").performClick();
            Dialog sheet = ShadowDialog.getLatestDialog();
            assertTrue(hasText(sheet.getWindow().getDecorView(), "Colors not applied"));
            assertEquals(ThemePresets.STOCK, ThemeState.load(controller.get()).kind);
            assertEquals(View.GONE, restartBar(root).getVisibility());
        }
    }

    @Test @Config(sdk = 30) public void materialYouNeedsAndroid12() {
        try (var controller = appearance()) {
            View root = ShadowDialog.getLatestDialog().getWindow().getDecorView();
            assertNotNull(row(root, "OLED"));
            assertNull(row(root, "Material You"));
        }
    }

    @Test @Config(sdk = 29) public void androidTenKeepsSpotifysColorsWithANote() {
        try (var controller = appearance()) {
            View root = ShadowDialog.getLatestDialog().getWindow().getDecorView();
            assertNull(row(root, "OLED"));
            assertNull(row(root, "Custom"));
            assertTrue(hasTextContaining(root, "Themes need Android 11 or later."));
        }
    }

    private org.robolectric.android.controller.ActivityController<Activity> appearance() {
        var controller = Robolectric.buildActivity(Activity.class).setup();
        SpicetifySettingsScreen.open(controller.get(), SpicetifySettingsScreen.PAGE_APPEARANCE);
        return controller;
    }

    private View restartBar(View root) {
        Button restart = find(root, "Restart");
        assertNotNull(restart);
        return (View) restart.getParent().getParent();
    }

    private View row(View view, String description) {
        if (view.isClickable() && view.getContentDescription() != null && description.contentEquals(view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = row(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Button button(Dialog dialog, String label) {
        Button found = find(dialog.getWindow().getDecorView(), label);
        if (found == null) throw new AssertionError("Missing button: " + label);
        return found;
    }

    private Button find(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = find(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private <T extends View> T first(View view, Class<T> kind) {
        if (kind.isInstance(view)) return kind.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = first(group.getChildAt(i), kind);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean hasTextContaining(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasTextContaining(group.getChildAt(i), text)) return true;
        }
        return false;
    }

    private boolean hasText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasText(group.getChildAt(i), text)) return true;
        }
        return false;
    }
}
