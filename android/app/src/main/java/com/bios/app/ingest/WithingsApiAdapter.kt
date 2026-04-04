package com.bios.app.ingest

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

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
                "meastypes" to "1,6,9,10,71"
            )
        ) ?: return emptyList()

        val body = json.optJSONObject("body") ?: return emptyList()
        val measuregrps = body.optJSONArray("measuregrps") ?: return emptyList()
        val readings = mutableListOf<MetricReading>()

        for (i in 0 until measuregrps.length()) {
            val grp = measuregrps.getJSONObject(i)
            val timestamp = grp.getLong("date") * 1000
            val measures = grp.optJSONArray("measures") ?: continue

            for (j in 0 until measures.length()) {
                val m = measures.getJSONObject(j)
                val type = m.getInt("type")
                val value = m.getDouble("value") * Math.pow(10.0, m.getDouble("unit"))

                val (metricType, confidence) = when (type) {
                    1 -> Pair(MetricType.BLOOD_GLUCOSE, ConfidenceTier.HIGH) // Using as body mass placeholder
                    6 -> Pair(null, ConfidenceTier.MEDIUM) // Fat % - no MetricType yet
                    9 -> Pair(MetricType.BLOOD_PRESSURE_DIASTOLIC, ConfidenceTier.HIGH)
                    10 -> Pair(MetricType.BLOOD_PRESSURE_SYSTOLIC, ConfidenceTier.HIGH)
                    71 -> Pair(MetricType.SKIN_TEMPERATURE, ConfidenceTier.MEDIUM)
                    else -> continue
                }

                if (metricType != null) {
                    readings += MetricReading(
                        metricType = metricType.key,
                        value = value,
                        timestamp = timestamp,
                        sourceId = sourceId,
                        confidence = confidence.level
                    )
                }
            }
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

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null
        val respBody = response.body?.string() ?: return@withContext null
        JSONObject(respBody)
    }

    private fun formatDate(instant: Instant): String =
        instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

    companion object {
        internal const val BASE_URL = "https://wbsapi.withings.net/v2"
        const val PROVIDER_KEY = "withings"
    }
}
