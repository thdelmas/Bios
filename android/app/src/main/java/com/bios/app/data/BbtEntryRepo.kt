package com.bios.app.data

import android.content.Context
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.DataSource
import com.bios.app.model.MetricReading
import com.bios.app.model.ReadingKind
import com.bios.app.model.SensorType
import com.bios.app.model.SourceType
import com.bios.contracts.MetricType

/**
 * Persistence path for manually logged basal body temperature readings,
 * plus the cycle-phase derivation that runs on every save.
 *
 * BBT lives on its own entry surface (not the biomarker form) because it's
 * a daily morning measurement, not a lab value, and because saving it has a
 * downstream effect: [CycleInference] is re-run over the last 90 days of
 * BBT history so the owner sees the cycle classification update in real
 * time.
 *
 * **Storage isolation.** BBT and the derived `CYCLE_PHASE` rows are
 * persisted in [ReproductiveDatabase] — a separate SQLCipher file with an
 * independent encryption key, independent retention, and priority
 * destruction on LETHE duress PIN / dead-man's-switch. The owner can wipe
 * reproductive data without touching the main DB. The repo lazily calls
 * [ReproductiveDatabase.initialize] on first construction so the entry
 * surface "just works" — no separate enable flow — while still landing on
 * the isolated key. Owners who want to set an explicit passphrase can do
 * so before first BBT entry; the lazy init only fires when no key exists.
 *
 * Reuses the same `SELF_REPORTED` [DataSource] tag as [BiomarkerEntryRepo]
 * for provenance. Note that the source rows live in the reproductive DB
 * (foreign-key constraint requires them in the same SQLCipher file as the
 * readings), so a separate `SELF_REPORTED` row will appear there
 * alongside the one in the main DB.
 *
 * Cycle-phase rows are written with a deterministic id keyed by the UTC
 * day bucket so re-derivation is idempotent (REPLACE on conflict).
 */
class BbtEntryRepo(context: Context) {

    private val appContext = context.applicationContext

    init {
        // Idempotent — no-op if a key already exists.
        ReproductiveDatabase.initialize(appContext)
    }

    private val db: ReproductiveDatabase
        get() = ReproductiveDatabase.getInstance(appContext)

    /** Physiological BBT bounds (°C). Outside this range = data-entry error. */
    private val minTempC = 35.0
    private val maxTempC = 39.0

    suspend fun addBbt(temperatureC: Double, timestamp: Long) {
        require(temperatureC in minTempC..maxTempC) {
            "BBT $temperatureC°C is outside the $minTempC–$maxTempC range"
        }
        val sourceId = SelfReportedSource.getOrCreate(db)
        db.readingDao().insert(
            MetricReading(
                metricType = MetricType.BASAL_BODY_TEMPERATURE.key,
                value = temperatureC,
                timestamp = timestamp,
                sourceId = sourceId,
                confidence = ConfidenceTier.HIGH.level
            )
        )
        CycleDerivation.rederive(db, sourceId)
    }

    suspend fun fetchRecentBbts(limit: Int = 30): List<MetricReading> =
        db.readingDao().fetchLatest(MetricType.BASAL_BODY_TEMPERATURE.key, limit)

    suspend fun fetchLatestCyclePhase(): MetricReading? =
        db.readingDao().fetchLatest(MetricType.CYCLE_PHASE.key, limit = 1).firstOrNull()

    companion object {
        /**
         * Deterministic primary key for a derived CYCLE_PHASE row. Kept as
         * a forwarding alias for callers that pinned to this name before
         * the helper was extracted into [CycleDerivation].
         */
        fun stableCyclePhaseId(timestamp: Long): String =
            CycleDerivation.stableCyclePhaseId(timestamp)
    }
}

/**
 * Shared resolver for the SELF_REPORTED [DataSource] row in the isolated
 * reproductive DB. Both [BbtEntryRepo] and [PeriodEntryRepo] write through
 * this single row so manual entries appear under one provenance tag.
 */
internal object SelfReportedSource {
    suspend fun getOrCreate(db: ReproductiveDatabase): String {
        val dao = db.dataSourceDao()
        dao.findByType(SourceType.SELF_REPORTED.key)?.let { return it.id }
        val source = DataSource(
            sourceType = SourceType.SELF_REPORTED.key,
            deviceName = "Self-reported",
            sensorType = SensorType.DERIVED.name,
            readingKind = ReadingKind.SELF_REPORTED.name
        )
        dao.insert(source)
        return source.id
    }
}
