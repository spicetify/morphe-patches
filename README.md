# Spicetify Android patches

Spotify Android customizations for use with [Morphe](https://morphe.software/).
This repository publishes patch source and bundles, not Spotify APKs.

<!-- prettier-ignore -->
> [!NOTE]
> This is an experimental feature currently under active development.
> The initial target is Spotify 9.1.80.2221, ARM64. Runtime compatibility is
> still being verified. See the [verification record](docs/verification.md).

## Patches

The first milestone contains two patches. Colors are optional because they
change only selected Android resources, not every Spotify screen.

| Patch | Default | Behavior |
| --- | --- | --- |
| Clean sharing links | Enabled | Removes `si`, `pi`, and known `utm_*` parameters from `open.spotify.com` links. Preserves timestamps, context, other parameters, and fragments. |
| Theme colors | Disabled | Sets selected background, accent, and pressed-accent colors. The default background is AMOLED black. Hardcoded colors and animations can retain Spotify's colors. |

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0-dev.1](https://github.com/spicetify/morphe-patches/releases/tag/v1.0.0-dev.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;2 patches total
<details open>
<summary>📦 Spotify&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 🧪&nbsp;9.1.80.2221 |
| :---: |
| Experimental Android customization patches; see the repository verification report. |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Clean sharing links](#clean-sharing-links) | Removes sharing identifiers and marketing parameters from open.spotify.com links. Keeps playback timestamps, context, and other parameters. |  |
| [Theme colors](#theme-colors) | Changes selected background and accent color resources; defaults to AMOLED black. Some screens, hardcoded colors, and animations retain Spotify's colors. | • Primary background color<br>• Accent color<br>• Pressed accent color |

</details>

<!-- PATCHES_END -->

## Try the experimental source

The [first prerelease](https://github.com/spicetify/morphe-patches/releases/tag/v1.0.0-dev.1)
is available for testing. Source download, patching, and installation work in
Morphe Manager 1.31.1 on the test emulator. Playback and real sharing still
need verification; there is no verified stable release.

To install the experimental source in Manager:

1. Open **Sources**, select **Add**, and choose **Remote**.
2. Paste the following source URL, then select **Add**.

   ```text
   https://raw.githubusercontent.com/spicetify/morphe-patches/refs/heads/dev/patches-bundle.json
   ```

3. Expand **Spicetify Android patches** and enable **Experimental app versions**.
4. Return to the app list. Spotify appears with the target version
   `9.1.80.2221`, ARM64 build `145767611`.

This feed stays on experimental releases. Keep your own stock APK or split-APK
archive for patching; the repository does not distribute Spotify.

## Try a local build

Build the bundle using the [development instructions](CONTRIBUTING.md), then
load `patches/build/libs/patches-*.mpp` in
[Morphe Desktop](https://github.com/MorpheApp/morphe-desktop). Select your own
stock Spotify APK or split-APK archive matching the declared target.

Use a spare Android device or emulator for the initial tests. A patched APK
uses a different signing certificate from stock Spotify. Installing it with
the same package name requires removing stock Spotify first, which removes
its local app data and downloads. Keep the patcher's signing key for future
updates; a different key requires another uninstall.

## Development

Read [CONTRIBUTING.md](CONTRIBUTING.md) for setup, tests, and release steps.
The accepted investigation is preserved in [the plan](docs/plan.md).

This project is independent of Spotify and the Morphe project. Its repository
slug is `morphe-patches`; its display name is Spicetify Android patches.

## License

The patch code is licensed under [GPL-3.0](LICENSE), with the upstream
[NOTICE](NOTICE) retained. See [third-party sources](THIRD_PARTY_NOTICES.md)
for the template and historical implementations used during development.
