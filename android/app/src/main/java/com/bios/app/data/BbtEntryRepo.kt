package com.bios.app.data

import com.bios.app.engine.CycleInference
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
 * time. Cycle-phase rows are written with a deterministic id keyed by the
 * UTC day bucket so re-derivation is idempotent (REPLACE on conflict).
 *
 * Reuses the same `SELF_REPORTED` [DataSource] tag as [BiomarkerEntryRepo]
 * so the baseline engine and anomaly detector keep ignoring it per decision
 * 3 in `docs/SELF_REPORTED_DATA_HOME.md`.
 */
class BbtEntryRepo(private val db: BiosDatabase) {

    /** Physiological BBT bounds (°C). Outside this range = data-entry error. */
    private val minTempC = 35.0
    private val maxTempC = 39.0

    /** How far back cycle inference looks each time a BBT is saved. */
    private val rederivationWindowMs = 90L * 86_400_000L

    suspend fun addBbt(temperatureC: Double, timestamp: Long) {
        require(temperatureC in minTempC..maxTempC) {
            "BBT $temperatureC°C is outside the $minTempC–$maxTempC range"
        }
        val sourceId = getOrCreateSelfReportedSource()
        db.metricReadingDao().insert(
            MetricReading(
                metricType = MetricType.BASAL_BODY_TEMPERATURE.key,
                value = temperatureC,
                timestamp = timestamp,
                sourceId = sourceId,
                confidence = ConfidenceTier.HIGH.level
            )
        )
        rederiveCyclePhases(sourceId)
    }

    suspend fun fetchRecentBbts(limit: Int = 30): List<MetricReading> =
        db.metricReadingDao().fetchLatest(MetricType.BASAL_BODY_TEMPERATURE.key, limit)

    suspend fun fetchLatestCyclePhase(): MetricReading? =
        db.metricReadingDao().fetchLatest(MetricType.CYCLE_PHASE.key, limit = 1).firstOrNull()

    private suspend fun rederiveCyclePhases(sourceId: String) {
        val windowStart = System.currentTimeMillis() - rederivationWindowMs
        val bbts = db.metricReadingDao().fetch(
            MetricType.BASAL_BODY_TEMPERATURE.key,
            windowStart,
            Long.MAX_VALUE
        )
        val phases = CycleInference.deriveCyclePhases(bbts, sourceId)
        if (phases.isEmpty()) return
        val withStableIds = phases.map { it.copy(id = stableCyclePhaseId(it.timestamp)) }
        db.metricReadingDao().insertAll(withStableIds)
    }

    private suspend fun getOrCreateSelfReportedSource(): String {
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

    companion object {
        /**
         * Deterministic primary key for a derived CYCLE_PHASE row. Keyed by
         * the UTC day bucket so re-running [CycleInference] over a growing
         * BBT series produces stable row ids — `OnConflictStrategy.REPLACE`
         * on `MetricReadingDao.insertAll` handles dedupe.
         */
        fun stableCyclePhaseId(timestamp: Long): String {
            val day = timestamp / 86_400_000L
            return "cycle_phase_self_reported_$day"
        }
    }
}
