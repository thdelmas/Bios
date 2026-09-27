#!/usr/bin/env python3
"""Derive Bios-shaped readings from the Mishra et al. (2020) Fitbit dataset.

Input : the unzipped COVID-19-Wearables directory (per-user *_hr.csv, *_steps.csv,
        *_sleep.csv; 5-second HR, 1-minute steps, sleep stage segments) plus a
        label table with ParticipantID, Category, Symptom_dates, covid_diagnosis_dates,
        recovery_dates (the paper's Supplementary Table 1 as re-published by
        AmirMage2020/vision data/covid_symptom_dates_raw.csv).
Output: <out>/readings.csv  user,metric,timestamp_ms,value,duration_sec
        <out>/labels.csv    user,category,symptom_onset,diagnosis,recovery  (ISO dates or empty)

Shapes mirror what HealthConnectAdapter writes so the replay exercises the shipped
engine on production-shaped rows:
  resting_heart_rate : one reading per day at 06:00 local = mean of HR samples in
                       00:00–07:00 whose preceding 12 minutes had zero steps
                       (Mishra 2020 / Li 2017 definition of RHR).
  steps              : hourly buckets, timestamp = bucket start, duration_sec 3600.
  sleep_stage        : one reading per segment, value = Bios SleepStage code
                       (AWAKE 0, LIGHT 1, DEEP 2, REM 3), duration_sec = segment length.
No pandas: stdlib only, streams the 3 GB of HR rows.
"""
import csv, os, sys, re, glob
from collections import defaultdict
from datetime import datetime, timezone

SRC, LABELS, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(OUT, exist_ok=True)
UTC = timezone.utc

def ms(s):
    return int(datetime.strptime(s.split(".")[0], "%Y-%m-%d %H:%M:%S").replace(tzinfo=UTC).timestamp() * 1000)

STAGE = {"wake": 0, "awake": 0, "restless": 0, "light": 1, "asleep": 1, "deep": 2, "rem": 3}

def prep_user(uid, w):
    steps_path = f"{SRC}/{uid}_steps.csv"
    hr_path = f"{SRC}/{uid}_hr.csv"
    if not (os.path.exists(steps_path) and os.path.exists(hr_path)):
        return 0
    # minute -> steps
    minute_steps = {}
    hourly = defaultdict(int)
    with open(steps_path) as f:
        for row in csv.DictReader(f):
            t = ms(row["datetime"])
            n = int(float(row["steps"]))
            minute_steps[t // 60000] = n
            hourly[t // 3600000] += n
    for h, n in sorted(hourly.items()):
        w.writerow([uid, "steps", h * 3600000, n, 3600])
    # RHR per day
    day_acc = defaultdict(lambda: [0.0, 0])
    with open(hr_path) as f:
        for row in csv.DictReader(f):
            t = ms(row["datetime"])
            hour = (t // 3600000) % 24
            if hour >= 7:
                continue
            m = t // 60000
            if any(minute_steps.get(m - k, 0) > 0 for k in range(0, 12)):
                continue
            acc = day_acc[t // 86400000]
            acc[0] += float(row["heartrate"]); acc[1] += 1
    days = 0
    for d, (s, n) in sorted(day_acc.items()):
        if n >= 60:  # at least 5 minutes of qualifying samples
            w.writerow([uid, "resting_heart_rate", d * 86400000 + 6 * 3600000, round(s / n, 1), 0])
            days += 1
    sleep_path = f"{SRC}/{uid}_sleep.csv"
    if os.path.exists(sleep_path):
        with open(sleep_path) as f:
            for row in csv.DictReader(f):
                code = STAGE.get(row["stage"])
                if code is None:
                    continue
                w.writerow([uid, "sleep_stage", ms(row["datetime"]), code, int(float(row["stage_duration"]))])
    return days

def first_date(cell):
    m = re.search(r"(\d{4}-\d{2}-\d{2})", cell or "")
    return m.group(1) if m else ""

users = sorted({os.path.basename(p).split("_")[0] for p in glob.glob(f"{SRC}/*_hr.csv")})
labeled = {}
with open(LABELS) as f:
    for row in csv.DictReader(f):
        labeled[row["ParticipantID"]] = row
with open(f"{OUT}/labels.csv", "w", newline="") as lf:
    lw = csv.writer(lf); lw.writerow(["user", "category", "symptom_onset", "diagnosis", "recovery"])
    for u in users:
        r = labeled.get(u)
        if r:
            lw.writerow([u, r["Category"], first_date(r["Symptom_dates"]), first_date(r["covid_diagnosis_dates"]), first_date(r["recovery_dates"])])
        else:
            lw.writerow([u, "Healthy", "", "", ""])
with open(f"{OUT}/readings.csv", "w", newline="") as rf:
    w = csv.writer(rf); w.writerow(["user", "metric", "timestamp_ms", "value", "duration_sec"])
    for i, u in enumerate(users):
        d = prep_user(u, w)
        print(f"{i+1}/{len(users)} {u} rhr_days={d}", flush=True)
print("DONE")
