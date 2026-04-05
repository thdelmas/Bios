package com.bios.app.platform

import android.util.Log
import com.bios.app.data.BiosDatabase
import com.bios.app.model.AlertTier
import com.bios.app.model.MetricType
import com.bios.app.model.PersonalBaseline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Localhost-only HTTP server exposing health status to the LETHE agent.
 *
 * Binds to 127.0.0.1:8080/health/ — not accessible from the network.
 * The LETHE agent (org.osmosis.lethe.agent) queries these endpoints
 * to surface health cards in the Void launcher.
 *
 * Endpoints:
 *   GET /health/status    — overall state (normal/observation/notice/advisory/urgent) + alert count
 *   GET /health/summary   — today's key metrics vs baseline
 *   GET /health/alerts    — active unacknowledged alerts (metadata only, no raw readings)
 *   GET /health/baseline/{type} — personal baseline for a specific metric type
 *
 * No raw health readings are ever exposed. Only computed summaries and alert metadata.
 */
class HealthApiServer(
    private val db: BiosDatabase,
    private val scope: CoroutineScope
) {
    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true

        scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(PORT, 5, InetAddress.getLoopbackAddress())
                Log.i(TAG, "Health API server started on localhost:$PORT")

                while (running) {
                    val socket = try {
                        serverSocket?.accept() ?: break
                    } catch (_: Exception) {
                        break
                    }

                    scope.launch(Dispatchers.IO) {
                        try {
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                            val requestLine = reader.readLine() ?: return@launch
                            val parts = requestLine.split(" ")
                            val method = parts.getOrNull(0) ?: return@launch
                            val path = parts.getOrNull(1) ?: return@launch

                            // Read headers to get Content-Length for POST body
                            var contentLength = 0
                            var line = reader.readLine()
                            while (!line.isNullOrBlank()) {
                                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                                }
                                line = reader.readLine()
                            }

                            // Read POST body if present
                            pendingPostBody = if (contentLength > 0 && method == "POST") {
                                val body = CharArray(contentLength)
                                reader.read(body, 0, contentLength)
                                String(body)
                            } else null

                            val response = handleRequest(path, method)

                            val writer = PrintWriter(socket.getOutputStream(), true)
                            writer.print("HTTP/1.1 200 OK\r\n")
                            writer.print("Content-Type: application/json\r\n")
                            writer.print("Content-Length: ${response.toByteArray().size}\r\n")
                            writer.print("Connection: close\r\n")
                            writer.print("\r\n")
                            writer.print(response)
                            writer.flush()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error handling request", e)
                        } finally {
                            socket.close()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Health API server failed", e)
            }
        }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        Log.i(TAG, "Health API server stopped")
    }

    private var pendingPostBody: String? = null

    private suspend fun handleRequest(path: String, method: String): String {
        return when {
            path == "/health/status" && method == "GET" -> handleStatus()
            path == "/health/summary" && method == "GET" -> handleSummary()
            path == "/health/alerts" && method == "GET" -> handleAlerts()
            path == "/health/acknowledge" && method == "POST" -> {
                handleAcknowledge(pendingPostBody ?: "")
            }
            path.startsWith("/health/baseline/") && method == "GET" -> {
                val type = path.removePrefix("/health/baseline/")
                handleBaseline(type)
            }
            else -> JSONObject().put("error", "not found").toString()
        }
    }

    private suspend fun handleStatus(): String {
        val unacked = db.anomalyDao().fetchUnacknowledged()
        val highestSeverity = unacked.maxOfOrNull { it.severity } ?: 0

        val state = when {
            highestSeverity >= AlertTier.URGENT.level -> HealthState.URGENT
            highestSeverity >= AlertTier.ADVISORY.level -> HealthState.ADVISORY
            highestSeverity >= AlertTier.NOTICE.level -> HealthState.NOTICE
            unacked.isNotEmpty() -> HealthState.OBSERVATION
            else -> HealthState.NORMAL
        }

        return JSONObject().apply {
            put("state", state.name.lowercase())
            put("activeAlerts", unacked.size)
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }

    private suspend fun handleSummary(): String {
        val now = Instant.now()
        val dayStart = now.minus(24, ChronoUnit.HOURS).toEpochMilli()
        val nowMillis = now.toEpochMilli()

        val summaryMetrics = listOf(
            MetricType.RESTING_HEART_RATE,
            MetricType.HEART_RATE_VARIABILITY,
            MetricType.BLOOD_OXYGEN,
            MetricType.STEPS,
            MetricType.SLEEP_DURATION
        )

        val metrics = JSONArray()
        for (type in summaryMetrics) {
            val baseline = db.personalBaselineDao().fetch(type.key)
            val readings = db.metricReadingDao().fetchValues(type.key, dayStart, nowMillis)
            if (readings.isEmpty()) continue

            val current = readings.last()
            val json = JSONObject().apply {
                put("metric", type.key)
                put("current", current)
                put("unit", type.unit.symbol)
                if (baseline != null) {
                    put("baselineMean", baseline.mean)
                    put("baselineStdDev", baseline.stdDev)
                    put("trend", baseline.trend)
                }
            }
            metrics.put(json)
        }

        return JSONObject().apply {
            put("metrics", metrics)
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }

    private suspend fun handleAlerts(): String {
        val unacked = db.anomalyDao().fetchUnacknowledged()
        val alerts = JSONArray()
        for (a in unacked) {
            alerts.put(JSONObject().apply {
                put("id", a.id)
                put("severity", AlertTier.fromLevel(a.severity).label)
                put("title", a.title)
                put("explanation", a.explanation)
                put("detectedAt", a.detectedAt)
            })
        }

        return JSONObject().apply {
            put("alerts", alerts)
            put("count", unacked.size)
        }.toString()
    }

    private suspend fun handleBaseline(typeKey: String): String {
        val baseline = db.personalBaselineDao().fetch(typeKey)
            ?: return JSONObject().put("error", "no baseline for $typeKey").toString()

        return JSONObject().apply {
            put("metricType", baseline.metricType)
            put("mean", baseline.mean)
            put("stdDev", baseline.stdDev)
            put("p5", baseline.p5)
            put("p95", baseline.p95)
            put("trend", baseline.trend)
            put("trendSlope", baseline.trendSlope)
            put("windowDays", baseline.windowDays)
            put("computedAt", baseline.computedAt)
        }.toString()
    }

    private suspend fun handleAcknowledge(body: String): String {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            return JSONObject().put("error", "invalid JSON body").toString()
        }
        val alertId = json.optString("id", "")
        if (alertId.isBlank()) {
            return JSONObject().put("error", "id is required").toString()
        }
        db.anomalyDao().acknowledge(alertId)
        return JSONObject().apply {
            put("status", "acknowledged")
            put("id", alertId)
        }.toString()
    }

    companion object {
        private const val TAG = "BiosHealthApi"
        private const val PORT = 8080
    }
}
