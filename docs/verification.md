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
The combined output hash is
`d943ae352dacea106e7c97020bb22131d757014f6c8feb8ec89d2883f174025d`.

Selecting the sharing patch for the unrelated Manager package applies zero
patches. Desktop still signs an unchanged output and returns success in that
case. A CLI exit code alone is insufficient; check the report's applied patch
names and run the artifact verifier.

An invalid color initially exposed Morphe's option setter retaining defaults
after validator errors. Commit `56c4cc0` moves format validation into patch
execution. The repeated CLI test now exits 1, reports the invalid format, and
produces no APK. Regression tests use Morphe's actual option setter for all
three fields.

A source review found a verifier gap: a same-name call with a mismatched
method descriptor could pass. Synthetic DEX tests reproduced that false pass.
The verifier now requires the exact String-to-String descriptor and a public
static helper. It accepts the valid fixture and rejects four malformed cases.

Initial Android 16 emulator startup was overloaded and showed a Spotify ANR.
Stock Spotify subsequently reached its welcome screen in the same emulator.
The combined patched APK installs and reaches the same welcome-screen
accessibility controls on a controlled retry. Hardware-rendered screenshots
show a black app area, so visual color verification is still open. This does
not establish logged-in behavior or Manager compatibility.

## Runtime and release checklist

Use the normal Manager entry point before claiming release readiness.

- [x] Build the Android bundle and run all unit tests.
- [x] Inspect generated patch metadata and extension contents.
- [x] Patch sharing, colors, and their combination against the stock fixture.
- [ ] Check informative failures for unsupported inputs and invalid options.
- [ ] Install and launch the output in a disposable Android environment.
- [ ] Add the source in Manager and patch through its visible flow.
- [ ] Test login, playback, queue, Connect, background playback, notifications.
- [ ] Test sharing for tracks, albums, playlists, episodes, and timestamps.
- [ ] Check Home, library, player, settings, and dialog colors.
- [ ] Test source updates, same-key reinstall, cancellation, and stock recovery.

No patched application has been installed on the source phone. No stable
compatibility claim is made from a successful build or static match.
