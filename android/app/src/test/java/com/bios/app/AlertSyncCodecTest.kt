package com.bios.app

import com.bios.app.engine.AnomalyDetector
import com.bios.app.model.Anomaly
import com.bios.app.sync.AlertSyncCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Alerts between the owner's devices: what is sent carries no journal
 * answers, and what an earlier-version peer sends cannot bring old wording
 * back or erase what this device holds.
 */
class AlertSyncCodecTest {

    private val deviation = Anomaly(
        id = "d", detectedAt = 5_000L, metricTypes = "[\"resting_heart_rate\"]",
        deviationScores = "{\"resting_heart_rate\":3.7}", combinedScore = 0.75,
        patternId = AnomalyDetector.ML_PATTERN_ID, severity = 1, title = AnomalyDetector.TITLE,
        explanation = "Resting heart rate 66 bpm over the last 24 h, above your usual 53–60 bpm.",
        acknowledged = true, acknowledgedAt = 6_000L, feedbackAt = 7_000L, feltSick = true, visitedDoctor = false,
        diagnosis = "Cold", symptoms = "Sore throat", notes = "Late dinner", outcomeAccurate = true,
        signalSnapshot = "[{\"metric\":\"resting_heart_rate\",\"value\":66.0}]",
    )

    /** What a peer on an earlier version sends for a pattern alert. */
    private fun earlierPeerPayload() = JSONObject().apply {
        put("id", "p"); put("detectedAt", 1_000L)
        put("metricTypes", "[\"resting_heart_rate\"]")
        put("deviationScores", "{\"resting_heart_rate\":2.4}")
        put("combinedScore", 2.3)
        put("patternId", "infection_onset")
        put("severity", 2); put("title", "Possible infection onset")
        put("explanation", "Elevated resting heart rate with reduced HRV can precede illness.")
        put("suggestedAction", "Consider resting and monitoring symptoms.")
        put("acknowledged", false)
        put("acknowledgedAt", JSONObject.NULL)
    }

    @Test
    fun `what is sent has no journal answers and no next step`() {
        val sent = AlertSyncCodec.toJson(deviation)
        for (key in listOf("feltSick", "visitedDoctor", "diagnosis", "symptoms", "outcomeAccurate", "suggestedAction")) {
            assertFalse(key, sent.has(key))
        }
        assertEquals("Late dinner", sent.getString("notes"))
        assertEquals(deviation.signalSnapshot, sent.getString("signalSnapshot"))
    }

    @Test
    fun `a deviation alert round-trips onto a device that does not hold it`() {
        val received = AlertSyncCodec.fromPeer(JSONObject(AlertSyncCodec.toJson(deviation).toString()), existing = null)
        assertEquals(
            deviation.copy(feltSick = null, visitedDoctor = null, diagnosis = null, symptoms = null, outcomeAccurate = null),
            received,
        )
    }

    @Test
    fun `a device keeps its own journal answers, note and snapshot when the peer sends none`() {
        val fromPeer = JSONObject(AlertSyncCodec.toJson(deviation.copy(notes = null, signalSnapshot = null)).toString())
        assertEquals(deviation, AlertSyncCodec.fromPeer(fromPeer, existing = deviation))
    }

    @Test
    fun `an acknowledgement on either device holds`() {
        val unacknowledged = deviation.copy(acknowledged = false, acknowledgedAt = null)
        val received = AlertSyncCodec.fromPeer(JSONObject(AlertSyncCodec.toJson(unacknowledged).toString()), deviation)
        assertTrue(received.acknowledged)
        assertEquals(6_000L, received.acknowledgedAt)
    }

    @Test
    fun `an earlier peer cannot bring the old wording back`() {
        val received = AlertSyncCodec.fromPeer(earlierPeerPayload(), existing = null)
        assertEquals("earlier_alert", received.patternId)
        assertEquals("Earlier alert", received.title)
        assertNull(received.suggestedAction)
        val text = "${received.patternId} ${received.title} ${received.explanation}"
        for (word in listOf("infection", "illness", "symptom", "Consider")) {
            assertFalse("'$word' in: $text", text.contains(word, ignoreCase = true))
        }
        assertEquals(2, received.severity)
        assertEquals("{\"resting_heart_rate\":2.4}", received.deviationScores)
    }

    @Test
    fun `an earlier peer's deviation alert takes the current title and keeps its measured lines`() {
        val payload = earlierPeerPayload().apply {
            put("patternId", AnomalyDetector.ML_PATTERN_ID)
            put("title", "Unusual health pattern detected")
            put("explanation", deviation.explanation)
        }
        val received = AlertSyncCodec.fromPeer(payload, existing = null)
        assertEquals(AnomalyDetector.TITLE, received.title)
        assertEquals(deviation.explanation, received.explanation)
        assertNull(received.suggestedAction)
    }

    @Test
    fun `an alert with no identifier is an earlier alert`() {
        assertEquals("earlier_alert", AlertSyncCodec.neutralised(deviation.copy(patternId = null)).patternId)
    }
}
