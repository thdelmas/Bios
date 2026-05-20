package com.bios.app.data

/**
 * Provenance attached to a manually entered reading produced through the
 * general manual-reading surface. Shape mirrors a clinical-visit triage
 * row: one shared timestamp + clinic name + visit note + optional source
 * artifact (chart PDF/photo) span N readings captured together (BP, SpO2,
 * pulse, temp, RR, pain, GCS, O2 flow as one event).
 *
 * Single-reading entries (owner reading a cuff at home) use the same shape
 * with [clinicName] / [visitNote] null and no bundle id.
 *
 * Some metrics carry a per-row provenance dimension that the bundle-level
 * fields can't express. [CONSCIOUSNESS_LEVEL][com.bios.contracts.MetricType.CONSCIOUSNESS_LEVEL]
 * is canonical GCS but accepts AVPU as a lossless input shortcut — when the
 * owner enters "V" we store 13 as the value and ride the original scale +
 * value alongside in [originalScale] / [originalValue]. Owner-recall text
 * rides [note] (per-reading) and lives in `metric_readings.note`. Engines
 * never read either.
 *
 * All fields are optional. A bare reading (metric + value + timestamp)
 * leaves every field null and writes nothing to the sidecar.
 */
data class ManualReadingContext(
    val clinicName: String? = null,
    val visitNote: String? = null,
    val sourceUri: String? = null,
    val bundleId: String? = null,
    val originalScale: String? = null,
    val originalValue: String? = null,
    val note: String? = null,
) {
    val isEmpty: Boolean
        get() = clinicName.isNullOrBlank() &&
            visitNote.isNullOrBlank() &&
            sourceUri.isNullOrBlank() &&
            bundleId.isNullOrBlank() &&
            originalScale.isNullOrBlank() &&
            originalValue.isNullOrBlank() &&
            note.isNullOrBlank()
}

/**
 * Field keys for manual-reading provenance in the `event_payloads` sidecar.
 * Stability contract: renames are breaking changes — see DATA_MODEL.md.
 *
 * Distinct namespace from [BiomarkerPayloadKeys]: a biomarker entry stores
 * lab/fasting/specimen; a clinical-visit entry stores clinic/visit/bundle.
 * The two surfaces share the SELF_REPORTED source but not the sidecar
 * vocabulary, so consumers reading payloads can tell which surface a
 * reading came from by which keys are present.
 */
object ManualReadingPayloadKeys {
    const val CLINIC_NAME = "clinic_name"
    const val VISIT_NOTE = "visit_note"
    const val SOURCE_URI = "source_uri"
    const val BUNDLE_ID = "bundle_id"
    const val ORIGINAL_SCALE = "original_scale"
    const val ORIGINAL_VALUE = "original_value"
}
