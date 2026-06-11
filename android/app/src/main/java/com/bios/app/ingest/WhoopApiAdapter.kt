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
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

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

    private val client = defaultApiClient()

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

    /**
     * Reads WHOOP `/activity/workout` records in the window and returns each
     * as a composite [ExerciseSession] — parent EXERCISE_SESSION reading +
     * `event_payloads` rows. Mirrors the HC / Oura emission shape so the
     * pattern engine sees one schema regardless of source.
     *
     * WHOOP's workout record carries:
     *  - `start` / `end` ISO timestamps
     *  - `sport_id` (numeric) and, on newer responses, `sport_name` (string)
     *  - `score.average_heart_rate`
     *
     * Modality buckets fall back through three layers: prefer the
     * `sport_name` string (matched the same way as Oura activity strings),
     * else a small numeric `sport_id` map for the well-documented IDs, else
     * `OTHER`. RPE isn't in the workout payload, so the field is omitted.
     */
    suspend fun fetchExerciseSessions(
        startTime: Instant, endTime: Instant, sourceId: String
    ): List<ExerciseSession> {
        val token = getToken() ?: return emptyList()
        val json = apiGet(token, "activity/workout", startTime, endTime) ?: return emptyList()
        val records = json.optJSONArray("records") ?: return emptyList()

        val sessions = mutableListOf<ExerciseSession>()
        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            val startIso = record.optString("start").takeIf { it.isNotEmpty() } ?: continue
            val endIso = record.optString("end").takeIf { it.isNotEmpty() } ?: continue
            val startMs = parseTimestamp(startIso)
            val endMs = parseTimestamp(endIso)
            val durationSec = ((endMs - startMs) / 1000L).toInt()
            if (durationSec <= 0) continue

            val reading = MetricReading(
                id = UUID.randomUUID().toString(),
                metricType = MetricType.EXERCISE_SESSION.key,
                value = 1.0,
                timestamp = startMs,
                durationSec = durationSec,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level,
            )

            val modality = mapWhoopSportToModality(
                sportName = record.optString("sport_name").takeIf { it.isNotEmpty() },
                sportId = if (record.has("sport_id") && !record.isNull("sport_id"))
                    record.optInt("sport_id") else null,
            )

            val payload = mutableListOf(
                EventPayloadField(
                    readingId = reading.id,
                    fieldKey = ExerciseSessionFields.MODALITY,
                    stringValue = modality,
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

            val avgHr = record.optJSONObject("score")
                ?.optDouble("average_heart_rate", Double.NaN) ?: Double.NaN
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

        client.getJson(request)
    }

    private fun parseTimestamp(isoString: String): Long =
        parseIsoTimestampOrNull(isoString) ?: 0L

    companion object {
        internal const val BASE_URL = "https://api.prod.whoop.com/developer/v1"
        const val PROVIDER_KEY = "whoop"

        /**
         * Maps a WHOOP workout to a Bios [ExerciseModality] bucket. Prefers
         * the `sport_name` string (matched case-insensitively against the
         * same normalized vocabulary the Oura adapter uses), falling back
         * to a minimal `sport_id` map for the well-documented IDs in
         * WHOOP's public catalog. Anything unknown — including null both
         * sides — lands on [ExerciseModality.OTHER].
         */
        internal fun mapWhoopSportToModality(sportName: String?, sportId: Int?): String {
            if (!sportName.isNullOrBlank()) {
                val normalized = sportName.lowercase().replace('-', '_').replace(' ', '_')
                val mapped = when (normalized) {
                    "running", "trail_running", "walking", "hiking",
                    "cycling", "indoor_cycling", "biking", "stationary_bike",
                    "elliptical", "rowing", "indoor_rowing",
                    "swimming", "open_water_swimming",
                    "stair_climber", "stair_climbing", "stairs" -> ExerciseModality.CARDIO

                    "weightlifting", "weight_lifting", "powerlifting",
                    "strength_training", "calisthenics" -> ExerciseModality.STRENGTH

                    "hiit", "crossfit", "interval_training", "tabata" -> ExerciseModality.INTERVAL

                    "yoga", "pilates", "stretching", "mobility" -> ExerciseModality.MOBILITY

                    else -> null
                }
                if (mapped != null) return mapped
            }
            // Numeric fallback for known WHOOP sport_ids. Conservative set —
            // only the IDs whose modality is clearly documented in WHOOP's
            // public catalog. Anything else falls to OTHER.
            return when (sportId) {
                0 -> ExerciseModality.CARDIO        // Running
                1 -> ExerciseModality.CARDIO        // Cycling
                18 -> ExerciseModality.CARDIO       // Rowing
                30 -> ExerciseModality.CARDIO       // Swimming
                35 -> ExerciseModality.STRENGTH     // Weightlifting
                36 -> ExerciseModality.MOBILITY     // Yoga
                39 -> ExerciseModality.INTERVAL     // HIIT
                63 -> ExerciseModality.STRENGTH     // CrossFit-style mixed
                else -> ExerciseModality.OTHER
            }
        }
    }
}
