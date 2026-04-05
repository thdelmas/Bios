# Wearables & Early Detection Matrix

## 1. Current Wearable Devices & Their Sensors

### Smartwatches

| Device | HR | HRV | ECG | SpO2 | Temp | Blood Pressure | Sleep Stages | Accelerometer | GPS | Other |
|---|---|---|---|---|---|---|---|---|---|---|
| **Apple Watch Series 11** | Yes | Yes | Yes (FDA-cleared) | Yes | Yes (wrist) | No | Yes | Yes | Yes | Irregular rhythm notification (FDA), crash detection |
| **Samsung Galaxy Watch 7 / Ultra** | Yes | Yes | Yes | Yes | Yes (skin) | Yes (calibration required) | Yes | Yes | Yes | Body composition (BIA), AI hypertension detection, sleep apnea detection (FDA) |
| **Google Pixel Watch 3** | Yes | Yes | Yes | Yes | Yes | No | Yes | Yes | Yes | Fitbit-powered health algorithms, stress management |
| **Garmin Venu 3 / Fenix 8** | Yes | Yes | No | Yes | No | No | Yes | Yes | Yes (multi-band) | Body Battery, training readiness, respiration rate |

### Smart Rings

| Device | HR | HRV | ECG | SpO2 | Temp | Sleep Stages | Other |
|---|---|---|---|---|---|---|---|
| **Oura Ring Gen 4** | Yes | Yes | No | Yes | Yes (7 sensors) | Yes | Readiness score, cycle tracking, resilience metric |
| **Samsung Galaxy Ring** | Yes | Yes | No | Yes | Yes | Yes | Body composition, energy score |
| **Ultrahuman Ring Air** | Yes | Yes | No | Yes | Yes | Yes | Movement index, metabolism tracking |

### Fitness Bands

| Device | HR | HRV | ECG | SpO2 | Temp | Sleep Stages | Other |
|---|---|---|---|---|---|---|---|
| **WHOOP 5.0 / MG** | Yes | Yes | Yes (MG) | Yes | Yes | Yes | Strain score, recovery score, blood pressure (MG), respiratory rate |
| **Fitbit Charge 6** | Yes | Yes | Yes | Yes | No | Yes | EDA (stress), Daily Readiness Score |
| **Xiaomi Smart Band 9 Pro** | Yes | Yes | No | Yes | No | Yes | Stress monitoring, vitality score |
| **Amazfit Band 7** | Yes | No | No | Yes | No | Yes | PAI (Personal Activity Intelligence) |

### Continuous Glucose Monitors (CGMs)

| Device | Type | Wear Time | Connectivity | Target |
|---|---|---|---|---|
| **Dexcom G7** | Prescription CGM | 15.5 days | Bluetooth, phone app | Diabetes (Type 1 & 2) |
| **Dexcom Stelo** | OTC biosensor | 15 days | Bluetooth, phone app | General wellness, non-insulin users |
| **Abbott FreeStyle Libre 3 Plus** | Prescription CGM | 15 days | Bluetooth (33ft range) | Diabetes (Type 1 & 2) |
| **Abbott Lingo** | OTC biosensor | 14 days | Bluetooth, phone app | Metabolic health / wellness |
| **Abbott Libre Rio** | OTC CGM | 15 days | Bluetooth, phone app | Type 2, non-insulin |

### Specialized Medical Wearables

| Device | Purpose | Sensors |
|---|---|---|
| **Withings ScanWatch 2** | Medical-grade watch | ECG, SpO2, temperature, respiratory rate |
| **BioButton** | Continuous vitals patch | HR, HRV, respiratory rate, temperature, SpO2, activity |
| **Wesper** | Sleep apnea home test | Chest patch with respiration, SpO2, body position |
| **Polar H10 / Verity Sense** | Clinical-grade HR | Chest-strap ECG-grade HR, HRV |

### Phone Built-in Sensors (No Wearable Required)

| Sensor | What It Captures |
|---|---|
| **Accelerometer / Gyroscope** | Gait analysis, tremor detection, fall detection, activity levels |
| **Microphone** | Cough frequency, respiratory sounds, voice biomarkers (stress, depression) |
| **Camera (photoplethysmography)** | Heart rate via fingertip, respiratory rate via chest movement |
| **Ambient light sensor** | Light exposure patterns (circadian rhythm) |
| **GPS / Location** | Activity patterns, mobility changes |

---

## 2. Sensor-to-Condition Detection Map

This maps which sensors enable early detection of which conditions, and what kind of signal to look for.

### Cardiovascular

| Condition | Key Sensors | Early Signal | Detection Confidence |
|---|---|---|---|
| **Atrial fibrillation** | ECG, optical HR | Irregular R-R intervals, erratic HR patterns | High (FDA-cleared on Apple Watch, Samsung) |
| **Hypertension** | Blood pressure, HRV, pulse wave | Elevated BP trends, reduced HRV over time | Medium-High (Samsung AI detection) |
| **Bradycardia / Tachycardia** | Optical HR, ECG | Resting HR consistently outside 50-100 bpm | High |
| **Heart failure (early)** | HRV, SpO2, activity, respiratory rate | Declining HRV + reduced activity tolerance + nocturnal SpO2 drops | Medium (pattern-based) |
| **Coronary artery disease** | HRV, ECG, activity tolerance | Reduced HRV, abnormal HR recovery post-exercise | Medium (AI algorithms report ~95% accuracy in studies) |

### Respiratory

| Condition | Key Sensors | Early Signal | Detection Confidence |
|---|---|---|---|
| **Sleep apnea** | SpO2, accelerometer, respiratory rate | Repeated SpO2 desaturation during sleep, breathing interruptions | Medium-High (FDA-cleared on Apple Watch, Samsung) |
| **Asthma / COPD exacerbation** | SpO2, respiratory rate, activity | Declining SpO2 trend, increased respiratory rate, reduced activity | Medium |
| **Respiratory infections** | Temperature, respiratory rate, SpO2, cough (mic) | Temperature spike + elevated respiratory rate + SpO2 dip | Medium |

### Metabolic

| Condition | Key Sensors | Early Signal | Detection Confidence |
|---|---|---|---|
| **Type 2 diabetes (pre-diabetes)** | CGM, activity, HRV | Frequent glucose spikes, impaired glucose tolerance patterns | High (with CGM) |
| **Metabolic syndrome** | CGM, body composition (BIA), activity, HR | Glucose dysregulation + body fat % + low activity + elevated resting HR | Medium-High |
| **Thyroid disorders** | Temperature, HR, HRV, sleep | Persistent low body temp + bradycardia + sleep changes (hypo) or elevated HR + temp (hyper) | Low-Medium (suggestive, not diagnostic) |

### Sleep & Neurological

| Condition | Key Sensors | Early Signal | Detection Confidence |
|---|---|---|---|
| **Insomnia / sleep disorders** | Accelerometer, HR, HRV, sleep stages | Reduced deep/REM sleep, high sleep latency, frequent awakenings | High |
| **Circadian rhythm disorders** | Light exposure, activity, sleep timing | Irregular sleep-wake patterns, misaligned light exposure | Medium |
| **Early Parkinson's indicators** | Accelerometer, gyroscope | Micro-tremors, gait asymmetry, reduced arm swing | Low-Medium (research stage) |
| **Seizure detection** | Accelerometer, HR, EDA | Sudden rhythmic movements + HR spike + EDA surge | Medium (Fitbit/Embrace devices) |

### Mental Health & Stress

| Condition | Key Sensors | Early Signal | Detection Confidence |
|---|---|---|---|
| **Chronic stress** | HRV, EDA, sleep, activity | Persistently low HRV, elevated EDA, disrupted sleep, reduced activity | Medium |
| **Depression indicators** | Activity, sleep, HR, voice (mic) | Reduced mobility, hypersomnia or insomnia, flattened HR variability, voice changes | Low-Medium (behavioral patterns) |
| **Anxiety** | HRV, EDA, respiratory rate, HR | Elevated resting HR, low HRV, high EDA events, rapid breathing | Medium |
| **Burnout** | HRV, sleep quality, recovery scores | Declining recovery trend, worsening sleep, persistent low HRV | Medium |

### Infectious & Inflammatory

| Condition | Key Sensors | Early Signal | Detection Confidence |
|---|---|---|---|
| **Fever / infection onset** | Temperature, resting HR, HRV, respiratory rate | Temperature rise + elevated resting HR + HRV drop (often detectable 1-2 days before symptoms) | Medium-High |
| **COVID-19 / flu early detection** | Temperature, SpO2, HRV, respiratory rate | Combined deviation in multiple vitals from personal baseline | Medium (demonstrated in Stanford/Scripps studies) |
| **Inflammatory flares (autoimmune)** | Temperature, HRV, sleep, activity | Low-grade temp elevation + HRV drop + increased fatigue pattern | Low-Medium |

### Women's Health

| Condition | Key Sensors | Early Signal | Detection Confidence |
|---|---|---|---|
| **Menstrual cycle irregularities** | Temperature, HRV, sleep | Absent or irregular temperature shifts, cycle length variation | Medium-High |
| **Perimenopause** | Temperature, HR, sleep, HRV | Increased temperature variability, sleep disruption patterns, HR changes | Medium |
| **Pregnancy (early indicators)** | Temperature, resting HR, HRV | Sustained elevated basal temperature, rising resting HR | Medium (suggestive) |

---

## 3. MediWatch Integration Priority

Based on user adoption rates and detection value, the recommended integration priority:

### Phase 1 — Highest Impact, Widest Adoption
1. **Apple Watch** (HealthKit) — largest health wearable ecosystem
2. **Samsung Galaxy Watch** (Samsung Health / Health Connect) — largest Android wearable ecosystem
3. **Phone sensors** (no wearable needed) — zero barrier to entry
4. **Fitbit / Google** (Health Connect / Fitbit Web API)

### Phase 2 — High Value, Enthusiast Users
5. **Oura Ring** (Oura API) — best-in-class sleep and recovery data
6. **WHOOP** (WHOOP API) — deepest strain/recovery metrics
7. **Garmin** (Garmin Connect API) — massive sports/fitness user base
8. **Withings** (Withings API) — medical-grade measurements

### Phase 3 — Specialized / Emerging
9. **Dexcom / Abbott CGMs** (Dexcom API / LibreView) — metabolic health layer
10. **Polar** (Polar AccessLink API) — clinical-grade HRV
11. **Xiaomi / Amazfit** (Zepp Health API) — budget-friendly global reach
12. **Medical patches** (BioButton, etc.) — continuous clinical monitoring

---

## 4. Key Takeaway

With just the sensors available in today's consumer wearables and smartphones, MediWatch can realistically monitor for early signs of **20+ conditions** across cardiovascular, respiratory, metabolic, neurological, mental health, infectious, and women's health domains. The most powerful detections come not from any single reading, but from **cross-correlating multiple sensor streams over time** against a user's personal baseline — which is exactly what MediWatch is built to do.

---

## Sources
- [Wearable health devices: Examples & 2026 technology trends](https://www.sermo.com/resources/wearable-devices-for-healthcare/)
- [Best Wearable Devices for Health Tracking 2026](https://doccure.io/best-wearable-devices-for-health-tracking-2026-ai-integration-and-reviews/)
- [Wearable Devices in Healthcare: Complete 2026 Guide](https://pi.tech/blog/wearable-technology-in-healthcare)
- [Wearable Health Tech 2026: AI Sensors, Disease Detection & ROI](https://vertu.com/lifestyle/the-revolution-of-wearable-technology-in-preventative-healthcare/)
- [The Role of Wearable Devices in Chronic Disease Monitoring - PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC11461032/)
- [Wearable Sensors and AI for Sleep Apnea Detection - PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC12089203/)
- [Samsung Galaxy Watch AI Blood Pressure Detection](https://www.archyde.com/samsungs-galaxy-watch-now-features-ai-enabled-high-blood-pressure-detection-for-enhanced-health-monitoring/)
- [Apple vs Samsung Galaxy Watch Health Sensors Comparison](https://technologymoment.com/apple-vs-samsung-galaxy-watch-health-sensors/)
- [Best Sleep Tracker 2026: Oura vs WHOOP vs Fitbit](https://healthyhomeupgrade.com/best-sleep-tracker/)
- [Garmin vs Oura vs WHOOP HRV Accuracy](https://the5krunner.com/2025/10/06/garmin-beaten-by-oura-whoop-in-hrv-accuracy-showdown/)
- [Dexcom G7 & Libre 3 Comparison](https://www.adces.org/education/danatech/glucose-monitoring/continuous-glucose-monitors-(cgm)/cgm-selection-training/dexcom-g7-libre-3-comparison)
- [6 Best CGMs for 2026](https://www.type1strong.org/blog-post/5-best-continuous-glucose-monitors-for-2025)
- [Stelo OTC Glucose Biosensor](https://www.stelo.com/en-us)
- [Samsung Sleep Apnea & ECG Detection](https://www.inventorkr.com/2026/02/galaxy-watch-health-your-wrist-worn.html)
