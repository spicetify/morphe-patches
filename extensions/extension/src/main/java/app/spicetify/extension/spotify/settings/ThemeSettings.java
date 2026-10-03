package app.spicetify.extension.spotify.settings;

import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.spicetify.extension.spotify.theme.ThemePresets;
import app.spicetify.extension.spotify.theme.ThemeRuntime;
import app.spicetify.extension.spotify.theme.ThemeState;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** The Appearance page: a list of themes, with individually picked colors as the custom option. */
final class ThemeSettings {
    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})");
    static final int[] BACKGROUNDS = {0xFF000000, 0xFF121212, 0xFF0B1026, 0xFF1E1E2E, 0xFF282A36, 0xFF2E3440};
    static final int[] SURFACES = {0xFF121212, 0xFF282828, 0xFF1C2340, 0xFF313244, 0xFF44475A, 0xFF3B4252};
    static final int[] ACCENTS = {0xFF1ED760, 0xFF509BF5, 0xFFF573A0, 0xFFFF6437, 0xFFF59B23, 0xFFCBA6F7};
    /** Spotify's own background, surface and accent, shown where a theme keeps them. */
    private static final int[] SPOTIFY = {0xFF121212, 0xFF282828, 0xFF1ED760};

    private ThemeSettings() {}

    static void build(SpicetifySettingsScreen activity, LinearLayout content) {
        if (!ThemeRuntime.supported()) {
            content.addView(intro(activity, "Themes need Android 11 or later. On this Android version, Spotify keeps its own colors."));
            return;
        }
        content.addView(intro(activity, "A theme applies fully after Spotify restarts. "
                + "Some screens and hardcoded colors keep Spotify's own colors."));

        ThemeState.Selection current = ThemeState.load(activity);
        for (ThemePresets.Preset preset : ThemePresets.ALL) {
            boolean materialYou = ThemePresets.MATERIAL_YOU.equals(preset.kind) || ThemePresets.MATERIAL_YOU_BLACK.equals(preset.kind);
            if (materialYou && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) continue;
            ThemeState.Selection theme = ThemeState.Selection.preset(preset.kind, preset.name);
            boolean selected = preset.kind.equals(current.kind);
            SpotifyStyle.themeRow(content, preset.name, null, colors(activity, theme), selected, view -> {
                if (!selected) save(activity, content, preset.name, theme);
            });
        }
        boolean custom = ThemeState.CUSTOM.equals(current.kind);
        // Custom starts from the colors of the theme in use.
        int[] colors = colors(activity, current);
        SpotifyStyle.themeRow(content, "Custom", "Pick each color yourself", colors, custom, view -> {
            if (!custom) save(activity, content, "your colors", custom(colors[0], colors[1], colors[2]));
        });
        if (!custom) return;

        SpotifyStyle.sectionTitle(content, "Custom colors", true).setPadding(SpotifyStyle.dp(activity, 16),
                SpotifyStyle.dp(activity, 24), SpotifyStyle.dp(activity, 16), SpotifyStyle.dp(activity, 8));
        SpotifyStyle.colorRow(content, "Background", colors[0],
                view -> pick(activity, "Background color", colors[0], BACKGROUNDS,
                        color -> save(activity, content, "your colors", custom(color, colors[1], colors[2]))));
        SpotifyStyle.colorRow(content, "Surface", colors[1],
                view -> pick(activity, "Surface color", colors[1], SURFACES,
                        color -> save(activity, content, "your colors", custom(colors[0], color, colors[2]))));
        SpotifyStyle.colorRow(content, "Accent", colors[2],
                view -> pick(activity, "Accent color", colors[2], ACCENTS,
                        color -> save(activity, content, "your colors", custom(colors[0], colors[1], color))));
    }

    interface Choice {
        void chosen(int color);
    }

    static Integer parse(String value) {
        String trimmed = value.trim();
        if (!HEX.matcher(trimmed).matches()) return null;
        String digits = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
        long parsed = Long.parseLong(digits, 16);
        return digits.length() == 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
    }

    private static TextView intro(Context context, String text) {
        TextView intro = SpotifyStyle.body(context, text);
        intro.setPadding(SpotifyStyle.dp(context, 16), SpotifyStyle.dp(context, 16), SpotifyStyle.dp(context, 16), SpotifyStyle.dp(context, 8));
        return intro;
    }

    /** A theme's background, surface and accent: its main, card and button roles, or Spotify's own. */
    private static int[] colors(Context context, ThemeState.Selection theme) {
        Map<String, Integer> roles = ThemeRuntime.roleColors(context, theme);
        String[] names = {"main", "card", "button"};
        int[] colors = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            Integer color = roles.get(names[i]);
            colors[i] = color == null ? SPOTIFY[i] : color;
        }
        return colors;
    }

    private static ThemeState.Selection custom(int background, int surface, int accent) {
        Map<String, Integer> colors = new LinkedHashMap<>();
        colors.put("main", background);
        colors.put("card", surface);
        colors.put("button", accent);
        return new ThemeState.Selection(ThemeState.CUSTOM, "Custom", colors);
    }

    private static void pick(SpicetifySettingsScreen activity, String title, int current, int[] presets, Choice choice) {
        EditText hex = new EditText(activity);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setSingleLine(true);
        hex.setText(SpotifyStyle.hex(current));
        hex.setHint("#RRGGBB");
        hex.setContentDescription("Hex color");
        SpotifyStyle.style(hex);
        hex.setTypeface(SpotifyStyle.font(activity, SpotifyStyle.Font.REGULAR));
        hex.setGravity(Gravity.CENTER);

        LinearLayout swatches = new LinearLayout(activity);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        swatches.setGravity(Gravity.CENTER);
        View[] views = new View[presets.length];
        for (int i = 0; i < presets.length; i++) {
            int color = presets[i];
            View swatch = new View(activity);
            swatch.setContentDescription(SpotifyStyle.hex(color));
            swatch.setOnClickListener(view -> hex.setText(SpotifyStyle.hex(color)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(SpotifyStyle.dp(activity, 40), SpotifyStyle.dp(activity, 40));
            params.setMargins(SpotifyStyle.dp(activity, 6), 0, SpotifyStyle.dp(activity, 6), 0);
            swatches.addView(swatch, params);
            views[i] = swatch;
        }
        Runnable highlight = () -> {
            Integer typed = parse(hex.getText().toString());
            for (int i = 0; i < presets.length; i++) {
                views[i].setBackground(SpotifyStyle.swatch(activity, presets[i], typed != null && typed == presets[i]));
            }
        };
        highlight.run();
        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { hex.setError(null); highlight.run(); }
            @Override public void afterTextChanged(Editable text) {}
        });

        LinearLayout body = SpotifyStyle.column(activity);
        body.addView(swatches);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fieldParams.topMargin = SpotifyStyle.dp(activity, 16);
        body.addView(hex, fieldParams);

        new SpotifySheet(activity, title, null)
                .view(body)
                .primary("Save", () -> {
                    Integer color = parse(hex.getText().toString());
                    if (color == null) {
                        hex.setError("Use #RRGGBB or #AARRGGBB.");
                        return false;
                    }
                    choice.chosen(color);
                    return true;
                })
                .secondary("Cancel")
                .show();
    }

    /**
     * Applies and saves a theme. Spotify's views and Compose screens built from then on take it, and
     * colors some screens read once take it after a restart, so a restart is offered.
     */
    private static void save(SpicetifySettingsScreen activity, LinearLayout content, String name, ThemeState.Selection theme) {
        if (!ThemeRuntime.select(activity, theme)) {
            new SpotifySheet(activity, "Colors not applied",
                    "Spotify could not load the new colors, so the theme is unchanged.")
                    .primary("OK", () -> true).show();
            return;
        }
        PatchSettings.markRestartRequired();
        content.removeAllViews();
        build(activity, content);
        activity.refreshRestartBar();
        SpotifyRestart.prompt(activity, "Restart Spotify to finish applying " + name + "?");
    }
}
