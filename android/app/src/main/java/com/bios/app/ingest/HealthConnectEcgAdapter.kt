package com.bios.app.ingest

import android.content.Context
import com.bios.app.data.EcgStripRepo
import com.bios.app.model.EcgStrip
import java.time.Instant

/**
 * Pulls owner-captured single-lead ECG strips from upstream sources into
 * Bios's [com.bios.app.data.dao.EcgStripDao] (#188, audit gap §2.8 in
 * docs/audits/CARDIOLOGY_POV.md).
 *
 * **Current state — platform gap, intentionally stubbed.** Health
 * Connect's `ElectrocardiogramRecord` is *not* part of the Bios-pinned
 * androidx.health.connect:connect-client 1.1.0-alpha10 API surface as of
 * 2026-05. Apple HealthKit and Samsung Health both expose ECG strips
 * through their **own** SDKs (HealthKit FHIR / SHealth REST), not
 * Health Connect's unified record layer. Until Google promotes
 * `ElectrocardiogramRecord` out of internal staging — tracked at
 * Google-side issue — Bios ingests ECG strips exclusively through the
 * file-import path in [AppleHealthEcgImporter] (Apple Health export
 * XML covers the Apple Watch flow; KardiaMobile and Withings ScanWatch
 * users export to Apple Health, which then dumps to XML).
 *
 * This adapter exists so the data layer is ready: when Health Connect
 * ships ECG records, only [fetchStrips] needs implementing — the
 * permission set, repo plumbing, and [IngestManager] hookup are
 * already in place. The [fetchStrips] entry point is wired so callers
 * can depend on it today without re-routing through the file importer.
 */
class HealthConnectEcgAdapter(
    @Suppress("unused") private val context: Context,
    private val repo: EcgStripRepo,
) {

    /**
     * Reads ECG strips from Health Connect that fall in `[start, end]`
     * and persists them through [EcgStripRepo].
     *
     * Today this returns an empty list — see class-level comment. The
     * method signature mirrors [HealthConnectAdapter.fetchReadings]
     * so [IngestManager] can call it identically once the platform
     * API lands.
     *
     * @return number of strips inserted (always 0 today; will be the
     *   actual count once Health Connect exposes ECG records).
     */
    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    suspend fun fetchStrips(start: Instant, end: Instant, sourceVendor: String): Int {
        // TODO(#188 follow-up): when Health Connect promotes
        // `ElectrocardiogramRecord` out of internal staging, read with
        // ReadRecordsRequest(ElectrocardiogramRecord::class, ...) and
        // map each record to an [EcgStrip] via [mapToStrip]. The
        // permission to request will be
        // `HealthPermission.getReadPermission(ElectrocardiogramRecord::class)`.
        return 0
    }

    companion object {
        /**
         * Pure mapper from a Health-Connect-shaped ECG record into a Bios
         * [EcgStrip]. Kept separate from the suspend fetch so it can be
         * unit-tested without the Health Connect client.
         *
         * Today this is parameterised on the primitive fields rather
         * than the not-yet-available record type. When the type lands,
         * a thin wrapper inside [fetchStrips] will pull each attribute
         * off `ElectrocardiogramRecord` and call this function.
         */
        fun mapToStrip(
            timestampEpochMs: Long,
            durationSeconds: Int,
            samplingRateHz: Int,
            samplesInt16Le: ByteArray,
            sourceVendor: String,
            classificationRaw: String? = null,
        ): EcgStrip {
            return EcgStrip(
                timestamp = timestampEpochMs,
                durationSeconds = durationSeconds,
                samplingRateHz = samplingRateHz,
                samples = samplesInt16Le,
                voltageScale = 0.001,
                voltageOffset = 0.0,
                sampleEncoding = "int16_le",
                classification = AppleHealthEcgImporter.mapClassification(classificationRaw),
                sourceVendor = sourceVendor,
            )
        }
    }
}
