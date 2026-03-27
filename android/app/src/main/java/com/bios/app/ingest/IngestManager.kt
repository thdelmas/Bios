package com.bios.app.ingest

import com.bios.app.data.BiosDatabase
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.DataSource
import com.bios.app.model.MetricReading
import com.bios.app.model.SensorType
import com.bios.app.model.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Calendar

/**
 * Orchestrates data ingestion from Health Connect and direct API adapters
 * (Oura, etc.), handles deduplication, and triggers downstream processing.
 */
class IngestManager(
    private val healthConnect: HealthConnectAdapter,
    private val db: BiosDatabase,
    private val ouraAdapter: OuraApiAdapter? = null,
    private val phoneSensorAdapter: PhoneSensorAdapter? = null
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

    private var healthConnectSourceId: String? = null
    private var ouraSourceId: String? = null
    private var phoneSensorSourceId: String? = null

    // MARK: - Setup

    suspend fun setup() {
        val sourceId = getOrCreateHealthConnectSource()
        healthConnectSourceId = sourceId

        if (ouraAdapter?.isConnected == true) {
            ouraSourceId = getOrCreateOuraSource()
        }

        if (phoneSensorAdapter?.hasAccelerometer == true ||
            phoneSensorAdapter?.hasStepCounter == true
        ) {
            phoneSensorSourceId = getOrCreatePhoneSensorSource()
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
        val hcSourceId = healthConnectSourceId ?: return
        if (_isSyncing.value) return
        _isSyncing.value = true

        try {
            val end = Instant.now()
            val start = end.minus(24, ChronoUnit.HOURS)

            val allReadings = mutableListOf<MetricReading>()
            allReadings += healthConnect.fetchReadings(start, end, hcSourceId)
            allReadings += fetchOuraReadings(start, end)
            allReadings += fetchPhoneSensorReadings()

            val deduped = deduplicate(allReadings)
            readingDao.insertAll(deduped)

            _lastSyncTime.value = System.currentTimeMillis()
            updateDataAge()
        } finally {
            _isSyncing.value = false
        }
    }

    /** Sync the last 30 days (initial setup). */
    suspend fun syncHistoricalData() {
        val hcSourceId = healthConnectSourceId ?: return
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
                val allReadings = mutableListOf<MetricReading>()
                allReadings += healthConnect.fetchReadings(current, chunkEnd, hcSourceId)
                allReadings += fetchOuraReadings(current, chunkEnd)

                val deduped = deduplicate(allReadings)
                readingDao.insertAll(deduped)
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

    private suspend fun getOrCreateHealthConnectSource(): String {
        val existing = sourceDao.findByType(SourceType.HEALTH_CONNECT.key)
        if (existing != null) return existing.id

        val source = DataSource(
            sourceType = SourceType.HEALTH_CONNECT.key,
            deviceName = "Android Wearable",
            sensorType = SensorType.OPTICAL_HR.name
        )
        sourceDao.insert(source)
        return source.id
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
            readings += adapter.sampleAccelerometer(PHONE_SAMPLE_DURATION_MS, sourceId)
            val stepReading = adapter.readStepCounter(sourceId)
            if (stepReading != null) readings += stepReading
            readings
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun getOrCreatePhoneSensorSource(): String {
        val existing = sourceDao.findByType(SourceType.PHONE_SENSOR.key)
        if (existing != null) return existing.id

        val source = DataSource(
            sourceType = SourceType.PHONE_SENSOR.key,
            deviceName = "Phone Sensors",
            sensorType = SensorType.ACCELEROMETER.name
        )
        sourceDao.insert(source)
        return source.id
    }

    private suspend fun getOrCreateOuraSource(): String {
        val existing = sourceDao.findByType(SourceType.OURA_API.key)
        if (existing != null) return existing.id

        val source = DataSource(
            sourceType = SourceType.OURA_API.key,
            deviceName = "Oura Ring",
            sensorType = SensorType.OPTICAL_HR.name
        )
        sourceDao.insert(source)
        return source.id
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

    companion object {
        private const val PHONE_SAMPLE_DURATION_MS = 10_000L // 10 seconds
    }
}
