package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Owner-imported single-lead ECG strip: a waveform plus its metadata, as
 * the source device recorded it.
 *
 * Bios stores and draws the strip. It does not read the rhythm. The
 * classification field carries the source's own text, verbatim, and is
 * shown with the source named.
 *
 * **Waveform storage:** persisted as a [ByteArray] BLOB in the main
 * encrypted SQLCipher database. Typical strip size is 5–30 KB
 * (30 s × 512 Hz × 1–2 bytes/sample). Storing inline keeps the
 * waveform inside Bios's single encryption envelope — moving large
 * binaries to a separate file would split the threat model. The
 * trade-off is that very-long strips (>1 MB) would bloat the DB; if
 * that becomes a concern, switch this column to a `waveformPath`
 * reference into encrypted scoped storage. Today's vendors cap at
 * 30 s so this is a future problem.
 *
 * **Lead placement:** Apple Watch and KardiaMobile capture LEAD_I
 * equivalent (left-arm vs right-finger contact). Withings ScanWatch
 * is similar. Lead-II derivation (chest electrode) is rare on
 * consumer devices. UNKNOWN covers manual imports and devices whose
 * documentation doesn't specify.
 */
@Entity(
    tableName = "ecg_strips",
    indices = [Index("timestamp"), Index("classification")],
)
data class EcgStrip(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** Epoch millis at the **start** of the recording. */
    val timestamp: Long,
    /** Recording length in seconds. Consumer devices ship 30 s strips. */
    val durationSeconds: Int,
    /** Samples per second. Typically 512 (Apple Watch) or 250–300 (others). */
    val samplingRateHz: Int,
    /** Anatomical lead — see class comment. */
    val leadPlacement: LeadPlacement = LeadPlacement.UNKNOWN,
    /**
     * Raw waveform samples, stored verbatim from the vendor export.
     * Format is vendor-specific (typically int16 little-endian
     * microvolts for Apple; CSV-decoded floats for KardiaMobile PDF
     * extraction). The renderer reads [samplingRateHz] + [voltageScale]
     * + [voltageOffset] to interpret bytes back to millivolts.
     *
     * Stored as BLOB so the original byte stream is preserved for
     * export — the renderer's decoding is downstream.
     */
    val samples: ByteArray,
    /**
     * Conversion factor from raw sample value to millivolts:
     * `mv = rawValue * voltageScale + voltageOffset`. For Apple HealthKit
     * exports the scale is documented per-record; for unknown sources
     * a sensible default (1.0 / 1000.0 for µV → mV) lives in the importer.
     */
    val voltageScale: Double = 1.0,
    val voltageOffset: Double = 0.0,
    /**
     * Encoding hint for the samples blob. Lets the renderer know how
     * many bytes per sample to consume. Common values: "int16_le",
     * "int32_le", "float32_le", "csv_floats".
     */
    val sampleEncoding: String = "int16_le",
    /**
     * The source's own classification text, verbatim, or null when the
     * source file carried none. Rows imported by earlier versions hold one
     * of four coarse names those versions reduced the text to.
     */
    val classification: String? = null,
    /**
     * Human-readable vendor / source identifier. Examples: "Apple Watch",
     * "Samsung Galaxy Watch", "KardiaMobile", "Withings ScanWatch",
     * "Fitbit Sense", "Manual import". Free-text — the FHIR export
     * uses this verbatim as `Observation.device.display`.
     */
    val sourceVendor: String,
    /**
     * Optional owner free-text annotation. Never read by any engine.
     */
    val note: String? = null,
    /** Epoch millis when the row was inserted into Bios. */
    val createdAt: Long = System.currentTimeMillis(),
) {
    // Generated `equals` / `hashCode` over a ByteArray uses array identity,
    // not contents, which makes diff-style tests flaky. We treat two strips
    // as equal when every persisted field — including the waveform bytes —
    // matches. The cost is O(n) on the samples array; that's the right
    // shape for round-trip tests and the row count is tiny.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EcgStrip) return false
        return id == other.id &&
            timestamp == other.timestamp &&
            durationSeconds == other.durationSeconds &&
            samplingRateHz == other.samplingRateHz &&
            leadPlacement == other.leadPlacement &&
            samples.contentEquals(other.samples) &&
            voltageScale == other.voltageScale &&
            voltageOffset == other.voltageOffset &&
            sampleEncoding == other.sampleEncoding &&
            classification == other.classification &&
            sourceVendor == other.sourceVendor &&
            note == other.note &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + durationSeconds
        result = 31 * result + samplingRateHz
        result = 31 * result + leadPlacement.hashCode()
        result = 31 * result + samples.contentHashCode()
        result = 31 * result + voltageScale.hashCode()
        result = 31 * result + voltageOffset.hashCode()
        result = 31 * result + sampleEncoding.hashCode()
        result = 31 * result + (classification?.hashCode() ?: 0)
        result = 31 * result + sourceVendor.hashCode()
        result = 31 * result + (note?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        return result
    }
}

/**
 * Lead placement — anatomical configuration that the strip represents.
 * Consumer wearables almost universally capture LEAD_I-equivalent.
 */
enum class LeadPlacement {
    LEAD_I,
    LEAD_II_DERIVED,
    UNKNOWN,
}
