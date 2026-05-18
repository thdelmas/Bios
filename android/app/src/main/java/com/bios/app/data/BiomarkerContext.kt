package com.bios.app.data

import com.bios.app.model.Specimen

/**
 * Optional provenance attached to a biomarker reading. Holds the context the
 * lab report carried that the scalar value alone discards — which lab drew
 * the sample, whether the owner was fasting, what specimen the assay ran on,
 * a link to the source artifact, and a free-text note for owner recall.
 *
 * Engine-relevant fields (lab, fasting, specimen) ride the existing
 * `event_payloads` sidecar via [BiomarkerPayloadKeys]. The owner-recall note
 * rides the `MetricReading.note` column directly. Reasoning split in
 * docs/SELF_REPORTED_DATA_HOME.md and docs/DATA_MODEL.md.
 *
 * All fields are optional. A bare biomarker entry (value + date) leaves
 * every field null and writes nothing to the sidecar.
 */
data class BiomarkerContext(
    val labName: String? = null,
    val fasting: Boolean? = null,
    val specimen: Specimen? = null,
    val sourceUri: String? = null,
    val note: String? = null
) {
    val isEmpty: Boolean
        get() = labName.isNullOrBlank() &&
            fasting == null &&
            specimen == null &&
            sourceUri.isNullOrBlank() &&
            note.isNullOrBlank()
}

/**
 * Field keys for biomarker provenance in the `event_payloads` sidecar.
 * Stability contract: renames are breaking changes — see DATA_MODEL.md.
 */
object BiomarkerPayloadKeys {
    const val LAB_NAME = "lab_name"
    const val FASTING = "fasting"
    const val SPECIMEN = "specimen"
    const val SOURCE_URI = "source_uri"
}
