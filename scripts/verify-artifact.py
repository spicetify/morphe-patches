#!/usr/bin/env python3
"""Inspect a patched APK independently of the patcher's success report."""

import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path


def colors(aapt2, apk):
    output = subprocess.check_output([aapt2, "dump", "resources", str(apk)], text=True)
    values = {}
    for match in re.finditer(
        r"^    resource (0x[0-9a-f]+) color/(\S+)\n(.*?)(?=^    resource |\Z)",
        output, re.MULTILINE | re.DOTALL,
    ):
        default = re.search(r"^      \(\) (.+)$", match[3], re.MULTILINE)
        if default:
            values[match[2]] = (match[1], default[1])
    if not values:
        raise ValueError("No default color resources found")
    return values


def digest(path):
    checksum = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest()


def xml_color(aapt2, apk, value):
    match = re.fullmatch(r"\(file\) (\S+) type=XML", value)
    if not match:
        return None
    output = subprocess.check_output([
        aapt2, "dump", "xmltree", str(apk), "--file", match[1],
    ], text=True)
    return re.sub(r" \(line=\d+\)", "", output)


def manifest_blocks(aapt2, apk):
    output = subprocess.check_output([
        aapt2, "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml",
    ], text=True)
    lines = output.splitlines()
    blocks = []
    for index, line in enumerate(lines):
        match = re.match(r"( *)E: ([\w-]+) ", line)
        if not match:
            continue
        end = index + 1
        while end < len(lines):
            candidate = lines[end]
            if candidate.strip() and len(candidate) - len(candidate.lstrip()) <= len(match[1]):
                break
            end += 1
        blocks.append((match[2], "\n".join(lines[index:end])))
    return blocks


def verify_manifest(aapt2, stock, patched, server_files=False):
    before, after = (manifest_blocks(aapt2, apk) for apk in (stock, patched))
    activity_name = "app.spicetify.extension.spotify.settings.SpicetifySettingsActivity"
    activities = [body for kind, body in after if kind == "activity"
                  and f'="{activity_name}"' in body]
    if len(activities) != 1:
        raise AssertionError("Expected exactly one Spicetify settings activity")
    activity = activities[0]
    if not re.search(r":exported\(0x[0-9a-f]+\)=false", activity):
        raise AssertionError("Spicetify settings activity must be non-exported")
    if "E: intent-filter" in activity:
        raise AssertionError("Spicetify settings activity must have no public intent filter")
    providers = [body for kind, body in after if kind == "provider"
                 and '="app.spicetify.extension.spotify.localserver.ServerFileProvider"' in body]
    if len(providers) != int(server_files):
        raise AssertionError("Server provider does not match the selected patches")
    if providers:
        provider = providers[0]
        for attribute in ("exported", "grantUriPermissions"):
            if not re.search(rf":{attribute}\(0x[0-9a-f]+\)=false", provider):
                raise AssertionError(f"Server provider must disable {attribute}")
        if '="com.spotify.music.spicetify.localserver"' not in provider:
            raise AssertionError("Server provider authority changed")
        if "E: grant-uri-permission" in provider or "E: intent-filter" in provider:
            raise AssertionError("Server provider must not expose URI grants or intent filters")
    def permissions(blocks):
        return sorted(re.sub(r" \(line=\d+\)", "", body)
                      for kind, body in blocks if kind.startswith("uses-permission"))
    if permissions(before) != permissions(after):
        raise AssertionError("Settings patch changed app permissions")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stock", type=Path, required=True, help="Stock base APK")
    parser.add_argument("--patched", type=Path, required=True)
    parser.add_argument("--bundle", type=Path, required=True, help="Exact patch bundle used for this APK")
    parser.add_argument("--desktop", type=Path, required=True, help="Morphe Desktop all.jar")
    parser.add_argument("--java", default="java")
    parser.add_argument("--aapt2", required=True)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--sharing", action="store_true")
    parser.add_argument("--home-pins", action="store_true")
    parser.add_argument("--server-files", action="store_true")
    parser.add_argument("--theme", nargs=3, metavar=("BACKGROUND", "ACCENT", "PRESSED"))
    args = parser.parse_args()
    before = colors(args.aapt2, args.stock)
    after = colors(args.aapt2, args.patched)
    expected = {}
    if args.theme:
        normalized = []
        for value in args.theme:
            if not re.fullmatch(r"#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?", value):
                parser.error("Colors must be #RRGGBB or #AARRGGBB")
            normalized.append(("#ff" + value[1:] if len(value) == 7 else value).lower())
        background, accent, pressed = normalized
        expected = dict.fromkeys([
            "gray_7", "gray_10", "dark_base_background_base",
            "dark_base_background_elevated_base", "bg_gradient_end_color", "sthlm_blk",
        ], background)
        expected.update(dict.fromkeys([
            "dark_brightaccent_background_base", "dark_base_text_brightaccent", "green_light",
        ], accent))
        expected["dark_brightaccent_background_press"] = pressed
    for name, (resource_id, value) in before.items():
        wanted = (resource_id, expected.get(name, value))
        if after.get(name) != wanted:
            actual = after.get(name)
            # Resource rebuilding can relocate an unchanged compiled selector.
            original_xml = xml_color(args.aapt2, args.stock, value) if name not in expected else None
            if (actual is None or actual[0] != resource_id or original_xml is None
                    or xml_color(args.aapt2, args.patched, actual[1]) != original_xml):
                raise AssertionError(f"Color {name}: expected {wanted}, got {actual}")
    if missing := expected.keys() - before.keys():
        raise AssertionError(f"Stock fixture lacks colors: {sorted(missing)}")
    subprocess.run([
        args.java, "-Xmx2g", "-cp", str(args.desktop),
        str(Path(__file__).with_name("VerifySharingDex.java")),
        str(args.patched), "1" if args.sharing else "0",
        "1" if args.sharing or args.theme or args.home_pins or args.server_files else "0",
    ], check=True)
    if args.sharing or args.theme or args.home_pins or args.server_files:
        verify_manifest(args.aapt2, args.stock, args.patched, args.server_files)
        subprocess.run([
            args.java, "-Xmx2g", "-cp", str(args.desktop),
            str(Path(__file__).with_name("VerifySettingsDex.java")),
            str(args.patched), "1" if args.sharing else "0", "1" if args.theme else "0",
            str(args.bundle),
            "1" if args.home_pins else "0", "1" if args.server_files else "0",
        ], check=True)
    subprocess.run([args.apksigner, "verify", str(args.patched)], check=True)
    print(json.dumps({
        "stockSha256": digest(args.stock), "patchedSha256": digest(args.patched),
        "bundleSha256": digest(args.bundle),
        "defaultColorsChecked": len(before), "themeColorsChanged": len(expected),
        "sharing": args.sharing, "signatureVerified": True,
        "homePins": args.home_pins, "serverFiles": args.server_files,
    }, indent=2))


if __name__ == "__main__":
    main()
