#!/usr/bin/env python3
"""TimesFM 2.5 replay over the Mishra 2020 derived RHR series.

Question: does a TimesFM one-step forecast (quantile band) make a better
resting-heart-rate anomaly detector than the shipped Bios rule
(z > +1.5 sigma vs a 14-day baseline)?

Control first: the shipped rule is re-implemented here and must reproduce the
figures of docs/validation/mishra-2020-replay.md (24 evaluable, 21 detected,
median lead 9 d, 120 hits on 1,138 healthy person-days). If it does not, the
scoring differs from the Kotlin harness and no TimesFM figure may be compared.

Usage: timesfm_replay.py <derived_dir> <out_dir> [--no-model]
"""
import csv
import json
import math
import sys
from collections import defaultdict
from datetime import date, timedelta

import numpy as np

DAY_MS = 86_400_000
PRE, POST, GAP = 14, 7, 21          # same windows as MishraReplayTest
MIN_SAMPLES = 10                     # BaselineEngine.MIN_SAMPLES_FOR_BASELINE
CONTEXTS = [14, 28, 64]              # calendar days of history given to TimesFM
THRESHOLDS = [round(0.5 + 0.1 * i, 1) for i in range(31)]   # 0.5 .. 3.5


def load(derived):
    rhr = defaultdict(dict)
    for r in csv.DictReader(open(f"{derived}/readings.csv")):
        if r["metric"] != "resting_heart_rate":
            continue
        d = date.fromordinal(date(1970, 1, 1).toordinal() + int(r["timestamp_ms"]) // DAY_MS)
        rhr[r["user"]].setdefault(d, []).append(float(r["value"]))
    rhr = {u: {d: sum(v) / len(v) for d, v in days.items()} for u, days in rhr.items()}
    labels = {r["user"]: r for r in csv.DictReader(open(f"{derived}/labels.csv"))}
    return rhr, labels


def pdate(s):
    return date.fromisoformat(s) if s else None


class Windows:
    def __init__(self, lab):
        self.onset = pdate(lab["symptom_onset"])
        rec = pdate(lab["recovery"])
        self.pre_start = self.onset - timedelta(days=PRE)
        self.post_end = self.onset + timedelta(days=POST)
        self.healthy_end = rec + timedelta(days=POST) if rec else self.post_end

    def healthy(self, d):
        return d < self.onset - timedelta(days=GAP) or d > self.healthy_end

    def pre(self, d):
        return self.pre_start <= d <= self.onset


def zscore_series(days, window):
    """Shipped rule: baseline over [d-window, d] incl. day d, kept when stale."""
    out = {}
    base = None
    d, last = min(days), max(days)
    while d <= last:
        # engine clock = 23:00 of day d, readings stamped 06:00 -> window covers
        # days d-window+1 .. d, plus day d-window only if stamped after 23:00 (never)
        vals = [days[x] for x in (d - timedelta(days=i) for i in range(window)) if x in days]
        if len(vals) >= MIN_SAMPLES:
            m = sum(vals) / len(vals)
            sd = math.sqrt(sum((v - m) ** 2 for v in vals) / len(vals))
            base = (m, sd)
        if base and d in days and base[1] > 0:
            out[d] = (days[d] - base[0]) / base[1]
        d += timedelta(days=1)
    return out


def context_for(days, d, n):
    """Daily grid of the n days before d; gaps interpolated, edges held."""
    grid = [days.get(d - timedelta(days=i)) for i in range(n, 0, -1)]
    real = [i for i, v in enumerate(grid) if v is not None]
    if len(real) < MIN_SAMPLES:
        return None
    xs = np.array(real, dtype=float)
    ys = np.array([grid[i] for i in real], dtype=float)
    full = np.interp(np.arange(real[0], n), xs, ys)   # leading gap dropped, trailing held
    return full.astype(np.float32)


def cusum(scores, k=0.5, h=3.0):
    """One-sided CuSum over a dated score series, reset after each alarm."""
    s, out = 0.0, {}
    prev = None
    for d in sorted(scores):
        if prev and (d - prev).days > 3:
            s = 0.0
        s = max(0.0, s + scores[d] - k)
        out[d] = s >= h
        if out[d]:
            s = 0.0
        prev = d
    return out


def evaluate(hits_by_user, monitored_by_user, labels, covid_users):
    """hits_by_user[u] = {date: bool}. Returns the harness figures."""
    evaluable = detected = healthy_days = healthy_hits = 0
    leads = []
    for u in covid_users:
        w = Windows(labels[u])
        mon = monitored_by_user[u]
        if not any(w.pre(d) for d in mon):
            healthy_days += sum(1 for d in mon if w.healthy(d))
            healthy_hits += sum(1 for d in mon if w.healthy(d) and hits_by_user[u].get(d))
            continue
        evaluable += 1
        pre_hits = [d for d in mon if w.pre(d) and hits_by_user[u].get(d)]
        if pre_hits:
            detected += 1
            leads.append((w.onset - min(pre_hits)).days)
        healthy_days += sum(1 for d in mon if w.healthy(d))
        healthy_hits += sum(1 for d in mon if w.healthy(d) and hits_by_user[u].get(d))
    leads.sort()
    return {
        "evaluable": evaluable,
        "detected": detected,
        "sens": detected / evaluable if evaluable else None,
        "median_lead": leads[len(leads) // 2] if leads else None,
        "healthy_days": healthy_days,
        "healthy_hits": healthy_hits,
        "fa_month": healthy_hits * 30.0 / healthy_days if healthy_days else None,
    }


def control_fa(hits_by_user, monitored_by_user, users):
    days = sum(len(monitored_by_user[u]) for u in users)
    hits = sum(1 for u in users for d in monitored_by_user[u] if hits_by_user[u].get(d))
    return {"users": len(users), "days": days, "hits": hits,
            "fa_month": hits * 30.0 / days if days else None}


def main():
    derived, out_dir = sys.argv[1], sys.argv[2]
    no_model = "--no-model" in sys.argv
    rhr, labels = load(derived)
    covid = sorted(u for u in rhr if labels[u]["category"] == "COVID-19" and labels[u]["symptom_onset"])
    healthy = sorted(u for u in rhr if labels[u]["category"] == "Healthy")
    users = covid + healthy

    z14 = {u: zscore_series(rhr[u], 14) for u in users}
    z28 = {u: zscore_series(rhr[u], 28) for u in users}
    monitored = {u: sorted(z14[u]) for u in users}   # same person-days for every detector

    result = {"n_covid": len(covid), "n_healthy": len(healthy), "detectors": {}}

    def add(name, scores, note=""):
        curve = []
        for t in THRESHOLDS:
            hits = {u: {d: s > t for d, s in scores[u].items()} for u in users}
            e = evaluate(hits, monitored, labels, covid)
            e["threshold"] = t
            e["healthy_controls"] = control_fa(hits, monitored, healthy)
            curve.append(e)
        cs = {u: cusum(scores[u]) for u in users}
        e = evaluate(cs, monitored, labels, covid)
        e["healthy_controls"] = control_fa(cs, monitored, healthy)
        result["detectors"][name] = {"note": note, "curve": curve, "cusum_k0.5_h3": e}

    add("z14_shipped", z14, "shipped rule, 14-day baseline")
    add("z28", {u: {d: z28[u][d] for d in monitored[u] if d in z28[u]} for u in users}, "28-day baseline")

    ctrl = next(c for c in result["detectors"]["z14_shipped"]["curve"] if c["threshold"] == 1.5)
    print("CONTROL z14 @1.5:", json.dumps({k: ctrl[k] for k in
          ("evaluable", "detected", "median_lead", "healthy_days", "healthy_hits", "fa_month")}))
    expected = {"evaluable": 24, "detected": 21, "median_lead": 9, "healthy_days": 1138, "healthy_hits": 120}
    result["control_expected"] = expected
    result["control_match"] = all(ctrl[k] == v for k, v in expected.items())
    print("CONTROL MATCH:", result["control_match"])

    if not no_model:
        import timesfm
        import torch
        torch.set_num_threads(12)
        model = timesfm.TimesFM_2p5_200M_torch.from_pretrained("google/timesfm-2.5-200m-pytorch")
        model.compile(timesfm.ForecastConfig(
            max_context=64, max_horizon=32, normalize_inputs=True,
            use_continuous_quantile_head=True, force_flip_invariance=True,
            infer_is_positive=True, fix_quantile_crossing=True, per_core_batch_size=64))
        for n in CONTEXTS:
            keys, ctxs = [], []
            for u in users:
                for d in monitored[u]:
                    c = context_for(rhr[u], d, n)
                    if c is not None:
                        keys.append((u, d))
                        ctxs.append(c)
            print(f"context {n}: {len(ctxs)} forecasts", flush=True)
            scores = {u: {} for u in users}
            band = {u: {} for u in users}
            abs_err = []
            for i in range(0, len(ctxs), 256):
                _, q = model.forecast(horizon=1, inputs=ctxs[i:i + 256])
                for (u, d), row in zip(keys[i:i + 256], q[:, 0, :]):
                    q10, q50, q90 = float(row[1]), float(row[5]), float(row[9])
                    width = max(q90 - q10, 1e-6)
                    # 2.563 sigma between q10 and q90 of a normal -> z-equivalent
                    scores[u][d] = (rhr[u][d] - q50) / (width / 2.563)
                    band[u][d] = rhr[u][d] > q90
                    abs_err.append(abs(rhr[u][d] - q50))
                print(f"  {min(i + 256, len(ctxs))}/{len(ctxs)}", flush=True)
            covered = sum(len(scores[u]) for u in users) / sum(len(monitored[u]) for u in users)
            add(f"timesfm_ctx{n}", scores, f"TimesFM 2.5 one-step forecast, {n}-day context, "
                                          f"covers {covered:.0%} of monitored person-days")
            e = evaluate(band, monitored, labels, covid)
            e["healthy_controls"] = control_fa(band, monitored, healthy)
            result["detectors"][f"timesfm_ctx{n}"]["native_above_q90"] = e
            # forecast accuracy vs naive predictors on healthy person-days
            err_last, err_mean = [], []
            for u in users:
                for d in scores[u]:
                    c = context_for(rhr[u], d, n)
                    err_last.append(abs(rhr[u][d] - c[-1]))
                    err_mean.append(abs(rhr[u][d] - float(np.mean(c))))
            result["detectors"][f"timesfm_ctx{n}"]["mae"] = {
                "timesfm_q50": float(np.mean(abs_err)),
                "naive_last": float(np.mean(err_last)), "naive_mean": float(np.mean(err_mean))}
            result["detectors"][f"timesfm_ctx{n}"]["_scores_n"] = len(keys)
            # model MAE needs q50: recompute from scores is lossy, so store directly

    json.dump(result, open(f"{out_dir}/result.json", "w"), indent=1, default=str)
    print("written", f"{out_dir}/result.json")


if __name__ == "__main__":
    main()
