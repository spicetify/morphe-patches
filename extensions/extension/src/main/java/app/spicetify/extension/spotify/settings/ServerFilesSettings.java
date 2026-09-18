package app.spicetify.extension.spotify.settings;

import android.content.Context;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import app.spicetify.extension.spotify.localserver.ServerConfig;
import app.spicetify.extension.spotify.localserver.ServerIndex;

final class ServerFilesSettings extends LinearLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView status;
    private String validationError;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            String next = validationError == null ? ServerIndex.status() : validationError;
            if (!TextUtils.equals(status.getText(), next)) status.setText(next);
            handler.postDelayed(this, 1000);
        }
    };

    ServerFilesSettings(Context activity) {
        super(activity);
        setOrientation(VERTICAL);
        if (Build.VERSION.SDK_INT >= 26) setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        if (Build.VERSION.SDK_INT < 26) {
            label("Server files", 18);
            status = label("Server files requires Android 8 or later.", 14);
            return;
        }
        ServerConfig.Snapshot saved = ServerConfig.snapshot();
        label("Server files", 18);
        label("Stream your music from an HTTPS WebDAV folder. Enable Local audio files in Spotify's "
                + "Apps and devices settings to show scanned tracks in Local Files. "
                + "Save applies folder changes and starts a scan. Turning this off stops new requests and clears the track list.", 14);

        Switch enabled = new Switch(activity);
        enabled.setText("Use server files");
        enabled.setTextColor(Color.WHITE);
        enabled.setMinHeight(dp(56));
        enabled.setChecked(saved.enabled);
        addView(enabled);
        EditText url = input("WebDAV folder URL", saved.rootUrl(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://server.example/music/");
        EditText username = input("Username", saved.username(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        EditText password = input("Password or app password", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSaveEnabled(false);
        password.setHint(saved.hasPassword() ? "Saved password" : "Password");
        label("Leave blank to keep the saved password for this folder and username. "
                + "Use an app password when supported.", 14);
        status = label(ServerIndex.status(), 14);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

        Button save = new Button(activity);
        save.setText("Save and scan");
        enabled.setOnCheckedChangeListener((button, checked) -> {
            save.setText(checked ? "Save and scan" : "Save");
            if (!checked) {
                ServerConfig.enabled(false);
                validationError = null;
                status.setText(ServerIndex.status());
            }
        });
        save.setText(enabled.isChecked() ? "Save and scan" : "Save");
        save.setOnClickListener(view -> {
            try {
                String secret = password.getText().length() == 0 ? null : password.getText().toString();
                ServerConfig.configure(enabled.isChecked(), url.getText().toString(), username.getText().toString(), secret);
                validationError = null;
                password.setText("");
                password.setHint(ServerConfig.snapshot().hasPassword() ? "Saved password" : "Password");
                if (enabled.isChecked()) ServerIndex.scanAsync();
                status.setText(ServerIndex.status());
            } catch (IllegalArgumentException error) {
                validationError = error.getMessage();
                status.setText(validationError);
            }
        });
        addView(save);
        Button forget = new Button(activity);
        forget.setText("Forget server");
        forget.setOnClickListener(view -> new AlertDialog.Builder(activity)
                .setTitle("Forget this server?")
                .setMessage("Remove its saved credentials and tracks from Spotify. Files on the server stay unchanged.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Forget", (dialog, which) -> {
                    ServerConfig.configure(false, "", "", "");
                    validationError = null;
                    enabled.setChecked(false);
                    url.setText("");
                    username.setText("");
                    password.setText("");
                    password.setHint("Password");
                    status.setText(ServerIndex.status());
                }).show());
        addView(forget);
    }

    private TextView label(String value, int size) {
        TextView text = new TextView(getContext());
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(size == 18 ? Color.WHITE : Color.LTGRAY);
        text.setPadding(0, dp(12), 0, dp(8));
        addView(text);
        return text;
    }

    private EditText input(String name, String value, int type) {
        TextView label = label(name, 14);
        EditText input = new EditText(getContext());
        input.setId(View.generateViewId());
        label.setLabelFor(input.getId());
        input.setInputType(type);
        input.setTypeface(Typeface.DEFAULT);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setText(value);
        input.setMinHeight(dp(48));
        addView(input, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return input;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (Build.VERSION.SDK_INT >= 26) handler.post(refresh);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(refresh);
        super.onDetachedFromWindow();
    }
}
