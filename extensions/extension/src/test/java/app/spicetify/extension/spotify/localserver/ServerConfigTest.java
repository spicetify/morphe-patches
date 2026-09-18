package app.spicetify.extension.spotify.localserver;

import static org.junit.Assert.*;
import android.app.Application;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class ServerConfigTest {
    @Before public void setup() {
        Application app = RuntimeEnvironment.getApplication();
        ServerConfig.initialize(app);
        ServerConfig.configure(false, "", "", "");
    }
    @Test public void reconfigurationInvalidatesOldSnapshotAndClearsIndex() {
        ServerConfig.configure(true, "https://dav.example/old/", "old-user", "old-pass");
        ServerConfig.Snapshot old = ServerConfig.snapshot();
        ServerConfig.configure(true, "https://dav.example/new/", "new-user", "new-pass");
        assertFalse(ServerConfig.isCurrent(old));
        assertFalse(ServerConfig.publish(old, () -> fail("Stale scan published")));
        assertTrue(ServerIndex.tracks().isEmpty());
    }
    @Test public void disableCancelsExistingSession() {
        ServerConfig.configure(true, "https://dav.example/music/", "user", "pass");
        ServerConfig.Snapshot old = ServerConfig.snapshot();
        ServerConfig.enabled(false);
        assertFalse(ServerConfig.isCurrent(old));
        assertEquals("Disabled", ServerIndex.status());
        ServerIndex.scanAsync();
        assertEquals("Disabled", ServerIndex.status());
    }
    @Test public void blankPasswordRetentionIsScopedToTheSameRootAndAccount() {
        ServerConfig.configure(true, "https://dav.example/music/", "user", "pass");
        ServerConfig.configure(true, "https://dav.example/music", "user", null);
        assertTrue(ServerConfig.snapshot().hasPassword());
        ServerConfig.configure(true, "https://other.example/music/", "user", null);
        assertFalse(ServerConfig.snapshot().hasPassword());
    }
    @Test public void repeatedInitializationPreservesActiveSession() {
        ServerConfig.configure(true, "https://dav.example/music/", "user", "pass");
        ServerConfig.Snapshot before = ServerConfig.snapshot();
        ServerConfig.initialize(RuntimeEnvironment.getApplication());
        assertSame(before, ServerConfig.snapshot());
    }
    @Test public void explicitEmptyPasswordClearsTheSecret() {
        ServerConfig.configure(true, "https://dav.example/music/", "user", "pass");
        ServerConfig.configure(true, "https://dav.example/music/", "user", "");
        assertFalse(ServerConfig.snapshot().hasPassword());
    }
}
