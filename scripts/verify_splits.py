#!/usr/bin/env python3
"""Assert actual binary manifests. Never assume the APK filename equals its split id."""
import argparse, pathlib, re, subprocess, zipfile
p=argparse.ArgumentParser();p.add_argument("directory",type=pathlib.Path);p.add_argument("--aapt2",required=True);args=p.parse_args()
def dump(apk):
    return subprocess.check_output([args.aapt2,"dump","xmltree",str(apk),"--file","AndroidManifest.xml"],text=True)
from manifest_attributes import has_attribute as attr
apks=list(args.directory.rglob("*.apk"))
if not apks: raise SystemExit("No APKs found")
density=None;base=None;native=False
for apk in apks:
    manifest=dump(apk)
    if attr(manifest,"split","config.xxhdpi"): density=(apk,manifest)
    if not re.search(r'\bsplit(?:\([^)]*\))?=',manifest): base=(apk,manifest)
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if name.startswith("lib/") and name.endswith("/libnative-lib.so"): native=True
if not density: raise SystemExit("FAIL: config.xxhdpi manifest not found")
if not base: raise SystemExit("FAIL: base manifest not found")
(args.directory/"density-manifest.txt").write_text(density[1]);(args.directory/"base-manifest.txt").write_text(base[1])
print(density[1],flush=True)
assert attr(density[1],"splitTypes","base__density"), "Missing base__density"
assert attr(density[1],"hasCode",False), "Density split must have hasCode=false"
assert attr(base[1],"extractNativeLibs",True), "Base must have extractNativeLibs=true"
assert native, "No libnative-lib.so found"
with zipfile.ZipFile(density[0]) as z: assert not any(n.endswith(".dex") for n in z.namelist()), "Density split contains dex"
print("PASS density:",density[0]);print("PASS base:",base[0]);print("PASS native library present; no density dex")
(args.directory/"density-manifest.txt").write_text(density[1]);(args.directory/"base-manifest.txt").write_text(base[1])
