package app.spicetify.extension.spotify.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import app.spicetify.extension.spotify.home.HomePins;
import java.util.ArrayList;
import java.util.List;

public final class SpicetifySettingsActivity extends Activity {
    public static void open(Activity activity) {
        activity.startActivity(new Intent(activity, SpicetifySettingsActivity.class));
    }

    @Override
    protected void onCreate(Bundle state) {
        setTheme(android.R.style.Theme_Material);
        super.onCreate(state);
        setTitle("Spicetify");
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setBackgroundDrawable(new ColorDrawable(Color.rgb(18, 18, 18)));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(18, 18, 18));
        scroll.setFitsSystemWindows(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(24);
        content.setPadding(padding, dp(16), padding, padding);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean sharingInstalled = InstalledPatches.cleanSharing();
        boolean themeInstalled = InstalledPatches.themeColors();
        if (sharingInstalled) {
            Switch cleanSharing = new Switch(this);
            cleanSharing.setText("Clean sharing links");
            cleanSharing.setTextSize(18);
            cleanSharing.setTextColor(Color.WHITE);
            cleanSharing.setMinHeight(dp(56));
            cleanSharing.setSwitchPadding(dp(24));
            cleanSharing.setThumbTintList(new ColorStateList(
                    new int[][] {new int[] {android.R.attr.state_checked}, new int[0]},
                    new int[] {Color.rgb(30, 215, 96), Color.LTGRAY}));
            cleanSharing.setChecked(PatchSettings.cleanSharingEnabled());
            cleanSharing.setOnCheckedChangeListener((button, enabled) ->
                    PatchSettings.setCleanSharingEnabled(enabled));
            content.addView(cleanSharing, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            content.addView(text("Remove tracking parameters from Spotify links you share. "
                    + "Timestamps and playback context are preserved. Changes apply immediately.", false));
        }

        if (themeInstalled) {
            TextView heading = text("Theme colors", true);
            heading.setPadding(0, dp(24), 0, 0);
            content.addView(heading);
            content.addView(text("Your colors were selected in Morphe Manager. "
                    + "Change those options and repatch Spotify to use different colors.", false));
        }

        if (InstalledPatches.homePins()) {
            content.addView(text("Home shortcuts", true));
            content.addView(text("Choose which shortcuts appear first when Spotify includes them on Home. "
                    + "Return to Home once to load the choices. Restart Spotify after changing pins.", false));
            Button choose = new Button(this);
            choose.setText("Choose pinned shortcuts");
            choose.setOnClickListener(view -> chooseHomePins());
            content.addView(choose);
        }

        if (InstalledPatches.serverFiles()) {
            content.addView(new ServerFilesSettings(this));
        }

        if (!sharingInstalled && !themeInstalled && !InstalledPatches.homePins()
                && !InstalledPatches.serverFiles()) {
            content.addView(text("No configurable Spicetify patches are installed.", false));
        }
        setContentView(scroll);
    }

    private void chooseHomePins() {
        List<HomePins.Choice> choices = HomePins.choices();
        if (choices.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("No Home shortcuts loaded")
                    .setMessage("Return to Home and let its shortcuts load, then open this menu again.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String[] labels = new String[choices.size()];
        boolean[] selected = new boolean[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            HomePins.Choice choice = choices.get(i);
            boolean duplicate = false;
            for (HomePins.Choice other : choices) {
                if (!other.id.equals(choice.id) && other.label.equals(choice.label)) duplicate = true;
            }
            labels[i] = duplicate ? choice.label + "\n" + choice.id : choice.label;
            selected[i] = choice.pinned;
        }
        AlertDialog picker = new AlertDialog.Builder(this).setTitle("Pinned Home shortcuts")
                .setMultiChoiceItems(labels, selected, (dialog, index, checked) -> selected[index] = checked)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null).create();
        picker.setOnShowListener(ignored -> picker.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    List<String> ids = new ArrayList<>();
                    for (int i = 0; i < choices.size(); i++) if (selected[i]) ids.add(choices.get(i).id);
                    try {
                        HomePins.setPinned(ids);
                    } catch (IllegalArgumentException changedSelection) {
                        Toast.makeText(this, changedSelection.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    picker.dismiss();
                    new AlertDialog.Builder(this).setMessage("Pins saved. Restart Spotify to refresh Home.")
                            .setPositiveButton("OK", null).show();
                }));
        picker.show();
    }

    private TextView text(String value, boolean heading) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(heading ? Color.WHITE : Color.rgb(179, 179, 179));
        view.setTextSize(heading ? 18 : 14);
        view.setPadding(0, dp(8), 0, dp(8));
        if (heading) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
