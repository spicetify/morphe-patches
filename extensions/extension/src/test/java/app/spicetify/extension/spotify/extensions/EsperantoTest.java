package app.spicetify.extension.spotify.extensions;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;

public class EsperantoTest {
    // ---- State ----

    @Test
    public void parseStateReadsTheTrackItsArtistsAndAdvertisement() throws IOException {
        Esperanto.PlayerState parsed = Esperanto.parseState(contextPlayerState("spotify:track:x"));

        assertEquals("spotify:track:x", parsed.trackUri);
        assertEquals("uid-1", parsed.trackUid);
        assertEquals(Arrays.asList("spotify:artist:a1", "spotify:artist:a2"), parsed.artistUris);
        assertFalse(parsed.advertisement);
        assertFalse(parsed.episode);
    }

    @Test
    public void parseStateReadsAnAdvertisement() throws IOException {
        Wire.Writer contextTrack = new Wire.Writer();
        contextTrack.string(1, "spotify:ad:x");
        contextTrack.message(3, metadataEntry("is_advertisement", "true"));

        assertTrue(Esperanto.parseState(state(contextTrack)).advertisement);
    }

    @Test
    public void parseStateDetectsEpisodeTracks() throws IOException {
        for (String uri : new String[] {"spotify:episode:x", "spotify:podcast-chapter:x", "spotify:clip:x"}) {
            Wire.Writer contextTrack = new Wire.Writer();
            contextTrack.string(1, uri);
            assertTrue(uri, Esperanto.parseState(state(contextTrack)).episode);
        }
    }

    // ---- Request builders ----

    @Test
    public void getStateAsksForNoTracksBeforeOrAfterTheCurrentOne() {
        // prev_tracks_cap{value 0}, next_tracks_cap{value 0}
        assertArrayEquals(new byte[] {0x0a, 0x02, 0x08, 0x00, 0x12, 0x02, 0x08, 0x00}, Esperanto.getState());
    }

    // ---- Fixtures ----

    /**
     * A {@code ContextPlayerState} playing {@code trackUri} (uid {@code uid-1}): artists {@code a1}
     * and {@code a2} plus a blank third, {@code is_advertisement=false}, and a context and a playback
     * id the parser skips. Other tests send it as a state body.
     */
    static byte[] contextPlayerState(String trackUri) {
        Wire.Writer contextTrack = new Wire.Writer();
        contextTrack.string(1, trackUri);
        contextTrack.string(2, "uid-1");
        contextTrack.message(3, metadataEntry("artist_uri", "spotify:artist:a1"));
        contextTrack.message(3, metadataEntry("artist_uri:1", "spotify:artist:a2"));
        contextTrack.message(3, metadataEntry("artist_uri:2", ""));
        contextTrack.message(3, metadataEntry("is_advertisement", "false"));

        Wire.Writer providedTrack = new Wire.Writer();
        providedTrack.message(1, contextTrack);

        Wire.Writer state = new Wire.Writer();
        state.string(2, "spotify:playlist:p");
        state.message(7, providedTrack);
        state.string(8, "playback-1");
        return state.toByteArray();
    }

    /** A {@code ContextPlayerState} playing {@code trackUri} as row {@code trackUid}, by {@code artistUris}. */
    static byte[] contextPlayerState(String trackUri, String trackUid, String... artistUris) {
        Wire.Writer contextTrack = new Wire.Writer();
        contextTrack.string(1, trackUri);
        contextTrack.string(2, trackUid);
        for (int i = 0; i < artistUris.length; i++) {
            contextTrack.message(3, metadataEntry(i == 0 ? "artist_uri" : "artist_uri:" + i, artistUris[i]));
        }
        return state(contextTrack);
    }

    private static byte[] state(Wire.Writer contextTrack) {
        Wire.Writer providedTrack = new Wire.Writer();
        providedTrack.message(1, contextTrack);
        Wire.Writer state = new Wire.Writer();
        state.message(7, providedTrack);
        return state.toByteArray();
    }

    private static Wire.Writer metadataEntry(String key, String value) {
        Wire.Writer entry = new Wire.Writer();
        entry.string(1, key);
        entry.string(2, value);
        return entry;
    }
}
