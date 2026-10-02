package app.spicetify.extension.spotify.extensions;

import android.content.Context;
import android.util.Log;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * The Java side of the track and artist menu hooks. The patch's {@code MenuBridge} smali asks which
 * items to add and builds Spotify's own menu items from them, and its {@code MenuAction} calls back
 * here on a tap. Only plain strings cross: each item is {id, title}, and the bridge gives it the trash
 * icon.
 * <p>
 * The menus pass Spotify's protobuf {@code CollectionTrack} or {@code CollectionArtist}. R8 renamed
 * their metadata getters, so the URI comes from the metadata field, whose name protobuf-lite needs
 * at runtime, and then the metadata's kept {@code getLink()}.
 * <p>
 * The items are built on an Rx thread, and building one only reads the switch and the trash sets.
 * Taps come on the main thread. Nothing here throws.
 */
public final class ExtensionMenus {
    private static final String TRASH_SONG = "trash_song";
    private static final String TRASH_ARTIST = "trash_artist";
    private static final String TRACK_METADATA = "trackMetadata_";
    private static final String ARTIST_METADATA = "artistMetadata_";

    private ExtensionMenus() {}

    /** The track menu's items for {@code collectionTrack}: the Trash item, when Trash Bin is on and there's a URI. */
    public static List<String[]> trackItems(Object collectionTrack) {
        List<String[]> items = new ArrayList<>();
        try {
            Context context = Extensions.appContext();
            if (context == null || !Extensions.isOn(context, Extensions.TRASH_BIN)) return items;
            String uri = link(collectionTrack, TRACK_METADATA);
            if (uri != null) {
                items.add(new String[] {TRASH_SONG,
                        TrashBin.isSongTrashed(uri) ? "Take song out of trash" : "Throw song to trash"});
            }
            return items;
        } catch (Throwable e) {
            Log.w("Spicetify", "Couldn't list the track menu items", e);
            return new ArrayList<>();
        }
    }

    /** The artist menu's items for {@code collectionArtist}: the Trash item, when Trash Bin is on and there's a URI. */
    public static List<String[]> artistItems(Object collectionArtist) {
        List<String[]> items = new ArrayList<>();
        try {
            Context context = Extensions.appContext();
            if (context == null || !Extensions.isOn(context, Extensions.TRASH_BIN)) return items;
            String uri = link(collectionArtist, ARTIST_METADATA);
            if (uri != null) {
                items.add(new String[] {TRASH_ARTIST,
                        TrashBin.isArtistTrashed(uri) ? "Take artist out of trash" : "Throw artist to trash"});
            }
            return items;
        } catch (Throwable e) {
            Log.w("Spicetify", "Couldn't list the artist menu items", e);
            return new ArrayList<>();
        }
    }

    /** A tap on track menu item {@code id}: {@code trash_song} toggles the menu's song, and any other id does nothing. */
    public static void onTrackItem(String id, Object collectionTrack) {
        try {
            Context context = Extensions.appContext();
            if (context == null || !TRASH_SONG.equals(id)) return;
            String uri = link(collectionTrack, TRACK_METADATA);
            if (uri != null) TrashBin.setSong(context, uri, !TrashBin.isSongTrashed(uri));
        } catch (Throwable e) {
            Log.w("Spicetify", "Track menu item " + id + " failed", e);
        }
    }

    /** A tap on artist menu item {@code id}: {@code trash_artist} toggles the menu's artist, and any other id does nothing. */
    public static void onArtistItem(String id, Object collectionArtist) {
        try {
            Context context = Extensions.appContext();
            if (context == null || !TRASH_ARTIST.equals(id)) return;
            String uri = link(collectionArtist, ARTIST_METADATA);
            if (uri != null) TrashBin.setArtist(context, uri, !TrashBin.isArtistTrashed(uri));
        } catch (Throwable e) {
            Log.w("Spicetify", "Artist menu item " + id + " failed", e);
        }
    }

    /**
     * The link of the protobuf metadata in {@code target}'s field {@code field}, or null when the
     * field is unset, the link is empty or reflection fails. A reflection failure, such as a missing
     * field after Spotify's protobuf changed, also becomes Trash Bin's status.
     */
    private static String link(Object target, String field) {
        if (target == null) return null;
        try {
            Field metadataField = target.getClass().getDeclaredField(field);
            metadataField.setAccessible(true);
            Object metadata = metadataField.get(target);
            if (metadata == null) return null;
            Object link = metadata.getClass().getMethod("getLink").invoke(metadata);
            return link instanceof String && !((String) link).isEmpty() ? (String) link : null;
        } catch (Exception e) {
            Log.w("Spicetify", "Couldn't read the menu's " + field, e);
            Extensions.status(Extensions.TRASH_BIN, "Couldn't read the menu's " + field + ": " + e);
            return null;
        }
    }
}
