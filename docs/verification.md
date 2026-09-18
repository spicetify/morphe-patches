# Verification record

The initial target is experimental. This record separates source checks from
patching, installation, and Android runtime evidence. Unchecked items remain
release blockers.

## Target and tools

The stock fixture was copied from an installed Android application on
September 17, 2026. Only APK files were copied, without account or app data.

| Field | Value |
| --- | --- |
| Package | `com.spotify.music` |
| Version | `9.1.80.2221` |
| Version code | `145767611` |
| ABI | `arm64-v8a` |
| Stock base APK SHA-256 | `3dc0c561236d4dc01c17acc12dd219914fa2de61992a3d1428ff6ee677cd45ee` |
| Stock signing certificate SHA-256 | `6505b181933344f93893d586e399b94616183f04349cb572a9e81a3335e28ffd` |
| Build JDK | Temurin 21 |
| Morphe Patcher | `1.13.0` |
| Morphe Gradle plugin | `1.3.4` |
| Morphe Desktop | `1.16.0` |

## Evidence

Static inspection found one current share URL builder with the expected
parameter signature and tracking strings. The historical `ShareUrl(url=`
constructor fingerprint no longer matches this build. All ten selected
theme resource names exist in the stock resource table.

The bundle builds with Java 21. Five Java URL tests, 22 Kotlin resource
cases, and three option-submission cases pass. Android extension lint passes.
Generated metadata contains exactly two public patches, with the expected
options, defaults, ARM64 version code, and experimental target.

Morphe Desktop 1.16.0 patched the sharing-only, theme-only, and combined
profiles successfully. Independent artifact checks verify 1,145 default
color entries and resource IDs. Theme profiles change the ten selected
colors; other default colors stay equivalent. Where resource rebuilding
relocates XML selectors, the checker compares decoded XML contents.
The sharing helper and its call appear exactly once when enabled and are
absent from the theme-only output. Android signature verification passes.
These runs use FULL bytecode mode. The input base APK hash is listed above.
The combined output rebuilt after the option-validation fix has SHA-256
`f82cfe6de544ffddd6c90f3addfe7360e22a2056a93e0a4e7c5fea73520bcbc7`.

Selecting the sharing patch for the unrelated Manager package applies zero
patches. Desktop still signs an unchanged output and returns success in that
case. A CLI exit code alone is insufficient; check the report's applied patch
names and run the artifact verifier.

An invalid color initially exposed Morphe's option setter retaining defaults
after validator errors. Commit `56c4cc0` moves format validation into patch
execution. The repeated CLI test now exits 1, reports the invalid format, and
produces no APK. Regression tests use Morphe's actual option setter for all
three fields.

On September 18, 2026, `scripts/verify-failures.py` checked the published
`v1.0.0-dev.1` bundle against disposable copies of the stock base APK. Removing
the sharing fingerprint's string marker reports zero share URL builders.
Renaming `dark_base_background_base` reports that missing resource. Submitting
an invalid value for each of the three color options reports the option's
format requirement. All five cases exit 1, identify the expected failed patch,
and produce no output APK. These altered fixtures simulate input drift; they
do not establish compatibility with another Spotify version or verify Manager's
failure screens.

A source review found a verifier gap: a same-name call with a mismatched
method descriptor could pass. Synthetic DEX tests reproduced that false pass.
The verifier now requires the exact String-to-String descriptor and a public
static helper. It accepts the valid fixture and rejects four malformed cases.

Initial Android 16 emulator startup was overloaded and showed a Spotify ANR.
Stock Spotify subsequently reached its welcome screen in the same emulator.
The combined patched APK installs and reaches the same welcome-screen
accessibility controls on a controlled retry. Hardware-rendered screenshots
showed a black app area. Restarting the emulator with SwiftShader and Vulkan
disabled restored visible Manager rendering. Spotify's welcome and login
controls respond through accessibility. Its login activity sets Android's
`SECURE` window flag, so black captures there do not prove a rendering bug.
Spotify's visual color checks and logged-in behavior remain unverified.

Morphe Manager 1.31.1 imports the Android bundle and lists both patches.
Enabling **Experimental app versions** makes Spotify visible. The default
flow accepts the stock split archive and starts the sharing patch. Cancelling
that job and confirming **Stop patcher** returns to the app list.

An earlier local import contained JVM classes without `classes.dex`, and
Manager reported zero patches. Replacing that stale file with the
`buildAndroid` output resolved it. A `.mpp` filename alone does not establish
Android compatibility.

## Published prerelease

[CI run 35241568299](https://github.com/spicetify/morphe-patches/actions/runs/35241568299)
passed tests, extension lint, bundle compilation, release, and attestation.
It published [v1.0.0-dev.1](https://github.com/spicetify/morphe-patches/releases/tag/v1.0.0-dev.1)
as a prerelease from source commit `22b1524`. Stable publishing remains gated.

[CI run 35247182789](https://github.com/spicetify/morphe-patches/actions/runs/35247182789)
also passes on `07fd462`. Both runs were explicitly dispatched. Subsequent
SSH pushes did not create Actions runs during this test, despite enabled
workflows and matching branch triggers. Automatic push-triggered release
admission remains unverified.

The downloaded `patches-1.0.0-dev.1.mpp` contains `classes.dex` and
`extensions/spotify.mpe`. Its SHA-256 is
`fec60b3452aa9af9f195d2ccc544015cc2f04444c7ef1352052507b2590d76fa`.
`gh attestation verify` succeeds for this file and repository. Manager downloads
this release from the following feed and lists both patches:

```text
https://raw.githubusercontent.com/spicetify/morphe-patches/refs/heads/dev/patches-bundle.json
```

The explicit `refs/heads/dev` URL keeps this feed on experimental releases.
Enable **Experimental app versions** in the source's controls to show Spotify.
The remote source was tested with the duplicate local source disabled.
In **Expert mode**, both patches were selected with default colors. Manager
merged the five stock splits, patched with its default **Fast** bytecode mode
and 896 MB process limit, and reported success. Its installation flow detected
the existing Desktop-signed test app, explained the certificate conflict and
data loss, and offered uninstall. After uninstalling that unauthenticated test
app, Android installed the Manager-built APK and Manager marked it installed.

The installed APK was copied back from the emulator and independently checked.
Its sharing call and helper, all 1,145 default colors and resource IDs, the ten
expected theme changes, and Android signing verification pass. Its SHA-256 is
`23dcb4098406f60c70bb6657f22ece21d524f6f36332afe0401e5f57f69b43b3`.
The Manager-built app reaches its welcome and login screens after an emulator
restart. A later source version update and same-key reinstall still need
verification.

## Physical-device preparation

On September 18, 2026, Morphe Manager 1.31.1 on a Pixel 8 running Android 17
downloaded `v1.0.0-dev.1` from the experimental feed and listed both patches.
After enabling **Experimental app versions**, its default flow accepted the
stock split archive through Android's file picker and built the clean-sharing
patch. Manager did not need **All files access** for this flow.

The APK exported through Manager's **Save** action has SHA-256
`3e89dcc081a4da519821156734b54dba95851299e9bd156e728bffe4466d9653`.
Independent checks confirm one sharing call and helper, all 1,145 default color
values and IDs unchanged, and a valid APK signature. This is a phone-built
artifact, not evidence of installation or authenticated runtime behavior.

After explicit approval of local-data loss, Manager uninstalled stock Spotify
and started installing this APK. Android's Play Protect prompt requested
biometric confirmation for a one-time installation without scanning. After
the device owner confirmed, installation completed. No Play Protect setting
was disabled.

The installed package reports Spotify `9.1.80.2221`, version code `145767611`,
and `arm64-v8a`. Its sole installed APK has the same SHA-256 as the exported
Manager build above. Android accepted the normal launcher intent. The device
owner signed in successfully.

## Signed-in phone tests and sharing fix

The installed `v1.0.0-dev.1` build loads Home and an album page. Playback
advances through tracks, the queue opens, and Android reports local playback.
Playback continues after leaving Spotify. Android's media card pauses and
resumes playback, with matching `PAUSED` and `PLAYING` session states. The
test ended paused. The device owner confirmed normal audible playback.
The device picker reaches the nearby-devices permission explanation; no
permission was granted and no Connect transfer was tested.

The normal album flow, **Share > More sharing options**, exposed a defect:
Android's share preview still contained `si` and
`utm_source=native-share-menu`. Static inspection found a server URL generator
that bypasses the patched local builder. Both generators produce a common
result with separate shareable URL, share ID, Spotify URI, and full URL fields.
The native share intent reads that result's URL. This proves the bypass exists;
the phone's exact generator branch was not instrumented.

The fix also sanitizes the two final URL arguments before storage, preserving
the separate share ID and Spotify URI. Resolution follows named protobuf
fields and their getter-to-constructor dataflow, and rejects incompatible or
ambiguous layouts. The original local hook remains for its other caller.

The updated artifact checker rejects the old phone APK because both final URL
hooks are absent. A newly patched local APK passes all three hook checks,
signature verification, and preservation of all 1,145 default colors and IDs.
Its SHA-256 is
`cea209048243490de3dcbc07f85df40fc1c01002c5175ca3644882006d97aeaa`.
A combined build using Fast mode and an 896 MB heap also passes, including
the ten expected default theme changes. Its SHA-256 is
`cb5068005b5a3ee6e9bc266ea2e6780872d61ed9d741ca597b6d11ae19ae32e3`.
Seven resolver tests, twelve verifier cases, the existing unit suite, and six
actual-APK refusal cases pass. Phone verification follows below.

## Verified dev.2 update on Pixel 8

The fix shipped in experimental release `v1.0.0-dev.2`. Its published bundle
has SHA-256
`9d12f13ef91afde27bdeba029735acf6994f07a8b2a06e5dd1f2d6fa296143d5`.
The release tag contains fix commit `c01bddb`, and GitHub build provenance
verification passed. Release run `35332792343` passed tests, publishing, and
attestation. The push did not start Actions; manual workflow dispatch was
required again. Stable publishing remains disabled.

Manager's source update control fetched `dev.2`. The existing Spotify entry's
**Patch** action rebuilt the sharing-only profile, and **Save** exported it.
The export passed all three hook checks, signature verification, and all 1,145
unchanged default color values and IDs. Its signing certificate matches the
previous Manager build. Installing through Manager updated Spotify without
an uninstall. The installed APK exactly matches the verified export:
`28e4f09b2cf395afce4ef3dd36e36aaa61c3c1ffc8f9a489ba6112777ea3c6c2`.

The signed-in Home screen and paused player state survived the update. The
device owner confirmed the updated app works. Android's share preview showed
the expected album, track, and playlist destinations with no query parameters.
The playlist preview includes Spotify's introductory text before the clean
URL. No recipient was selected and no message was sent. Episode sharing,
timestamp preservation, copied-link contents, and opening the resulting links
still need runtime checks. Device interaction stopped after the owner's
confirmation. The nearby-device prompt was declined; Connect remains untested.

CI also uploaded a bundle with the previous version's filename alongside the
correct `dev.2` asset. The extra asset was removed; the metadata feed always
pointed to the correct bundle. Commit `1caf103` removes validation-build bundles
before release version selection. Workflow run `35333603978` passed. The
`dev.3` release below confirms only the correctly versioned asset was uploaded.

## Local settings implementation

The local settings build adds a native **Spicetify** row before **Log out**,
a private Activity, persistent clean-sharing preferences, and installed-patch
capability flags. The theme-only profile bundles the shared extension but has
zero sharing hooks. Older evidence above describes the release tested at that
time, including the previous helper-absence check.

Build, unit tests, extension lint, and all three patch profiles pass. The
sharing verifier checks one local and two result hooks into `onShareUrl`, plus
the branch that preserves the original URL when cleanup is disabled. Its 26
regression cases pass. Four preference tests and two native snapshot tests
pass. Independent settings DEX checks verify application initialization, menu
insertion, capability constants, the reserved analytics mapping, bridge
references, and navigation signatures. Fifteen mutated artifact cases are
rejected. Manifest inspection confirms one non-exported settings Activity,
no intent filter, and unchanged app permissions.

Seven actual-APK refusal cases pass with no output file. Altering the root
settings marker fails the `xlt` snapshot. Altering the sharing marker fails
the earlier `ion` snapshot because this obfuscated class also contains the
marker. Changed server response fields and all existing theme refusal cases
still report their expected failures.

The README source button uses `github=spicetify/morphe-patches/tree/dev`.
Official website code preserves the branch, and Manager 1.31.1 converts it to
the raw dev metadata URL and enables prereleases. Isolated execution of the
website script confirms the generated intent. On the Pixel, the deployed
page opened in Firefox and **Open in Morphe** reached Manager's **Add source**
confirmation with the correct GitHub repository and `dev` branch. Cancelling
preserved the existing source without adding a duplicate.

Manager 1.31.1 with Patcher 1.14.0 built the combined local settings bundle
on the Android 16 emulator using **Fast** mode and its cached original APK.
The same-key installation preserved app data, and Android's bytecode
verification returned **Success**. The installed APK independently passes
the sharing, settings, manifest, signature, and 1,145 color-resource checks.
Its SHA-256 is
`916f8d56ae85fc7674f6dd4608083cba15b12fad20e293eb5e6a9d7ee2b09239`.
Spotify reaches its login screen, so this run does not verify the signed-in
settings menu.

The first settings release attempt passed tests and bundle compilation but
failed while generating the source archive. Commit `740d7a9` makes Gradle
track the native bridge producer for every resource consumer. Running
`generatePatchesList buildAndroid` together then passed locally.

[Release run 35339516642](https://github.com/spicetify/morphe-patches/actions/runs/35339516642)
passed tests, Android lint, source packaging, publishing, and attestation.
The `v1.0.0-dev.3` tag contains settings commit `7822e51` and packaging fix
`740d7a9`. Only the correctly versioned bundle was uploaded. Its SHA-256 is
`5a36f432cc717e08bb6a3b2998a54537d7ad1e6809d65f79ea0a9d31836f8eba`.
GitHub provenance verification passes, and the dev feed points to this asset.
Manager's existing source update control fetched `dev.3` on the Pixel.
This run was manually dispatched; stable publishing remains disabled.

The first dedicated review attempt could not complete because reviewer
capacity was unavailable. A later six-part review completed against `757d279`,
covering correctness, tests, maintainability, security, reliability, and
adversarial cases. It found no confirmed production-code defect. It reproduced
an output-verifier gap: replacing the menu-insertion method with `return-void`
still passed. A separate probe also accepted a missing settings Activity.

The verifier now requires the Activity and its constructor, lifecycle method,
and launch method. It resolves extension-owned bridge references and compares
all four complete installed bridge classes with the exact patch bundle's
canonical `extensions/settings.dex`. It serializes each class separately to
avoid differences from APK-level DEX layout. The artifact report records the
bundle hash as well as the APK hashes. Matching the bundle complements source
review and runtime testing; it does not prove arbitrary bundle code is correct.

Both false passes were reproduced before the fixes. All 22 mutation cases now
pass, including empty menu and navigation methods, missing Activity methods,
and the previous hook and ABI cases. Real Manager and Desktop outputs match
the published bundle's bridge. A focused follow-up review independently reran
the 22 cases and confirmed the argument wiring and refusal of a bundle without
the bridge. Both findings are resolved. No external code-review service was
used.

## Verified dev.3 settings on Pixel 8

Manager 1.31.1 fetched `dev.3` through the existing source update control.
Spotify's **Patch** action rebuilt the default sharing-only profile from the
cached original APK. The exported APK passed the independent sharing, settings,
manifest, signature, and 1,145 unchanged color-resource checks. Its certificate
matches the installed `dev.2` build. Installing through Manager updated the
app without uninstalling it and preserved the signed-in Home screen.

The installed APK matches the verified export byte for byte, with SHA-256
`3099526fa835387c3ba6f16a2ba8729e20bdcb4e291ebb6f33ca2af486c6962a`.
The device remains Pixel 8, Android 17, ARM64, Spotify `9.1.80.2221`.

The normal **Settings and privacy** list shows one **Spicetify** row above
**Log out**. It opens the private Activity with **Clean sharing links** enabled
and no theme control, as expected for this profile. Toolbar Back and Android
Back return to Spotify. Repeated opening and a full process restart retain
one row. Spotify's existing **Playback** settings still open normally.

Turning cleanup off makes Android's share preview for the current track retain
`si` and `utm_source`. Reopening the menu and restarting Spotify preserve the
off state. Turning cleanup on immediately produces the same track destination
without query parameters, with no further restart. No recipient was selected
and no message was sent. The final switch state is enabled.

The settings screen remains responsive during playback. Android reports
`PLAYING`, and the player proceeds from the original track through ads to
another track. Playback was paused at the end. Portrait captures at normal
and 150% text size, plus landscape at 150%, show readable labels, no overlapping
controls, and content clear of system bars. The switch retains its state
through Activity recreation and exposes a labeled, checkable Android switch
to accessibility.
The original font size and portrait rotation were restored. TalkBack speech
was not tested.

Manager's Expert mode then built a combined profile using Patcher 1.14.0,
**Fast** mode, a 1024 MB process limit, and the cached original APK. Only this
source's two patches were selected. The exported APK passed the updated
artifact checker, including exact bridge comparison, all ten expected theme
changes, and unchanged app permissions. Its SHA-256 is
`44c018ec587245c18d215c4d93439110d2795560169fdea63a374a4a1a33413d`.
Installation again preserved login. The sharing switch had been set off
before updating and remained off afterward, proving preference retention
across a same-key settings update. The combined menu shows both the switch
and the theme repatching instructions, with readable labels and spacing.

Manager also built and installed the theme-only profile. Its exported APK has
SHA-256 `665daba026014fe5c9b829e4ebde101dff7a8d3b37eff54e9b3c7baa8c7180b2`
and passes artifact checks with zero sharing hooks, the theme capability,
ten color changes, and the canonical settings bridge. The signed-in settings
screen shows only theme instructions, without a sharing switch. Back returns
to Spotify. Library and its sorting dialog remain readable and usable.

After these tests, ADB installed the previously verified, Manager-signed
default APK as a data-preserving update. This restoration used ADB rather
than repeating the already-tested Manager installation flow. Clean sharing
was restored to enabled through Spotify's visible settings screen, and
Manager's Expert mode was turned off. Playback remained paused.

Another notification/queue pass on `dev.3` and the broader checklist below
remain open.

## Optional feature development checkpoint

On September 18, the additional settings, Home pinning, and server-file source
compiled and applied to the stock Spotify fixture in an isolated evaluation.
Five characterization tests against that unchanged source reproduced
cross-origin credential transmission, incorrect bytes after an ignored range
request, colliding track identifiers, stale indexes after changing servers,
and missing shortcut discovery on first use. Only fake credentials and
loopback fixtures were used. Its APK was not installed on the Pixel.

The port uses the existing private Spicetify settings screen. Home pinning
copies the verified native list and identifies shortcuts by URI. Server files
use an immutable configuration, constrained HTTPS URLs, validated byte ranges,
bounded scans, and a private provider without disk audio caching. Both new
patches are disabled by default. Android 7 keeps the other patches available;
server streaming requires Android 8 or later.

The final local suite passes 53 extension tests, 34 patch tests, 26 sharing
verifier cases, 22 settings-verifier negative cases, Android lint, and bundle
packaging. Independent review
verified the eight Home snapshots and four local-file snapshots against stock
DEX, then checked the four injected hooks and their public static targets.
Review findings about excess pin selections, disabling with invalid draft
fields, disappearing validation errors, and repeated HTTP headers were fixed
and covered by regression tests.

All four patches apply together. The initial tested local bundle has SHA-256
`4df88f5e62a958ae972fd5d00827f27cba3d93667846c6ddf453f65c062a521a`.
Gradle repackaged the bundle while running the settings-verifier cases.
The frozen checkpoint bundle has SHA-256
`49bd91d851af7dae330081b157214f35e31589422a3035c59b386fe59b10d388`;
the independent artifact check also passes against that exact bundle.
The signed base-APK artifact has SHA-256
`bfac0f27e3796ffd3abe9322091787eea07914b077c89138f3cd30df50ec608c`.
Independent checks pass for the sharing hooks, exact settings bridge, four
capabilities, private Activity/provider, unchanged permissions, signature,
and 1,145 default color resources with ten expected changes. This base-only
artifact is for inspection, not installation without its split resources.
Both new altered-model cases exit with status 1, report the expected ABI
failure, and leave no output APK.

These optional features are not published. Their remaining release checks
are the normal Manager installation flow, signed-in Home ordering and
unpinning, Android HTTPS validation, metadata extraction, Local Files
playback/seeking, disabling during reads, and configuration replacement.
The default Pixel installation remains the published `dev.3` sharing profile.

### Recreated emulator installation check

After the temporary test files were removed, the stock archive and the
Manager-signed default recovery APK were recovered from their known Downloads
paths on the Pixel. Their checksums match the recorded fixtures. The original
Java 21 build environment was restored from a checksum-verified distribution.

Commit `47a04d5` avoids unchanged status updates, repeated audio-pattern
compilation, and repeated linear searches for duplicate tracks. The 53
extension tests, 34 patch tests, 26 sharing-verifier cases, Android lint, and
Android bundle build pass. The frozen bundle has SHA-256
`25e717af66944f3396eb8b0f5e54a4939913bb9f8cc7811d33be1a3b7690d70e`.

A recreated disposable Android 16 ARM64 emulator used Manager 1.31.1, the
640 MB process runtime, and the stock split archive. Through Manager's visible
flow, the local bundle imported, all four patches were selected, patching
completed, and Android installed the result. Launching Spotify's normal main
Activity reached **Log in** and **Sign up free**. No account was entered. The
emulator was then closed.

The installed APK has SHA-256
`523eb6690b6b56962423511960ec4f075f3b97303e3dbea5dd80ef85637b638e`.
Independent artifact checks pass against the frozen bundle for sharing hooks,
all four capabilities, the canonical settings bridge, private components,
unchanged permissions, signature, and ten expected theme-color changes.
This proves installation and unauthenticated launch, not signed-in Home
ordering or server-file playback.

The Pixel's local bundle import works through Android's system picker after
turning off Manager's **System > Custom file picker** preference. It does not
require granting **All files access** on the phone. Manager built the same
four-patch profile from its saved original APK with the 1,024 MB process
runtime. Its exported APK has SHA-256
`7b64bd88ff6ba397a1f182376c2da6ba477de5e59acf68130332657684ae6b85`.
It passes the same artifact checks and uses the existing Pixel Manager
signing certificate. Android's update flow is waiting for direct fingerprint
confirmation. The installed APK still matches the default sharing profile;
signed-in optional-feature testing remains open.

## Runtime and release checklist

Use the normal Manager entry point before claiming release readiness.

- [x] Build the Android bundle and run all unit tests.
- [x] Inspect generated patch metadata and extension contents.
- [x] Patch sharing, colors, and their combination against the stock fixture.
- [ ] Check informative failures for unsupported inputs and invalid options.
- [x] Install and launch the output in a disposable Android environment.
- [x] Add the source in Manager and patch through its visible flow.
- [ ] Test login, playback, queue, Connect, background playback, notifications.
- [ ] Test sharing for tracks, albums, playlists, episodes, and timestamps.
- [ ] Check Home, library, player, settings, and dialog colors.
- [ ] Test source updates, same-key reinstall, cancellation, and stock recovery.
- [ ] Verify a push starts CI and prerelease automation for the expected commit.

The verified clean-sharing build is installed on the source phone. No stable
compatibility claim is made from installation or static verification alone.
