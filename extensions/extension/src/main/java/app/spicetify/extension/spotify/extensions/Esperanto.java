package app.spicetify.extension.spotify.extensions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The esperanto messages the extensions send and receive over {@code sp://esperanto/<service>/
 * <method>}: request builders, response parsers and the player state value. The field numbers
 * were read from Spotify 9.1.80.2221's protobuf classes, and the extensions patch checks them
 * again against the APK it patches.
 */
final class Esperanto {
    static final String CONTEXT_PLAYER = "spotify.player.esperanto.proto.ContextPlayer";

    private Esperanto() {}

    /** A parsed {@code ContextPlayerState}: the fields the extensions read. */
    static final class PlayerState {
        String trackUri;
        String trackUid;
        List<String> artistUris = new ArrayList<>();
        boolean advertisement;
        boolean episode;
    }

    /** {@code ContextPlayerState{7 track: ProvidedTrack{1 context_track}}}; everything else is skipped. */
    static PlayerState parseState(byte[] contextPlayerState) throws IOException {
        PlayerState state = new PlayerState();
        Wire.Reader reader = new Wire.Reader(contextPlayerState);
        while (reader.next()) {
            if (reader.field() == 7) {
                readProvidedTrack(reader.message(), state);
            } else {
                reader.skip();
            }
        }
        return state;
    }

    private static void readProvidedTrack(Wire.Reader providedTrack, PlayerState state) throws IOException {
        while (providedTrack.next()) {
            if (providedTrack.field() == 1) {
                readContextTrack(providedTrack.message(), state);
            } else {
                providedTrack.skip();
            }
        }
    }

    /** {@code ContextTrack{1 uri, 2 uid, 3 metadata: map<string, string>}}. */
    private static void readContextTrack(Wire.Reader contextTrack, PlayerState state) throws IOException {
        Map<String, String> metadata = new TreeMap<>();
        while (contextTrack.next()) {
            switch (contextTrack.field()) {
                case 1:
                    state.trackUri = contextTrack.string();
                    break;
                case 2:
                    state.trackUid = contextTrack.string();
                    break;
                case 3:
                    readMetadataEntry(contextTrack.message(), metadata);
                    break;
                default:
                    contextTrack.skip();
            }
        }
        // artist_uri, artist_uri:1, artist_uri:2 and so on, in that order.
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (entry.getKey().startsWith("artist_uri") && !entry.getValue().isEmpty()) {
                state.artistUris.add(entry.getValue());
            }
        }
        state.advertisement = "true".equals(metadata.get("is_advertisement"));
        state.episode = isEpisodeUri(state.trackUri);
    }

    private static void readMetadataEntry(Wire.Reader entry, Map<String, String> metadata) throws IOException {
        String key = null;
        String value = "";
        while (entry.next()) {
            switch (entry.field()) {
                case 1:
                    key = entry.string();
                    break;
                case 2:
                    value = entry.string();
                    break;
                default:
                    entry.skip();
            }
        }
        if (key != null) {
            metadata.put(key, value);
        }
    }

    private static boolean isEpisodeUri(String uri) {
        return uri != null
                && (uri.startsWith("spotify:episode:")
                        || uri.startsWith("spotify:podcast-chapter:")
                        || uri.startsWith("spotify:clip:"));
    }

    // ---- Request builders ----

    /**
     * {@code GetStateRequest{1 prev_tracks_cap{1 0}, 2 next_tracks_cap{1 0}}}, each cap an
     * {@code OptionalInt64}. The extensions read only the current track, so a state leaves out the
     * tracks before and after it and stays small on a big playlist.
     */
    static byte[] getState() {
        Wire.Writer none = new Wire.Writer();
        none.varint(1, 0);
        Wire.Writer request = new Wire.Writer();
        request.message(1, none);
        request.message(2, none);
        return request.toByteArray();
    }
}
