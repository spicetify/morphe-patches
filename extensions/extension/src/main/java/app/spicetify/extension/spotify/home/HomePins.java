package app.spicetify.extension.spotify.home;

import android.content.Context;
import android.content.SharedPreferences;
import app.spicetify.extension.spotify.extensions.Library;
import java.lang.reflect.Field;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Reorders only shortcuts supplied by Spotify; it never creates or retains native tile objects. */
public final class HomePins {
    private static final int MAX_SHORTCUTS = 64;
    private static SharedPreferences preferences;
    private static final LinkedHashMap<String, Pin> pins = new LinkedHashMap<>();
    private static final LinkedHashMap<String, String> observed = new LinkedHashMap<>();
    private static final LinkedHashMap<String, Pin> offered = new LinkedHashMap<>();

    private HomePins() {}

    public static final class Choice {
        public final String id;
        public final String label;
        public final boolean pinned;

        private Choice(String id, String label, boolean pinned) {
            this.id = id;
            this.label = label;
            this.pinned = pinned;
        }
    }

    /** A pin's title and cover; either is null when unknown, as a cover is for pins saved before covers were kept. */
    private static final class Pin {
        final String title;
        final String image;

        Pin(String title, String image) {
            this.title = title == null || title.trim().isEmpty() ? null : title;
            this.image = image == null || image.isEmpty() ? null : image;
        }
    }

    public static synchronized void initialize(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences("spicetify_home_pins", Context.MODE_PRIVATE);
        observed.clear();
        offered.clear();
        pins.clear();
        try {
            JSONArray saved = new JSONArray(preferences.getString("pins", "[]"));
            for (int i = 0; i < Math.min(saved.length(), MAX_SHORTCUTS); i++) {
                JSONObject pin = saved.getJSONObject(i);
                // Pins saved before covers were kept have an id and a label instead of a uri and a title.
                String id = key(pin.optString("uri", pin.optString("id")));
                if (!validId(id)) continue;
                pins.put(id, new Pin(pin.optString("title", pin.optString("label")), pin.optString("image", null)));
            }
        } catch (JSONException ignored) {
            pins.clear();
        }
    }

    /** The pins, then Home's tiles: {@link #choices(List)} before the library comes. */
    public static List<Choice> choices() {
        return choices(Collections.emptyList());
    }

    /**
     * The picker's list: the pins in pin order, then Home's tiles, then {@code library}'s playlists,
     * then its albums, each of those three alphabetically, with every uri once. Pins found in
     * {@code library} take its title and cover, and are saved with them.
     */
    public static synchronized List<Choice> choices(List<Library.Item> library) {
        Map<String, Library.Item> inLibrary = new LinkedHashMap<>();
        for (Library.Item item : library) if (validId(key(item.uri))) inLibrary.putIfAbsent(key(item.uri), item);
        boolean refreshed = false;
        for (Map.Entry<String, Pin> pin : pins.entrySet()) {
            Library.Item item = inLibrary.get(pin.getKey());
            if (item == null) continue;
            // An entry without a name or a cover keeps the stored one.
            Pin fresh = new Pin(item.title, item.image);
            pin.setValue(new Pin(fresh.title != null ? fresh.title : pin.getValue().title,
                    fresh.image != null ? fresh.image : pin.getValue().image));
            refreshed = true;
        }
        if (refreshed) save(pins);

        offered.clear();
        List<Choice> result = new ArrayList<>();
        for (Map.Entry<String, Pin> pin : pins.entrySet()) {
            String title = observed.containsKey(pin.getKey()) ? observed.get(pin.getKey()) : pin.getValue().title;
            result.add(offer(pin.getKey(), title, pin.getValue().image, true));
        }
        List<Choice> home = new ArrayList<>();
        for (Map.Entry<String, String> tile : observed.entrySet()) {
            if (pins.containsKey(tile.getKey())) continue;
            Library.Item item = inLibrary.get(tile.getKey());
            home.add(offer(tile.getKey(), tile.getValue(), item == null ? null : item.image, false));
        }
        List<Choice> playlists = new ArrayList<>();
        List<Choice> albums = new ArrayList<>();
        for (Map.Entry<String, Library.Item> entry : inLibrary.entrySet()) {
            if (pins.containsKey(entry.getKey()) || observed.containsKey(entry.getKey())) continue;
            Library.Item item = entry.getValue();
            (item.album ? albums : playlists).add(offer(entry.getKey(), item.title, item.image, false));
        }
        Collator alphabetically = Collator.getInstance();
        for (List<Choice> group : Arrays.asList(home, playlists, albums)) {
            Collections.sort(group, (a, b) -> alphabetically.compare(a.label, b.label));
            result.addAll(group);
        }
        return Collections.unmodifiableList(result);
    }

    /** A choice for {@code id}, which {@link #setPinned} then saves with this title and cover. */
    private static Choice offer(String id, String title, String image, boolean pinned) {
        offered.put(id, new Pin(title, image));
        return new Choice(id, label(title, id), pinned);
    }

    /** Selection order becomes pin order. Absent pins remain selectable until explicitly removed. */
    public static synchronized void setPinned(List<String> ids) {
        if (preferences == null) throw new IllegalStateException("Home pins are not initialized.");
        if (ids == null || ids.size() > MAX_SHORTCUTS) throw new IllegalArgumentException("Too many Home pins.");
        LinkedHashMap<String, Pin> selected = new LinkedHashMap<>();
        for (String chosen : ids) {
            String id = key(chosen);
            Pin known = offered.containsKey(id) ? offered.get(id) : pins.get(id);
            if (known == null && !observed.containsKey(id)) {
                throw new IllegalArgumentException("Choose a shortcut shown in Home pins.");
            }
            selected.put(id, new Pin(observed.containsKey(id) ? observed.get(id) : known.title,
                    known == null ? null : known.image));
        }
        save(selected);
        pins.clear();
        pins.putAll(selected);
    }

    /** Saves {@code selected} as the pins, each {uri, title, image}, leaving out a title or cover it doesn't know. */
    private static void save(Map<String, Pin> selected) {
        JSONArray saved = new JSONArray();
        try {
            for (Map.Entry<String, Pin> pin : selected.entrySet()) {
                saved.put(new JSONObject().put("uri", pin.getKey()).put("title", pin.getValue().title)
                        .put("image", pin.getValue().image));
            }
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        preferences.edit().putString("pins", saved.toString()).apply();
    }

    /** Field names and the constructor call site are checked against the stock DEX before patching. */
    public static ArrayList<?> reorder(ArrayList<?> input) {
        if (input == null || input.size() > MAX_SHORTCUTS) return input;
        try {
            String[] ids = new String[input.size()];
            String[] titles = new String[input.size()];
            for (int i = 0; i < input.size(); i++) {
                Object row = input.get(i);
                if (row == null || !row.getClass().getName().equals("p.goz0")) return input;
                Field itemField = row.getClass().getField("a");
                Object item = itemField.get(row);
                if (item == null || !item.getClass().getName().equals("p.nnz0")) return input;
                ids[i] = (String) item.getClass().getField("d").get(item);
                titles[i] = (String) item.getClass().getField("b").get(item);
            }
            int[] order = captureAndOrder(ids, titles);
            ArrayList<Object> result = new ArrayList<>(input.size());
            for (int index : order) result.add(input.get(index));
            return result;
        } catch (ReflectiveOperationException | ClassCastException | SecurityException changedNativeModel) {
            return input;
        }
    }

    static synchronized int[] captureAndOrder(String[] ids, String[] titles) {
        int[] result = new int[ids.length];
        if (preferences == null) {
            for (int i = 0; i < ids.length; i++) result[i] = i;
            return result;
        }
        String[] keys = new String[ids.length];
        for (int i = 0; i < ids.length; i++) keys[i] = key(ids[i]);
        observed.clear();
        for (int i = 0; i < keys.length; i++) {
            if (validId(keys[i])) observed.put(keys[i], label(titles[i], keys[i]));
        }
        int cursor = 0;
        for (String pin : pins.keySet()) {
            for (int i = 0; i < keys.length; i++) if (pin.equals(keys[i])) result[cursor++] = i;
        }
        for (int i = 0; i < keys.length; i++) if (!pins.containsKey(keys[i])) result[cursor++] = i;
        return result;
    }

    /**
     * Liked Songs has four uris, and the pins, Home's tiles and the library all name it by
     * {@link Library#LIKED_SONGS}, so it's one choice and a pin finds Home's tile whichever uri each
     * uses. Any other uri, or null, stays as it is.
     */
    private static String key(String uri) {
        return Library.isLikedSongs(uri) ? Library.LIKED_SONGS : uri;
    }

    private static boolean validId(String id) {
        return id != null && id.startsWith("spotify:") && id.length() > 8 && id.length() <= 2048
                && id.indexOf('\n') < 0 && id.indexOf('\r') < 0;
    }

    private static String label(String title, String id) {
        return title == null || title.trim().isEmpty() ? id : title;
    }
}
