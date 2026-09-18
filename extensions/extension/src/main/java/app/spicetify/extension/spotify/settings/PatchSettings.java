package app.spicetify.extension.spotify.settings;

import android.content.Context;
import android.content.SharedPreferences;
import app.spicetify.extension.spotify.home.HomePins;
import app.spicetify.extension.spotify.localserver.ServerConfig;

public final class PatchSettings {
    private static final String FILE = "spicetify_patch_settings";
    private static final String CLEAN_SHARING = "clean_sharing";
    private static volatile SharedPreferences preferences;

    private PatchSettings() {}

    public static void initialize(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        if (InstalledPatches.homePins()) HomePins.initialize(context);
        if (InstalledPatches.serverFiles()) ServerConfig.initialize(context);
    }

    public static boolean cleanSharingEnabled() {
        SharedPreferences current = preferences;
        return current == null || current.getBoolean(CLEAN_SHARING, true);
    }

    public static void setCleanSharingEnabled(boolean enabled) {
        SharedPreferences current = preferences;
        if (current == null) throw new IllegalStateException("Spicetify settings are not initialized.");
        current.edit().putBoolean(CLEAN_SHARING, enabled).apply();
    }
}
