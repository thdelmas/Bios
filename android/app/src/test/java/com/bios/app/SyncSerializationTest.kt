package com.bios.app

import com.bios.app.model.Anomaly
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
    fun `Anomaly with nullable fields round-trips through JSON`() {
        val anomaly = Anomaly(
            id = "a-1", detectedAt = 3000L, metricTypes = "[\"heart_rate\"]",
            deviationScores = "{\"heart_rate\":2.1}", combinedScore = 2.1,
            patternId = null, severity = 2, title = "HR elevated",
            explanation = "Resting HR +2σ for 48h", suggestedAction = null,
            acknowledged = false, acknowledgedAt = null,
            feltSick = true, visitedDoctor = null, diagnosis = null,
            symptoms = "fatigue", notes = null, outcomeAccurate = null
        )

        val json = JSONObject().apply {
            put("id", anomaly.id); put("detectedAt", anomaly.detectedAt)
            put("metricTypes", anomaly.metricTypes)
            put("deviationScores", anomaly.deviationScores)
            put("combinedScore", anomaly.combinedScore)
            put("patternId", anomaly.patternId ?: JSONObject.NULL)
            put("severity", anomaly.severity); put("title", anomaly.title)
            put("explanation", anomaly.explanation)
            put("suggestedAction", anomaly.suggestedAction ?: JSONObject.NULL)
            put("acknowledged", anomaly.acknowledged)
            put("acknowledgedAt", anomaly.acknowledgedAt ?: JSONObject.NULL)
            put("feedbackAt", anomaly.feedbackAt ?: JSONObject.NULL)
            put("feltSick", anomaly.feltSick ?: JSONObject.NULL)
            put("visitedDoctor", anomaly.visitedDoctor ?: JSONObject.NULL)
            put("diagnosis", anomaly.diagnosis ?: JSONObject.NULL)
            put("symptoms", anomaly.symptoms ?: JSONObject.NULL)
            put("notes", anomaly.notes ?: JSONObject.NULL)
            put("outcomeAccurate", anomaly.outcomeAccurate ?: JSONObject.NULL)
        }

        val o = json
        val restored = Anomaly(
            id = o.getString("id"), detectedAt = o.getLong("detectedAt"),
            metricTypes = o.getString("metricTypes"),
            deviationScores = o.getString("deviationScores"),
            combinedScore = o.getDouble("combinedScore"),
            patternId = if (o.isNull("patternId")) null else o.getString("patternId"),
            severity = o.getInt("severity"), title = o.getString("title"),
            explanation = o.getString("explanation"),
            suggestedAction = if (o.isNull("suggestedAction")) null else o.getString("suggestedAction"),
            acknowledged = o.getBoolean("acknowledged"),
            acknowledgedAt = if (o.isNull("acknowledgedAt")) null else o.getLong("acknowledgedAt"),
            feedbackAt = if (o.isNull("feedbackAt")) null else o.getLong("feedbackAt"),
            feltSick = if (o.isNull("feltSick")) null else o.getBoolean("feltSick"),
            visitedDoctor = if (o.isNull("visitedDoctor")) null else o.getBoolean("visitedDoctor"),
            diagnosis = if (o.isNull("diagnosis")) null else o.getString("diagnosis"),
            symptoms = if (o.isNull("symptoms")) null else o.getString("symptoms"),
            notes = if (o.isNull("notes")) null else o.getString("notes"),
            outcomeAccurate = if (o.isNull("outcomeAccurate")) null else o.getBoolean("outcomeAccurate")
        )

        assertEquals(anomaly, restored)
        assertNull(restored.patternId)
        assertEquals(true, restored.feltSick)
        assertEquals("fatigue", restored.symptoms)
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
