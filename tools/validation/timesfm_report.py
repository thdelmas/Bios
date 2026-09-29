import json, sys
r = json.load(open(sys.argv[1]))
def f(x, n=2): return "-" if x is None else f"{x:.{n}f}"
def row(name, e, thr="-"):
    hc = e["healthy_controls"]
    return (f"| {name} | {thr} | {e['detected']}/{e['evaluable']} | {e['median_lead']} | "
            f"{f(e['fa_month'])} ({e['healthy_hits']}/{e['healthy_days']}) | {f(hc['fa_month'])} ({hc['hits']}/{hc['days']}) |")
head = "| Detector | Threshold | Detected pre-onset | Median lead (d) | Alarm days / person-month, healthy windows of cases | Same, 73 healthy participants |\n|---|---|---|---|---|---|"
print("control_match:", r["control_match"], "\n")
print("## At each detector's z-equivalent 1.5\n"); print(head)
for n, d in r["detectors"].items():
    print(row(n, next(c for c in d["curve"] if c["threshold"] == 1.5), 1.5))
    if "native_above_q90" in d: print(row(n + " (value > q90)", d["native_above_q90"]))
print("\n## Matched sensitivity: lowest false-alarm rate that still detects >= 21\n"); print(head)
for n, d in r["detectors"].items():
    ok = [c for c in d["curve"] if c["detected"] >= 21]
    print(row(n, max(ok, key=lambda c: c["threshold"]), max(ok, key=lambda c: c["threshold"])["threshold"]) if ok else f"| {n} | never reaches 21 | | | | |")
print("\n## Matched false alarms: best detection at <= 1.0 and <= 0.66 alarm days / person-month\n"); print(head)
for lim in (1.0, 0.66):
    for n, d in r["detectors"].items():
        ok = [c for c in d["curve"] if c["fa_month"] <= lim]
        if ok:
            b = min(ok, key=lambda c: c["threshold"])
            print(row(f"{n} (<= {lim})", b, b["threshold"]))
        else:
            print(f"| {n} (<= {lim}) | not reached by z 3.5 | | | | |")
print("\n## CuSum over each score (k 0.5, h 3, reset after alarm)\n"); print(head)
for n, d in r["detectors"].items(): print(row(n, d["cusum_k0.5_h3"]))
print("\n## Forecast error, mean absolute, bpm\n")
for n, d in r["detectors"].items():
    if "mae" in d: print(n, d["note"], json.dumps(d["mae"]))
