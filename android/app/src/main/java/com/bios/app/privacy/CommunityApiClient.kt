package com.bios.app.privacy

import android.content.Context
import android.util.Log
import com.bios.app.platform.IpfsClient
import com.bios.app.platform.PlatformDetector
import com.bios.app.ingest.ApiTokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Submits anonymous community contributions.
 *
 * On LETHE (with IPFS): publishes to the `bios-community` PubSub topic.
 * No server involved — other devices subscribe and aggregate locally.
 *
 * On stock Android (without IPFS): falls back to HTTP POST to the
 * configured backend server, if one is set.
 */
class CommunityApiClient(private val context: Context) {

    private val ipfs = IpfsClient()
    private val tokenStore = ApiTokenStore(context)
    private val prefs = context.getSharedPreferences("bios_settings", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Submit an anonymous contribution via the best available transport.
     * @return number of successfully submitted metric summaries
     */
    fun submit(contribution: AnonymousContribution): Int {
        return runBlocking {
            if (ipfs.isAvailable()) {
                submitViaPubSub(contribution)
            } else {
                submitViaHttp(contribution)
            }
        }
    }

    /**
     * Publish the full contribution as a single PubSub message.
     * Any subscribed device can aggregate — no central server needed.
     */
    private suspend fun submitViaPubSub(contribution: AnonymousContribution): Int {
        val payload = JSONObject().apply {
            put("v", 1)
            put("metrics", JSONArray().apply {
                for (s in contribution.metricSummaries) {
                    put(JSONObject().apply {
                        put("type", s.metricType)
                        put("range", s.meanRange)
                        put("std_dev", s.noisyStdDev)
                        put("samples", s.sampleBracket)
                    })
                }
            })
            put("age_bracket", contribution.ageBracket ?: JSONObject.NULL)
            put("device_class", contribution.deviceClass)
        }

        val ok = ipfs.pubsubPublish(PUBSUB_TOPIC, payload.toString().toByteArray(Charsets.UTF_8))
        val count = contribution.metricSummaries.size
        if (ok) {
            Log.i(TAG, "Published $count metrics to PubSub topic $PUBSUB_TOPIC")
        } else {
            Log.w(TAG, "PubSub publish failed")
        }
        return if (ok) count else 0
    }

    /**
     * Fallback: POST to centralized backend (stock Android without IPFS).
     */
    private fun submitViaHttp(contribution: AnonymousContribution): Int {
        val serverUrl = prefs.getString("backend_url", null) ?: return 0
        val token = tokenStore.getToken(PROVIDER_KEY) ?: return 0

        var submitted = 0
        for (summary in contribution.metricSummaries) {
            val body = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("metric_type", summary.metricType)
                put("period", "7d")
                put("mean", summary.noisyStdDev)
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
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) submitted++
                    else Log.w(TAG, "HTTP contribution rejected: ${response.code}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "HTTP contribution failed: ${e.message}")
            }
        }

        Log.i(TAG, "HTTP submitted $submitted/${contribution.metricSummaries.size}")
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
        const val PUBSUB_TOPIC = "bios-community"
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
