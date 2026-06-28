# Lab-Report OCR Ingestion

> The owner has a paper or PDF lab report. Their lab portal does not export
> FHIR (most don't — La Meva Salut, Synlab, Cerba, hospital PDFs). Today the
> only path into Bios is typing ~25 values by hand on a phone keyboard. This
> feature lets the owner photograph or pick the report, have it parsed
> **on-device**, eyeball the pre-filled values, and confirm — turning a
> 10-minute transcription chore into a 30-second review.
>
> It is a thin extraction layer in front of the already-shipped §8.6 lab
> pipeline. It adds no new persistence, no new metric keys, no network, and
> no Google Play Services. The owner confirms every value before it is
> written — OCR proposes, the owner disposes.

Status: **DESIGN** (targets v0.3). Builds on §8.6 (lab inbound surface,
COMPLETE).

---

## 1. Why not FHIR-only

FHIR import (`export/FhirImporter.kt`) is shipped and is the clean path —
*for the rare owner whose portal exports a FHIR R4 Bundle*. In practice the
artifact in the owner's hand is a **PDF or a photo**. So FHIR import serves
the power-user edge; the median owner needs the report read off the page.

This feature does not replace FHIR import or manual entry. It is a third
input that **funnels into the same review-and-confirm UI** and the same
write path:

```
                                 ┌─ FHIR file ──→ FhirImporter ─────┐
  user-initiated import ─────────┼─ photo/PDF ──→ LabScanner (NEW) ─┼─→ Review screen ─→ BiomarkerEntryRepo.add()
                                 └─ manual entry (one row at a time) ┘
```

All three converge on a **`LabScanSummary`-shaped** accepted/skipped result
(§5), a review screen the owner confirms (§6), and the existing
`SELF_REPORTED` write path (§7). The OCR layer is everything left of "Review
screen" on the middle arrow.

---

## 2. On-device constraint (non-negotiable)

Per principles 2 ("on-device processing… never data monetization") and 6
("No Play Services. Every feature works on degoogled devices"), the OCR
stack must be **fully on-device and Play-Services-free**. Two viable engines:

| Engine | Artifact | Play Services? | Quality | Size |
|--------|----------|----------------|---------|------|
| **ML Kit text recognition (bundled)** | `com.google.mlkit:text-recognition` | **No** — model embedded in the AAR, runs offline | High | ~? +4 MB |
| **Tesseract** | `cz.adaptech.tesseract4android:tesseract4android` | No — fully FOSS | Lower on dense tables, needs `.traineddata` per language | +trained data |

`com.google.mlkit:text-recognition` is the **bundled** variant (not the
`com.google.android.gms:play-services-mlkit-*` variant) — it ships the model
inside the app and does **not** call out to Play Services at runtime, so it
runs on a degoogled device. It is still a Google-authored library; the
purist alternative is Tesseract (`tesseract4android`), which is GPL/Apache
and Google-free but weaker on dense numeric tables and needs bundled
`eng`/`spa`/`cat` trained data.

**Recommendation:** ship **ML Kit bundled** behind a `LabOcrEngine`
interface so Tesseract can be swapped in (or offered as a degoogled-build
flavor) without touching the extraction logic. Decide at implementation
time; the interface is the hedge.

**PDF rasterization** uses the framework `android.graphics.pdf.PdfRenderer`
(API 21+, zero dependency, no Google) — render each page to a `Bitmap`, feed
the bitmap to the OCR engine.

**CameraX is already a dependency** (`androidx.camera:*` 1.4.1, used for
fingertip-PPG) — reuse it for the "photograph report" capture path. No new
camera dependency.

New dependency footprint: **one line** (`com.google.mlkit:text-recognition`)
or the Tesseract equivalent. Everything else is already in the build.

---

## 3. Pipeline

```
pick/photo → [PdfRenderer if PDF] → Bitmap(s)
           → LabOcrEngine.recognize() → List<OcrLine> (text + bbox)
           → PanelMetadataExtractor    → specimen, collection date, lab name   (§4.1)
           → LineBiomarkerExtractor     → List<ExtractedReading | SkippedLine>  (§4.2)
           → LabScanSummary             → review screen                          (§5,§6)
           → owner confirms             → BiomarkerEntryRepo.add() per row       (§7)
```

Runs in a coroutine off the main thread (WorkManager not required — it is
user-initiated and foreground, like the FHIR import). No reading is
persisted until the owner taps confirm.

---

## 4. Extraction

### 4.1 Panel-level metadata (extract once, apply to all rows)

A lab report has one specimen date, often one specimen type and one lab
name, printed in a header — not repeated per analyte. Extract them once and
pre-fill the shared `BiomarkerContext`:

- **Lab name** → `lab_name`. Match the report's "Centre / Centro" /
  facility line, or the most prominent header text. Editable in review.
- **Collection date** → `timestamp`. Anchor on labelled date fields
  ("Data i hora presa de la mostra", "Fecha de toma", "Collected",
  "Specimen date"). Parse `dd/mm/yy(yy)` and ISO. **Never** silently
  default to "today" — if no date is found, the review screen forces the
  owner to pick one before confirm is enabled.
- **Specimen** → `Specimen`. Map free text: `Sèrum`/`Suero`/`Serum` →
  `SERUM`; `Sang EDTA`/`Sangre`/`Whole blood`/`EDTA` → `WHOLE_BLOOD`;
  `Plasma` → `PLASMA`; else `OTHER`.
- **Source provenance** → `source_uri`. The original image/PDF is copied
  into app-private storage and its `content://` URI is attached as
  `source_uri` to **every** row from the scan (one shared provenance, same
  as the FHIR import attaches the source file). The owner can always open
  the original to audit a value.

### 4.2 Per-line biomarker extraction

Lab lines have a stable shape across labs and languages:

```
<analyte name> … <value> <unit> <ref-low> – <ref-high>
```

e.g. (representative Catalan/Spanish lines; values are fabricated examples):

```
Tirotropina            2.10  mU/L    0.550 - 4.780
Colesterol d'HDL          55  mg/dL
Monòcits %               6.5  %       2.0 - 11.0
Monòcits                 0.5  10E9/L  0.1 - 1.0
```

For each OCR line:

1. **Tokenize** into `(leadingText, numbers[], unitToken?, range?)`. Parse
   decimals with a strict numeric regex that respects locale decimal marks
   (`.` and `,`) and does **not** strip non-digit separators blindly —
   gluing `6.5` + `%` + `2.0` into one figure is the classic
   strip-`\D`-concatenation bug. Each printed number is parsed as its own
   token.
2. **Resolve the analyte** by normalizing `leadingText` (lowercase, strip
   accents/diacritics, collapse whitespace, drop trailing `%`/units) and
   looking it up in the **alias table** (§4.3). `Tirotropina` → `TSH`,
   `Monòcits %` → `MONOCYTES_PCT`, `Monòcits` (abs) → `ABSOLUTE_MONOCYTE…`.
3. **Pick the value.** When a line carries multiple numbers (value + ref
   range), the value is the token *before* the unit / before the range
   separator. The range (`low – high`) is captured separately and used only
   as a **confirmation signal** (§4.4) — never written.
4. **Unit guard (the disambiguation gate).** The extracted unit must be
   convertible to `metricType.unit` via the existing
   `FhirImporter.normaliseUnit` / `conversionFactor` machinery. If the name
   matches but the unit is incompatible (e.g. an OCR misread, or two
   analytes share a name across panels), **skip-and-report** — do not guess.
   This reuse means OCR and FHIR share one unit-conversion source of truth.
5. **Emit** an `ExtractedReading` (with a confidence tier) or a
   `SkippedLine` (with a reason). No line is dropped silently.

### 4.3 Alias table — derived from `MetricType`, not parallel to it

`MetricType` today carries no synonyms, and a biomarker's English `key`
(`tsh`, `total_cholesterol`) will not match a Catalan/Spanish report
(`Tirotropina`, `Colesterol total`). So aliases are the **one genuinely new
data artifact** this feature needs.

**Critical design rule:** the alias resolver is built by **scanning
`MetricType.entries`**, exactly as `FhirImporter.loincToMetricType` is built
by scanning entries through `loincCode()`. A hand-maintained
`Map<String, MetricType>` *parallel* to the enum silently drifts the day a
new biomarker key lands without an alias — the same dual-source drift the
LOINC map was deliberately designed to avoid. So:

```kotlin
// One source: aliases keyed BY MetricType. Resolver derived FROM it.
fun biomarkerAliases(metric: MetricType): List<String>? = when (metric) {
    MetricType.TSH               -> listOf("tsh", "tirotropina", "tirotropina (tsh)", "thyrotropin")
    MetricType.TOTAL_CHOLESTEROL -> listOf("colesterol total", "colesterol", "cholesterol total", "colesterol (total)")
    MetricType.MONOCYTES_PCT     -> listOf("monocits %", "monocitos %", "monocytes %", "monocits", "monocitos")
    // … one entry per BIOMARKER-domain MetricType
    else -> null
}

val aliasToMetricType: Map<String, MetricType> by lazy {
    buildMap {
        for (type in MetricType.entries) {
            for (alias in biomarkerAliases(type).orEmpty()) put(normalize(alias), type)
        }
    }
}
```

A `MetricTypeTest`-style unit test asserts **every `MetricDomain.BIOMARKER`
entry with `allowsManualEntry` has at least one alias**, so adding a
biomarker key without an alias fails CI — the drift guard. Ship aliases for
ES / CA / EN (the owner's locales) at minimum; the `RegionConfigProvider`
localization layer is the eventual home for broader language coverage.

### 4.4 Confidence tiers (drive the review UX, not silent acceptance)

| Tier | Condition | Review UI |
|------|-----------|-----------|
| **HIGH** | exact alias hit **and** unit compatible **and** value within the printed reference range's order of magnitude (or within physiological bounds) | row pre-checked ✓ |
| **LOW** | fuzzy/partial alias, or no unit on the line, or value implausible vs range | row shown, **unchecked**, flagged for the owner to verify |
| **SKIPPED** | no alias match, or unit incompatible with the resolved metric, or no numeric value | listed in a "couldn't read" section with the raw line + reason |

The printed reference range is a free plausibility check the lab gives us:
a value three orders of magnitude outside the printed range is an OCR
misread (decimal point lost — the magnitude trap), so it drops to LOW rather
than being written confidently. The range is **never persisted**; it only
gates confidence.

---

## 5. Result type — mirror `FhirImportSummary`

The FHIR importer already returns an accepted/skipped/fileError shape and
the entry screen already renders a summary dialog over it. Mirror it so the
review screen and the post-write summary reuse the same UI:

```kotlin
data class ExtractedReading(
    val metricType: MetricType,
    val value: Double,            // already unit-normalized to metricType.unit
    val timestamp: Long,          // panel collection date
    val context: BiomarkerContext,// labName / specimen / sourceUri shared from §4.1
    val confidence: Confidence,   // HIGH | LOW
    val rawLine: String,          // the OCR line, shown for verification
)

data class SkippedLine(
    val rawLine: String,
    val reason: String,           // "no analyte match", "unit mg/dL not compatible with %", …
)

data class LabScanSummary(
    val readings: List<ExtractedReading>,
    val skipped: List<SkippedLine>,
    val panel: PanelMetadata,     // date / specimen / labName, all editable
    val fileError: String?,       // unreadable file / no pages / OCR failure
)
```

`ExtractedReading` carries `rawLine` (FHIR's `AcceptedReading` doesn't need
it — OCR does, because the owner is verifying a machine read of fuzzy
pixels).

---

## 6. Review screen — owner confirms before anything is written

A new Compose screen (route `"lab_scan_review"`), reached from a new
**"Scan lab report"** card on `BiomarkerEntryScreen` alongside the existing
"Import from FHIR" card. It is a **pre-filled, editable batch** of the same
fields the single-entry `BiomarkerEntryScreen` already renders:

- **Panel header** (editable): collection date, specimen, lab name —
  applied to all rows. Confirm is disabled until a date is set.
- **One row per `ExtractedReading`:** metric name, parsed value (editable
  number field with the metric's unit suffix), a checkbox, the `rawLine` in
  small text beneath, and confidence styling (HIGH pre-checked; LOW
  unchecked + amber flag). Tapping a row opens the full per-metric editor
  (the existing entry-field widgets) to fix the metric or value.
- **"Couldn't read" section:** the `SkippedLine`s with their reasons and a
  "+ add manually" shortcut into the existing single-entry flow — **no
  silent truncation**; the owner sees exactly what was dropped and why.
- **Confirm** writes only the checked rows.

Reusing `BiomarkerEntryScreen`'s field composables keeps one editing UX and
avoids a second numeric-validation path.

---

## 7. Write path — unchanged

Each confirmed row calls the **existing** §8.6 write path, once per reading:

```kotlin
viewModel.addManualBiomarker(reading.metricType, reading.value, reading.timestamp, reading.context)
// → BiomarkerEntryRepo.add(...) → SELF_REPORTED DataSource, ConfidenceTier.HIGH
```

No new repository, table, metric key, or `DataSource`. Rows land as
`SELF_REPORTED` and so are excluded from sensor baselines per decision 3 in
`SELF_REPORTED_DATA_HOME.md`, exactly like manually-entered and
FHIR-imported labs.

**Confidence note:** `BiomarkerEntryRepo.add` hardcodes
`ConfidenceTier.HIGH`. An owner-confirmed OCR row *is* a human-verified
value, so HIGH is correct — the OCR engine's own uncertainty is resolved at
the review gate, not carried into storage. (If a future change wants to
record "this came from OCR" provenance, thread a `sourceType`/note rather
than lowering the tier — the owner verified it.)

---

## 8. Privacy & boundary

- **Fully on-device.** OCR model bundled, PDF rendered by the framework, no
  byte leaves the device. Consistent with the FHIR import (also local).
- **Source file** is copied into app-private (SQLCipher-adjacent) storage
  and referenced by `source_uri`; it is destroyed by the existing
  `DataDestroyer` wipe path along with the readings.
- **No new companion, no metric-bus change.** Lab data is canonical
  multi-system body signal owned by Bios (per §8.6 and
  `ECOSYSTEM_BOUNDARIES.md`); this is an input method, not a new data class.
- **Manifesto:** the feature reads numbers, it does not evaluate them. Any
  judgment ("Borderline") is the *separate*, already-shipped clinical-bands
  surface; OCR only gets the value onto the time-series.

---

## 9. Acceptance

- Owner can pick a PDF **or** photograph a report; both produce a
  `LabScanSummary`.
- A representative ES/CA/EN panel (lipids, CBC + differential, glucose,
  creatinine, ALT/GGT, TSH) extracts its in-schema analytes at HIGH
  confidence; unknown/unsupported lines surface in "couldn't read" with a
  reason — none dropped silently.
- Multilingual analyte names resolve via the alias table (`Tirotropina` →
  `TSH`, `Monòcits %` → `MONOCYTES_PCT`).
- Unit incompatibility skips-and-reports; it never writes a guessed value.
- Every `BIOMARKER` + `allowsManualEntry` `MetricType` has ≥1 alias
  (CI-enforced) — no dual-source drift.
- Nothing is persisted until the owner confirms; only checked rows write.
- No new dependency beyond the one OCR engine line; no Play Services; runs
  on a degoogled build.

---

## 10. Phasing

- **v0.3 (MVP):** photo + PDF input, ML Kit bundled engine behind
  `LabOcrEngine`, ES/CA/EN aliases for the common ~30 panel analytes,
  review screen, panel-metadata extraction. Single-page and simple
  multi-page reports.
- **v0.4:** broader alias/language coverage hoisted into
  `RegionConfigProvider`; better multi-page / multi-column table handling;
  optional Tesseract degoogled-purist engine flavor; auto lab-name
  detection from a known-labs gazetteer.
