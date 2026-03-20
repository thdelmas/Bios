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
 * Orchestrates data ingestion from Health Connect, handles deduplication,
 * and triggers downstream processing.
 */
class IngestManager(
    private val healthConnect: HealthConnectAdapter,
    private val db: BiosDatabase
) {
    private val readingDao = db.metricReadingDao()
    private val sourceDao = db.dataSourceDao()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _dataAgeDays = MutableStateFlow(0)
    val dataAgeDays: StateFlow<Int> = _dataAgeDays

    private var healthConnectSourceId: String? = null

    // MARK: - Setup

    suspend fun setup() {
        val sourceId = getOrCreateHealthConnectSource()
        healthConnectSourceId = sourceId
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
        val sourceId = healthConnectSourceId ?: return
        if (_isSyncing.value) return
        _isSyncing.value = true

        try {
            val end = Instant.now()
            val start = end.minus(24, ChronoUnit.HOURS)

            val readings = healthConnect.fetchReadings(start, end, sourceId)
            val deduped = deduplicate(readings)
            readingDao.insertAll(deduped)

            _lastSyncTime.value = System.currentTimeMillis()
            updateDataAge()
        } finally {
            _isSyncing.value = false
        }
    }

    /** Sync the last 30 days (initial setup). */
    suspend fun syncHistoricalData() {
        val sourceId = healthConnectSourceId ?: return
        _isSyncing.value = true

        try {
            val end = Instant.now()
            val start = end.minus(30, ChronoUnit.DAYS)

            // Fetch in daily chunks to avoid memory pressure
            var current = start
            while (current.isBefore(end)) {
                val chunkEnd = minOf(current.plus(1, ChronoUnit.DAYS), end)
                val readings = healthConnect.fetchReadings(current, chunkEnd, sourceId)
                val deduped = deduplicate(readings)
                readingDao.insertAll(deduped)
                current = chunkEnd
            }

            _lastSyncTime.value = System.currentTimeMillis()
            updateDataAge()
        } finally {
            _isSyncing.value = false
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
}
