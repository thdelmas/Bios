package com.bios.app.ingest

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import com.bios.app.model.SleepStage
import java.time.Instant

/**
 * Reads health data from Gadgetbridge's ContentProvider.
 *
 * Gadgetbridge is the open-source wearable companion app for degoogled Android,
 * supporting 40+ devices (Mi Band, PineTime, Amazfit, Fossil, Casio, etc.).
 * It stores data in a local SQLite database and exposes it via ContentProvider.
 *
 * This adapter gives Bios access to wearable data on LETHE and other degoogled
 * ROMs where Health Connect may not be available.
 */
class GadgetbridgeAdapter(private val context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    val isAvailable: Boolean
        get() = try {
            val cursor = resolver.query(
                ACTIVITY_URI, null, null, null, null
            )
            cursor?.close()
            cursor != null
        } catch (_: Exception) {
            false
        }

    /**
     * Fetch all available readings from Gadgetbridge for the given time range.
     */
    suspend fun fetchReadings(
        startTime: Instant,
        endTime: Instant,
        sourceId: String
    ): List<MetricReading> {
        val readings = mutableListOf<MetricReading>()
        readings += fetchActivitySamples(startTime, endTime, sourceId)
        readings += fetchSleepSamples(startTime, endTime, sourceId)
        return readings
    }

    /**
     * Fetch activity samples (HR, steps, intensity) from Gadgetbridge.
     *
     * Gadgetbridge stores activity data as periodic samples with:
     * - timestamp (Unix seconds)
     * - heart_rate (bpm, 0 or 255 = invalid)
     * - steps (count in sample interval)
     * - raw_intensity (device-specific)
     */
    private fun fetchActivitySamples(
        startTime: Instant,
        endTime: Instant,
        sourceId: String
    ): List<MetricReading> {
        val readings = mutableListOf<MetricReading>()
        val startSec = startTime.epochSecond
        val endSec = endTime.epochSecond

        val cursor: Cursor? = try {
            resolver.query(
                ACTIVITY_URI,
                arrayOf("timestamp", "heart_rate", "steps", "raw_intensity"),
                "timestamp >= ? AND timestamp <= ?",
                arrayOf(startSec.toString(), endSec.toString()),
                "timestamp ASC"
            )
        } catch (_: Exception) {
            null
        }

        cursor?.use {
            val tsIdx = it.getColumnIndex("timestamp")
            val hrIdx = it.getColumnIndex("heart_rate")
            val stepsIdx = it.getColumnIndex("steps")

            while (it.moveToNext()) {
                val timestamp = it.getLong(tsIdx) * 1000 // Convert to millis

                // Heart rate (0 and 255 are invalid markers in Gadgetbridge)
                if (hrIdx >= 0) {
                    val hr = it.getInt(hrIdx)
                    if (hr > 0 && hr < 255) {
                        readings += MetricReading(
                            metricType = MetricType.HEART_RATE.key,
                            value = hr.toDouble(),
                            timestamp = timestamp,
                            sourceId = sourceId,
                            confidence = ConfidenceTier.MEDIUM.level
                        )
                    }
                }

                // Steps
                if (stepsIdx >= 0) {
                    val steps = it.getInt(stepsIdx)
                    if (steps > 0) {
                        readings += MetricReading(
                            metricType = MetricType.STEPS.key,
                            value = steps.toDouble(),
                            timestamp = timestamp,
                            sourceId = sourceId,
                            confidence = ConfidenceTier.MEDIUM.level
                        )
                    }
                }
            }
        }

        return readings
    }

    /**
     * Fetch sleep samples from Gadgetbridge.
     *
     * Gadgetbridge encodes sleep as activity samples with raw_kind values:
     * - 1 = light sleep
     * - 2 = deep sleep
     * - 4 = not worn / awake
     * - 5 = REM sleep (device-dependent)
     */
    private fun fetchSleepSamples(
        startTime: Instant,
        endTime: Instant,
        sourceId: String
    ): List<MetricReading> {
        val readings = mutableListOf<MetricReading>()
        val startSec = startTime.epochSecond
        val endSec = endTime.epochSecond

        val cursor: Cursor? = try {
            resolver.query(
                ACTIVITY_URI,
                arrayOf("timestamp", "raw_kind"),
                "timestamp >= ? AND timestamp <= ? AND raw_kind IN (1, 2, 4, 5)",
                arrayOf(startSec.toString(), endSec.toString()),
                "timestamp ASC"
            )
        } catch (_: Exception) {
            null
        }

        cursor?.use {
            val tsIdx = it.getColumnIndex("timestamp")
            val kindIdx = it.getColumnIndex("raw_kind")

            while (it.moveToNext()) {
                val timestamp = it.getLong(tsIdx) * 1000
                val kind = it.getInt(kindIdx)

                val stage = when (kind) {
                    1 -> SleepStage.LIGHT
                    2 -> SleepStage.DEEP
                    4 -> SleepStage.AWAKE
                    5 -> SleepStage.REM
                    else -> continue
                }

                readings += MetricReading(
                    metricType = MetricType.SLEEP_STAGE.key,
                    value = stage.value.toDouble(),
                    timestamp = timestamp,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }
        }

        return readings
    }

    companion object {
        private val ACTIVITY_URI: Uri = Uri.parse(
            "content://nodomain.freeyourgadget.gadgetbridge.database/activity"
        )
    }
}
