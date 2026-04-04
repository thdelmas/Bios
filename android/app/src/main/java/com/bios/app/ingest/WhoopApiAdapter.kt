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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Fetches health data from the WHOOP v2 REST API.
 *
 * WHOOP provides: HR (sampled 26x/sec), HRV, skin temp, SpO2,
 * sleep stages, strain score, recovery score.
 *
 * Token stored in EncryptedSharedPreferences via [ApiTokenStore].
 * Destroyed on LETHE wipe signals via [DataDestroyer].
 */
class WhoopApiAdapter(
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

        readings += fetchRecovery(token, startTime, endTime, sourceId)
        readings += fetchSleep(token, startTime, endTime, sourceId)
        readings += fetchWorkouts(token, startTime, endTime, sourceId)

        return readings
    }

    private suspend fun fetchRecovery(
        token: String, start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val json = apiGet(token, "recovery", start, end) ?: return emptyList()
        val records = json.optJSONArray("records") ?: return emptyList()
        val readings = mutableListOf<MetricReading>()

        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            val score = record.optJSONObject("score") ?: continue
            val timestamp = parseTimestamp(record.getString("created_at"))

            val restingHr = score.optDouble("resting_heart_rate", Double.NaN)
            if (!restingHr.isNaN()) {
                readings += MetricReading(
                    metricType = MetricType.RESTING_HEART_RATE.key,
                    value = restingHr,
                    timestamp = timestamp,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }

            val hrv = score.optDouble("hrv_rmssd_milli", Double.NaN)
            if (!hrv.isNaN()) {
                readings += MetricReading(
                    metricType = MetricType.HEART_RATE_VARIABILITY.key,
                    value = hrv,
                    timestamp = timestamp,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }

            val spo2 = score.optDouble("spo2_percentage", Double.NaN)
            if (!spo2.isNaN()) {
                readings += MetricReading(
                    metricType = MetricType.BLOOD_OXYGEN.key,
                    value = spo2,
                    timestamp = timestamp,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }

            val skinTemp = score.optDouble("skin_temp_celsius", Double.NaN)
            if (!skinTemp.isNaN()) {
                readings += MetricReading(
                    metricType = MetricType.SKIN_TEMPERATURE.key,
                    value = skinTemp,
                    timestamp = timestamp,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }

            val recoveryScore = score.optInt("recovery_score", -1)
            if (recoveryScore >= 0) {
                readings += MetricReading(
                    metricType = MetricType.RECOVERY_SCORE.key,
                    value = recoveryScore.toDouble(),
                    timestamp = timestamp,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.VENDOR_DERIVED.level
                )
            }
        }

        return readings
    }

    private suspend fun fetchSleep(
        token: String, start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val json = apiGet(token, "activity/sleep", start, end) ?: return emptyList()
        val records = json.optJSONArray("records") ?: return emptyList()
        val readings = mutableListOf<MetricReading>()

        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            val score = record.optJSONObject("score") ?: continue
            val timestamp = parseTimestamp(record.getString("created_at"))

            val totalSleep = score.optInt("total_in_bed_time_milli", 0) / 1000
            if (totalSleep > 0) {
                readings += MetricReading(
                    metricType = MetricType.SLEEP_DURATION.key,
                    value = totalSleep.toDouble(),
                    timestamp = timestamp,
                    durationSec = totalSleep,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }

            // Sleep stages from stage_summary
            val stages = score.optJSONObject("stage_summary")
            if (stages != null) {
                val lightMs = stages.optInt("total_light_sleep_time_milli", 0)
                val deepMs = stages.optInt("total_slow_wave_sleep_time_milli", 0)
                val remMs = stages.optInt("total_rem_sleep_time_milli", 0)
                val awakeMs = stages.optInt("total_awake_time_milli", 0)

                if (lightMs > 0) readings += sleepStageReading(SleepStage.LIGHT, lightMs / 1000, timestamp, sourceId)
                if (deepMs > 0) readings += sleepStageReading(SleepStage.DEEP, deepMs / 1000, timestamp, sourceId)
                if (remMs > 0) readings += sleepStageReading(SleepStage.REM, remMs / 1000, timestamp, sourceId)
                if (awakeMs > 0) readings += sleepStageReading(SleepStage.AWAKE, awakeMs / 1000, timestamp, sourceId)
            }
        }

        return readings
    }

    private suspend fun fetchWorkouts(
        token: String, start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val json = apiGet(token, "activity/workout", start, end) ?: return emptyList()
        val records = json.optJSONArray("records") ?: return emptyList()
        val readings = mutableListOf<MetricReading>()

        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            val score = record.optJSONObject("score") ?: continue
            val timestamp = parseTimestamp(record.getString("created_at"))

            val strain = score.optDouble("strain", Double.NaN)
            if (!strain.isNaN()) {
                readings += MetricReading(
                    metricType = MetricType.ACTIVE_CALORIES.key,
                    value = score.optDouble("kilojoule", 0.0) / 4.184,
                    timestamp = timestamp,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.VENDOR_DERIVED.level
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

    private suspend fun apiGet(
        token: String, endpoint: String, start: Instant, end: Instant
    ): JSONObject? = withContext(Dispatchers.IO) {
        val startStr = start.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val endStr = end.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val url = "$BASE_URL/$endpoint?start=$startStr&end=$endStr"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null
        val body = response.body?.string() ?: return@withContext null
        JSONObject(body)
    }

    private fun parseTimestamp(isoString: String): Long =
        Instant.parse(isoString).toEpochMilli()

    companion object {
        internal const val BASE_URL = "https://api.prod.whoop.com/developer/v1"
        const val PROVIDER_KEY = "whoop"
    }
}
