package com.bios.app.ingest

import com.bios.app.data.BiosDatabase
import com.bios.app.engine.DetectionLatencyTracker
import com.bios.app.engine.PipelineStage
import com.bios.app.engine.SignalQualityFilter
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.DataSource
import com.bios.app.model.MetricReading
import com.bios.app.model.SensorType
import com.bios.app.model.SourceType
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
    private val latencyTracker: DetectionLatencyTracker? = null
) {
    private val readingDao = db.metricReadingDao()
    private val sourceDao = db.dataSourceDao()

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

    // MARK: - Setup

    suspend fun setup() {
        // Health Connect (preferred if available)
        if (healthConnect.isAvailable) {
            healthConnectSourceId = getOrCreateSource(
                SourceType.HEALTH_CONNECT, "Android Wearable", SensorType.OPTICAL_HR
            )
        }

        // Gadgetbridge (fallback for degoogled devices)
        if (gadgetbridgeAdapter?.isAvailable == true) {
            gadgetbridgeSourceId = getOrCreateSource(
                SourceType.GADGETBRIDGE, "Gadgetbridge Device", SensorType.OPTICAL_HR
            )
        }

        // Direct sensor APIs (HR, HRV from hardware sensors)
        if (directSensorAdapter?.hasAnySensor == true) {
            directSensorSourceId = getOrCreateSource(
                SourceType.DIRECT_SENSOR, "Direct Sensors", SensorType.OPTICAL_HR
            )
        }

        // Oura API
        if (ouraAdapter?.isConnected == true) {
            ouraSourceId = getOrCreateSource(
                SourceType.OURA_API, "Oura Ring", SensorType.OPTICAL_HR
            )
        }

        // Phone sensors (always available as last resort)
        if (phoneSensorAdapter?.hasAccelerometer == true ||
            phoneSensorAdapter?.hasStepCounter == true
        ) {
            phoneSensorSourceId = getOrCreateSource(
                SourceType.PHONE_SENSOR, "Phone Sensors", SensorType.ACCELEROMETER
            )
        }

        updateDataAge()

        // Only fetch full history on first launch; otherwise just sync recent data
        if (_dataAgeDays.value == 0) {
            syncHistoricalData()
        } else {
            syncRecentData()
        }
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
                            async { healthConnect.fetchReadings(start, end, id) }
                        },
                        async { fetchGadgetbridgeReadings(start, end) },
                        async { fetchDirectSensorReadings() },
                        async { fetchOuraReadings(start, end) },
                        async { fetchPhoneSensorReadings() }
                    )
                    jobs.awaitAll().flatten()
                }

                val deduped = deduplicate(allReadings)
                val quality = SignalQualityFilter.filter(deduped, lastReadingPerMetric)
                readingDao.insertAll(quality)
                updateLastReadings(quality)
            }

            if (latencyTracker != null) {
                latencyTracker.track(PipelineStage.DATA_INGEST) { ingestBlock() }
            } else {
                ingestBlock()
            }

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
                        async { fetchOuraReadings(current, chunkEnd) }
                    )
                    jobs.awaitAll().flatten()
                }

                val deduped = deduplicate(allReadings)
                val quality = SignalQualityFilter.filter(deduped, lastReadingPerMetric)
                readingDao.insertAll(quality)
                updateLastReadings(quality)
                current = chunkEnd
                completedDays++
                _syncProgress.value = completedDays / totalDays
            }

            _lastSyncTime.value = System.currentTimeMillis()
            updateDataAge()
        } finally {
            _isSyncing.value = false
            _syncProgress.value = 1f
        }
    }

    // MARK: - Deduplication

    /**
     * Remove duplicate readings based on metric type + timestamp.
     * When multiple sources report the same metric at the same time,
     * keep the highest-confidence one.
     */
    private fun deduplicate(readings: List<MetricReading>): List<MetricReading> {
        val seen = mutableMapOf<String, MetricReading>()

        for (reading in readings) {
            val key = "${reading.metricType}_${reading.timestamp}"
            val existing = seen[key]

            if (existing == null || reading.confidence > existing.confidence) {
                seen[key] = reading
            }
        }

        return seen.values.sortedBy { it.timestamp }
    }

    // MARK: - Helpers

    private suspend fun getOrCreateSource(
        type: SourceType,
        deviceName: String,
        sensorType: SensorType
    ): String {
        val existing = sourceDao.findByType(type.key)
        if (existing != null) return existing.id

        val source = DataSource(
            sourceType = type.key,
            deviceName = deviceName,
            sensorType = sensorType.name
        )
        sourceDao.insert(source)
        return source.id
    }

    private suspend fun fetchGadgetbridgeReadings(
        start: Instant,
        end: Instant
    ): List<MetricReading> {
        val sourceId = gadgetbridgeSourceId ?: return emptyList()
        val adapter = gadgetbridgeAdapter ?: return emptyList()
        return try {
            adapter.fetchReadings(start, end, sourceId)
        } catch (_: Exception) {
            emptyList()
        }
    }

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

    private suspend fun fetchOuraReadings(start: Instant, end: Instant): List<MetricReading> {
        val sourceId = ouraSourceId ?: return emptyList()
        val adapter = ouraAdapter ?: return emptyList()
        return try {
            adapter.fetchReadings(start, end, sourceId)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchPhoneSensorReadings(): List<MetricReading> {
        val sourceId = phoneSensorSourceId ?: return emptyList()
        val adapter = phoneSensorAdapter ?: return emptyList()
        return try {
            val readings = mutableListOf<MetricReading>()
            readings += adapter.sampleAccelerometer(SENSOR_SAMPLE_DURATION_MS, sourceId)
            val stepReading = adapter.readStepCounter(sourceId)
            if (stepReading != null) readings += stepReading
            readings
        } catch (_: Exception) {
            emptyList()
        }
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
    }
}
