package com.bios.app.privacy

import android.content.Context
import android.util.Log
import com.bios.app.ingest.ApiTokenStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Transmits anonymous community contributions to the Bios backend.
 *
 * Each MetricSummary in an AnonymousContribution is sent as a separate
 * AggregateContribution matching the backend's expected schema.
 *
 * The server requires Bearer token auth. The token is stored in
 * ApiTokenStore under the "bios_backend" provider key.
 */
class CommunityApiClient(context: Context) {

    private val tokenStore = ApiTokenStore(context)
    private val prefs = context.getSharedPreferences("bios_settings", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Submit an anonymous contribution to the backend.
     * Sends one POST per metric summary to /v1/community/contribute.
     *
     * @return number of successfully submitted metric summaries
     */
    fun submit(contribution: AnonymousContribution): Int {
        val serverUrl = prefs.getString("backend_url", null) ?: return 0
        val token = tokenStore.getToken(PROVIDER_KEY) ?: return 0

        var submitted = 0
        for (summary in contribution.metricSummaries) {
            val body = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("metric_type", summary.metricType)
                put("period", "7d")
                put("mean", summary.noisyStdDev) // noisy aggregate, not raw
                put("std_dev", summary.noisyStdDev)
                put("sample_count", sampleBracketToCount(summary.sampleBracket))
                put("age_group", contribution.ageBracket ?: "")
            }

            val request = Request.Builder()
                .url("$serverUrl/v1/community/contribute")
                .addHeader("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    submitted++
                } else {
                    Log.w(TAG, "Contribution rejected: ${response.code} for ${summary.metricType}")
                }
                response.close()
            } catch (e: Exception) {
                Log.w(TAG, "Contribution failed for ${summary.metricType}: ${e.message}")
            }
        }

        Log.i(TAG, "Submitted $submitted/${contribution.metricSummaries.size} metric summaries")
        return submitted
    }

    private fun sampleBracketToCount(bracket: String): Int = when (bracket) {
        "low" -> 25
        "medium" -> 125
        "high" -> 350
        "very_high" -> 500
        else -> 100
    }

    companion object {
        private const val TAG = "BiosCommunityApi"
        const val PROVIDER_KEY = "bios_backend"
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
