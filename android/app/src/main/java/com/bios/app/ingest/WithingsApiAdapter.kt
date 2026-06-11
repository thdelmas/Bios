package com.bios.app.ingest

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Fetches health data from the Withings Health Mate API (OAuth 2.0).
 *
 * Withings provides: weight, body composition (fat %), blood pressure,
 * sleep, activity, skin temperature.
 *
 * Withings scales and BPMs expand Bios beyond the wearable-first audience.
 */
class WithingsApiAdapter(
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

        readings += fetchMeasures(token, startTime, endTime, sourceId)
        readings += fetchSleep(token, startTime, endTime, sourceId)

        return readings
    }

    /**
     * Fetch body measurements: weight, fat %, systolic/diastolic BP, temp.
     * Withings uses measure types: 1=weight, 6=fat%, 9=diastolic, 10=systolic, 71=temp
     */
    private suspend fun fetchMeasures(
        token: String, start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val json = apiPost(
            token, "measure", "getmeas",
            mapOf(
                "startdate" to start.epochSecond.toString(),
                "enddate" to end.epochSecond.toString(),
                // Types: 1=weight, 5=lean (fat-free) mass, 6=fat%, 9=BP-dia,
                // 10=BP-sys, 71=skin temp, 76=hydration (kg), 88=bone mass.
                "meastypes" to "1,5,6,9,10,71,76,88"
            )
        ) ?: return emptyList()

        val body = json.optJSONObject("body") ?: return emptyList()
        val measuregrps = body.optJSONArray("measuregrps") ?: return emptyList()
        val readings = mutableListOf<MetricReading>()

        for (i in 0 until measuregrps.length()) {
            val grp = measuregrps.getJSONObject(i)
            val timestamp = grp.getLong("date") * 1000
            val measures = grp.optJSONArray("measures") ?: continue
            readings += parseWithingsGroupMeasures(measures, timestamp, sourceId)
        }

        return readings
    }

    private suspend fun fetchSleep(
        token: String, start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val json = apiPost(
            token, "sleep", "getsummary",
            mapOf(
                "startdateymd" to formatDate(start),
                "enddateymd" to formatDate(end)
            )
        ) ?: return emptyList()

        val body = json.optJSONObject("body") ?: return emptyList()
        val series = body.optJSONArray("series") ?: return emptyList()
        val readings = mutableListOf<MetricReading>()

        for (i in 0 until series.length()) {
            val session = series.getJSONObject(i)
            val timestamp = session.getLong("startdate") * 1000

            val totalSleep = session.optInt("data.total_sleep_time", 0)
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

            val hr = session.optDouble("data.hr_average", Double.NaN)
            if (!hr.isNaN()) {
                readings += MetricReading(
                    metricType = MetricType.RESTING_HEART_RATE.key,
                    value = hr,
                    timestamp = timestamp,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }

            val respRate = session.optDouble("data.breathing_disturbances_intensity", Double.NaN)
            if (!respRate.isNaN()) {
                readings += MetricReading(
                    metricType = MetricType.RESPIRATORY_RATE.key,
                    value = respRate,
                    timestamp = timestamp,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.MEDIUM.level
                )
            }
        }

        return readings
    }

    private suspend fun apiPost(
        token: String, service: String, action: String, params: Map<String, String>
    ): JSONObject? = withContext(Dispatchers.IO) {
        val bodyBuilder = FormBody.Builder()
            .add("action", action)
        for ((k, v) in params) bodyBuilder.add(k, v)

        val request = Request.Builder()
            .url("$BASE_URL/$service")
            .header("Authorization", "Bearer $token")
            .post(bodyBuilder.build())
            .build()

        client.getJson(request)
    }

    private fun formatDate(instant: Instant): String =
        instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

    companion object {
        internal const val BASE_URL = "https://wbsapi.withings.net/v2"
        const val PROVIDER_KEY = "withings"
    }
}

/**
 * Walks a Withings `measuregrps[].measures[]` block twice: first pass
 * indexes raw values by `type`, second pass emits the canonical
 * MetricReadings — including the BODY_WATER_PCT derivation, which
 * needs the same group's weight (type 1) as a denominator. Withings
 * reports hydration as **mass in kg** (type 76); the clinical
 * convention is total-body-water as a percent of body weight, so the
 * conversion happens here at the adapter boundary.
 *
 * When hydration is present but weight isn't in the same group, the
 * derivation is skipped silently. The Withings scale always emits
 * both in the same event in practice; this is a defensive path.
 *
 * Top-level (no instance state needed) so the unit tests can exercise
 * the full extraction matrix without instantiating the adapter — which
 * requires an Android Context that the unit-test layer doesn't have.
 */
internal fun parseWithingsGroupMeasures(
    measures: JSONArray,
    timestamp: Long,
    sourceId: String,
): List<MetricReading> {
    val byType = mutableMapOf<Int, Double>()
    for (j in 0 until measures.length()) {
        val m = measures.getJSONObject(j)
        byType[m.getInt("type")] =
            m.getDouble("value") * Math.pow(10.0, m.getDouble("unit"))
    }

    val out = mutableListOf<MetricReading>()
    fun emit(metric: MetricType, value: Double, tier: ConfidenceTier = ConfidenceTier.HIGH) {
        out += MetricReading(
            metricType = metric.key,
            value = value,
            timestamp = timestamp,
            sourceId = sourceId,
            confidence = tier.level,
        )
    }

    byType[1]?.let { emit(MetricType.BODY_MASS, it) }
    byType[5]?.let { emit(MetricType.LEAN_MASS, it) }
    byType[6]?.let { emit(MetricType.BODY_FAT_PCT, it) }
    byType[9]?.let { emit(MetricType.BLOOD_PRESSURE_DIASTOLIC, it) }
    byType[10]?.let { emit(MetricType.BLOOD_PRESSURE_SYSTOLIC, it) }
    byType[71]?.let { emit(MetricType.SKIN_TEMPERATURE, it, ConfidenceTier.MEDIUM) }
    byType[88]?.let { emit(MetricType.BONE_MASS, it) }

    // BODY_WATER_PCT = hydration_kg / weight_kg × 100. Drop if either
    // side is missing or weight is non-positive (defensive against
    // corrupted scale uploads).
    val hydrationKg = byType[76]
    val weightKg = byType[1]
    if (hydrationKg != null && weightKg != null && weightKg > 0.0) {
        emit(MetricType.BODY_WATER_PCT, hydrationKg / weightKg * 100.0)
    }

    return out
}
