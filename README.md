# Spicetify Android patches

Spotify Android customizations for use with [Morphe](https://morphe.software/).
This repository publishes patch source and bundles, not Spotify APKs.

<!-- prettier-ignore -->
> [!NOTE]
> This is an experimental feature currently under active development.
> The initial target is Spotify 9.1.80.2221, ARM64. Runtime compatibility is
> still being verified. See the [verification record](docs/verification.md).

[**➕ Add Spicetify to Morphe**](https://morphe.software/add-source?github=spicetify/morphe-patches/tree/dev)

Open this link on Android with Morphe Manager installed to add the
experimental source.

## Patches

The bundle contains four patches. Clean sharing is enabled by default;
colors, Home shortcut pinning, and HTTPS WebDAV server files are optional.
See [optional feature setup and limits](docs/optional-features.md).

| Patch | Default | Behavior |
| --- | --- | --- |
| Clean sharing links | Enabled | Removes `si`, `pi`, and known `utm_*` parameters from `open.spotify.com` links. Preserves timestamps, context, other parameters, and fragments. |
| Theme colors | Disabled | Sets selected background, accent, and pressed-accent colors. The default background is AMOLED black. Hardcoded colors and animations can retain Spotify's colors. |
| Pin shortcuts on Home | Disabled | Moves selected native Home shortcuts first. Configure pins in Spotify's Spicetify settings, then restart Spotify. |
| Local files from a server | Disabled | Streams an HTTPS WebDAV folder into Local Files. Requires Android 8 or later and byte-range support; configure the server in Spotify's Spicetify settings. |

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0-dev.4](https://github.com/spicetify/morphe-patches/releases/tag/v1.0.0-dev.4)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;4 patches total
<details open>
<summary>📦 Spotify&nbsp;&nbsp;•&nbsp;&nbsp;4 patches</summary>
<br>

**🎯 Supported versions:**

| 🧪&nbsp;9.1.80.2221 |
| :---: |
| Experimental Android customization patches; see the repository verification report. |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Clean sharing links](#clean-sharing-links) | Removes sharing identifiers and marketing parameters from open.spotify.com links. Keeps playback timestamps, context, and other parameters. |  |
| [Local files from a server](#local-files-from-a-server) | Streams audio from an HTTPS WebDAV folder into Local Files. Configure the server in Spicetify settings. Experimental; requires byte-range support. |  |
| [Pin shortcuts on Home](#pin-shortcuts-on-home) | Choose which of Spotify's Home shortcuts appear first in Spicetify settings. Pins are saved on this device. Restart Spotify after changing pins. |  |
| [Theme colors](#theme-colors) | Changes selected background and accent color resources; defaults to AMOLED black. Some screens, hardcoded colors, and animations retain Spotify's colors. | • Primary background color<br>• Accent color<br>• Pressed accent color |

</details>

<!-- PATCHES_END -->

## Try the experimental source

The current [experimental release](https://github.com/spicetify/morphe-patches/releases/tag/v1.0.0-dev.4)
is `1.0.0-dev.4`. Manager 1.31.1 downloads all four patches, and its default
profile was patched and installed on a Pixel 8 while preserving login and
the sharing setting. Runtime testing also covers Home pinning, synthetic
server playback and seeking, interrupted scans, and recovery on that device.
See the exact tested artifacts and remaining checks in the
[verification record](docs/verification.md). There is no stable release.

Use a spare Android device or emulator for the initial tests. A patched APK
uses a different signing certificate from stock Spotify. Installing it with
the same package name requires removing stock Spotify first, which removes
its local app data and downloads. Keep Manager's signing key for future
updates; a different key requires another uninstall.

Use the **Add Spicetify to Morphe** link above, then confirm the source in
Manager. If you already added this repository manually, keep that source
instead of adding it again. To add it manually and patch Spotify:

1. Open **Sources**, select **Add**, and choose **Remote**.
2. Paste the following source URL, then select **Add**.

   ```text
   https://raw.githubusercontent.com/spicetify/morphe-patches/refs/heads/dev/patches-bundle.json
   ```

3. Expand **Spicetify Android patches** and enable **Experimental app versions**.
4. Return to the app list. Spotify appears with the target version
   `9.1.80.2221`, ARM64 build `145767611`.
5. Optional: To select colors, Home pins, or server files, open
   **Settings > Advanced** and enable
   **Expert mode** before selecting Spotify. The default flow applies only
   **Clean sharing links**.
6. Select **Spotify**, choose **No, I already have an APK**, and select your
   original APK or split-APK archive. To use Android's system picker without
   granting **All files access**, turn off **Settings > System > Custom file
   picker** in Manager first.
7. Read the experimental-support notice and select **Proceed anyway** if you
   want to test this build. In Expert mode, select the optional patches you
   want. Use **Theme colors** settings to adjust colors before patching, then
   select **Proceed to patching**.
8. Wait for **Patching complete**, then select **Install**. If Manager reports
   a certificate conflict, uninstall the existing app only after accepting
   the data loss described above. Confirm installation in Android's dialog.
9. Open Spotify from Android's app launcher. Sign in if needed.

This feed stays on experimental releases. Keep your own stock APK or split-APK
archive for patching; the repository does not distribute Spotify.

## Change settings in Spotify

Version `1.0.0-dev.3` adds a **Spicetify** row to Spotify's settings.

1. Open your profile menu, then **Settings and privacy**.
2. Scroll down and select **Spicetify**, just above **Log out**.
3. Turn **Clean sharing links** on or off. The next share uses your choice
   immediately. The setting survives restarting Spotify.

Only installed patches appear here. If you selected **Theme colors**, the
screen explains how to change them in Manager and repatch Spotify. Colors
are still selected when patching; they cannot be changed live in Spotify.
Home pins and server files have their own controls here when installed.
Follow the [optional feature setup](docs/optional-features.md) to use them.

To update an existing Manager-signed installation, update **Spicetify Android
patches** in **Sources**, open Spotify's entry in Manager, and select **Patch**.
Install the resulting APK with the same Manager signing key to preserve app
data. If Android reports a certificate conflict, stop and check the signing
key before considering an uninstall.

## Try a local build

Build the bundle using the [development instructions](CONTRIBUTING.md), then
load `patches/build/libs/patches-*.mpp` in
[Morphe Desktop](https://github.com/MorpheApp/morphe-desktop). Select your own
stock Spotify APK or split-APK archive matching the declared target.

Keep the patcher's signing key if you use Desktop for later updates. A
Desktop-signed APK and a Manager-signed APK can use different keys.

## Development

Read [CONTRIBUTING.md](CONTRIBUTING.md) for setup, tests, and release steps.
The accepted investigation is preserved in [the plan](docs/plan.md).

This project is independent of Spotify and the Morphe project. Its repository
slug is `morphe-patches`; its display name is Spicetify Android patches.

## License

The patch code is licensed under [GPL-3.0](LICENSE), with the upstream
[NOTICE](NOTICE) retained. See [third-party sources](THIRD_PARTY_NOTICES.md)
for the template and historical implementations used during development.
