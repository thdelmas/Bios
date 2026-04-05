package com.bios.app.ingest

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import com.bios.app.model.SleepStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Fetches health data from the Polar AccessLink API.
 *
 * Polar provides clinical-grade ECG (H10) and optical HR (Verity Sense),
 * making it valuable for high-confidence HRV and HR data.
 *
 * Supported metrics: HR, HRV (RMSSD), resting HR, sleep stages, steps, calories.
 * Token stored in EncryptedSharedPreferences via [ApiTokenStore].
 * Destroyed on LETHE wipe signals via [DataDestroyer].
 *
 * API docs: https://www.polar.com/accesslink-api/
 */
class PolarApiAdapter(
    private val getToken: () -> String?,
    private val hasToken: () -> Boolean
) {
    constructor(tokenStore: ApiTokenStore) : this(
        getToken = { tokenStore.getToken(PROVIDER_KEY) },
        hasToken = { tokenStore.hasToken(PROVIDER_KEY) }
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val isConnected: Boolean get() = hasToken()

    suspend fun fetchReadings(
        startTime: Instant,
        endTime: Instant,
        sourceId: String
    ): List<MetricReading> {
        val token = getToken() ?: return emptyList()
        val readings = mutableListOf<MetricReading>()

        readings += fetchDailyActivity(token, startTime, endTime, sourceId)
        readings += fetchSleep(token, startTime, endTime, sourceId)
        readings += fetchExercises(token, startTime, endTime, sourceId)

        return readings
    }

    private suspend fun fetchDailyActivity(
        token: String, start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val date = start.atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT)
        val json = apiGet(token, "users/daily-activity/$date") ?: return emptyList()
        val readings = mutableListOf<MetricReading>()
        val timestamp = start.toEpochMilli()

        val steps = json.optInt("active-steps", 0)
        if (steps > 0) {
            readings += MetricReading(
                metricType = MetricType.STEPS.key,
                value = steps.toDouble(),
                timestamp = timestamp,
                sourceId = sourceId,
                confidence = ConfidenceTier.HIGH.level
            )
        }

        val calories = json.optInt("active-calories", 0)
        if (calories > 0) {
            readings += MetricReading(
                metricType = MetricType.ACTIVE_CALORIES.key,
                value = calories.toDouble(),
                timestamp = timestamp,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }

        return readings
    }

    private suspend fun fetchSleep(
        token: String, start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val date = start.atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT)
        val json = apiGet(token, "users/sleep-data/$date") ?: return emptyList()
        val readings = mutableListOf<MetricReading>()
        val timestamp = start.toEpochMilli()

        // Sleep duration
        val durationSec = json.optInt("sleep_duration", 0)
        if (durationSec > 0) {
            readings += MetricReading(
                metricType = MetricType.SLEEP_DURATION.key,
                value = durationSec.toDouble(),
                timestamp = timestamp,
                durationSec = durationSec,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }

        // Sleep stages (Polar Nightly Recharge)
        val hypnogram = json.optJSONObject("hypnogram")
        if (hypnogram != null) {
            val lightSec = hypnogram.optInt("light_sleep", 0)
            val deepSec = hypnogram.optInt("deep_sleep", 0)
            val remSec = hypnogram.optInt("rem_sleep", 0)
            val awakeSec = hypnogram.optInt("awake", 0)

            if (lightSec > 0) readings += sleepStageReading(SleepStage.LIGHT, lightSec, timestamp, sourceId)
            if (deepSec > 0) readings += sleepStageReading(SleepStage.DEEP, deepSec, timestamp, sourceId)
            if (remSec > 0) readings += sleepStageReading(SleepStage.REM, remSec, timestamp, sourceId)
            if (awakeSec > 0) readings += sleepStageReading(SleepStage.AWAKE, awakeSec, timestamp, sourceId)
        }

        // Nightly Recharge HRV (RMSSD) — clinical-grade on H10
        val nightlyRecharge = json.optJSONObject("nightly_recharge")
        if (nightlyRecharge != null) {
            val hrv = nightlyRecharge.optDouble("hrv_rmssd", Double.NaN)
            if (!hrv.isNaN()) {
                readings += MetricReading(
                    metricType = MetricType.HEART_RATE_VARIABILITY.key,
                    value = hrv,
                    timestamp = timestamp,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.HIGH.level
                )
            }

            val restingHr = nightlyRecharge.optDouble("resting_heart_rate", Double.NaN)
            if (!restingHr.isNaN()) {
                readings += MetricReading(
                    metricType = MetricType.RESTING_HEART_RATE.key,
                    value = restingHr,
                    timestamp = timestamp,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.HIGH.level
                )
            }
        }

        return readings
    }

    private suspend fun fetchExercises(
        token: String, start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val json = apiGet(token, "users/exercise-transactions") ?: return emptyList()
        val exercises = json.optJSONArray("exercises") ?: return emptyList()
        val readings = mutableListOf<MetricReading>()

        for (i in 0 until exercises.length()) {
            val exercise = exercises.getJSONObject(i)
            val timestamp = parseTimestamp(exercise.optString("start-time", ""))
            if (timestamp == 0L) continue

            val avgHr = exercise.optDouble("heart-rate-average", Double.NaN)
            if (!avgHr.isNaN()) {
                readings += MetricReading(
                    metricType = MetricType.HEART_RATE.key,
                    value = avgHr,
                    timestamp = timestamp,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.HIGH.level
                )
            }

            val calories = exercise.optInt("calories", 0)
            if (calories > 0) {
                readings += MetricReading(
                    metricType = MetricType.ACTIVE_CALORIES.key,
                    value = calories.toDouble(),
                    timestamp = timestamp,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }

            val durationMs = exercise.optLong("duration", 0)
            if (durationMs > 0) {
                readings += MetricReading(
                    metricType = MetricType.ACTIVE_MINUTES.key,
                    value = (durationMs / 60000.0),
                    timestamp = timestamp,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.HIGH.level
                )
            }
        }

        return readings
    }

    private fun sleepStageReading(
        stage: SleepStage, durationSec: Int, timestamp: Long, sourceId: String
    ) = MetricReading(
        metricType = MetricType.SLEEP_STAGE.key,
        value = stage.value.toDouble(),
        timestamp = timestamp,
        durationSec = durationSec,
        sourceId = sourceId,
        confidence = ConfidenceTier.MEDIUM.level
    )

    private suspend fun apiGet(token: String, endpoint: String): JSONObject? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL/$endpoint")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            JSONObject(body)
        }

    private fun parseTimestamp(isoString: String): Long {
        if (isoString.isBlank()) return 0L
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    companion object {
        internal const val BASE_URL = "https://www.polaraccesslink.com/v3"
        const val PROVIDER_KEY = "polar"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
