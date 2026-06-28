package com.bios.app.labocr

import com.bios.app.data.BiomarkerContext
import com.bios.app.model.Specimen
import com.bios.contracts.MetricType

/**
 * Result types for lab-report OCR ingestion (Phase 10). Deliberately shaped
 * to mirror [com.bios.app.export.FhirImportSummary] / `AcceptedReading` so the
 * review screen and the existing FHIR summary dialog share one mental model:
 * a batch of accepted readings, a list of skipped lines with reasons, and an
 * optional file-level error.
 *
 * Nothing here is persisted. These objects feed the owner-facing review
 * screen; only the rows the owner confirms ever reach
 * [com.bios.app.data.BiomarkerEntryRepo]. OCR proposes, the owner disposes.
 *
 * See docs/LAB_OCR_INGESTION.md.
 */

/** One physical text line recognised by a [LabOcrEngine], with its page-space bounds. */
data class OcrLine(
    val text: String,
    /** Source page index (0-based) the line came from — multi-page reports. */
    val page: Int = 0,
    /** Top edge in page pixels; used only to order lines and locate the header band. */
    val top: Int = 0,
)

/**
 * Confidence in a single extracted reading. Drives the review UX — never
 * silent acceptance. HIGH rows are pre-checked; LOW rows are shown unchecked
 * with a flag for the owner to verify before it counts.
 */
enum class Confidence { HIGH, LOW }

/**
 * Panel-level metadata extracted once from the report header and applied to
 * every row (a report has one specimen date, usually one specimen type and
 * lab). All fields are editable in the review screen before confirm.
 *
 * [collectionDate] is intentionally nullable: OCR must never silently default
 * a lab's draw date to "today". When null, the review screen forces the owner
 * to pick a date before confirm is enabled.
 */
data class PanelMetadata(
    val labName: String? = null,
    val collectionDate: Long? = null,
    val specimen: Specimen? = null,
    /** content:// URI of the copied source image/PDF, attached to every row. */
    val sourceUri: String? = null,
) {
    /** Build the shared [BiomarkerContext] for a confirmed row. */
    fun toContext(): BiomarkerContext = BiomarkerContext(
        labName = labName?.ifBlank { null },
        specimen = specimen,
        sourceUri = sourceUri?.ifBlank { null },
    )
}

/**
 * A biomarker reading parsed off one report line, already unit-normalised to
 * the metric's canonical unit. [rawLine] is carried (unlike FHIR's
 * `AcceptedReading`) because the owner is verifying a machine read of fuzzy
 * pixels and needs to see the source text.
 */
data class ExtractedReading(
    val metricType: MetricType,
    val value: Double,
    val confidence: Confidence,
    val rawLine: String,
)

/** A line that could not become a reading, with a human-readable reason. */
data class SkippedLine(
    val rawLine: String,
    val reason: String,
)

/**
 * Everything the review screen needs. Mirrors `FhirImportSummary`:
 * [readings] + [skipped] + an optional [fileError] for an unreadable file /
 * no pages / OCR failure. [panel] carries the editable shared header.
 */
data class LabScanSummary(
    val readings: List<ExtractedReading>,
    val skipped: List<SkippedLine>,
    val panel: PanelMetadata,
    val fileError: String? = null,
) {
    val isFileError: Boolean get() = fileError != null
    val readingCount: Int get() = readings.size
    val skippedCount: Int get() = skipped.size

    companion object {
        fun fileError(message: String): LabScanSummary = LabScanSummary(
            readings = emptyList(),
            skipped = emptyList(),
            panel = PanelMetadata(),
            fileError = message,
        )
    }
}
