package com.bios.app.ingest

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.EventPayloadField
import com.bios.app.model.ExerciseModality
import com.bios.app.model.ExerciseSession
import com.bios.app.model.ExerciseSessionFields
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import com.bios.app.model.SleepStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

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

    private val client = defaultApiClient()

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

        // Garmin exposes vo2Max on the dailies endpoint when the watch has
        // a recent fitness estimate (typically refreshed weekly after an
        // outdoor run). NaN / 0 / absent → skip silently rather than emit
        // a meaningless reading.
        val vo2Max = json.optDouble("vo2Max", Double.NaN)
        if (!vo2Max.isNaN() && vo2Max > 0) {
            readings += MetricReading(
                metricType = MetricType.VO2_MAX.key,
                value = vo2Max,
                timestamp = start.toEpochMilli(),
                sourceId = sourceId,
                confidence = ConfidenceTier.VENDOR_DERIVED.level
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

    /**
     * Reads Garmin `/activities` records in the window and returns each as a
     * composite [ExerciseSession] — parent EXERCISE_SESSION reading +
     * `event_payloads` rows (modality, start_utc, end_utc, optional
     * avg_hr_bpm). Mirrors the HC / Oura / WHOOP emission shape.
     *
     * Garmin Wellness API activity records carry `activityType` as a string
     * (FIT SDK enum names like "RUNNING", "TREADMILL_RUNNING",
     * "STRENGTH_TRAINING"), `startTimeInSeconds`, `durationInSeconds`, and
     * `averageHeartRateInBeatsPerMinute`. RPE isn't in the payload, so the
     * field is omitted.
     */
    suspend fun fetchExerciseSessions(
        startTime: Instant, endTime: Instant, sourceId: String
    ): List<ExerciseSession> {
        val token = getToken() ?: return emptyList()
        val records = apiGetArray(token, "activities", startTime, endTime) ?: return emptyList()

        val sessions = mutableListOf<ExerciseSession>()
        for (i in 0 until records.length()) {
            val item = records.optJSONObject(i) ?: continue
            val startSec = item.optLong("startTimeInSeconds", -1L)
            val durationSec = item.optInt("durationInSeconds", 0)
            if (startSec <= 0 || durationSec <= 0) continue
            val startMs = startSec * 1000L
            val endMs = startMs + durationSec * 1000L

            val reading = MetricReading(
                id = UUID.randomUUID().toString(),
                metricType = MetricType.EXERCISE_SESSION.key,
                value = 1.0,
                timestamp = startMs,
                durationSec = durationSec,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level,
            )

            val payload = mutableListOf(
                EventPayloadField(
                    readingId = reading.id,
                    fieldKey = ExerciseSessionFields.MODALITY,
                    stringValue = mapGarminActivityToModality(item.optString("activityType")),
                ),
                EventPayloadField(
                    readingId = reading.id,
                    fieldKey = ExerciseSessionFields.START_UTC,
                    longValue = startMs,
                ),
                EventPayloadField(
                    readingId = reading.id,
                    fieldKey = ExerciseSessionFields.END_UTC,
                    longValue = endMs,
                ),
            )

            val avgHr = item.optDouble("averageHeartRateInBeatsPerMinute", Double.NaN)
            if (!avgHr.isNaN() && avgHr > 0.0) {
                payload += EventPayloadField(
                    readingId = reading.id,
                    fieldKey = ExerciseSessionFields.AVG_HR_BPM,
                    doubleValue = avgHr,
                )
            }

            sessions += ExerciseSession(reading = reading, payload = payload)
        }
        return sessions
    }

    /**
     * Variant of [apiGet] that decodes the response body as a `JSONArray`
     * rather than an object — Garmin's `/activities` endpoint returns a
     * top-level array of activity records (no wrapping envelope).
     */
    private suspend fun apiGetArray(
        token: String, endpoint: String, start: Instant, end: Instant
    ): JSONArray? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/$endpoint?uploadStartTimeInSeconds=${start.epochSecond}&uploadEndTimeInSeconds=${end.epochSecond}"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null
        val body = response.body?.string() ?: return@withContext null
        runCatching { JSONArray(body) }.getOrNull()
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

        client.getJson(request)
    }

    companion object {
        internal const val BASE_URL = "https://apis.garmin.com/wellness-api/rest"
        const val PROVIDER_KEY = "garmin"

        /**
         * Maps a Garmin `activityType` string (FIT SDK enum, uppercase
         * snake_case like "RUNNING", "STRENGTH_TRAINING") to a Bios
         * [ExerciseModality] bucket. Unknown / blank → OTHER, matching
         * the policy in the HC and Oura adapters.
         */
        internal fun mapGarminActivityToModality(activityType: String?): String {
            if (activityType.isNullOrBlank()) return ExerciseModality.OTHER
            return when (activityType.uppercase()) {
                "RUNNING", "TREADMILL_RUNNING", "TRAIL_RUNNING", "INDOOR_RUNNING",
                "WALKING", "INDOOR_WALKING", "HIKING",
                "CYCLING", "INDOOR_CYCLING", "ROAD_BIKING", "MOUNTAIN_BIKING",
                "ELLIPTICAL", "STAIR_CLIMBING", "INDOOR_CARDIO",
                "ROWING", "INDOOR_ROWING",
                "LAP_SWIMMING", "OPEN_WATER_SWIMMING", "SWIMMING",
                "CARDIO" -> ExerciseModality.CARDIO

                "STRENGTH_TRAINING", "WEIGHTLIFTING",
                "BODY_WEIGHT", "CALISTHENICS" -> ExerciseModality.STRENGTH

                "HIIT", "INTERVAL_TRAINING", "CROSS_TRAINING" -> ExerciseModality.INTERVAL

                "YOGA", "PILATES", "STRETCHING", "MEDITATION",
                "MOBILITY" -> ExerciseModality.MOBILITY

                else -> ExerciseModality.OTHER
            }
        }
    }
}
