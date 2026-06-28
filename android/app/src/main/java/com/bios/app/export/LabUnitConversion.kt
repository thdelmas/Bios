package com.bios.app.export

import com.bios.contracts.MetricType

/**
 * One unit-reconciliation source of truth shared by FHIR import
 * ([FhirImporter.normaliseUnit]) and lab-report OCR ingestion
 * (`LabLineExtractor`). Both surfaces read a value plus a free-text unit
 * token off an external artifact and must reconcile it against the
 * canonical Bios unit for a [MetricType] before anything is stored — and
 * they must agree, or the same lab value imports differently depending on
 * which door it came through.
 *
 * The conversion table is intentionally tiny: every entry is a unit pair
 * Bios has actually seen on a real report. Bios is a health guardian, not a
 * general unit-conversion service — an unknown pair fails closed (skip and
 * report) rather than guessing and storing a wrong-by-10× value.
 */

/** Outcome of reconciling an OCR/FHIR unit token against a metric's canonical unit. */
sealed class CanonicalUnit {
    /** Value is in (or was converted to) the canonical unit; safe to store. */
    data class Ok(val value: Double) : CanonicalUnit()

    /** No known conversion from [incoming] to [target]; caller must skip-and-report. */
    data class Incompatible(val incoming: String, val target: String) : CanonicalUnit()
}

/**
 * Reconcile [rawUnit] (as printed on the report / FHIR `valueQuantity`)
 * against the canonical unit of [metric] and return [value] in that
 * canonical unit.
 *
 *  - A blank/absent unit returns [CanonicalUnit.Ok] unchanged: the analyte
 *    was already resolved by name/LOINC, so trust the mapping. (The OCR
 *    caller separately drops a unit-less line to LOW confidence.)
 *  - A unit equal to the canonical UCUM code or the human symbol passes through.
 *  - A registered conversion factor is applied.
 *  - Anything else is [CanonicalUnit.Incompatible] — fail closed.
 */
fun normalizeToCanonicalUnit(metric: MetricType, rawUnit: String?, value: Double): CanonicalUnit {
    val incoming = rawUnit?.trim().orEmpty()
    if (incoming.isEmpty()) return CanonicalUnit.Ok(value)

    val target = ucumCode(metric)
    val canon = canonicaliseUnitToken(incoming)
    if (canon == target || incoming == metric.unit.symbol || canon == metric.unit.symbol) {
        return CanonicalUnit.Ok(value)
    }
    val factor = conversionFactor(canon, target)
        ?: return CanonicalUnit.Incompatible(incoming, target)
    return CanonicalUnit.Ok(value * factor)
}

/**
 * Returns the multiplicative factor to convert [from] to [to], or null when
 * no conversion is registered. Both arguments are expected to already be
 * canonical UCUM tokens (run [canonicaliseUnitToken] first for free text).
 *
 * Issue #354 — hospital labs report standard CRP in mg/dL while Bios stores
 * mg/L; a naive import underestimates by 10×. Kept deliberately minimal.
 */
internal fun conversionFactor(from: String, to: String): Double? {
    if (from == to) return 1.0
    // mg/dL ↔ mg/L. mg/dL × 10 = mg/L.
    if (from == "mg/dL" && to == "mg/L") return 10.0
    if (from == "mg/L" && to == "mg/dL") return 0.1
    return null
}

/**
 * Folds the many ways a lab or OCR engine spells a unit into the canonical
 * UCUM token [ucumCode] emits, so the comparison in [normalizeToCanonicalUnit]
 * is apples-to-apples. Case- and accent-insensitive on the lookup key; the
 * returned value is the exact UCUM string Bios uses internally.
 *
 * Unknown tokens fall through unchanged — they simply won't match a target
 * and the caller fails closed. This is a gazetteer of real report spellings,
 * not an attempt at general UCUM parsing.
 */
internal fun canonicaliseUnitToken(raw: String): String =
    UNIT_CANON[unitLookupKey(raw)] ?: raw.trim()

/**
 * True if [raw] reads as a unit of measure (a known spelling, or carries a
 * `/` or `%`), as opposed to an abnormal-result flag ("H", "L", "*", "alto")
 * that can sit in the same column position on a report. The line extractor
 * uses this so a flag never gets treated as an incompatible unit and wrongly
 * skips an otherwise-good reading.
 */
internal fun isKnownUnitToken(raw: String): Boolean {
    if (raw.contains('/') || raw.contains('%')) return true
    return UNIT_CANON.containsKey(unitLookupKey(raw))
}

private fun unitLookupKey(raw: String): String = raw.lowercase()
    .replace("µ", "u")
    .replace("μ", "u")
    .replace("⁹", "9")
    .replace("¹²", "12")
    .replace("^", "")
    .replace("·", ".")
    .replace(" ", "")

/** Lowercased real-report unit spelling → canonical UCUM token [ucumCode] emits. */
private val UNIT_CANON: Map<String, String> = mapOf(
    "mg/dl" to "mg/dL",
    "mg/l" to "mg/L",
    "g/dl" to "g/dL",
    "g/l" to "g/L",
    "%" to "%",
    "pct" to "%",
    "u/l" to "U/L",
    "ui/l" to "U/L",
    "iu/l" to "U/L",
    "miu/l" to "m[IU]/L",
    "mui/l" to "m[IU]/L",
    "mu/l" to "m[IU]/L",
    "uiu/ml" to "u[IU]/mL",
    "uui/ml" to "u[IU]/mL",
    "miu/ml" to "m[IU]/mL",
    "mui/ml" to "m[IU]/mL",
    "iu/ml" to "[IU]/mL",
    "ui/ml" to "[IU]/mL",
    "ng/ml" to "ng/mL",
    "ng/dl" to "ng/dL",
    "ng/l" to "ng/L",
    "pg/ml" to "pg/mL",
    "pg" to "pg",
    "ug/dl" to "ug/dL",
    "ug/l" to "ug/L",
    "nmol/l" to "nmol/L",
    "umol/l" to "umol/L",
    "mmol/l" to "meq/L",
    "meq/l" to "meq/L",
    "fl" to "fL",
    "10e9/l" to "10*9/L",
    "109/l" to "10*9/L",
    "10*9/l" to "10*9/L",
    "x109/l" to "10*9/L",
    "10e3/ul" to "10*9/L",
    "10e12/l" to "10*12/L",
    "1012/l" to "10*12/L",
    "10*12/l" to "10*12/L",
    "x1012/l" to "10*12/L",
    "10e6/ul" to "10*12/L",
    "ml/min/1.73m2" to "mL/min/{1.73_m2}",
    "ml/min/1,73m2" to "mL/min/{1.73_m2}",
    "ml/min" to "mL/min/{1.73_m2}",
    "/ul" to "/uL",
    "ul" to "/uL",
    "s" to "s",
    "sec" to "s",
    "seg" to "s",
)
