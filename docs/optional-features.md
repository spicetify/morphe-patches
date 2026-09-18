# Optional Home pins and server files

Development builds add two optional patches to the existing Spicetify settings
screen. Both patches are disabled by default. The published `dev.3` bundle
does not include them. Runtime testing must finish before publishing them.

## Home shortcuts

Enable **Pin shortcuts on Home** when patching. Open Spotify's Home page and
let its shortcuts load, then open **Settings and privacy > Spicetify >
Choose pinned shortcuts**. Select shortcuts and save. Restart Spotify to
refresh Home.

The patch moves selected shortcuts ahead of other shortcuts that Spotify
supplies. It does not add a playlist absent from Spotify's current Home list.
Saved pins remain available in the picker when temporarily absent from Home.
Shortcuts use Spotify URIs as identifiers. Duplicate names show their URIs in
the picker so you can distinguish them. You can save up to 64 pins.

The patch copies the native shortcut list before reordering it. It preserves
the original row objects, images, titles, analytics, and click handlers.
Eight class snapshots guard the constructor, data model, and renderers in
Spotify `9.1.80.2221`.

## Server files

Enable **Local files from a server** when patching. This feature requires
Android 8 or later and an HTTPS WebDAV folder with byte-range support.

1. Open **Settings and privacy > Spicetify**.
2. Enter the full WebDAV folder URL, username, and password or app password.
3. Enable **Use server files**, then select **Save and scan**.
4. Wait for the track count. Enable Spotify's local audio files option and
   open **Local Files** in your library.

The URL must identify the WebDAV folder, not the server's web interface.
Blank password fields retain a saved password only when the folder and
username stay unchanged. Changing the server or account never transfers the
old password. **Forget server** removes the saved configuration and index.
Turning **Use server files** off stops server access immediately, including
when the form contains an invalid draft URL.

Credentials stay in Spotify's private app preferences. The feature does not
log passwords or include them in content URIs. All file and redirect URLs
must stay inside the configured HTTPS origin and folder. Android validates
the server certificate. The provider is private and grants no URI access to
other apps.

Scans accept up to 500 audio files, 64 folders, six directory levels, and
2 MB per folder listing. Audio files must report a positive size of at most
2 GB. A server must return the requested HTTP 206 byte range and total size.
Unsupported responses fail instead of returning audio from the wrong offset.
An unreadable file currently fails the scan. Check the folder, credentials,
and server's range support, then scan again.

The index is scoped to the saved configuration. A scan from an old
configuration cannot publish into a new one. Audio is streamed through
Android's file-descriptor API without a persistent audio cache. The app scans
again after starting; this feature does not provide offline downloads.

## Verification still required

The extension tests cover URL confinement, range responses, index scoping,
disabled access, protobuf serialization, pin identity and persistence, and
settings recovery. Patch-time snapshots match the inspected stock APK, and
all four patches apply together. These checks do not prove native playback.

Before release, exercise the visible settings flow on Android, Home ordering
after restart, and an HTTPS fixture through metadata extraction, Local Files,
playback, seeking, disabling, and a configuration change. Keep the default
phone installation until the optional profiles pass. Record the evidence in
[the verification record](verification.md).
