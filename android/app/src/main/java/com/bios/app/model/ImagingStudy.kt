package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Owner-recorded imaging study (#356). Audit gap surfaced by the Sagrat
 * Cor discharge — a CT head report had no place to live. EcgStrip is the
 * design precedent (storage-only, no on-device classification); this is
 * the textual-report analogue.
 *
 * **Scope of this entity:**
 *  - Stores the radiology report **text**: technique, findings,
 *    conclusion, plus modality and body region for indexing.
 *  - Does **not** store DICOM blobs. Pixel data lives elsewhere — either
 *    on the imaging centre's PACS or as a separate binary store the
 *    owner explicitly opts into in a future PR.
 *  - Does **not** interpret. The clinician's words are stored verbatim;
 *    Bios never extracts findings or scores severity. Matches the
 *    manifesto: instrument, not coach.
 *
 * **Erasure by design.** Hard delete is allowed; there is no soft-delete
 * history. Incidental findings on imaging are a real source of anxiety,
 * and the owner decides when to revisit.
 */
@Entity(
    tableName = "imaging_studies",
    indices = [Index("studyDate"), Index("modality"), Index("bodyRegion")],
)
data class ImagingStudy(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    val modality: ImagingModality,
    val bodyRegion: ImagingBodyRegion,
    /** Date the imaging was performed (epoch millis). */
    val studyDate: Long,
    /** Owner / clinician chief-complaint or routine-screening reason. */
    val indication: String? = null,
    /** Facility that performed the study, free-text. */
    val facility: String? = null,
    /** Verbatim radiology-report sections. All optional — some imports
     *  ship only a conclusion line, others ship the full report. */
    val reportTechnique: String? = null,
    val reportFindings: String? = null,
    val reportConclusion: String? = null,
    /** ISO 639-1 language tag for the report text ("es", "en", "ca"). */
    val reportLanguage: String? = null,
    /** Free-text owner note. */
    val ownerNote: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Imaging modalities Bios accepts. Mirrors the FHIR ImagingStudy
 * `modality` value-set (DICOM modality codes) at the granularity owners
 * actually distinguish. Vendor-specific subtypes (CT vs. CTA, MRI vs.
 * fMRI) collapse into the parent — refine later if a clinical pattern
 * needs the distinction.
 */
enum class ImagingModality { CT, MRI, XRAY, ULTRASOUND, PET, DEXA, MAMMOGRAM, ENDOSCOPY, OTHER }

/**
 * Anatomical region. Pragmatic taxonomy chosen for the way owners
 * describe what was scanned, not the radiologic gold-standard
 * BodySite. The free-text `indication` and `reportFindings` carry the
 * precise anatomy where it matters.
 */
enum class ImagingBodyRegion {
    HEAD, NECK, CHEST, ABDOMEN, PELVIS, SPINE, EXTREMITY, WHOLE_BODY, OTHER
}
