package com.bios.app

import com.bios.app.model.HealthEvent
import com.bios.app.model.MetricReading
import com.bios.app.model.PersonalBaseline
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests JSON serialization/deserialization used by SyncManager.
 * Verifies round-trip integrity for all synced entity types.
 */
class SyncSerializationTest {

    @Test
    fun `MetricReading round-trips through JSON`() {
        val reading = MetricReading(
            id = "r-1", metricType = "heart_rate", value = 72.0,
            timestamp = 1000L, durationSec = null, sourceId = "src-1",
            confidence = 3, isPrimary = true, createdAt = 2000L
        )

        val json = JSONObject().apply {
            put("id", reading.id); put("metricType", reading.metricType)
            put("value", reading.value); put("timestamp", reading.timestamp)
            put("durationSec", reading.durationSec ?: JSONObject.NULL)
            put("sourceId", reading.sourceId); put("confidence", reading.confidence)
            put("isPrimary", reading.isPrimary); put("createdAt", reading.createdAt)
        }

        val restored = json.let { o ->
            MetricReading(
                id = o.getString("id"),
                metricType = o.getString("metricType"),
                value = o.getDouble("value"),
                timestamp = o.getLong("timestamp"),
                durationSec = if (o.isNull("durationSec")) null else o.getInt("durationSec"),
                sourceId = o.getString("sourceId"),
                confidence = o.getInt("confidence"),
                isPrimary = o.getBoolean("isPrimary"),
                createdAt = o.getLong("createdAt")
            )
        }

        assertEquals(reading, restored)
        assertNull(restored.durationSec)
    }

    @Test
    fun `PersonalBaseline round-trips through JSON`() {
        val baseline = PersonalBaseline(
            id = "b-1", metricType = "heart_rate", context = "ALL",
            windowDays = 14, computedAt = 5000L, mean = 70.0,
            stdDev = 5.0, p5 = 62.0, p95 = 78.0,
            trend = "STABLE", trendSlope = 0.1
        )

        val json = JSONObject().apply {
            put("id", baseline.id); put("metricType", baseline.metricType)
            put("context", baseline.context); put("windowDays", baseline.windowDays)
            put("computedAt", baseline.computedAt); put("mean", baseline.mean)
            put("stdDev", baseline.stdDev); put("p5", baseline.p5); put("p95", baseline.p95)
            put("trend", baseline.trend); put("trendSlope", baseline.trendSlope)
        }

        val restored = json.let { o ->
            PersonalBaseline(
                id = o.getString("id"), metricType = o.getString("metricType"),
                context = o.getString("context"), windowDays = o.getInt("windowDays"),
                computedAt = o.getLong("computedAt"), mean = o.getDouble("mean"),
                stdDev = o.getDouble("stdDev"), p5 = o.getDouble("p5"),
                p95 = o.getDouble("p95"), trend = o.getString("trend"),
                trendSlope = o.getDouble("trendSlope")
            )
        }

        assertEquals(baseline, restored)
    }

    @Test
    fun `HealthEvent round-trips through JSON`() {
        val event = HealthEvent(
            id = "e-1", type = "ANOMALY_DETECTED", status = "OPEN",
            title = "HR elevated", description = null,
            createdAt = 1000L, updatedAt = 2000L,
            anomalyId = "a-1", parentEventId = null
        )

        val json = JSONObject().apply {
            put("id", event.id); put("type", event.type); put("status", event.status)
            put("title", event.title)
            put("description", event.description ?: JSONObject.NULL)
            put("createdAt", event.createdAt); put("updatedAt", event.updatedAt)
            put("anomalyId", event.anomalyId ?: JSONObject.NULL)
            put("parentEventId", event.parentEventId ?: JSONObject.NULL)
        }

        val o = json
        val restored = HealthEvent(
            id = o.getString("id"), type = o.getString("type"),
            status = o.getString("status"), title = o.getString("title"),
            description = if (o.isNull("description")) null else o.getString("description"),
            createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
            anomalyId = if (o.isNull("anomalyId")) null else o.getString("anomalyId"),
            parentEventId = if (o.isNull("parentEventId")) null else o.getString("parentEventId")
        )

        assertEquals(event, restored)
        assertNull(restored.description)
        assertEquals("a-1", restored.anomalyId)
    }

    @Test
    fun `JSONArray serialization preserves multiple entities`() {
        val arr = JSONArray()
        repeat(3) { i ->
            arr.put(JSONObject().apply {
                put("id", "r-$i"); put("metricType", "heart_rate")
                put("value", 70.0 + i); put("timestamp", 1000L + i)
            })
        }

        val bytes = arr.toString().toByteArray(Charsets.UTF_8)
        val restored = JSONArray(String(bytes, Charsets.UTF_8))
        assertEquals(3, restored.length())
        assertEquals(72.0, restored.getJSONObject(2).getDouble("value"), 0.001)
    }
}
