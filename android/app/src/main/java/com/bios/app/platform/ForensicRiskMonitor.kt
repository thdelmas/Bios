package com.bios.app.platform

import android.content.Context
import com.bios.app.data.BiosDatabase
import com.bios.app.data.ReproductiveDatabase
import com.bios.app.data.dao.AnomalyDao
import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.ingest.SyncWorker
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Monitors the owner's data footprint and surfaces forensic risk information.
 *
 * The owner should know what they're accumulating. On a device that could be seized,
 * 90 days of health data is a liability. This class provides the data needed to
 * make informed decisions about retention and wiping.
 *
 * Aligned with LETHE's principle: every action visible and explainable.
 */
class ForensicRiskMonitor(
    private val context: Context,
    private val db: BiosDatabase
) {
    /**
     * Compute a snapshot of the owner's current data footprint.
     */
    suspend fun getDataFootprint(): DataFootprint {
        val readingCount = db.metricReadingDao().countAll()
        val oldestTimestamp = db.metricReadingDao().oldestTimestamp()
        val dbFile = context.getDatabasePath("bios.db")
        val dbSizeBytes = if (dbFile.exists()) dbFile.length() else 0L

        val dataAgeDays = if (oldestTimestamp != null) {
            ChronoUnit.DAYS.between(
                Instant.ofEpochMilli(oldestTimestamp),
                Instant.now()
            ).toInt()
        } else 0

        val sources = db.dataSourceDao().getAll()

        val hasReproductiveData = ReproductiveDatabase.hasData(context)

        val letheCompat = LetheCompat.create(context)
        val burnerModeActive = letheCompat.isBurnerModeActive()
        val dmsArmed = letheCompat.isDeadManSwitchArmed()

        return DataFootprint(
            totalReadings = readingCount,
            dataAgeDays = dataAgeDays,
            databaseSizeBytes = dbSizeBytes,
            connectedSourceCount = sources.size,
            retentionDays = SyncWorker.RETENTION_DAYS,
            hasReproductiveData = hasReproductiveData,
            isLethe = PlatformDetector.isLethe(context),
            burnerModeActive = burnerModeActive,
            deadManSwitchArmed = dmsArmed
        )
    }

    /**
     * Delete readings (and anomalies) from the last [days] days, keeping
     * older data so baselines and long-term trends survive. Inverse of
     * retention pruning: it removes the newest window, not the oldest.
     */
    suspend fun wipeRecentData(days: Int) =
        wipeRecentWindow(days, db.metricReadingDao(), db.anomalyDao())

    /** Owner-facing alias retained for the privacy-dashboard call site. */
    suspend fun quickWipe(days: Int) = wipeRecentData(days)
}

/**
 * DAO-only wipe so the seed/wipe/survive contract from #313 can be exercised
 * without spinning up Room. Tests inject fake DAOs and a fixed [nowMillis].
 */
internal suspend fun wipeRecentWindow(
    days: Int,
    readings: MetricReadingDao,
    anomalies: AnomalyDao,
    nowMillis: Long = Instant.now().toEpochMilli(),
) {
    val cutoff = Instant.ofEpochMilli(nowMillis)
        .minus(days.toLong(), ChronoUnit.DAYS)
        .toEpochMilli()
    readings.deleteAfter(cutoff)
    anomalies.deleteAfter(cutoff)
}

data class DataFootprint(
    val totalReadings: Int,
    val dataAgeDays: Int,
    val databaseSizeBytes: Long,
    val connectedSourceCount: Int,
    val retentionDays: Int,
    val hasReproductiveData: Boolean,
    val isLethe: Boolean,
    val burnerModeActive: Boolean,
    val deadManSwitchArmed: Boolean
) {
    val databaseSizeMb: Double
        get() = databaseSizeBytes / (1024.0 * 1024.0)

    /** True if retention exceeds 30 days and burner mode is off — worth surfacing to the owner. */
    val hasForensicRisk: Boolean
        get() = dataAgeDays > 30 && !burnerModeActive

    /** True if on LETHE with burner mode off — the owner may not realize data is accumulating. */
    val shouldWarnBurnerModeOff: Boolean
        get() = isLethe && !burnerModeActive && dataAgeDays > 7
}
