package com.bios.app.privacy

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Receives and surfaces anonymized population-level health signals.
 *
 * Architecture:
 * - The server aggregates Community tier contributions into regional patterns
 * - Bios downloads these patterns on-device (no location data sent)
 * - Signals are displayed as informational notices: "Respiratory illness activity
 *   elevated in your area"
 *
 * Privacy:
 * - No server knows the owner's location — signals are derived from anonymized
 *   aggregate patterns, not individual data
 * - The owner can disable population signals independently of their Community tier
 * - Signals are fetched over Tor on LETHE; over HTTPS on stock Android
 *
 * This is receive-only: the owner's device never reveals anything about itself
 * when fetching population signals.
 */
class PopulationHealthSignals(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch current population health signals from the server.
     * Returns signals relevant to the owner (server does not know who is asking).
     */
    suspend fun fetchSignals(serverUrl: String): List<PopulationSignal> {
        if (!isEnabled()) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$serverUrl/population/signals")
                    .header("Accept", "application/json")
                    // No auth headers — anonymous fetch
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext emptyList()

                val body = response.body?.string() ?: return@withContext emptyList()
                parseSignals(body)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch population signals", e)
                emptyList()
            }
        }
    }

    fun isEnabled(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, false) // Disabled by default
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
            Log.w(TAG, "Failed to parse population signals", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "BiosPopHealth"
        private const val PREFS_NAME = "bios_population"
        private const val KEY_ENABLED = "population_signals_enabled"
    }
}

/**
 * A population-level health signal derived from anonymized Community contributions.
 * Informational only — never identifies individuals.
 */
data class PopulationSignal(
    val id: String,
    val category: String,         // "respiratory", "gastrointestinal", "general"
    val severity: String,         // "info", "elevated", "high"
    val title: String,            // "Respiratory illness activity elevated"
    val description: String,      // "Aggregate patterns suggest increased respiratory illness..."
    val region: String,           // Broad region (never precise — "Northeast US", "Western Europe")
    val timestamp: Long,
    val source: String            // "aggregate" — always derived, never individual
)
