package com.bios.app.ingest

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import com.bios.app.model.SleepStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Fetches health data from the Garmin Connect API (OAuth 1.0a).
 *
 * Garmin provides: HR, HRV (if available), sleep, steps, SpO2,
 * stress, body battery, respiration rate.
 *
 * Rate limits are conservative — schedule sync no more than every 15 min.
 */
class GarminApiAdapter(
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

        readings += fetchDailies(token, startTime, endTime, sourceId)
        readings += fetchSleep(token, startTime, endTime, sourceId)
        readings += fetchHeartRate(token, startTime, endTime, sourceId)

        return readings
    }

    private suspend fun fetchDailies(
        token: String, start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val json = apiGet(token, "dailies", start, end) ?: return emptyList()
        val readings = mutableListOf<MetricReading>()

        val steps = json.optInt("totalSteps", 0)
        if (steps > 0) {
            readings += MetricReading(
                metricType = MetricType.STEPS.key,
                value = steps.toDouble(),
                timestamp = start.toEpochMilli(),
                durationSec = 86400,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }

        val calories = json.optInt("activeKilocalories", 0)
        if (calories > 0) {
            readings += MetricReading(
                metricType = MetricType.ACTIVE_CALORIES.key,
                value = calories.toDouble(),
                timestamp = start.toEpochMilli(),
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }

        val restingHr = json.optInt("restingHeartRate", 0)
        if (restingHr > 0) {
            readings += MetricReading(
                metricType = MetricType.RESTING_HEART_RATE.key,
                value = restingHr.toDouble(),
                timestamp = start.toEpochMilli(),
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }

        val spo2 = json.optDouble("averageSpo2", Double.NaN)
        if (!spo2.isNaN() && spo2 > 0) {
            readings += MetricReading(
                metricType = MetricType.BLOOD_OXYGEN.key,
                value = spo2,
                timestamp = start.toEpochMilli(),
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }

        val respRate = json.optDouble("averageRespirationRate", Double.NaN)
        if (!respRate.isNaN() && respRate > 0) {
            readings += MetricReading(
                metricType = MetricType.RESPIRATORY_RATE.key,
                value = respRate,
                timestamp = start.toEpochMilli(),
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }

        return readings
    }

    private suspend fun fetchSleep(
        token: String, start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val json = apiGet(token, "sleep", start, end) ?: return emptyList()
        val readings = mutableListOf<MetricReading>()

        val totalSleepSec = json.optInt("sleepTimeInSeconds", 0)
        if (totalSleepSec > 0) {
            readings += MetricReading(
                metricType = MetricType.SLEEP_DURATION.key,
                value = totalSleepSec.toDouble(),
                timestamp = start.toEpochMilli(),
                durationSec = totalSleepSec,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }

        val deepSec = json.optInt("deepSleepDurationInSeconds", 0)
        val lightSec = json.optInt("lightSleepDurationInSeconds", 0)
        val remSec = json.optInt("remSleepInSeconds", 0)
        val awakeSec = json.optInt("awakeDurationInSeconds", 0)

        val ts = start.toEpochMilli()
        if (deepSec > 0) readings += sleepStage(SleepStage.DEEP, deepSec, ts, sourceId)
        if (lightSec > 0) readings += sleepStage(SleepStage.LIGHT, lightSec, ts, sourceId)
        if (remSec > 0) readings += sleepStage(SleepStage.REM, remSec, ts, sourceId)
        if (awakeSec > 0) readings += sleepStage(SleepStage.AWAKE, awakeSec, ts, sourceId)

        return readings
    }

    private suspend fun fetchHeartRate(
        token: String, start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val json = apiGet(token, "heartRate", start, end) ?: return emptyList()
        val samples = json.optJSONArray("heartRateValues") ?: return emptyList()

        return (0 until samples.length()).mapNotNull { i ->
            val pair = samples.optJSONArray(i) ?: return@mapNotNull null
            val timestamp = pair.optLong(0)
            val hr = pair.optInt(1)
            if (hr <= 0 || hr > 250) return@mapNotNull null

            MetricReading(
                metricType = MetricType.HEART_RATE.key,
                value = hr.toDouble(),
                timestamp = timestamp,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        }
    }

    private fun sleepStage(stage: SleepStage, durationSec: Int, timestamp: Long, sourceId: String) =
        MetricReading(
            metricType = MetricType.SLEEP_STAGE.key,
            value = stage.value.toDouble(),
            timestamp = timestamp,
            durationSec = durationSec,
            sourceId = sourceId,
            confidence = ConfidenceTier.MEDIUM.level
        )

    private suspend fun apiGet(
        token: String, endpoint: String, start: Instant, end: Instant
    ): JSONObject? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/$endpoint?uploadStartTimeInSeconds=${start.epochSecond}&uploadEndTimeInSeconds=${end.epochSecond}"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null
        val body = response.body?.string() ?: return@withContext null
        JSONObject(body)
    }

    companion object {
        internal const val BASE_URL = "https://apis.garmin.com/wellness-api/rest"
        const val PROVIDER_KEY = "garmin"
    }
}
