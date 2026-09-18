# In-app settings

Add a **Spicetify** entry to Spotify's normal settings list. It opens an
internal settings screen where installed patches expose their runtime
options. The first control is **Clean sharing links**, enabled by default.
Changing it takes effect on the next shared link and survives app restarts.

## Implementation shape

The patch resolves Spotify's settings model while building the APK and
assembles a small typed bridge. The extension owns Android preferences and
the settings screen. Spotify's obfuscated types stay in the generated bridge.

| Component | Responsibility |
| --- | --- |
| Settings patch | Validate the native menu, callback, and initialization hooks; inject the bridge and target manifest entry. |
| Generated bridge | Create one native Spicetify row and open the internal screen in Spotify's task. |
| Settings state | Store preferences in an app-private, Spicetify-specific file. |
| Settings activity | Show installed patch controls, support Back and recreation, and preserve playback. |
| Sharing extension | Read the setting for each share operation, then call the existing pure URL sanitizer when enabled. |

The activity is non-exported and uses an explicit intent. It adds no
permissions, public deep links, restart service, or process termination.
Feature availability comes from the selected patches, not saved preferences
or the presence of an extension class. Repeated menu construction must not
duplicate the row or retain an old Activity.

The theme patch currently changes packaged resources. Its colors remain
patch-time options until a separate runtime color hook works. The menu must
not offer a color control that cannot affect Spotify.

## Design decision

Two designs were compared: a reflective runtime adapter and a bridge
generated while patching. Both use a native menu entry and an owned Android
screen. The typed bridge was selected because the target APK's callback
constructor casts `Function1` to its concrete Kotlin lambda base class. A
generic runtime proxy cannot satisfy that contract. Constructor descriptors
alone are insufficient; the callback's return value and its consumers also
need verification before implementing the bridge.

The APK trace distinguishes two boundaries. The `dtl` callback builds a
rendering model while constructing the settings list. It must not open the
activity. The navigation renderer handles the row's click later through
`tyh0.b`. The bridge must supply a fresh row and its own navigation action.
The traced root factory provides the Activity and navigation renderer. A
fresh renderer delegates existing navigation and handles only the reserved
`spicetify:settings` route. Analytics code `-1` maps to `spicetify_settings`;
the original standard-navigation codes are 1 through 65.

Keep the reflective design's separation between the pure URL sanitizer and
the preference-aware wrapper. Update artifact checks to distinguish an
installed sharing hook from a helper merely bundled with shared settings.
Avoid a general plugin registry or runtime constructor guessing for one menu
entry and one initial switch.

The [source research](research/2026-09-18-in-app-settings.md) records the Piko
pattern and Spotify integration leads. No cvnfork source is imported.

## Verification requirements

These checks are required before calling the settings feature complete.

- Match exactly one menu insertion point and a proven callback contract.
  Reject missing, ambiguous, or incompatible hooks before producing an APK.
- Inspect the output DEX and target manifest for the initialization, menu,
  sharing, capability, and non-exported activity declarations.
- Build sharing-only, theme-only, and combined profiles. Expose only controls
  that work in each profile.
- Open the screen through Spotify's visible settings entry. Verify Back,
  repeated opening, rotation, large text, and accessible control labels.
- Toggle sharing off and on, compare real links, and verify persistence after
  process restart and a same-key update. Preserve timestamps and context.
- Keep music playing while opening, changing, and leaving settings. Recheck
  queue and notification controls and existing Spotify settings actions.

Implementation and runtime evidence belong in [verification](verification.md).

## Current implementation status

The local implementation connects application initialization, the native
settings row, the non-exported Activity, capability flags, and all three
sharing hooks. Hash snapshots of 32 inspected native classes reject changes
to their schemas or code before hook injection. This intentionally supports
only the inspected Spotify build.

The bridge is original smali source, assembled into DEX during the build and
merged through Morphe's public extension API. It uses DEX API 24; API 35
assembly produced a DEX container header rejected by Morphe's extension reader.
Snapshot hashes include the full classes because the obfuscated classes share
methods across features. A change outside the settings branch can therefore
refuse patching too. Review the trace before replacing a snapshot.

Four preference tests, two snapshot tests, the existing unit suite, 26 sharing
verifier cases, Android lint, and the bundle build pass. Sharing-only,
theme-only, and combined APKs build. Seven altered-input or invalid-option
cases fail without output APKs. The published `dev.3` prerelease adds the
settings menu. On the Pixel 8, the native row, Back navigation, immediate
sharing toggle, restart persistence, playback, rotation, and enlarged text
pass. Theme-only and combined settings controls still need signed-in runtime
checks. See the verification record for the remaining review and test gaps.
