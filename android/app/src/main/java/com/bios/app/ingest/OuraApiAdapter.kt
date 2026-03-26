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
 * Fetches health data from the Oura Ring v2 REST API and converts it
 * to Bios unified MetricReadings.
 *
 * Oura provides: heart rate, HRV, resting HR, SpO2, skin temperature
 * deviation, sleep stages, steps, and active calories.
 */
class OuraApiAdapter(
    private val getToken: () -> String?,
    private val hasToken: () -> Boolean
) {
    constructor(tokenStore: OuraTokenStore) : this(
        getToken = { tokenStore.getToken() },
        hasToken = { tokenStore.hasToken() }
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
        val startDate = formatDate(startTime)
        val endDate = formatDate(endTime)

        val readings = mutableListOf<MetricReading>()

        readings += fetchHeartRate(token, startDate, endDate, sourceId)
        readings += fetchSleep(token, startDate, endDate, sourceId)
        readings += fetchDailyActivity(token, startDate, endDate, sourceId)
        readings += fetchDailyReadiness(token, startDate, endDate, sourceId)

        return readings
    }

    private suspend fun fetchHeartRate(
        token: String, startDate: String, endDate: String, sourceId: String
    ): List<MetricReading> {
        val json = apiGet(token, "heartrate", startDate, endDate) ?: return emptyList()
        val data = json.optJSONArray("data") ?: return emptyList()

        return (0 until data.length()).map { i ->
            val item = data.getJSONObject(i)
            MetricReading(
                metricType = MetricType.HEART_RATE.key,
                value = item.getDouble("bpm"),
                timestamp = parseTimestamp(item.getString("timestamp")),
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }
    }

    private suspend fun fetchSleep(
        token: String, startDate: String, endDate: String, sourceId: String
    ): List<MetricReading> {
        val json = apiGet(token, "sleep", startDate, endDate) ?: return emptyList()
        val data = json.optJSONArray("data") ?: return emptyList()

        val readings = mutableListOf<MetricReading>()

        for (i in 0 until data.length()) {
            val session = data.getJSONObject(i)
            val bedtimeStart = parseTimestamp(session.getString("bedtime_start"))

            // Total sleep duration
            val totalSleep = session.optInt("total_sleep_duration", 0)
            if (totalSleep > 0) {
                readings += MetricReading(
                    metricType = MetricType.SLEEP_DURATION.key,
                    value = totalSleep.toDouble(),
                    timestamp = bedtimeStart,
                    durationSec = totalSleep,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }

            // HRV from sleep
            val avgHrv = session.optDouble("average_hrv", Double.NaN)
            if (!avgHrv.isNaN()) {
                readings += MetricReading(
                    metricType = MetricType.HEART_RATE_VARIABILITY.key,
                    value = avgHrv,
                    timestamp = bedtimeStart,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }

            // Resting HR from sleep
            val lowestHr = session.optDouble("lowest_heart_rate", Double.NaN)
            if (!lowestHr.isNaN()) {
                readings += MetricReading(
                    metricType = MetricType.RESTING_HEART_RATE.key,
                    value = lowestHr,
                    timestamp = bedtimeStart,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }

            // Temperature deviation
            val tempDev = session.optDouble("temperature_deviation", Double.NaN)
            if (!tempDev.isNaN()) {
                readings += MetricReading(
                    metricType = MetricType.SKIN_TEMPERATURE_DEVIATION.key,
                    value = tempDev,
                    timestamp = bedtimeStart,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }

            // Sleep stages from sleep_phase_5_min encoding
            val phases = session.optString("sleep_phase_5_min", "")
            readings += parseSleepStages(phases, bedtimeStart, sourceId)
        }

        return readings
    }

    private suspend fun fetchDailyActivity(
        token: String, startDate: String, endDate: String, sourceId: String
    ): List<MetricReading> {
        val json = apiGet(token, "daily_activity", startDate, endDate) ?: return emptyList()
        val data = json.optJSONArray("data") ?: return emptyList()

        val readings = mutableListOf<MetricReading>()

        for (i in 0 until data.length()) {
            val day = data.getJSONObject(i)
            val dayTimestamp = parseDayTimestamp(day.getString("day"))

            val steps = day.optInt("steps", 0)
            if (steps > 0) {
                readings += MetricReading(
                    metricType = MetricType.STEPS.key,
                    value = steps.toDouble(),
                    timestamp = dayTimestamp,
                    durationSec = 86400,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }

            val calories = day.optInt("active_calories", 0)
            if (calories > 0) {
                readings += MetricReading(
                    metricType = MetricType.ACTIVE_CALORIES.key,
                    value = calories.toDouble(),
                    timestamp = dayTimestamp,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }
        }

        return readings
    }

    private suspend fun fetchDailyReadiness(
        token: String, startDate: String, endDate: String, sourceId: String
    ): List<MetricReading> {
        val json = apiGet(token, "daily_readiness", startDate, endDate) ?: return emptyList()
        val data = json.optJSONArray("data") ?: return emptyList()

        return (0 until data.length()).mapNotNull { i ->
            val day = data.getJSONObject(i)
            val score = day.optInt("score", -1)
            if (score < 0) return@mapNotNull null

            MetricReading(
                metricType = MetricType.RECOVERY_SCORE.key,
                value = score.toDouble(),
                timestamp = parseDayTimestamp(day.getString("day")),
                sourceId = sourceId,
                confidence = ConfidenceTier.VENDOR_DERIVED.level
            )
        }
    }

    // MARK: - Sleep stage parsing

    /**
     * Oura encodes sleep stages as a string of digits (5-min intervals):
     * 1=deep, 2=light, 3=REM, 4=awake
     */
    internal fun parseSleepStages(
        phases: String,
        bedtimeStart: Long,
        sourceId: String
    ): List<MetricReading> {
        if (phases.isEmpty()) return emptyList()

        val readings = mutableListOf<MetricReading>()
        var currentStage: SleepStage? = null
        var stageStart = bedtimeStart
        var stageCount = 0

        for (ch in phases) {
            val stage = when (ch) {
                '1' -> SleepStage.DEEP
                '2' -> SleepStage.LIGHT
                '3' -> SleepStage.REM
                '4' -> SleepStage.AWAKE
                else -> null
            } ?: continue

            if (stage == currentStage) {
                stageCount++
            } else {
                if (currentStage != null && stageCount > 0) {
                    readings += MetricReading(
                        metricType = MetricType.SLEEP_STAGE.key,
                        value = currentStage.value.toDouble(),
                        timestamp = stageStart,
                        durationSec = stageCount * 300,
                        sourceId = sourceId,
                        confidence = ConfidenceTier.MEDIUM.level
                    )
                }
                currentStage = stage
                stageStart = bedtimeStart + (phases.indexOf(ch) * 300_000L)
                stageCount = 1
            }
        }

        // Flush last stage
        if (currentStage != null && stageCount > 0) {
            readings += MetricReading(
                metricType = MetricType.SLEEP_STAGE.key,
                value = currentStage.value.toDouble(),
                timestamp = stageStart,
                durationSec = stageCount * 300,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }

        return readings
    }

    // MARK: - HTTP

    private suspend fun apiGet(
        token: String, endpoint: String, startDate: String, endDate: String
    ): JSONObject? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/$endpoint?start_date=$startDate&end_date=$endDate"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null
        val body = response.body?.string() ?: return@withContext null
        JSONObject(body)
    }

    // MARK: - Date helpers

    private fun formatDate(instant: Instant): String =
        instant.atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

    internal fun parseTimestamp(isoString: String): Long =
        Instant.parse(isoString).toEpochMilli()

    private fun parseDayTimestamp(dayString: String): Long =
        LocalDate.parse(dayString)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    companion object {
        internal const val BASE_URL = "https://api.ouraring.com/v2/usercollection"
    }
}
