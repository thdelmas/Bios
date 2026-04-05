package com.bios.app.privacy

import android.content.Context
import android.util.Log
import com.bios.app.platform.IpfsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Receives anonymized population-level health signals.
 *
 * On LETHE (with IPFS): resolves the `bios-signals` IPNS name to fetch
 * the latest signals bulletin. No server knows who is requesting.
 * All traffic is Tor-routed by the IPFS daemon.
 *
 * On stock Android (without IPFS): falls back to HTTP GET from the
 * configured backend server.
 *
 * This is receive-only: the owner's device never reveals anything
 * about itself when fetching population signals.
 */
class PopulationHealthSignals(private val context: Context) {

    private val ipfs = IpfsClient()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch current population health signals.
     */
    suspend fun fetchSignals(serverUrl: String? = null): List<PopulationSignal> {
        if (!isEnabled()) return emptyList()

        return if (ipfs.isAvailable()) {
            fetchViaIpns()
        } else if (serverUrl != null) {
            fetchViaHttp(serverUrl)
        } else {
            emptyList()
        }
    }

    /**
     * Resolve the IPNS bulletin and fetch signals from IPFS.
     * The IPNS name points to a signed JSON document updated by any
     * aggregator node. All traffic goes through Tor.
     */
    private suspend fun fetchViaIpns(): List<PopulationSignal> {
        return withContext(Dispatchers.IO) {
            try {
                val cid = ipfs.nameResolve(IPNS_NAME) ?: return@withContext emptyList()
                val data = ipfs.cat(cid) ?: return@withContext emptyList()
                val json = String(data, Charsets.UTF_8)
                parseSignals(json)
            } catch (e: Exception) {
                Log.w(TAG, "IPNS signal fetch failed: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Fallback: HTTP GET from centralized backend (stock Android).
     */
    private suspend fun fetchViaHttp(serverUrl: String): List<PopulationSignal> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$serverUrl/population/signals")
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string() ?: return@withContext emptyList()
                    parseSignals(body)
                }
            } catch (e: Exception) {
                Log.w(TAG, "HTTP signal fetch failed: ${e.message}")
                emptyList()
            }
        }
    }

    fun isEnabled(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    private fun parseSignals(json: String): List<PopulationSignal> {
        return try {
            val root = JSONObject(json)
            val signals = root.optJSONArray("signals") ?: return emptyList()
            (0 until signals.length()).mapNotNull { i ->
                val s = signals.getJSONObject(i)
                PopulationSignal(
                    id = s.getString("id"),
                    category = s.getString("category"),
                    severity = s.optString("severity", "info"),
                    title = s.getString("title"),
                    description = s.getString("description"),
                    region = s.optString("region", ""),
                    timestamp = s.getLong("timestamp"),
                    source = s.optString("source", "aggregate")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse signals: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "BiosPopHealth"
        private const val PREFS_NAME = "bios_population"
        private const val KEY_ENABLED = "population_signals_enabled"
        const val IPNS_NAME = "bios-signals"
    }
}

data class PopulationSignal(
    val id: String,
    val category: String,
    val severity: String,
    val title: String,
    val description: String,
    val region: String,
    val timestamp: Long,
    val source: String
)
