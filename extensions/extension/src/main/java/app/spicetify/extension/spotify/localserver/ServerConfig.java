package app.spicetify.extension.spotify.localserver;

import android.content.Context;
import android.content.SharedPreferences;

public final class ServerConfig {
    private static SharedPreferences preferences;
    private static Context context;
    private static volatile Snapshot current = new Snapshot(false, null);
    private ServerConfig() {}

    public static final class Snapshot {
        public final boolean enabled;
        private final ServerConnection connection;
        Snapshot(boolean enabled, ServerConnection connection) { this.enabled = enabled; this.connection = connection; }
        public String rootUrl() { return connection == null ? "" : connection.root.toASCIIString(); }
        public String username() { return connection == null ? "" : connection.username; }
        public boolean hasPassword() { return connection != null && connection.hasPassword(); }
        ServerConnection connection() { return connection; }
    }

    public static synchronized void initialize(Context supplied) {
        if (preferences != null) return;
        context = supplied.getApplicationContext();
        preferences = context.getSharedPreferences("spicetify_local_server", Context.MODE_PRIVATE);
        String url = preferences.getString("url", "");
        if (!url.isEmpty()) {
            try { current = new Snapshot(preferences.getBoolean("enabled", false), new ServerConnection(url,
                    preferences.getString("username", ""), preferences.getString("password", ""))); }
            catch (IllegalArgumentException ignored) { current = new Snapshot(false, null); }
        }
    }

    public static Snapshot snapshot() { return current; }
    static Context context() { return context; }
    static boolean isCurrent(Snapshot snapshot) { return current == snapshot && snapshot.enabled; }
    static synchronized boolean publish(Snapshot snapshot, Runnable update) {
        if (!isCurrent(snapshot)) return false;
        update.run(); return true;
    }

    /** Null password retains the saved password only for the same server/account. Empty clears it. */
    public static synchronized void configure(boolean enabled, String rootUrl, String username, String password) {
        if (enabled && android.os.Build.VERSION.SDK_INT < 26)
            throw new IllegalArgumentException("Server files requires Android 8 or later.");
        if (preferences == null) throw new IllegalStateException("Server settings are not initialized.");
        ServerConnection connection = null;
        if (!rootUrl.trim().isEmpty()) {
            connection = new ServerConnection(rootUrl, username, password == null ? "" : password);
            if (password == null && current.connection != null && connection.root.equals(current.connection.root)
                    && connection.username.equals(current.connection.username)) {
                connection = new ServerConnection(rootUrl, username, current.connection.password());
            }
        } else if (enabled) throw new IllegalArgumentException("Enter an HTTPS WebDAV folder URL first.");
        current = new Snapshot(enabled, connection);
        preferences.edit().putBoolean("enabled", enabled).putString("url", current.rootUrl())
                .putString("username", current.username()).putString("password", connection == null ? "" : connection.password()).apply();
        ServerIndex.invalidate();
    }

    public static void enabled(boolean value) {
        Snapshot saved = snapshot();
        configure(value, saved.rootUrl(), saved.username(), null);
    }
}
