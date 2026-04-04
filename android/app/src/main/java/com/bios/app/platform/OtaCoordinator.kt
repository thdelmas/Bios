package com.bios.app.platform

import android.content.Context
import com.bios.app.data.BiosDatabase
import com.bios.app.model.AlertTier
import com.bios.app.model.MetricType
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Coordinates with LETHE's OTA update system to determine reboot safety.
 *
 * LETHE's `lethe-ota-update.sh` queries Bios before rebooting the device.
 * Bios responds based on the owner's current health monitoring state:
 *
 * - SAFE: No active monitoring, no urgent alerts. Reboot freely.
 * - DELAY_SLEEP: Owner is in a sleep tracking window. Delay until wake.
 * - DELAY_MONITORING: Elevated health monitoring in progress (active alerts).
 * - DELAY_SYNC: Health data sync in progress. Brief delay.
 *
 * The owner can always override: "reboot now anyway."
 *
 * Post-OTA: Bios verifies database integrity and re-schedules workers.
 */
class OtaCoordinator(
    private val context: Context,
    private val db: BiosDatabase
) {
    /**
     * Query whether it's safe to reboot now.
     * Called by LETHE via the local health API (/health/reboot-safe).
     */
    suspend fun queryRebootSafety(): RebootDecision {
        // Check 1: Active urgent alerts — don't reboot during a health crisis
        val unacked = db.anomalyDao().fetchUnacknowledged()
        val hasUrgent = unacked.any { it.severity >= AlertTier.ADVISORY.level }
        if (hasUrgent) {
            return RebootDecision(
                safe = false,
                reason = RebootDelay.ELEVATED_MONITORING,
                message = "Active health advisory — monitoring elevated vitals",
                estimatedDelayMinutes = 60
            )
        }

        // Check 2: Sleep window — typical sleep hours (10 PM - 7 AM local)
        val localHour = Instant.now().atZone(ZoneId.systemDefault()).hour
        val isSleepWindow = localHour in 22..23 || localHour in 0..6
        if (isSleepWindow) {
            // Check if we have recent sleep data (actively tracking sleep)
            val sleepStart = System.currentTimeMillis() - 8L * 3600 * 1000
            val sleepReadings = db.metricReadingDao()
                .fetchValues(MetricType.SLEEP_STAGE.key, sleepStart, System.currentTimeMillis())

            if (sleepReadings.isNotEmpty()) {
                val minutesUntilWake = if (localHour < 7) (7 - localHour) * 60 else (24 - localHour + 7) * 60
                return RebootDecision(
                    safe = false,
                    reason = RebootDelay.SLEEP_TRACKING,
                    message = "Sleep tracking in progress",
                    estimatedDelayMinutes = minutesUntilWake
                )
            }
        }

        // Check 3: Recent sync activity — don't interrupt mid-write
        val recentReadingCount = db.metricReadingDao().fetchValues(
            MetricType.HEART_RATE.key,
            System.currentTimeMillis() - 60_000, // Last minute
            System.currentTimeMillis()
        ).size

        if (recentReadingCount > 0) {
            return RebootDecision(
                safe = false,
                reason = RebootDelay.SYNC_IN_PROGRESS,
                message = "Health data sync in progress",
                estimatedDelayMinutes = 5
            )
        }

        // All clear
        return RebootDecision(
            safe = true,
            reason = null,
            message = "Safe to reboot",
            estimatedDelayMinutes = 0
        )
    }

    /**
     * Post-OTA verification: check database integrity after reboot.
     * Called from BiosApplication.onCreate() after detecting a version change.
     */
    suspend fun postOtaVerify(): PostOtaResult {
        return try {
            // Verify database is openable
            val count = db.metricReadingDao().countAll()
            val baselines = db.personalBaselineDao().fetchAll()

            PostOtaResult(
                databaseIntact = true,
                readingCount = count,
                baselineCount = baselines.size,
                message = "Database verified: $count readings, ${baselines.size} baselines"
            )
        } catch (e: Exception) {
            PostOtaResult(
                databaseIntact = false,
                readingCount = 0,
                baselineCount = 0,
                message = "Database verification failed: ${e.message}"
            )
        }
    }
}

data class RebootDecision(
    val safe: Boolean,
    val reason: RebootDelay?,
    val message: String,
    val estimatedDelayMinutes: Int
)

enum class RebootDelay {
    SLEEP_TRACKING,
    ELEVATED_MONITORING,
    SYNC_IN_PROGRESS
}

data class PostOtaResult(
    val databaseIntact: Boolean,
    val readingCount: Int,
    val baselineCount: Int,
    val message: String
)
