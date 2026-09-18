package app.spicetify.extension.spotify.settings;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import app.spicetify.extension.spotify.home.HomePins;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowToast;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, shadows = HomePinsSettingsTest.Capabilities.class)
public class HomePinsSettingsTest {
    @Implements(value = InstalledPatches.class, isInAndroidSdk = false)
    public static class Capabilities {
        @Implementation public static boolean homePins() { return true; }
    }

    @Test public void emptyPickerExplainsHowToLoadShortcuts() {
        HomePins.initialize(RuntimeEnvironment.getApplication());
        SpicetifySettingsActivity activity = Robolectric.buildActivity(SpicetifySettingsActivity.class).setup().get();
        choose(activity.getWindow().getDecorView()).performClick();
        assertEquals("No Home shortcuts loaded", Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle());
    }

    @Test public void excessSelectionKeepsPickerOpenAndLeavesSavedPinsUntouched() throws Exception {
        JSONArray saved = new JSONArray();
        for (int i = 0; i < 64; i++) saved.put(new JSONObject().put("id", "spotify:playlist:" + i).put("label", "Playlist " + i));
        RuntimeEnvironment.getApplication().getSharedPreferences("spicetify_home_pins", 0)
                .edit().putString("pins", saved.toString()).commit();
        HomePins.initialize(RuntimeEnvironment.getApplication());
        java.lang.reflect.Method capture = HomePins.class.getDeclaredMethod("captureAndOrder", String[].class, String[].class);
        capture.setAccessible(true);
        capture.invoke(null, new String[]{"spotify:playlist:new"}, new String[]{"New playlist"});
        SpicetifySettingsActivity activity = Robolectric.buildActivity(SpicetifySettingsActivity.class).setup().get();
        choose(activity.getWindow().getDecorView()).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        AlertDialog picker = ShadowAlertDialog.getLatestAlertDialog();
        picker.getListView().performItemClick(null, 64, 64);
        picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(picker.isShowing());
        assertEquals("Too many Home pins.", ShadowToast.getTextOfLatestToast());
        assertEquals(64, HomePins.choices().stream().filter(choice -> choice.pinned).count());
        picker.getListView().performItemClick(null, 64, 64);
        picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(picker.isShowing());
    }

    private Button choose(View view) {
        if (view instanceof Button && "Choose pinned shortcuts".contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button result = choose(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }
}
