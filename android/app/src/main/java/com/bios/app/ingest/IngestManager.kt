package com.bios.app.ingest

import com.bios.app.data.BiosDatabase
import com.bios.app.engine.CircadianEngine
import com.bios.app.engine.HrbsEngine
import com.bios.app.engine.DetectionLatencyTracker
import com.bios.app.engine.GlucoseVariability
import com.bios.app.engine.HrRecoveryPersister
import com.bios.app.engine.PipelineStage
import com.bios.app.engine.SignalQualityFilter
import com.bios.app.engine.SleepDerivations
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.DataSource
import com.bios.app.model.ExerciseSession
import com.bios.app.model.MetricReading
import com.bios.app.model.SensorType
import com.bios.app.model.SourceType
import com.bios.contracts.MetricType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Calendar

/**
 * Orchestrates data ingestion from all available health data sources,
 * handles deduplication, and triggers downstream processing.
 *
 * Adapter selection priority:
 * 1. Health Connect (if available — Android 14+, or installed on older versions)
 * 2. Gadgetbridge (if installed — degoogled devices, LETHE)
 * 3. Direct sensor APIs (phone/watch hardware sensors)
 * 4. Third-party API adapters (Oura, etc.)
 * 5. Phone sensor adapter (accelerometer, step counter — always available)
 */
class IngestManager(
    private val healthConnect: HealthConnectAdapter,
    private val db: BiosDatabase,
    private val ouraAdapter: OuraApiAdapter? = null,
    private val phoneSensorAdapter: PhoneSensorAdapter? = null,
    private val gadgetbridgeAdapter: GadgetbridgeAdapter? = null,
    private val directSensorAdapter: DirectSensorAdapter? = null,
    private val withingsAdapter: WithingsApiAdapter? = null,
    private val whoopAdapter: WhoopApiAdapter? = null,
    private val garminAdapter: GarminApiAdapter? = null,
    private val polarAdapter: PolarApiAdapter? = null,
    private val bleAirQualityAdapter: BleAirQualityAdapter? = null,
    private val latencyTracker: DetectionLatencyTracker? = null
) {
    private val readingDao = db.metricReadingDao()
    private val sourceDao = db.dataSourceDao()
    private val payloadDao = db.eventPayloadFieldDao()
    private val toggleDao = db.sourceMetricToggleDao()
    private val circadianEngine = CircadianEngine(readingDao); private val hrbsEngine = HrbsEngine(readingDao)

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _dataAgeDays = MutableStateFlow(0)
    val dataAgeDays: StateFlow<Int> = _dataAgeDays

    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress: StateFlow<Float> = _syncProgress

    private val _syncStatus = MutableStateFlow("")
    val syncStatus: StateFlow<String> = _syncStatus

    /** Last reading per metric type, used for rate-of-change quality checks. */
    private val lastReadingPerMetric = mutableMapOf<String, MetricReading>()

    private var healthConnectSourceId: String? = null
    private var ouraSourceId: String? = null
    private var phoneSensorSourceId: String? = null
    private var gadgetbridgeSourceId: String? = null
    private var directSensorSourceId: String? = null
    private var withingsSourceId: String? = null
    private var whoopSourceId: String? = null
    private var garminSourceId: String? = null
    private var polarSourceId: String? = null
    private var bleAirQualitySourceId: String? = null

    // MARK: - Setup

    suspend fun setup() {
        // Health Connect (preferred if available)
        if (healthConnect.isAvailable) {
            healthConnectSourceId = sourceDao.getOrCreate(
                SourceType.HEALTH_CONNECT, "Android Wearable", SensorType.OPTICAL_HR
            )
        }

        // Gadgetbridge (fallback for degoogled devices)
        if (gadgetbridgeAdapter?.isAvailable == true) {
            gadgetbridgeSourceId = sourceDao.getOrCreate(
                SourceType.GADGETBRIDGE, "Gadgetbridge Device", SensorType.OPTICAL_HR
            )
        }

        // Direct sensor APIs (HR, HRV from hardware sensors)
        if (directSensorAdapter?.hasAnySensor == true) {
            directSensorSourceId = sourceDao.getOrCreate(
                SourceType.DIRECT_SENSOR, "Direct Sensors", SensorType.OPTICAL_HR
            )
        }

        // Oura API
        if (ouraAdapter?.isConnected == true) {
            ouraSourceId = sourceDao.getOrCreate(
                SourceType.OURA_API, "Oura Ring", SensorType.OPTICAL_HR
            )
        }

        // Withings API (scale-first device — body composition, BP, sleep summary)
        if (withingsAdapter?.isConnected == true) {
            withingsSourceId = sourceDao.getOrCreate(
                SourceType.WITHINGS_API, "Withings", SensorType.DERIVED
            )
        }

        // WHOOP API (strap — recovery, sleep, workouts)
        if (whoopAdapter?.isConnected == true) {
            whoopSourceId = sourceDao.getOrCreate(
                SourceType.WHOOP_API, "WHOOP", SensorType.OPTICAL_HR
            )
        }

        // Garmin API (watch — HR, sleep, steps, SpO2, respiration, activities)
        if (garminAdapter?.isConnected == true) {
            garminSourceId = sourceDao.getOrCreate(
                SourceType.GARMIN_API, "Garmin", SensorType.OPTICAL_HR
            )
        }

        // Polar API (clinical-grade HR/HRV from H10 + Verity Sense)
        if (polarAdapter?.isConnected == true) polarSourceId = sourceDao.getOrCreate(SourceType.POLAR_API, "Polar", SensorType.OPTICAL_HR)

        // Phone sensors (always available as last resort)
        if (phoneSensorAdapter?.hasAccelerometer == true ||
            phoneSensorAdapter?.hasStepCounter == true ||
            phoneSensorAdapter?.hasAmbientLight == true
        ) {
            phoneSensorSourceId = sourceDao.getOrCreate(
                SourceType.PHONE_SENSOR, "Phone Sensors", SensorType.ACCELEROMETER
            )
        }

        // BLE air-quality peripheral (#43). When a device is paired, register
        // its DataSource row and open the GATT connection so the streaming
        // notifications have a sourceId to route through.
        if (bleAirQualityAdapter?.isPaired == true) {
            val sourceId = sourceDao.getOrCreate(
                SourceType.BLE_PERIPHERAL, "BLE Air-Quality Sensor", SensorType.DERIVED
            )
            bleAirQualitySourceId = sourceId
            bleAirQualityAdapter.connect(sourceId)
        }

        updateDataAge()

        IngestTelemetry.log(
            "setup: hc=${healthConnectSourceId != null} gadgetbridge=${gadgetbridgeSourceId != null} " +
                "phone=${phoneSensorSourceId != null} dataAgeDays=${_dataAgeDays.value}"
        )

        // Backfill on first launch (no data at all) OR when a primary metric is
        // empty — handles the case where the user installed Bios, granted HC
        // permission later, or only just connected a watch. Without this, the
        // 24h syncRecentData loop would never recover the missing history.
        if (_dataAgeDays.value == 0 || hasEmptyPrimaryMetric()) {
            IngestTelemetry.log("setup: entering 30-day historical backfill")
            syncHistoricalData()
        } else {
            IngestTelemetry.log("setup: entering recent-24h sync")
            syncRecentData()
        }
    }

    private suspend fun hasEmptyPrimaryMetric(): Boolean {
        // Only worth retrying the historical pull when a wearable-history
        // source actually exists. Phone-only setups can't backfill HR no
        // matter how many times we ask, so don't burn syncs.
        val hasWearableHistorySource = healthConnectSourceId != null ||
            gadgetbridgeSourceId != null ||
            ouraSourceId != null ||
            withingsSourceId != null ||
            whoopSourceId != null ||
            garminSourceId != null ||
            polarSourceId != null
        if (!hasWearableHistorySource) return false
        for (metric in PRIMARY_METRICS_FOR_BACKFILL) {
            if (readingDao.count(metric.key) == 0) return true
        }
        return false
    }

    // MARK: - Sync

    /** Sync the last 24 hours (regular refresh). */
    suspend fun syncRecentData() {
        if (_isSyncing.value) return
        _isSyncing.value = true

        try {
            val ingestBlock: suspend () -> Unit = {
                val end = Instant.now()
                val start = end.minus(24, ChronoUnit.HOURS)

                val allReadings = coroutineScope {
                    val jobs = listOfNotNull(
                        healthConnectSourceId?.let { id ->
                            async { IngestTelemetry.timedFetch("health_connect") { healthConnect.fetchReadings(start, end, id) } }
                        },
                        async { IngestTelemetry.timedFetch("gadgetbridge") { fetchGadgetbridgeReadings(start, end) } },
                        async { IngestTelemetry.timedFetch("direct_sensor") { fetchDirectSensorReadings() } },
                        async { IngestTelemetry.timedFetch("oura") { fetchOuraReadings(start, end) } },
                        async { IngestTelemetry.timedFetch("withings") { fetchWithingsReadings(start, end) } },
                        async { IngestTelemetry.timedFetch("whoop") { fetchWhoopReadings(start, end) } },
                        async { IngestTelemetry.timedFetch("garmin") { fetchGarminReadings(start, end) } },
                        async { IngestTelemetry.timedFetch("polar") { fetchPolarReadings(start, end) } },
                        async { IngestTelemetry.timedFetch("phone_sensor") { fetchPhoneSensorReadings() } }
                    )
                    jobs.awaitAll().flatten()
                }

                val deduped = deduplicate(allReadings)
                val quality = SignalQualityFilter.filter(deduped, lastReadingPerMetric)
                val derived = deriveAll(quality)
                val toWrite = SourceMetricToggleFilter.apply(quality + derived, toggleDao, sourceDao)
                readingDao.insertAll(toWrite)
                updateLastReadings(quality)
                IngestTelemetry.syncSummary(
                    "recent24h", healthConnectSourceId != null, allReadings.size,
                    deduped.size, quality.size, derived.size, toWrite.size
                )

                // Composite events (EXERCISE_SESSION) take a parallel path —
                // the scalar dedupe/quality filter can't keep payload rows in
                // sync with a deduped parent today, so sessions skip it and
                // land directly. Multi-adapter dedupe lands when a second
                // session-emitting adapter exists.
                healthConnectSourceId?.let { id ->
                    persistSessions(healthConnect.fetchExerciseSessions(start, end, id))
                }
                persistApiSessions(ouraSourceId, ouraAdapter, start, end) { a, s, e, id -> a.fetchExerciseSessions(s, e, id) }
                persistApiSessions(whoopSourceId, whoopAdapter, start, end) { a, s, e, id -> a.fetchExerciseSessions(s, e, id) }
                persistApiSessions(garminSourceId, garminAdapter, start, end) { a, s, e, id -> a.fetchExerciseSessions(s, e, id) }
                persistApiSessions(polarSourceId, polarAdapter, start, end) { a, s, e, id -> a.fetchExerciseSessions(s, e, id) }
            }

            if (latencyTracker != null) {
                latencyTracker.track(PipelineStage.DATA_INGEST) { ingestBlock() }
            } else {
                ingestBlock()
            }

            circadianEngine.derive()?.let { readingDao.insert(it) }
            hrbsEngine.derive()?.let { readingDao.insert(it) }
            HrGapTibBackfill(readingDao, sourceDao).runForLookback(lookbackDays = 14)
            RestingHeartRateBackfill(readingDao, sourceDao).runForLookback(lookbackDays = 14)
            _lastSyncTime.value = System.currentTimeMillis()
            updateDataAge()
        } finally {
            _isSyncing.value = false
        }
    }

    /** Sync the last 30 days (initial setup). */
    suspend fun syncHistoricalData() {
        _isSyncing.value = true
        _syncProgress.value = 0f
        _syncStatus.value = "Syncing 30 days of history..."

        try {
            val end = Instant.now()
            val start = end.minus(30, ChronoUnit.DAYS)
            val totalDays = 30f

            // Fetch in daily chunks to avoid memory pressure
            var current = start
            var completedDays = 0
            while (current.isBefore(end)) {
                _syncStatus.value = "Syncing day ${completedDays + 1} of 30..."
                val chunkEnd = minOf(current.plus(1, ChronoUnit.DAYS), end)
                val allReadings = coroutineScope {
                    val jobs = listOfNotNull(
                        healthConnectSourceId?.let { id ->
                            async { healthConnect.fetchReadings(current, chunkEnd, id) }
                        },
                        async { fetchGadgetbridgeReadings(current, chunkEnd) },
                        async { fetchOuraReadings(current, chunkEnd) },
                        async { fetchWithingsReadings(current, chunkEnd) },
                        async { fetchWhoopReadings(current, chunkEnd) },
                        async { fetchGarminReadings(current, chunkEnd) },
                        async { fetchPolarReadings(current, chunkEnd) }
                    )
                    jobs.awaitAll().flatten()
                }

                val deduped = deduplicate(allReadings)
                val quality = SignalQualityFilter.filter(deduped, lastReadingPerMetric)
                val derived = deriveAll(quality)
                val toWrite = SourceMetricToggleFilter.apply(quality + derived, toggleDao, sourceDao)
                readingDao.insertAll(toWrite)
                updateLastReadings(quality)
                IngestTelemetry.syncSummary(
                    "historical day ${completedDays + 1}/30", healthConnectSourceId != null,
                    allReadings.size, deduped.size, quality.size, derived.size, toWrite.size
                )

                healthConnectSourceId?.let { id ->
                    persistSessions(healthConnect.fetchExerciseSessions(current, chunkEnd, id))
                }
                persistApiSessions(ouraSourceId, ouraAdapter, current, chunkEnd) { a, s, e, id -> a.fetchExerciseSessions(s, e, id) }
                persistApiSessions(whoopSourceId, whoopAdapter, current, chunkEnd) { a, s, e, id -> a.fetchExerciseSessions(s, e, id) }
                persistApiSessions(garminSourceId, garminAdapter, current, chunkEnd) { a, s, e, id -> a.fetchExerciseSessions(s, e, id) }
                persistApiSessions(polarSourceId, polarAdapter, current, chunkEnd) { a, s, e, id -> a.fetchExerciseSessions(s, e, id) }

                current = chunkEnd
                completedDays++
                _syncProgress.value = completedDays / totalDays
            }

            // Each derivation/backfill is idempotent and self-skips when
            // preconditions aren't met (e.g. <4 days of sleep history),
            // so this block is safe to call on every sync.
            circadianEngine.derive()?.let { readingDao.insert(it) }
            hrbsEngine.derive()?.let { readingDao.insert(it) }
            HrGapTibBackfill(readingDao, sourceDao).runForLookback(lookbackDays = 30)
            SleepDerivationsBackfill(readingDao).runForLookback(lookbackDays = 30)
            RestingHeartRateBackfill(readingDao, sourceDao).runForLookback(lookbackDays = 30)
            _lastSyncTime.value = System.currentTimeMillis()
            updateDataAge()
        } finally {
            _isSyncing.value = false
            _syncProgress.value = 1f
        }
    }

    // MARK: - Derivations

    /**
     * Compute derived readings (sleep latency, etc.) over a quality-filtered batch.
     * Grouping by sourceId keeps each derivation coherent — a session's latency is
     * computed from one source's stage rows, not a cross-source mix.
     */
    private fun deriveAll(quality: List<MetricReading>): List<MetricReading> {
        val derived = mutableListOf<MetricReading>()
        quality.groupBy { it.sourceId }.forEach { (sourceId, rows) ->
            SleepDerivations.deriveSleepLatency(rows, sourceId)?.let { derived += it }
            SleepDerivations.deriveSleepEfficiency(rows, sourceId)?.let { derived += it }
            SleepDerivations.deriveTimeInBed(rows, sourceId)?.let { derived += it }
            SleepDerivations.deriveSleepFragmentation(rows, sourceId)?.let { derived += it }
            SleepDerivations.deriveWakeAfterSleepOnset(rows, sourceId)?.let { derived += it }
            SleepDerivations.deriveSleepScore(rows, sourceId)?.let { derived += it }
            GlucoseVariability.deriveCv(rows, sourceId)?.let { derived += it }
            GlucoseVariability.deriveTimeInRange(rows, sourceId)?.let { derived += it }
            GlucoseVariability.derivePeak(rows, sourceId)?.let { derived += it }
            GlucoseVariability.deriveMage(rows, sourceId)?.let { derived += it }
        }
        return derived
    }

    // MARK: - Deduplication

    private fun deduplicate(readings: List<MetricReading>): List<MetricReading> =
        Deduplicator.deduplicate(readings)

    /**
     * Writes composite EXERCISE_SESSION readings + their payload rows.
     * Insert order matters — the parent reading must exist before its
     * EventPayloadField rows reference it via FK. Skipped silently if the
     * source list is empty (the common case when no HC sessions exist
     * in the window).
     */
    /** Common path for API adapters that expose `fetchExerciseSessions`.
     *  Skips when the source row hasn't been created or the adapter is null,
     *  and swallows fetch failures so a transient 5xx doesn't kill the sync. */
    private suspend fun <T> persistApiSessions(
        sourceId: String?,
        adapter: T?,
        start: Instant,
        end: Instant,
        fetch: suspend (T, Instant, Instant, String) -> List<ExerciseSession>,
    ) {
        if (sourceId == null || adapter == null) return
        persistSessions(runCatching { fetch(adapter, start, end, sourceId) }.getOrDefault(emptyList()))
    }

    private suspend fun persistSessions(sessions: List<ExerciseSession>) {
        if (sessions.isEmpty()) return
        readingDao.insertAll(sessions.map { it.reading })
        payloadDao.insertAll(sessions.flatMap { it.payload })
        HrRecoveryPersister.persistFor(sessions, readingDao)
    }

    // MARK: - Helpers (getOrCreateSource → SourceRegistry.kt, 500-line ceiling)

    /** Generic adapter-fetch helper. Returns empty when the source row
     *  hasn't been registered or the adapter is null; swallows fetch
     *  failures so a transient 5xx from one adapter doesn't kill the sync. */
    private suspend inline fun <T> fetchApiReadings(
        sourceId: String?,
        adapter: T?,
        crossinline fetch: suspend (T, String) -> List<MetricReading>,
    ): List<MetricReading> {
        if (sourceId == null || adapter == null) return emptyList()
        return runCatching { fetch(adapter, sourceId) }.getOrDefault(emptyList())
    }

    private suspend fun fetchGadgetbridgeReadings(s: Instant, e: Instant) = fetchApiReadings(gadgetbridgeSourceId, gadgetbridgeAdapter) { a, id -> a.fetchReadings(s, e, id) }

    private suspend fun fetchDirectSensorReadings(): List<MetricReading> {
        val sourceId = directSensorSourceId ?: return emptyList()
        val adapter = directSensorAdapter ?: return emptyList()
        return try {
            val readings = mutableListOf<MetricReading>()
            readings += adapter.sampleHeartRate(SENSOR_SAMPLE_DURATION_MS, sourceId)
            readings += adapter.sampleHrv(SENSOR_SAMPLE_DURATION_MS, sourceId)
            val steps = adapter.readSteps(sourceId)
            if (steps != null) readings += steps
            readings
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchOuraReadings(s: Instant, e: Instant) = fetchApiReadings(ouraSourceId, ouraAdapter) { a, id -> a.fetchReadings(s, e, id) }
    private suspend fun fetchWithingsReadings(s: Instant, e: Instant) = fetchApiReadings(withingsSourceId, withingsAdapter) { a, id -> a.fetchReadings(s, e, id) }
    private suspend fun fetchWhoopReadings(s: Instant, e: Instant) = fetchApiReadings(whoopSourceId, whoopAdapter) { a, id -> a.fetchReadings(s, e, id) }
    private suspend fun fetchGarminReadings(s: Instant, e: Instant) = fetchApiReadings(garminSourceId, garminAdapter) { a, id -> a.fetchReadings(s, e, id) }
    private suspend fun fetchPolarReadings(s: Instant, e: Instant) = fetchApiReadings(polarSourceId, polarAdapter) { a, id -> a.fetchReadings(s, e, id) }

    private suspend fun fetchPhoneSensorReadings(): List<MetricReading> {
        val sourceId = phoneSensorSourceId ?: return emptyList()
        val adapter = phoneSensorAdapter ?: return emptyList()
        return try {
            val readings = mutableListOf<MetricReading>()
            readings += adapter.sampleAccelerometer(SENSOR_SAMPLE_DURATION_MS, sourceId)
            val stepReading = adapter.readStepCounter(sourceId)
            if (stepReading != null) readings += stepReading
            val lightReading = adapter.sampleAmbientLight(sourceId)
            if (lightReading != null) readings += lightReading
            readings
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Called after the owner pairs a BLE air-quality peripheral from the
     * pair screen. Lazily creates the [SourceType.BLE_PERIPHERAL] row so
     * the adapter's incoming readings have a foreign key, then opens the
     * GATT connection. Safe to call repeatedly — `getOrCreateSource` is
     * idempotent and the adapter tears down any prior connection.
     */
    suspend fun onBleAirQualityPaired() {
        val adapter = bleAirQualityAdapter ?: return
        val sourceId = bleAirQualitySourceId ?: sourceDao.getOrCreate(
            SourceType.BLE_PERIPHERAL, "BLE Air-Quality Sensor", SensorType.DERIVED
        ).also { bleAirQualitySourceId = it }
        adapter.connect(sourceId)
    }

    private suspend fun updateDataAge() {
        val oldest = readingDao.oldestTimestamp()
        if (oldest != null) {
            val days = ChronoUnit.DAYS.between(
                Instant.ofEpochMilli(oldest),
                Instant.now()
            ).toInt()
            _dataAgeDays.value = days
        }
    }

    private fun updateLastReadings(readings: List<MetricReading>) {
        for (reading in readings) {
            lastReadingPerMetric[reading.metricType] = reading
        }
    }

    companion object {
        private const val SENSOR_SAMPLE_DURATION_MS = 10_000L // 10 seconds

        // Metrics whose absence triggers a re-backfill on the next setup().
        // Limited to wearable-sourced metrics so a phone-only run (steps from
        // the pedometer) doesn't repeatedly attempt full historical pulls
        // for metrics no source can provide.
        private val PRIMARY_METRICS_FOR_BACKFILL = listOf(
            MetricType.HEART_RATE,
            MetricType.RESTING_HEART_RATE,
            MetricType.SLEEP_DURATION,
        )
    }
}
