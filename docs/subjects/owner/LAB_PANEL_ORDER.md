# Lab Panel Order — Owner (Wave-1 + Wave-2)

**Purpose:** single-sheet clinic handoff. Print and hand to the GP / lab technician, or use as a checklist when ordering through a private lab (Synlab, Echevarne, Quirónsalud, etc.).

**Subject context:** male, age 28, Madrid. Most items below are annual baseline. Items flagged **(deferred)** are once-per-window tests with low pre-test probability at 28 — order opportunistically, not now, unless family history says otherwise.

**Source:** [Blueprint Protocol Audit §3.1 + §3.2](../../audits/BLUEPRINT_PROTOCOL_AUDIT.md). Bios maps all of these to first-class `MetricType` keys; values can be manual-entered from a paper report or FHIR-imported (LOINC table covers ~85 of them).

---

## Single annual phlebotomy panel — ask for all of this in one draw

### Lípidos + riesgo cardiovascular independiente
- Total cholesterol — *colesterol total*
- LDL cholesterol — *LDL*
- HDL cholesterol — *HDL*
- Triglycerides — *triglicéridos*
- **Apolipoprotein B (ApoB)** — *apolipoproteína B*
- **Lipoprotein(a)** — *lipoproteína(a) / Lp(a)* — single lifetime measurement is enough; strongest independent ASCVD marker
- **Homocysteine** — *homocisteína*

### Glucémico
- HbA1c — *hemoglobina glicosilada*
- Fasting glucose — *glucosa en ayunas*
- Fasting insulin — *insulina en ayunas*
- HOMA-IR — usually derived by the lab from fasting glucose + insulin

### Hemograma completo con fórmula (CBC + diferencial)
- Hemoglobin, hematocrit, WBC, RBC, platelets
- **MCV, MCH, MCHC, RDW, MPV** — *índices eritrocitarios*
- **5-cell differential:** neutrophils / lymphocytes / monocytes / eosinophils / basophils (%) — *fórmula leucocitaria*

### Perfil de hierro completo
- Ferritin — *ferritina*
- **Serum iron** — *hierro sérico*
- **Iron saturation %** — *índice de saturación de transferrina*
- **TIBC** — *capacidad total de fijación del hierro*

### Bioquímica básica completa (CMP)
- Sodium, potassium, chloride, CO₂ — *iones / electrolitos*
- Calcium serum — *calcio sérico*
- Phosphate — *fosfato*
- Total protein, albumin — *proteínas totales, albúmina*

### Función renal
- eGFR — *filtrado glomerular estimado*
- Creatinine — *creatinina*
- **BUN / urea** — *urea*
- **Uric acid** — *ácido úrico*

### Función hepática + pancreática
- ALT, AST, GGT
- **Alkaline phosphatase** — *fosfatasa alcalina*
- **Total bilirubin** — *bilirrubina total*
- **Amylase, lipase** — *amilasa, lipasa*

### Inflamación
- **hsCRP** — *PCR ultrasensible* (NOT plain CRP)

### Tiroides + autoinmunidad
- TSH, free T4, free T3
- **Thyroid peroxidase antibodies (TPO Ab)** — *anticuerpos anti-TPO*
- **Thyroglobulin antibodies (Tg Ab)** — *anticuerpos anti-tiroglobulina*

### Vitaminas y minerales
- Vitamin D (25-OH) — *vitamina D 25-OH*
- Vitamin B12 — *vitamina B12*
- Folate — *ácido fólico*
- Magnesium — *magnesio* (serum)
- **Vitamin K2** — *vitamina K2* (often only available at private/specialty labs)
- **Vitamin A (retinol)** — *vitamina A / retinol*
- **Vitamin E (alpha-tocopherol)** — *vitamina E / alfa-tocoferol*

### Endocrino masculino
- **Total testosterone** — *testosterona total*
- **Free testosterone** — *testosterona libre*
- **SHBG** — *globulina transportadora de hormonas sexuales*
- **DHEA-sulfate** — *DHEA-S*
- **FSH, LH** — gonadotropinas
- **Prolactin** — *prolactina*
- Cortisol (morning draw, ideally 8:00) — *cortisol matutino*
- **IGF-1** — *factor de crecimiento insulínico tipo 1*

---

## Once-per-window tests — order opportunistically

- **Coronary calcium score (CAC)** — CT-based, once-per-decade. Order if family history of premature CVD; otherwise defer until ~40.
- **DEXA bone density T-score** — typically 50+, or earlier with risk factors (low BMI, prolonged steroids, eating-disorder history). **Defer.**
- **PSA total + PSA free** — men 50+. **Defer.**
- **pTAU-217** — emerging Alzheimer's plasma biomarker. Research-grade availability in Spain; defer unless family history.
- **Telomere length** — TeloYears / SpectraCell. Optional research marker.
- **Epigenetic age (Dunedin PACE, GrimAge, PhenoAge, Horvath)** — TruDiagnostic mail-in. Optional; expensive.

---

## Pre-visit prep

- **Fasting 10–12h.** Water only. Critical for glucose, insulin, triglycerides, lipids, HOMA-IR.
- **Morning draw** preferred — cortisol, testosterone, and IGF-1 are diurnal.
- **No vigorous exercise the day before** — elevates CK, AST/ALT, and can skew testosterone.
- **No alcohol 48h before** — elevates GGT, MCV.
- **If on biotin supplements (incl. multivitamins with biotin):** stop 72h before. Interferes with TSH and troponin immunoassays.

---

## After the report arrives

Every value above maps to a `MetricType` key in Bios.

1. **PDF/photo → `source_uri` payload field.** Attach to the first `BIOMARKER` reading; serves as provenance for the whole panel.
2. **For each value:** Bios → Biomarkers → manual entry. Fill `lab_name` (e.g., "Synlab Madrid"), `fasting` (1), `specimen` (`SERUM` or `WHOLE_BLOOD` per the report).
3. **If the lab supports FHIR / HL7 export:** import the bundle via Bios's FHIR importer — populates everything in one pass via the LOINC table.

Once the panel is in, every preventive-alert pattern (`alerts/BiomarkerReference.kt`, `CardioOncologyPatterns.kt`, NAFLD/CKD/insulin-resistance screens) has ground truth to anchor against instead of leaning on wearable proxies.

---

## Spain-specific notes

- **Vitamin K2, free testosterone, ApoB, Lp(a), pTAU-217:** often **not** in standard Seguridad Social panels. Ask the GP to add them; if refused, route through a private lab (Synlab, Echevarne, Labco, Quirónsalud — most accept walk-in orders without a referral).
- **Comprehensive private panel cost (Madrid, 2026):** roughly €150–€350 depending on lab and how many of the Wave-2 micronutrients are included.
- **Lp(a) is one-and-done** — genetic, doesn't change with lifestyle. If a previous reading exists from any lifetime visit, skip it.
