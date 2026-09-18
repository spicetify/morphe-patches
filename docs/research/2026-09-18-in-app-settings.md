# In-app settings for Spotify

Research completed on September 18, 2026. The requested outcome is a
**Spicetify** entry inside Spotify that opens persistent patch preferences,
similar to Piko's Instagram settings. Piko demonstrates the architecture;
Spotify still needs its own verified entry-point hook. This note records
source findings, not runtime verification or a completed implementation.

## Sources and reuse boundaries

The following revisions were resolved from the public repositories during
this investigation. All implementation links below use these fixed revisions.

| Source | Revision | Reuse assessment |
| --- | --- | --- |
| Piko | `50744aa07bb41c4e1f942a06614ef4e6f2e3610c` | GPL-3.0 source with a separate NOTICE preservation requirement. Its settings depend on Instagram-specific hooks and shared extension code. |
| cvnfork Spotify patches | `289d59e2bdae0fa6f16ffa0e2769351be46a94e8` | README says GPL-3.0, but the inspected tree has no LICENSE or NOTICE. Installation links name a different owner. Keep the existing provenance hold before importing code. |

Piko's [LICENSE][piko-license] and [NOTICE][piko-notice] establish the
repository's stated terms. The NOTICE requires its preservation in source
and derivative distributions. The Spotify reference's
[README][spotify-readme] names `ImNoammm/morphe-spotify-patches` as the
installation source, while the inspected repository is under `cvnfork`.
The [pinned tree][spotify-tree] supplies no standalone license text. This
does not establish that the code is unlicensed; it leaves the previously
recorded provenance questions unresolved. No upstream code was imported as
part of this investigation.

## How Piko exposes settings

Piko injects a settings gear into Instagram's own UI. Its main-feed hook
matches the `BindMainFeedActionBar` string and an object-returning method,
then passes a discovered `ViewGroup` register to the extension before a
null-check branch. The runtime helper adds the gear when the corresponding
action-bar preference includes `SETTINGS_ICON`. The gear calls
`FragmentHook.startSettings`. Piko also supports profile, chat, and inbox
action bars. These are Instagram hooks, not portable Spotify fingerprints.
[Main-feed patch][piko-entry], [runtime action bar][piko-actionbar],
[gear creation][piko-ui].

The settings screen is an injected `SettingsActivity extends Activity`.
Piko declares it with `android:exported="false"` and a framework
`Theme.DeviceDefault.NoActionBar` theme. It opens the activity with an
explicit intent and uses extras for the selected category and title.
The activity builds a toolbar and content container, handles system-bar
insets, and hosts a framework `PreferenceFragment` with a programmatically
created `PreferenceScreen`. Category navigation creates another settings
activity with different extras. No browser or separate manager is needed.
[Manifest additions][piko-manifest], [activity launcher][piko-launch],
[settings activity][piko-activity].

Preference storage uses the same named preferences in the screen and shared
runtime helpers. `BooleanSetting` and `StringSetting` define keys and default
values without requiring an initialized Android context. The base setting
uses a volatile value; its source explains that Instagram can load settings
before context initialization. `Helper.setValue` writes changes through
`SharedPref`, and successful changes notify `SettingsRestart`.
[Setting definition][piko-setting], [preference helper][piko-helper],
[storage wrapper][piko-storage], [settings activity][piko-activity].

The screen only offers features present in the patched APK. Its
`SettingsStatus` flags describe installed patches, and `ScreenBuilder`
consults those flags when adding rows and categories. A saved preference
therefore does not imply that its patch was included. The current restart
helper marks changes, flushes preferences, and kills the app process after
the app task is removed. That Instagram behavior is not a recommendation
for Spotify, where background playback must continue.
[Installed-feature flags][piko-status], [screen construction][piko-screen],
[restart handling][piko-restart].

## What the Spotify reference hooks

The cvnfork implementation adds a native-looking **Morphe** row to Spotify's
main settings list. Its fingerprints look for `appLanguage` and `appIcon`
in one method, and `notificationsPage` in another object-returning method.
It declares package compatibility without a tested version list.
[Fingerprints][spotify-fingerprints], [patch implementation][spotify-patch].

The patch follows the `appIcon` constant to a row constructor whose first
three parameters are `String`, `Integer`, and `Integer`. Parameter index 9
identifies an accessor interface. It searches implementations for a
two-object constructor, then finds an action implementation with a single
`String` constructor. The patch writes the discovered holder and action
class names into the extension. Before a static object-array-to-list call,
it injects `captureTile(Object[])`. Before a later static call taking a
`List` in the main settings method, it injects `addToMainMenu(List)`.
[Patch implementation][spotify-patch].

At runtime, `SettingsTile` reflects over existing rows to find a navigation
template, constructs a new holder and destination action, and supplies the
destination `morphe://settings`. It constructs the row using reflected
fields, shared object identities, primitive defaults, and a special case
for flow-like interfaces with `collect` methods. It caches one tile and
inserts it immediately before a row containing the `logout` string, or at
the end if that row is absent. These assumptions need independent checking
against Spotify 9.1.80.2221; successful matching alone cannot prove a visible,
clickable row. [Runtime row construction][spotify-tile].

The reference registers an exported, browsable activity for the `morphe`
scheme. Its current settings UI edits WebDAV configuration through ordinary
Android views and a `ServerConfig` wrapper around private
`SharedPreferences`, using asynchronous `apply()` writes. The resource patch
also registers a provider for its local-server feature. Those services and
fields are unrelated to the first Spicetify settings screen and do not need
to come along with it. [Manifest additions][spotify-manifest],
[settings activity][spotify-activity], [configuration storage][spotify-storage].

## Implementation recommendation

Build a small shared settings extension and a Spotify-specific entry patch.
This is an engineering recommendation based on the source inspection above
and the repository's existing
[sharing patch][local-sharing] and [theme resource patch][local-theme].

1. Add a discoverable **Spicetify** row to Spotify settings. Independently
   derive the row and click path from the supported APK. Require unique,
   structurally verified matches and refuse patching when they differ.
   Do not transplant the reference's reflected-field heuristics unchecked.
2. Open a private activity through an explicit intent from Spotify's own
   process. Keep it unexported unless a verified product requirement needs
   external deep links. Preserve Back navigation and playback.
3. Store preferences under a dedicated Spicetify name. Initialize from the
   application context, and define deterministic defaults for reads before
   initialization. Keep Activity references out of static storage.
4. Make **Clean sharing links** a real runtime switch, enabled by default
   when the patch is installed. Its injected helper must read the current
   value on each share. Turning it off must return the original URL.
5. Show only controls backed by installed hooks. Current theme colors modify
   APK resources during patching, so a settings activity alone cannot make
   them editable at runtime. Runtime color controls require separately
   verified color hooks; until then, any theme information must say that
   changing colors requires repatching.
6. Keep the first screen focused on working preferences. Add categories as
   features need them, without importing Piko's unrelated patch registry,
   download UI, developer options, or process-restart service.

## Required evidence

The existing [verification record](../verification.md) remains the release
authority. Add separate evidence for the following settings behavior.

- Open the screen from Spotify's visible settings UI, then return with both
  toolbar Back and Android Back. Confirm one row after repeated navigation.
- Change a working preference, exercise its actual feature, relaunch the
  app, and verify the saved value and behavior. Test default values and an
  APK built without the optional feature.
- Verify long labels, larger text, screen rotation, accessibility semantics,
  system-bar insets, and the keyboard on any editable field.
- Keep music playing while opening settings and changing immediate options.
  Do not force a process restart for options that can apply on the next call.
- Inspect the final APK's activity declaration, injected method signatures,
  extension classes, and single entry-point hook. Test missing and ambiguous
  fingerprints as refusals that produce no patched APK.
- Repatch and install using the same signing key; confirm preferences
  survive that update. Do not infer persistence across uninstalling Spotify.

[piko-license]: https://github.com/crimera/piko/blob/50744aa07bb41c4e1f942a06614ef4e6f2e3610c/LICENSE
[piko-notice]: https://github.com/crimera/piko/blob/50744aa07bb41c4e1f942a06614ef4e6f2e3610c/NOTICE
[piko-entry]: https://github.com/crimera/piko/blob/50744aa07bb41c4e1f942a06614ef4e6f2e3610c/patches/src/main/kotlin/app/crimera/patches/instagram/misc/actionBar/mainFeedActionBarButton/MainFeedActionBarButtonPatch.kt
[piko-actionbar]: https://github.com/crimera/piko/blob/50744aa07bb41c4e1f942a06614ef4e6f2e3610c/extensions/instagram/src/main/java/app/morphe/extension/instagram/patches/actionbar/ActionBarPatch.java
[piko-ui]: https://github.com/crimera/piko/blob/50744aa07bb41c4e1f942a06614ef4e6f2e3610c/extensions/instagram/src/main/java/app/morphe/extension/instagram/constants/UI.java
[piko-manifest]: https://github.com/crimera/piko/blob/50744aa07bb41c4e1f942a06614ef4e6f2e3610c/patches/src/main/kotlin/app/crimera/patches/instagram/misc/settings/SettingsResourcePatch.kt
[piko-launch]: https://github.com/crimera/piko/blob/50744aa07bb41c4e1f942a06614ef4e6f2e3610c/extensions/instagram/src/main/java/app/morphe/extension/instagram/settings/ActivityHook.java
[piko-activity]: https://github.com/crimera/piko/blob/50744aa07bb41c4e1f942a06614ef4e6f2e3610c/extensions/instagram/src/main/java/app/morphe/extension/instagram/settings/SettingsActivity.java
[piko-setting]: https://github.com/crimera/piko/blob/50744aa07bb41c4e1f942a06614ef4e6f2e3610c/extensions/shared/library/src/main/java/app/morphe/extension/crimera/settings/Setting.java
[piko-helper]: https://github.com/crimera/piko/blob/50744aa07bb41c4e1f942a06614ef4e6f2e3610c/extensions/instagram/src/main/java/app/morphe/extension/instagram/settings/preference/Helper.java
[piko-storage]: https://github.com/crimera/piko/blob/50744aa07bb41c4e1f942a06614ef4e6f2e3610c/extensions/shared/library/src/main/java/app/morphe/extension/crimera/sharedPreference/SharedPref.java
[piko-status]: https://github.com/crimera/piko/blob/50744aa07bb41c4e1f942a06614ef4e6f2e3610c/extensions/instagram/src/main/java/app/morphe/extension/instagram/settings/SettingsStatus.java
[piko-screen]: https://github.com/crimera/piko/blob/50744aa07bb41c4e1f942a06614ef4e6f2e3610c/extensions/instagram/src/main/java/app/morphe/extension/instagram/settings/preference/ScreenBuilder.java
[piko-restart]: https://github.com/crimera/piko/blob/50744aa07bb41c4e1f942a06614ef4e6f2e3610c/extensions/instagram/src/main/java/app/morphe/extension/instagram/settings/SettingsRestart.java
[spotify-readme]: https://github.com/cvnfork/morphe-spotify-patches/blob/289d59e2bdae0fa6f16ffa0e2769351be46a94e8/README.md
[spotify-tree]: https://github.com/cvnfork/morphe-spotify-patches/tree/289d59e2bdae0fa6f16ffa0e2769351be46a94e8
[spotify-fingerprints]: https://github.com/cvnfork/morphe-spotify-patches/blob/289d59e2bdae0fa6f16ffa0e2769351be46a94e8/patches/src/main/kotlin/app/noam/patches/spotify/misc/settings/Fingerprints.kt
[spotify-patch]: https://github.com/cvnfork/morphe-spotify-patches/blob/289d59e2bdae0fa6f16ffa0e2769351be46a94e8/patches/src/main/kotlin/app/noam/patches/spotify/misc/settings/SettingsPatch.kt
[spotify-tile]: https://github.com/cvnfork/morphe-spotify-patches/blob/289d59e2bdae0fa6f16ffa0e2769351be46a94e8/extensions/extension/src/main/java/app/noam/extension/spotify/settings/SettingsTile.java
[spotify-manifest]: https://github.com/cvnfork/morphe-spotify-patches/blob/289d59e2bdae0fa6f16ffa0e2769351be46a94e8/patches/src/main/kotlin/app/noam/patches/spotify/misc/settings/SettingsResourcePatch.kt
[spotify-activity]: https://github.com/cvnfork/morphe-spotify-patches/blob/289d59e2bdae0fa6f16ffa0e2769351be46a94e8/extensions/extension/src/main/java/app/noam/extension/spotify/settings/MorpheSettingsActivity.java
[spotify-storage]: https://github.com/cvnfork/morphe-spotify-patches/blob/289d59e2bdae0fa6f16ffa0e2769351be46a94e8/extensions/extension/src/main/java/app/noam/extension/spotify/localserver/ServerConfig.java
[local-sharing]: ../../patches/src/main/kotlin/app/spicetify/patches/spotify/privacy/SharingLinksPatch.kt
[local-theme]: ../../patches/src/main/kotlin/app/spicetify/patches/spotify/theme/ThemePatch.kt
