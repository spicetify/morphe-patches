package app.spicetify.extension.spotify.theme;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns a Spicetify color scheme into Spotify color roles. Roles it leaves out keep Spotify's colors. */
public final class ThemeResolver {
    static final List<String> ROLES = Collections.unmodifiableList(Arrays.asList(
            "main", "main-elevated", "card", "highlight", "highlight-elevated", "text", "subtext",
            "button", "button-active", "on-button", "button-disabled", "selected-row", "tab-active",
            "notification", "notification-error", "shadow"));
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    public static final class Result {
        public final Map<String, Integer> colors;

        Result(Map<String, Integer> colors) {
            this.colors = Collections.unmodifiableMap(colors);
        }
    }

    private ThemeResolver() {}

    public static Result resolve(Map<String, Integer> scheme, String accentKey) {
        Map<String, Integer> roles = new LinkedHashMap<>();
        for (String role : ROLES) {
            if (role.equals("button") || role.equals("on-button")) continue;
            Integer color = scheme.get(role);
            if (color != null) roles.put(role, color);
        }
        Integer accent = scheme.get(accentKey);
        if (accent == null && !accentKey.equals("button")) {
            throw new ThemeException("Accent key \"" + accentKey + "\" is not in this color scheme.");
        }
        if (accent != null) roles.put("button", accent);

        Integer main = roles.get("main");
        if (main != null) {
            // Spotify's stock surfaces are #121212 mixed toward white by these amounts.
            int toward = roles.containsKey("text") ? roles.get("text") : WHITE;
            roles.putIfAbsent("main-elevated", ArgbColors.mix(main, toward, 0.06));
            roles.putIfAbsent("highlight", ArgbColors.mix(main, toward, 0.04));
            roles.putIfAbsent("highlight-elevated", ArgbColors.mix(main, toward, 0.10));
        }
        Integer button = roles.get("button");
        if (button != null) {
            Integer active = roles.get("button-active");
            // A custom accent key replaces the scheme's own pressed color, which belongs to the old accent.
            if (!accentKey.equals("button") || active == null || active.equals(button)) {
                roles.put("button-active", ArgbColors.mix(button, BLACK, 0.125));
            }
            roles.put("on-button", ArgbColors.contrast(button, BLACK) >= ArgbColors.contrast(button, WHITE) ? BLACK : WHITE);
        }
        return new Result(roles);
    }
}
