package app.spicetify.extension.spotify.settings;

public final class InstalledPatches {
    private InstalledPatches() {}

    // Selected patches replace these method bodies in the injected extension.
    public static boolean cleanSharing() {
        return false;
    }

    public static boolean themeColors() {
        return false;
    }
}
