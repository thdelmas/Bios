package com.bios.app.data

import com.bios.app.model.EcgClassification
import com.bios.app.model.EcgStrip
import com.bios.app.model.LeadPlacement

/**
 * Thin repository wrapper over [com.bios.app.data.dao.EcgStripDao] for the
 * ECG strips entry screen, the file-import Activity, and the future
 * rhythm-confirmation surface (#180 wires the PPG-derived AFib screen
 * against this table).
 *
 * Closes audit gap §2.8 in docs/audits/CARDIOLOGY_POV.md. Three other
 * audits (Emergency Medicine, Neurology stroke workup, the seven
 * traditional-medicine reviews flagging discarded rhythm context) all
 * converge on the same need: owner-captured ECG must round-trip cleanly
 * from vendor → Bios → FHIR export.
 */
class EcgStripRepo(private val db: BiosDatabase) {

    private val dao get() = db.ecgStripDao()

    /**
     * Persists a strip. Caller supplies the waveform bytes verbatim;
     * Bios does not transcode or downsample. The waveform is part of
     * the artefact the owner imported and must round-trip unchanged
     * for clinician review.
     */
    suspend fun add(
        timestamp: Long,
        durationSeconds: Int,
        samplingRateHz: Int,
        samples: ByteArray,
        sourceVendor: String,
        leadPlacement: LeadPlacement = LeadPlacement.UNKNOWN,
        classification: EcgClassification? = null,
        voltageScale: Double = 1.0,
        voltageOffset: Double = 0.0,
        sampleEncoding: String = "int16_le",
        note: String? = null,
    ): String {
        require(durationSeconds > 0) { "Duration must be positive" }
        require(samplingRateHz > 0) { "Sampling rate must be positive" }
        require(samples.isNotEmpty()) { "Waveform samples cannot be empty" }
        require(sourceVendor.isNotBlank()) { "Source vendor cannot be blank" }
        val strip = EcgStrip(
            timestamp = timestamp,
            durationSeconds = durationSeconds,
            samplingRateHz = samplingRateHz,
            leadPlacement = leadPlacement,
            samples = samples,
            voltageScale = voltageScale,
            voltageOffset = voltageOffset,
            sampleEncoding = sampleEncoding,
            classification = classification,
            sourceVendor = sourceVendor.trim(),
            note = note?.takeIf { it.isNotBlank() }?.trim(),
        )
        dao.insert(strip)
        return strip.id
    }

    suspend fun insert(strip: EcgStrip) = dao.insert(strip)
    suspend fun fetchById(id: String): EcgStrip? = dao.fetchById(id)
    suspend fun fetchAll(): List<EcgStrip> = dao.fetchAll()
    suspend fun fetchInWindow(startMs: Long, endMs: Long): List<EcgStrip> =
        dao.fetchInWindow(startMs, endMs)
    suspend fun remove(id: String) = dao.deleteById(id)
    suspend fun count(): Int = dao.count()
}
