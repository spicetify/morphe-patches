package app.spicetify.extension.spotify.extensions;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import app.spicetify.extension.spotify.settings.PatchSettings;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Android side of Spicetify's desktop extensions: each extension's switch, and the listener
 * that hears it.
 */
public final class Extensions {
    private static final String PREFERENCES = "spicetify_extensions";
    private static final Map<String, SwitchListener> SWITCHES = new ConcurrentHashMap<>();
    private static volatile Context appContext;

    /**
     * Hears extension {@code id} turned on or off. {@link #setOn} calls it on the UI thread, and
     * {@link #startEnabled} calls it with true on the bridge thread each time Spotify's core starts,
     * so the two can overlap: a listener should check {@link #isOn} again before it starts anything.
     */
    interface SwitchListener {
        void onSwitch(Context context, boolean on);
    }

    private Extensions() {}

    /** Whether the user turned extension {@code id} on; every extension starts off. */
    public static boolean isOn(Context context, String id) {
        return preferences(context).getBoolean(id, false);
    }

    /** Saves the switch, then tells the extension's listener. */
    public static void setOn(Context context, String id, boolean on) {
        preferences(context).edit().putBoolean(id, on).apply();
        SwitchListener listener = SWITCHES.get(id);
        if (listener != null) tell(listener, context, on);
    }

    /** Gives extension {@code id} the listener that {@link #setOn} and {@link #startEnabled} call. */
    static void onSwitch(String id, SwitchListener listener) {
        SWITCHES.put(id, listener);
    }

    /** Starts every extension that is on. The bridge calls this once Spotify's core is up. */
    static void startEnabled(Context context) {
        for (Map.Entry<String, SwitchListener> entry : SWITCHES.entrySet()) {
            if (isOn(context, entry.getKey())) tell(entry.getValue(), context, true);
        }
    }

    /** A failing listener is logged, so it can't undo the switch or stop the other extensions. */
    private static void tell(SwitchListener listener, Context context, boolean on) {
        try {
            listener.onSwitch(context, on);
        } catch (Throwable e) {
            Log.w("Spicetify", "An extension switch listener failed", e);
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    /**
     * Spotify's application context. The bridge sets it when Spotify's core starts. Before that,
     * or if the bridge couldn't find it, it's the one {@link PatchSettings} got in Spotify's onCreate.
     */
    static Context appContext() {
        Context found = appContext;
        return found != null ? found : PatchSettings.applicationContext();
    }

    static void setAppContext(Context context) {
        appContext = context;
    }
}
