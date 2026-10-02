package app.spicetify.extension.spotify.home;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.SharedPreferences;
import app.spicetify.extension.spotify.extensions.Library;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class HomePinsTest {
    private static final String A = "spotify:playlist:a";
    private static final String B = "spotify:playlist:b";
    private static final String C = "spotify:album:c";
    private static final String D = "spotify:playlist:d";
    /** A saved playlist that Home doesn't show. */
    private static final String X = "spotify:playlist:x";
    private static final String SHORTCUTS = "home-shortcuts";
    private static final String LIKED_SONGS_IMAGE = "https://misc.scdn.co/liked-songs/liked-songs-300.png";
    private Application application;

    @Before public void initialize() {
        application = RuntimeEnvironment.getApplication();
        application.deleteSharedPreferences("spicetify_home_pins");
        HomePins.initialize(application);
    }

    @Test public void firstRenderDiscoversChoicesBeforeAnyPinExists() {
        assertArrayEquals(new int[]{0, 1}, HomePins.captureAndOrder(
                new String[]{A, B}, new String[]{"One", "Two"}));
        assertEquals(2, HomePins.choices().size());
        assertFalse(HomePins.choices().get(0).pinned);
        HomePins.setPinned(Collections.singletonList(B));
        assertArrayEquals(new int[]{1, 0}, HomePins.captureAndOrder(
                new String[]{A, B}, new String[]{"One", "Two"}));
    }

    @Test public void duplicateLabelsUseUriIdentityAndRebindingUpdatesLabels() {
        HomePins.captureAndOrder(new String[]{A, B}, new String[]{"Same", "Same"});
        HomePins.setPinned(Collections.singletonList(B));
        HomePins.captureAndOrder(new String[]{B, C}, new String[]{"Renamed", "Same"});
        assertEquals(B, HomePins.choices().get(0).id);
        assertEquals("Renamed", HomePins.choices().get(0).label);
        assertTrue(HomePins.choices().get(0).pinned);
        assertFalse(HomePins.choices().get(1).pinned);
    }

    @Test public void absentPinsSurviveRestartAndReturnInTheirChosenOrder() {
        HomePins.captureAndOrder(new String[]{A, B, C}, new String[]{"A", "B", "C"});
        HomePins.setPinned(Arrays.asList(C, B));
        HomePins.initialize(application);
        assertEquals(2, HomePins.choices().size());
        assertEquals(C, HomePins.choices().get(0).id);
        HomePins.captureAndOrder(new String[]{A}, new String[]{"A"});
        assertEquals(3, HomePins.choices().size());
        assertArrayEquals(new int[]{2, 1, 0}, HomePins.captureAndOrder(
                new String[]{A, B, C}, new String[]{"A", "B", "C"}));
    }

    @Test public void unpinRestoresNativeOrderAndKeepsDuplicateNativeRows() {
        String[] ids = {A, B, A, C};
        String[] labels = {"A", "B", "A", "C"};
        HomePins.captureAndOrder(ids, labels);
        HomePins.setPinned(Arrays.asList(B, A));
        assertArrayEquals(new int[]{1, 0, 2, 3}, HomePins.captureAndOrder(ids, labels));
        HomePins.setPinned(Collections.emptyList());
        assertArrayEquals(new int[]{0, 1, 2, 3}, HomePins.captureAndOrder(ids, labels));
        assertArrayEquals(new String[]{A, B, A, C}, ids);
    }

    @Test public void invalidUrisAreNotOfferedAndUnknownSelectionsAreRejected() {
        HomePins.captureAndOrder(new String[]{null, "https://example.com", A},
                new String[]{"Missing", "Web", "A"});
        assertEquals(1, HomePins.choices().size());
        assertThrows(IllegalArgumentException.class,
                () -> HomePins.setPinned(Collections.singletonList(B)));
    }

    @Test public void pickerSelectionSurvivesAHomeRefreshWhileTheDialogIsOpen() {
        HomePins.captureAndOrder(new String[]{A, B}, new String[]{"A", "B"});
        HomePins.choices();
        HomePins.captureAndOrder(new String[]{C}, new String[]{"C"});
        HomePins.setPinned(Collections.singletonList(B));
        assertEquals(B, HomePins.choices().get(0).id);
        assertTrue(HomePins.choices().get(0).pinned);
    }

    @Test public void changedNativeObjectsFailClosedWithoutMutatingInput() {
        ArrayList<Object> nativeRows = rows(row(A, "A"), new Object());
        assertNull(HomePins.plan(SHORTCUTS, nativeRows));
        assertEquals(2, nativeRows.size());
        assertTrue("nothing is recorded", HomePins.choices().isEmpty());
    }

    @Test public void planPutsThePinsFirstInPinOrderAndRecordsHomesTitles() {
        ArrayList<Object> rows = rows(row(A, "One"), row(B, "Two"), row(C, "Three"));
        assertEquals(Arrays.asList("One", "Two", "Three"), describe(HomePins.plan(SHORTCUTS, rows), rows));
        assertEquals(Arrays.asList("One", "Three", "Two"), labels(HomePins.choices()));
        HomePins.setPinned(Arrays.asList(C, A));

        assertEquals(Arrays.asList("Three", "One", "Two"), describe(HomePins.plan(SHORTCUTS, rows), rows));
        assertEquals("Spotify's list is left alone", Arrays.asList("One", "Two", "Three"), describe(rows, rows));
    }

    @Test public void aPinSpotifyLeftOutGetsATileInItsPlaceAmongThePins() {
        ArrayList<Object> rows = rows(row(A, "A"), row(B, "B"), row(C, "C"), row(D, "D"));
        HomePins.plan(SHORTCUTS, rows);
        HomePins.choices(Collections.singletonList(new Library.Item(X, "Road trip", "spotify:image:x", false)));
        HomePins.setPinned(Arrays.asList(C, X, A));

        List<Object> plan = HomePins.plan(SHORTCUTS, rows);

        // Cut to the four rows Spotify sent, so D goes.
        assertEquals(Arrays.asList("C", "new Road trip", "A", "B"), describe(plan, rows));
        assertArrayEquals(new String[]{X, "Road trip", "spotify:image:x"}, (String[]) plan.get(1));
    }

    @Test public void aPinOnHomeReusesSpotifysRowForThatEntity() {
        // A row can open another uri than the entity it stands for.
        ArrayList<Object> rows = rows(row(A, "A"), row("spotify:station:playlist:d", D, "D radio"));
        HomePins.choices(Collections.singletonList(new Library.Item(D, "D", "spotify:image:d", false)));
        HomePins.setPinned(Collections.singletonList(D));

        assertEquals(Arrays.asList("D radio", "A"), describe(HomePins.plan(SHORTCUTS, rows), rows));
    }

    @Test public void aPinGetsNoTileWhenARowAlreadyOpensIt() {
        // The grid keys its rows by link. A is the link of a row for another entity, and B a row's entity.
        // The last row opens Liked Songs under another of its uris.
        ArrayList<Object> rows = rows(row(A, "spotify:playlist:e", "Opens A"), row(B, "B"), row(C, "C"),
                row("spotify:user:me:collection", "spotify:playlist:f", "Opens Liked Songs"));
        HomePins.choices(Arrays.asList(new Library.Item(A, "A", "spotify:image:a", false),
                new Library.Item(B, "B", "spotify:image:b", false),
                new Library.Item("spotify:collection:tracks", "Liked Songs", LIKED_SONGS_IMAGE, false)));
        HomePins.setPinned(Arrays.asList(A, B, "spotify:collection:tracks"));

        assertEquals(Arrays.asList("B", "Opens A", "C", "Opens Liked Songs"),
                describe(HomePins.plan(SHORTCUTS, rows), rows));
    }

    @Test public void otherSectionsAreOnlyReordered() {
        ArrayList<Object> rows = rows(row(A, "A"), row(B, "B"), row(C, "C"));
        HomePins.plan("anchors", rows);
        HomePins.choices(Collections.singletonList(new Library.Item(X, "Road trip", "spotify:image:x", false)));
        HomePins.setPinned(Arrays.asList(X, C));

        for (String section : Arrays.asList("anchors", "wrapped", null)) {
            assertEquals(section, Arrays.asList("C", "A", "B"), describe(HomePins.plan(section, rows), rows));
        }
        assertEquals(Arrays.asList("new Road trip", "C", "A"), describe(HomePins.plan(SHORTCUTS, rows), rows));
    }

    @Test public void theGridKeepsSpotifysSizeUnlessThePinsNeedMoreButNeverPassesTen() {
        List<Library.Item> library = new ArrayList<>();
        List<String> saved = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            library.add(new Library.Item("spotify:playlist:p" + i, "P" + i, "spotify:image:p" + i, false));
            saved.add("spotify:playlist:p" + i);
        }
        HomePins.choices(library);
        ArrayList<Object> ten = rows();
        for (int i = 0; i < 10; i++) ten.add(row("spotify:playlist:s" + i, "S" + i));
        ArrayList<Object> eight = new ArrayList<>(ten.subList(0, 8));
        ArrayList<Object> three = new ArrayList<>(ten.subList(0, 3));

        HomePins.setPinned(saved.subList(0, 3));
        assertEquals(Arrays.asList("new P0", "new P1", "new P2", "S0", "S1", "S2", "S3", "S4"),
                describe(HomePins.plan(SHORTCUTS, eight), eight));
        HomePins.setPinned(saved.subList(0, 5));
        assertEquals(Arrays.asList("new P0", "new P1", "new P2", "new P3", "new P4"),
                describe(HomePins.plan(SHORTCUTS, three), three));
        HomePins.setPinned(saved);
        assertEquals(Arrays.asList("new P0", "new P1", "new P2", "new P3", "new P4", "new P5", "new P6", "new P7",
                "new P8", "new P9"), describe(HomePins.plan(SHORTCUTS, three), three));
        HomePins.setPinned(Collections.emptyList());
        assertEquals(10, HomePins.plan(SHORTCUTS, ten).size());
    }

    @Test public void aPinWithoutATitleGetsNoTileAndDoesntGrowTheGrid() {
        application.getSharedPreferences("spicetify_home_pins", 0).edit().putString("pins", "[{\"uri\":\"" + C
                + "\"},{\"uri\":\"" + D + "\"},{\"uri\":\"" + X + "\",\"title\":\"Road trip\"}]").commit();
        HomePins.initialize(application);
        ArrayList<Object> rows = rows(row(A, "A"), row(B, "B"));

        // Three pins are saved, but only one has a tile, so the grid keeps Spotify's two.
        assertEquals(Arrays.asList("new Road trip", "A"), describe(HomePins.plan(SHORTCUTS, rows), rows));
    }

    @Test public void aTileForAPinWithoutACoverGetsAnEmptyImageNotNull() {
        // Spotify's renderers call Uri.parse on the image, which throws on null. Its own tiles get "" for no image.
        ArrayList<Object> rows = rows(row(A, "Home tile"), row(B, "B"));
        HomePins.plan(SHORTCUTS, rows);
        HomePins.setPinned(Collections.singletonList(A)); // a Home tile, which has no stored cover
        ArrayList<Object> later = rows(row(B, "B"), row(C, "C"));

        List<Object> plan = HomePins.plan(SHORTCUTS, later);

        assertEquals(Arrays.asList("new Home tile", "B"), describe(plan, later));
        assertArrayEquals(new String[]{A, "Home tile", ""}, (String[]) plan.get(0));
    }

    @Test public void aLikedSongsPinMatchesHomesTileWhicheverOfItsUrisEachUses() {
        // A pin saved from Home under Liked Songs' list uri, while Home's tile now uses a user uri.
        application.getSharedPreferences("spicetify_home_pins", 0).edit().putString("pins",
                "[{\"uri\":\"spotify:playlist:37i9dQZF1F5p3rmiWPIYgZ\",\"title\":\"Liked Songs\"},"
                        + "{\"uri\":\"" + X + "\",\"title\":\"Road trip\"}]").commit();
        HomePins.initialize(application);
        ArrayList<Object> rows = rows(row(A, "A"), row("spotify:user:me:collection", "Liked Songs"));

        assertEquals("Home's tile moves, with no second one, and the next pin comes after it",
                Arrays.asList("Liked Songs", "new Road trip"), describe(HomePins.plan(SHORTCUTS, rows), rows));
        ArrayList<Object> without = rows(row(A, "A"), row(B, "B"));
        List<Object> plan = HomePins.plan(SHORTCUTS, without);
        assertEquals(Arrays.asList("new Liked Songs", "new Road trip"), describe(plan, without));
        assertEquals("the tile opens the uri Spotify's navigator routes", "spotify:collection:tracks",
                ((String[]) plan.get(0))[0]);
    }

    @Test public void corruptSavedPinsDoNotBreakHome() {
        application.getSharedPreferences("spicetify_home_pins", 0).edit()
                .putString("pins", "not JSON").commit();
        HomePins.initialize(application);
        assertArrayEquals(new int[]{0}, HomePins.captureAndOrder(new String[]{A}, new String[]{"A"}));
        assertFalse(HomePins.choices().get(0).pinned);
    }

    @Test public void thePickerListsPinsInPinOrderThenHomeThenPlaylistsThenAlbumsEachAlphabetically() {
        List<Library.Item> library = Arrays.asList(
                new Library.Item("spotify:collection:tracks", "Liked Songs", LIKED_SONGS_IMAGE, false),
                new Library.Item("spotify:playlist:zulu", "Zulu", "spotify:image:z", false),
                new Library.Item("spotify:album:beta", "Beta", "spotify:image:b", true),
                new Library.Item("spotify:playlist:road", "Road trip", "spotify:image:r", false),
                new Library.Item("spotify:playlist:alpha", "alpha", null, false),
                new Library.Item("spotify:album:aardvark", "Aardvark", null, true));
        HomePins.choices(library);
        HomePins.setPinned(Arrays.asList("spotify:playlist:zulu", "spotify:album:beta"));
        HomePins.captureAndOrder(new String[]{"spotify:playlist:mixb", "spotify:playlist:road", "spotify:playlist:mixa"},
                new String[]{"Mix B", "Road trip", "mix a"});

        List<HomePins.Choice> choices = HomePins.choices(library);

        // Road trip is on Home and in the library, and shows once, with Home's tiles.
        assertEquals(Arrays.asList("Zulu", "Beta", "mix a", "Mix B", "Road trip", "alpha", "Liked Songs", "Aardvark"),
                labels(choices));
        assertEquals(Arrays.asList(true, true, false, false, false, false, false, false), pinned(choices));
        // Before the library comes, the pins and Home's tiles alone.
        assertEquals(Arrays.asList("Zulu", "Beta", "mix a", "Mix B", "Road trip"), labels(HomePins.choices()));
    }

    @Test public void pinsSavedBeforeCoversWereKeptStillLoad() {
        application.getSharedPreferences("spicetify_home_pins", 0).edit().putString("pins",
                "[{\"id\":\"" + A + "\",\"label\":\"Old A\"},{\"id\":\"" + B + "\",\"label\":\"Old B\"}]").commit();

        HomePins.initialize(application);

        List<HomePins.Choice> choices = HomePins.choices();
        assertEquals(Arrays.asList("Old A", "Old B"), labels(choices));
        assertEquals(Arrays.asList(true, true), pinned(choices));
        assertArrayEquals("they still come first on Home", new int[]{1, 2, 0},
                HomePins.captureAndOrder(new String[]{C, A, B}, new String[]{"C", "A", "B"}));
    }

    @Test public void openingThePickerRefreshesTheTitlesAndCoversOfPinsInTheLibrary() throws Exception {
        application.getSharedPreferences("spicetify_home_pins", 0).edit().putString("pins",
                "[{\"id\":\"" + A + "\",\"label\":\"Old A\"},{\"uri\":\"" + B
                        + "\",\"title\":\"B\",\"image\":\"spotify:image:b\"}]").commit();
        HomePins.initialize(application);

        List<HomePins.Choice> choices = HomePins.choices(Collections.singletonList(
                new Library.Item(A, "New A", "spotify:image:new", false)));

        assertEquals(Arrays.asList("New A", "B"), labels(choices));
        JSONArray saved = saved();
        assertEquals(2, saved.length());
        assertPin(saved.getJSONObject(0), A, "New A", "spotify:image:new");
        assertPin(saved.getJSONObject(1), B, "B", "spotify:image:b"); // not in the library, so left alone
        HomePins.initialize(application);
        assertEquals(Arrays.asList("New A", "B"), labels(HomePins.choices()));
    }

    @Test public void aRefreshKeepsTheStoredTitleAndCoverWhereTheLibraryHasNone() throws Exception {
        application.getSharedPreferences("spicetify_home_pins", 0).edit().putString("pins", "[{\"uri\":\"" + A
                + "\",\"title\":\"Old A\",\"image\":\"spotify:image:a\"},{\"uri\":\"" + B
                + "\",\"title\":\"Old B\",\"image\":\"spotify:image:b\"}]").commit();
        HomePins.initialize(application);

        List<HomePins.Choice> choices = HomePins.choices(Arrays.asList(new Library.Item(A, null, null, false),
                new Library.Item(B, "New B", "", false)));

        assertEquals(Arrays.asList("Old A", "New B"), labels(choices));
        assertPin(saved().getJSONObject(0), A, "Old A", "spotify:image:a");
        assertPin(saved().getJSONObject(1), B, "New B", "spotify:image:b");
    }

    @Test public void pinsAreSavedWithTheTitleAndCoverTheyWereOfferedWith() throws Exception {
        HomePins.captureAndOrder(new String[]{B, C}, new String[]{"Home tile", "Chill on Home"});
        HomePins.choices(Arrays.asList(new Library.Item(A, "Road trip", "spotify:image:r", false),
                new Library.Item(C, "Chill", "spotify:image:c", true)));

        HomePins.setPinned(Arrays.asList(A, B, C));

        JSONArray saved = saved();
        assertEquals(3, saved.length());
        assertPin(saved.getJSONObject(0), A, "Road trip", "spotify:image:r");
        assertPin(saved.getJSONObject(1), B, "Home tile", null);
        // A Home tile that's also saved takes Home's title and the library's cover.
        assertPin(saved.getJSONObject(2), C, "Chill on Home", "spotify:image:c");
    }

    @Test public void likedSongsIsOneChoiceWhicheverOfItsUrisHomeUses() {
        String[] home = {A, "spotify:user:u:collection"};
        HomePins.captureAndOrder(home, new String[]{"A", "Liked Songs"});
        List<HomePins.Choice> choices = HomePins.choices(Collections.singletonList(
                new Library.Item("spotify:collection:tracks", "Liked Songs", LIKED_SONGS_IMAGE, false)));
        assertEquals(Arrays.asList("A", "Liked Songs"), labels(choices));
        HomePins.setPinned(Collections.singletonList(choices.get(1).id));
        assertArrayEquals("Home's own Liked Songs tile comes first", new int[]{1, 0},
                HomePins.captureAndOrder(home, new String[]{"A", "Liked Songs"}));
    }

    @Test public void aLikedSongsPinSavedUnderAnotherUriIsRefreshedAndListedOnce() throws Exception {
        application.getSharedPreferences("spicetify_home_pins", 0).edit().putString("pins",
                "[{\"id\":\"spotify:user:u:collection\",\"label\":\"Liked Songs\"}]").commit();
        HomePins.initialize(application);
        List<HomePins.Choice> choices = HomePins.choices(Collections.singletonList(
                new Library.Item("spotify:collection:tracks", "Lieblingssongs", LIKED_SONGS_IMAGE, false)));
        assertEquals(Arrays.asList("Lieblingssongs"), labels(choices));
        assertTrue(choices.get(0).pinned);
        assertPin(saved().getJSONObject(0), "spotify:collection:tracks", "Lieblingssongs", LIKED_SONGS_IMAGE);
    }

    @Test public void likedSongsIsOneChoiceAndOnePinWhicheverOfItsUrisHomeTheLibraryAndThePickUse() throws Exception {
        HomePins.captureAndOrder(new String[]{"spotify:user:u:collection"}, new String[]{"Liked Songs"});
        List<HomePins.Choice> choices = HomePins.choices(Collections.singletonList(
                new Library.Item("spotify:internal:collection:tracks", "Liked Songs", LIKED_SONGS_IMAGE, false)));
        assertEquals(Arrays.asList("Liked Songs"), labels(choices));

        HomePins.setPinned(Collections.singletonList("spotify:playlist:37i9dQZF1F5p3rmiWPIYgZ"));

        assertPin(saved().getJSONObject(0), "spotify:collection:tracks", "Liked Songs", LIKED_SONGS_IMAGE);
    }

    @Test public void aNullHomeIdStillLeavesTheOrderAlone() {
        assertArrayEquals(new int[]{0, 1}, HomePins.captureAndOrder(new String[]{null, A}, new String[]{"x", "A"}));
    }

    /** Spotify's row for a tile that opens {@code uri} and stands for it. */
    private static p.goz0 row(String uri, String title) {
        return row(uri, uri, title);
    }

    /** Spotify's row for a tile that opens {@code link} and stands for {@code entity}, hidable as a shortcut. */
    private static p.goz0 row(String link, String entity, String title) {
        return new p.goz0(new p.nnz0(link, 2, title, "spotify:image:" + title, false, entity));
    }

    private static ArrayList<Object> rows(Object... rows) {
        return new ArrayList<>(Arrays.asList(rows));
    }

    /** Each entry of {@code plan}: the title of that very row of {@code rows}, or "new title" for a tile to make. */
    private static List<String> describe(List<Object> plan, List<Object> rows) {
        List<String> entries = new ArrayList<>();
        for (Object entry : plan) {
            if (entry instanceof String[]) entries.add("new " + ((String[]) entry)[1]);
            else entries.add(rows.contains(entry) ? ((p.goz0) entry).a.b : "a row Spotify didn't send");
        }
        return entries;
    }

    private JSONArray saved() throws Exception {
        SharedPreferences preferences = application.getSharedPreferences("spicetify_home_pins", 0);
        return new JSONArray(preferences.getString("pins", "[]"));
    }

    private static void assertPin(JSONObject pin, String uri, String title, String image) throws Exception {
        assertEquals(uri, pin.getString("uri"));
        assertEquals(title, pin.getString("title"));
        if (image == null) assertFalse(pin.toString(), pin.has("image"));
        else assertEquals(image, pin.getString("image"));
        assertFalse("the old keys are gone: " + pin, pin.has("id") || pin.has("label"));
    }

    private static List<String> labels(List<HomePins.Choice> choices) {
        List<String> labels = new ArrayList<>();
        for (HomePins.Choice choice : choices) labels.add(choice.label);
        return labels;
    }

    private static List<Boolean> pinned(List<HomePins.Choice> choices) {
        List<Boolean> pinned = new ArrayList<>();
        for (HomePins.Choice choice : choices) pinned.add(choice.pinned);
        return pinned;
    }
}
