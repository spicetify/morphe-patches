package app.spicetify.extension.spotify.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import app.spicetify.extension.spotify.localserver.ServerConfig;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class ServerFilesSettingsTest {
    private Activity activity;
    private ServerFilesSettings form;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        ServerConfig.initialize(activity);
        ServerConfig.configure(false, "https://dav.example/music/", "fixture-user", "fixture-secret");
        form = new ServerFilesSettings(activity);
    }

    @Test public void passwordIsNeverPrefilledOrSavedInViewState() {
        EditText password = inputs().get(2);
        assertEquals("", password.getText().toString());
        assertFalse(password.isSaveEnabled());
        button("Save").performClick();
        assertTrue(ServerConfig.snapshot().hasPassword());
        assertFalse(ServerConfig.snapshot().enabled);
    }

    @Test public void editingServerDoesNotSendSavedCredentialsToNewServer() {
        inputs().get(0).setText("https://another.example/music/");
        button("Save").performClick();
        assertEquals("https://another.example/music/", ServerConfig.snapshot().rootUrl());
        assertFalse(ServerConfig.snapshot().hasPassword());
        assertFalse(ServerConfig.snapshot().enabled);
    }

    @Test public void invalidUrlLeavesTheSavedConfigurationUntouched() {
        inputs().get(0).setText("http://insecure.example/music/");
        button("Save").performClick();
        assertEquals("https://dav.example/music/", ServerConfig.snapshot().rootUrl());
        assertTrue(ServerConfig.snapshot().hasPassword());
    }

    @Test public void forgettingRequiresConfirmationAndClearsCredentials() {
        button("Forget server").performClick();
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(ServerConfig.snapshot().hasPassword());
        button("Forget server").performClick();
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("", ServerConfig.snapshot().rootUrl());
        assertFalse(ServerConfig.snapshot().hasPassword());
        assertFalse(ServerConfig.snapshot().enabled);
    }

    @Test @Config(sdk = 24) public void androidSevenCannotEnableServerStreaming() {
        assertEquals(0, inputs().size());
        assertThrows(IllegalArgumentException.class, () ->
                ServerConfig.configure(true, "https://dav.example/music/", "user", "secret"));
        assertFalse(ServerConfig.snapshot().enabled);
    }

    @Test public void disablingStopsSavedServerEvenWithAnInvalidDraft() {
        ServerConfig.configure(true, "https://dav.example/music/", "fixture-user", "fixture-secret");
        form = new ServerFilesSettings(activity);
        inputs().get(0).setText("http://invalid.example/music/");
        for (int i = 0; i < form.getChildCount(); i++) {
            if (form.getChildAt(i) instanceof Switch) ((Switch) form.getChildAt(i)).setChecked(false);
        }
        assertFalse(ServerConfig.snapshot().enabled);
        button("Save").performClick();
        assertFalse(ServerConfig.snapshot().enabled);
        assertEquals("https://dav.example/music/", ServerConfig.snapshot().rootUrl());
    }

    @Test public void pollingDoesNotEraseValidationErrors() {
        activity.setContentView(form);
        inputs().get(0).setText("http://invalid.example/music/");
        button("Save").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        boolean visible = false;
        for (int i = 0; i < form.getChildCount(); i++) {
            View view = form.getChildAt(i);
            if (view instanceof TextView && ((TextView) view).getText().toString().startsWith("Use an HTTPS folder URL")) visible = true;
        }
        assertTrue(visible);
    }

    private List<EditText> inputs() {
        List<EditText> result = new ArrayList<>();
        for (int i = 0; i < form.getChildCount(); i++) {
            if (form.getChildAt(i) instanceof EditText) result.add((EditText) form.getChildAt(i));
        }
        return result;
    }

    private Button button(String label) {
        for (int i = 0; i < form.getChildCount(); i++) {
            View view = form.getChildAt(i);
            if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
        }
        throw new AssertionError("Missing button: " + label);
    }
}
