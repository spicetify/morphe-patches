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
        java.util.ArrayList<Object> nativeRows = new java.util.ArrayList<>();
        nativeRows.add(new Object());
        assertSame(nativeRows, HomePins.reorder(nativeRows));
        assertEquals(1, nativeRows.size());
        assertTrue(HomePins.choices().isEmpty());
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
