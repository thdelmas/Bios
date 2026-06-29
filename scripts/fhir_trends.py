#!/usr/bin/env python3
"""fhir_trends.py — one-command evolution report from a Bios FHIR export.

The Bios DB is SQLCipher-encrypted with a hardware-Keystore-wrapped key, so it
cannot be read off-device. The supported host-side path is the in-app FHIR
export (Settings -> Data & sources -> Export -> FHIR bundle), which the app
writes to /sdcard/Download as a password-protected (WinZip-AES) .zip.

Usage:
  # Pull the newest bios_fhir*.zip off the connected device, decrypt, report:
  python3 fhir_trends.py --password 'PASS'

  # Or analyze a local file:
  python3 fhir_trends.py --zip path/to/bios_fhir.json.zip --password 'PASS'
  python3 fhir_trends.py --json path/to/bios_fhir.json        # already decrypted

Requires pyzipper for AES zips:  pip install pyzipper  (use a venv on PEP-668 systems)
"""
import argparse, glob, json, os, statistics as st, subprocess, sys, tempfile
from collections import defaultdict

DEVICE_DIR = "/sdcard/Download"


def pull_newest_zip(dest_dir):
    out = subprocess.run(["adb", "shell", "ls", "-t", DEVICE_DIR],
                         capture_output=True, text=True).stdout.split()
    name = next((n for n in out if n.startswith("bios_fhir") and n.endswith(".zip")), None)
    if not name:
        sys.exit(f"No bios_fhir*.zip in {DEVICE_DIR} on device. Export from the app first.")
    local = os.path.join(dest_dir, name)
    subprocess.run(["adb", "pull", f"{DEVICE_DIR}/{name}", local], check=True)
    return local


def load_bundle(args, workdir):
    if args.json:
        return json.load(open(args.json))
    zip_path = args.zip or pull_newest_zip(workdir)
    if not args.password:
        sys.exit("--password is required to decrypt the export zip.")
    try:
        import pyzipper
    except ImportError:
        sys.exit("pyzipper not installed:  pip install pyzipper  (venv on PEP-668 systems)")
    with pyzipper.AESZipFile(zip_path) as z:
        z.setpassword(args.password.encode())
        z.extractall(workdir)
    return json.load(open(glob.glob(f"{workdir}/*.json")[0]))


def code_of(o):
    c = o.get("code", {})
    return c.get("text") or (c.get("coding", [{}]) or [{}])[0].get("display") or "??"


def dt_of(o):
    return (o.get("effectiveDateTime") or o.get("effectivePeriod", {}).get("start") or "")


def val_of(o):
    if "valueQuantity" in o:
        return o["valueQuantity"].get("value"), o["valueQuantity"].get("unit", "")
    for k in ("valueInteger", "valueString", "valueBoolean"):
        if k in o:
            return o[k], ""
    if "valueCodeableConcept" in o:
        cc = o["valueCodeableConcept"]
        return cc.get("text") or (cc.get("coding", [{}])[0].get("display")), ""
    return None, ""


def report(bundle):
    obs = [x["resource"] for x in bundle.get("entry", [])
           if x["resource"].get("resourceType") == "Observation"]
    series = defaultdict(list)
    for o in obs:
        d = dt_of(o)
        if d:
            v, u = val_of(o)
            series[code_of(o)].append((d[:10], v, u))
    for k in series:
        series[k].sort()

    alld = sorted(d for s in series.values() for d, _, _ in s)
    print(f"\n{'='*64}\nBIOS FHIR EVOLUTION REPORT")
    if alld:
        print(f"span: {alld[0]} -> {alld[-1]}   ({len(obs)} observations, {len(series)} metrics)")
    print("=" * 64)

    def is_num(x):
        return isinstance(x, (int, float)) and not isinstance(x, bool)

    print(f"\n{'metric':30s} {'n':>4}  {'1st-half':>9} {'2nd-half':>9} {'delta':>9}   range")
    for k in sorted(series, key=lambda k: -len(series[k])):
        vals = [v for _, v, _ in series[k] if is_num(v)]
        unit = next((u for _, _, u in series[k] if u), "")
        if len(vals) >= 4:
            h = len(vals) // 2
            a, z = st.mean(vals[:h]), st.mean(vals[h:])
            print(f"{k:30.30s} {len(vals):>4}  {a:9.2f} {z:9.2f} {z-a:+9.2f}   "
                  f"[{min(vals):g}-{max(vals):g}] {unit}")
        else:
            print(f"{k:30.30s} {len(series[k]):>4}  {'(sparse)':>9}")

    issues = [x["resource"] for x in bundle.get("entry", [])
              if x["resource"].get("resourceType") == "DetectedIssue"]
    if issues:
        print(f"\n{'-'*64}\nDETECTED ISSUES ({len(issues)}):")
        for r in issues:
            detail = r.get("detail") or r.get("code", {}).get("text") or ""
            print(f"  [{r.get('severity','?')}] {detail}")


def main():
    ap = argparse.ArgumentParser(description="Bios FHIR evolution report")
    ap.add_argument("--password", help="export zip password")
    ap.add_argument("--zip", help="local bios_fhir*.zip (skip adb pull)")
    ap.add_argument("--json", help="already-decrypted FHIR json")
    args = ap.parse_args()
    with tempfile.TemporaryDirectory() as wd:
        report(load_bundle(args, wd))


if __name__ == "__main__":
    main()
