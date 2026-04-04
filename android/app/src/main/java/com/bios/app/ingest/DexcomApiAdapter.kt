package com.bios.app.ingest

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
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
 * Fetches continuous glucose monitor (CGM) data from the Dexcom API (OAuth 2.0).
 *
 * Dexcom provides: glucose readings every 5 minutes (EGV = estimated glucose values).
 * Confidence: HIGH (FDA-cleared medical device).
 *
 * CGM data is extremely sensitive — same wipe/isolation protections apply.
 */
class DexcomApiAdapter(
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
        return fetchEgvs(token, startTime, endTime, sourceId)
    }

    /**
     * Fetch estimated glucose values (EGVs) — the core CGM reading.
     * Each reading is a 5-minute glucose sample in mg/dL.
     */
    private suspend fun fetchEgvs(
        token: String, start: Instant, end: Instant, sourceId: String
    ): List<MetricReading> {
        val startStr = formatDateTime(start)
        val endStr = formatDateTime(end)

        val json = apiGet(
            token,
            "egvs?startDate=$startStr&endDate=$endStr"
        ) ?: return emptyList()

        val records = json.optJSONArray("records") ?: return emptyList()
        val readings = mutableListOf<MetricReading>()

        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            val value = record.optDouble("value", Double.NaN)
            if (value.isNaN() || value <= 0) continue

            val timestamp = parseDisplayTime(record.getString("displayTime"))
            val unit = record.optString("unit", "mg/dL")

            readings += MetricReading(
                metricType = MetricType.BLOOD_GLUCOSE.key,
                value = if (unit == "mmol/L") value * 18.0182 else value, // Normalize to mg/dL
                timestamp = timestamp,
                durationSec = 300, // 5-minute sample
                sourceId = sourceId,
                confidence = ConfidenceTier.HIGH.level
            )
        }

        return readings
    }

    private suspend fun apiGet(
        token: String, path: String
    ): JSONObject? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/$path"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null
        val body = response.body?.string() ?: return@withContext null
        JSONObject(body)
    }

    private fun formatDateTime(instant: Instant): String =
        instant.atZone(ZoneId.of("UTC"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

    private fun parseDisplayTime(displayTime: String): Long {
        return try {
            Instant.parse(displayTime + "Z").toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.LocalDateTime.parse(displayTime)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    companion object {
        internal const val BASE_URL = "https://api.dexcom.com/v3/users/self"
        const val PROVIDER_KEY = "dexcom"
    }
}
