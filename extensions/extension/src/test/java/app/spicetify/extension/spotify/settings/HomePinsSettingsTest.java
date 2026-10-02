package app.spicetify.extension.spotify.settings;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import app.spicetify.extension.spotify.extensions.LibraryTest;
import app.spicetify.extension.spotify.extensions.PlayerBridgeTest;
import app.spicetify.extension.spotify.home.HomePins;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, shadows = HomePinsSettingsTest.Capabilities.class)
public class HomePinsSettingsTest {
    private static final String LOADING = "Loading your library";
    private static final String UNAVAILABLE = "Your library isn't available yet";
    private static final String A = "spotify:playlist:a";
    private static final String B = "spotify:playlist:b";

    @Implements(value = InstalledPatches.class, isInAndroidSdk = false)
    public static class Capabilities {
        @Implementation public static boolean homePins() { return true; }
    }

    @Before public void initialize() {
        RuntimeEnvironment.getApplication().deleteSharedPreferences("spicetify_home_pins");
        RuntimeEnvironment.getApplication().deleteSharedPreferences("spicetify_patch_settings");
        PatchSettings.initialize(RuntimeEnvironment.getApplication()); // and Home pins, which it starts
    }

    @After public void closePages() {
        // The settings screen keeps its open pages on a static list until they close, which would keep
        // every test's activity, page and picker for the rest of the run.
        for (Dialog dialog : new ArrayList<>(ShadowDialog.getShownDialogs())) dialog.dismiss();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void withNothingToListThePickerSaysYourLibraryIsntAvailableYet() throws Exception {
        PlayerBridgeTest.attachRouter(true); // Spotify destroyed its router, as on a logout
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();

        Dialog picker = openPicker(activity);

        awaitNote(picker, UNAVAILABLE);
        assertEquals(0, list(picker).getAdapter().getCount());
        assertNull("no sheet asks for a visit to Home", labeled(picker.getWindow().getDecorView(), "No Home shortcuts loaded"));
        // The sheet pads its buttons by its owner's insets, and a page hands it a themed wrapper of that activity.
        assertSame(activity, picker.getOwnerActivity());
    }

    @Test public void thePickerListsYourLibraryWithoutAVisitToHome() throws Exception {
        LibraryTest.attachLibrary(new String[]{"spotify:playlist:road", "Road trip", "spotify:image:r"},
                new String[]{"spotify:album:blue", "Blue", "spotify:image:b"},
                new String[]{"spotify:playlist:chill", "Chill", null},
                new String[]{"spotify:playlist:chill2", "Chill", null});

        Dialog picker = openPicker();

        assertEquals(LOADING, note(picker).getText().toString());
        // A name that two share shows each one's uri too.
        awaitRows(picker, "Chill\nspotify:playlist:chill", "Chill\nspotify:playlist:chill2", "Liked Songs", "Road trip",
                "Blue");
        assertEquals(View.GONE, note(picker).getVisibility());
    }

    @Test public void withoutTheBridgeThePickerListsHomesTilesAndSaysYourLibraryIsntAvailableYet() throws Exception {
        PlayerBridgeTest.attachRouter(true);
        observe(new String[]{B, A}, new String[]{"Two", "One"});

        Dialog picker = openPicker();

        awaitNote(picker, UNAVAILABLE);
        assertEquals(View.VISIBLE, note(picker).getVisibility());
        assertEquals(Arrays.asList("One", "Two"), rows(picker));
    }

    @Test public void searchNarrowsTheListAndWhatIsCheckedStaysChecked() throws Exception {
        PlayerBridgeTest.attachRouter(true);
        observe(new String[]{"spotify:playlist:mixb", "spotify:playlist:road", "spotify:playlist:mixa"},
                new String[]{"Mix B", "Road trip", "mix a"});
        Dialog picker = openPicker();
        awaitNote(picker, UNAVAILABLE);
        EditText search = find(picker.getWindow().getDecorView(), EditText.class);

        search.setText("MIX");
        assertEquals(Arrays.asList("mix a", "Mix B"), rows(picker));
        tap(picker, 1);
        search.setText("");

        assertEquals(Arrays.asList("mix a", "Mix B", "Road trip"), rows(picker));
        assertEquals(Arrays.asList(false, true, false), checked(picker));
        search.setText("a"); // Road trip takes Mix B's place in the list, unticked
        assertEquals(Arrays.asList("mix a", "Road trip"), rows(picker));
        assertEquals(Arrays.asList(false, false), checked(picker));
        button(picker, "Save").performClick(); // while the search hides the pick
        assertEquals(Arrays.asList("spotify:playlist:mixb"), pinned());
    }

    @Test public void aTouchOnARowTicksIt() throws Exception {
        PlayerBridgeTest.attachRouter(true);
        observe(new String[]{A, B}, new String[]{"One", "Two"});
        Dialog picker = openPicker();
        awaitNote(picker, UNAVAILABLE);

        touch(picker, 1);

        assertEquals(Arrays.asList(false, true), checked(picker));
        assertTrue("its box shows the tick", ((CheckBox) list(picker).getChildAt(1)).isChecked());
        button(picker, "Save").performClick();
        assertEquals(Arrays.asList(B), pinned());
    }

    @Test public void aBigLibraryGetsRowsOnlyForWhatsOnScreen() throws Exception {
        String[][] library = new String[2000][];
        for (int i = 0; i < library.length; i++) library[i] = new String[]{"spotify:playlist:p" + i, "Playlist " + i, null};
        LibraryTest.attachLibrary(library);
        Dialog picker = openPicker();
        finishFetch();

        assertEquals("Liked Songs and every playlist", 2001, list(picker).getAdapter().getCount());
        int made = onScreen(picker).size();
        assertTrue("rows only for what's on screen: " + made, made > 0 && made < 30);
        ListView list = list(picker);
        View row = list.getAdapter().getView(0, null, list);
        assertSame("a row that scrolls off is reused", row, list.getAdapter().getView(1, row, list));
        assertEquals("Playlist 0", ((TextView) row).getText().toString());
        int height = list.getHeight();
        find(picker.getWindow().getDecorView(), EditText.class).setText("playlist 1999");
        assertEquals(Arrays.asList("Playlist 1999"), rows(picker));
        assertEquals(Arrays.asList("Playlist 1999"), onScreen(picker));
        assertEquals("the sheet keeps its size while searching", height, list.getHeight());
    }

    @Test public void excessSelectionKeepsPickerOpenAndLeavesSavedPinsUntouched() throws Exception {
        JSONArray saved = new JSONArray();
        for (int i = 0; i < 64; i++) saved.put(new JSONObject().put("id", "spotify:playlist:" + i).put("label", "Playlist " + i));
        stored().edit().putString("pins", saved.toString()).commit();
        HomePins.initialize(RuntimeEnvironment.getApplication());
        observe(new String[]{"spotify:playlist:new"}, new String[]{"New playlist"});
        PlayerBridgeTest.attachRouter(true);
        Dialog picker = openPicker();
        awaitNote(picker, UNAVAILABLE);
        assertEquals(65, rows(picker).size());
        assertEquals("New playlist", rows(picker).get(64));

        tap(picker, 64);
        button(picker, "Save").performClick();

        assertTrue(picker.isShowing());
        assertEquals("the sheet says why", View.VISIBLE, labeled(picker.getWindow().getDecorView(), "Too many Home pins.").getVisibility());
        assertNull("not in a Toast", ShadowToast.getLatestToast());
        assertEquals(64, pinned().size());
        tap(picker, 64);
        button(picker, "Save").performClick();
        assertFalse(picker.isShowing());
    }

    @Test public void savingOffersToRestartSpotifyAndTheHomePageKeepsTheRestartBar() throws Exception {
        PlayerBridgeTest.attachRouter(true);
        observe(new String[]{A}, new String[]{"One"});
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        SpicetifySettingsScreen.open(activity, SpicetifySettingsScreen.PAGE_HOME);
        View home = ShadowDialog.getLatestDialog().getWindow().getDecorView();
        assertEquals(View.GONE, restartBar(home).getVisibility());
        choose(home).performClick();
        Dialog picker = ShadowDialog.getLatestDialog();
        awaitNote(picker, UNAVAILABLE);

        tap(picker, 0);
        button(picker, "Save").performClick();

        assertFalse(picker.isShowing());
        assertEquals(Arrays.asList(A), pinned());
        assertNotNull(labeled(ShadowDialog.getLatestDialog().getWindow().getDecorView(), "Restart Spotify to update Home?"));
        assertEquals(View.VISIBLE, restartBar(home).getVisibility());
    }

    @Test public void cancelSavesNothing() throws Exception {
        PlayerBridgeTest.attachRouter(true);
        observe(new String[]{A}, new String[]{"One"});
        Dialog picker = openPicker();
        awaitNote(picker, UNAVAILABLE);

        tap(picker, 0);
        button(picker, "Cancel").performClick();

        assertFalse(picker.isShowing());
        assertTrue(pinned().isEmpty());
        assertFalse(PatchSettings.restartRequired());
    }

    @Test public void aPickThatLeavesTheListWhenTheLibraryComesIsDropped() throws Exception {
        LibraryTest.attachLibrary(new String[]{"spotify:playlist:road", "Road trip", null});
        observe(new String[]{A, B}, new String[]{"Alpha", "Bravo"});
        Dialog picker = openPicker();
        tap(picker, 1);
        observe(new String[]{A}, new String[]{"Alpha"}); // Home refreshed without Bravo

        awaitRows(picker, "Alpha", "Liked Songs", "Road trip");
        button(picker, "Save").performClick();

        assertFalse("saved without a complaint", picker.isShowing());
        assertTrue(pinned().isEmpty());
    }

    /** Opens the Home and navigation page over a new activity and taps "Pinned Home shortcuts". */
    private Dialog openPicker() {
        return openPicker(Robolectric.buildActivity(Activity.class).setup().get());
    }

    private Dialog openPicker(Activity activity) {
        SpicetifySettingsScreen.open(activity, SpicetifySettingsScreen.PAGE_HOME);
        choose(ShadowDialog.getLatestDialog().getWindow().getDecorView()).performClick();
        return ShadowDialog.getLatestDialog();
    }

    /** Home's tiles, as its shortcut model hands them to HomePins. */
    private static void observe(String[] ids, String[] titles) throws Exception {
        java.lang.reflect.Method capture = HomePins.class.getDeclaredMethod("captureAndOrder", String[].class, String[].class);
        capture.setAccessible(true);
        capture.invoke(null, ids, titles);
    }

    /** The pins, in pin order. */
    private static List<String> pinned() {
        List<String> ids = new ArrayList<>();
        for (HomePins.Choice choice : HomePins.choices()) if (choice.pinned) ids.add(choice.id);
        return ids;
    }

    /** Where HomePins keeps the pins. */
    private static SharedPreferences stored() {
        return RuntimeEnvironment.getApplication().getSharedPreferences("spicetify_home_pins", 0);
    }

    /** Lets the library's fetch end, then checks that the picker lists {@code expected}. */
    private static void awaitRows(Dialog picker, String... expected) throws Exception {
        finishFetch();
        assertEquals(Arrays.asList(expected), rows(picker));
    }

    /** Lets the library's fetch end, then checks the picker's note reads {@code text}. */
    private static void awaitNote(Dialog picker, String text) throws Exception {
        finishFetch();
        assertEquals(text, note(picker).getText().toString());
    }

    /** Runs the picker's library fetch to its end: its tasks on the bridge thread, then the main looper, where its answer lands. */
    private static void finishFetch() throws Exception {
        LibraryTest.awaitFetch();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    /** The picker's list of choices. */
    private static ListView list(Dialog picker) {
        return find(picker.getWindow().getDecorView(), ListView.class);
    }

    /** Each listed row's text, as the list's adapter makes it, on screen or not. */
    private static List<String> rows(Dialog picker) {
        ListView list = list(picker);
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < list.getAdapter().getCount(); i++) {
            rows.add(((TextView) list.getAdapter().getView(i, null, list)).getText().toString());
        }
        return rows;
    }

    /** Whether each listed row is ticked. */
    private static List<Boolean> checked(Dialog picker) {
        ListView list = list(picker);
        List<Boolean> checked = new ArrayList<>();
        for (int i = 0; i < list.getAdapter().getCount(); i++) checked.add(list.isItemChecked(i));
        return checked;
    }

    /** Taps the listed row at {@code position}. */
    private static void tap(Dialog picker, int position) {
        ListView list = list(picker);
        list.performItemClick(null, position, list.getAdapter().getItemId(position));
    }

    /** Touches the row on screen at {@code index} as a finger would, then lets the list act on it. */
    private static void touch(Dialog picker, int index) {
        ListView list = list(picker);
        onScreen(picker);
        View row = list.getChildAt(index);
        float x = row.getWidth() / 2f;
        float y = row.getTop() + row.getHeight() / 2f;
        long now = SystemClock.uptimeMillis();
        list.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0));
        list.dispatchTouchEvent(MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, x, y, 0));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
    }

    /** Lets the sheet lay out, then gives the list's rows on screen: their number and texts. */
    private static List<String> onScreen(Dialog picker) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        ListView list = list(picker);
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) texts.add(((TextView) list.getChildAt(i)).getText().toString());
        return texts;
    }

    /** The line under the search field, whichever of its texts it shows. */
    private static TextView note(Dialog picker) {
        View sheet = picker.getWindow().getDecorView();
        TextView note = labeled(sheet, LOADING);
        if (note == null) note = labeled(sheet, UNAVAILABLE);
        assertNotNull("the picker has no note under its search field", note);
        return note;
    }

    private static Button button(Dialog dialog, String label) {
        List<Button> buttons = new ArrayList<>();
        collect(dialog.getWindow().getDecorView(), Button.class, buttons);
        for (Button button : buttons) if (label.contentEquals(button.getText())) return button;
        throw new AssertionError("Missing button: " + label);
    }

    private static <T extends View> void collect(View view, Class<T> kind, List<T> out) {
        if (kind.isInstance(view) && !(kind == Button.class && view instanceof CheckBox)) out.add(kind.cast(view));
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), kind, out);
        }
    }

    private static <T extends View> T find(View view, Class<T> kind) {
        List<T> found = new ArrayList<>();
        collect(view, kind, found);
        assertFalse("no " + kind.getSimpleName(), found.isEmpty());
        return found.get(0);
    }

    private static TextView labeled(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = labeled(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View choose(View view) {
        if (view.isClickable() && "Pinned Home shortcuts".contentEquals(view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = choose(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    private static View restartBar(View page) {
        List<Button> buttons = new ArrayList<>();
        collect(page, Button.class, buttons);
        for (Button button : buttons) if ("Restart".contentEquals(button.getText())) return (View) button.getParent().getParent();
        throw new AssertionError("no restart bar");
    }
}
