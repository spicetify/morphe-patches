package app.spicetify.extension.spotify.extensions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import app.spicetify.extension.spotify.settings.PatchSettings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class ExtensionsTest {
    private final Context context = RuntimeEnvironment.getApplication();

    // The switch registry is process-wide, so each test uses ids no other test or extension uses.

    @Test
    public void eachSwitchStartsOffAndIsSavedUnderItsId() {
        assertFalse(Extensions.isOn(context, "test_saved"));

        Extensions.setOn(context, "test_saved", true);
        assertTrue(Extensions.isOn(context, "test_saved"));
        assertFalse(Extensions.isOn(context, "test_other"));
        assertTrue(context.getSharedPreferences("spicetify_extensions", Context.MODE_PRIVATE)
                .getBoolean("test_saved", false));

        Extensions.setOn(context, "test_saved", false);
        assertFalse(Extensions.isOn(context, "test_saved"));
    }

    @Test
    public void setOnTellsTheSwitchListenerAfterSaving() {
        List<String> seen = new ArrayList<>();
        Extensions.onSwitch("test_switch", (switchContext, on) ->
                seen.add(on + ", saved " + Extensions.isOn(switchContext, "test_switch")));

        Extensions.setOn(context, "test_switch", true);
        Extensions.setOn(context, "test_switch", false);

        assertEquals(Arrays.asList("true, saved true", "false, saved false"), seen);
    }

    @Test
    public void aFailingSwitchListenerStillSavesTheSwitch() {
        Extensions.onSwitch("test_failing", (switchContext, on) -> {
            throw new IllegalStateException("listener failed");
        });

        Extensions.setOn(context, "test_failing", true);

        assertTrue(Extensions.isOn(context, "test_failing"));
    }

    @Test
    public void startEnabledStartsOnlyTheExtensionsThatAreOn() {
        List<String> started = new ArrayList<>();
        Extensions.onSwitch("test_on", (switchContext, on) -> started.add("test_on " + on));
        Extensions.onSwitch("test_off", (switchContext, on) -> started.add("test_off " + on));
        context.getSharedPreferences("spicetify_extensions", Context.MODE_PRIVATE).edit()
                .putBoolean("test_on", true).commit();

        Extensions.startEnabled(context);

        assertEquals(Collections.singletonList("test_on true"), started);
    }

    @Test
    public void appContextFallsBackToTheContextPatchSettingsGot() {
        Extensions.setAppContext(null);

        PatchSettings.initialize(context);

        assertSame(context, Extensions.appContext());
    }
}
