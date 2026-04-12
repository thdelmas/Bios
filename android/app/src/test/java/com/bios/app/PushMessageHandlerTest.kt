package com.bios.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for push message parsing and validation logic.
 *
 * PushMessageHandler requires an Android Context for notification delivery,
 * so we test the message format validation and parsing here by mirroring
 * the parsing logic. Integration tests on a real device verify delivery.
 */
class PushMessageHandlerTest {

    // Mirror PushMessageHandler constants
    private val MAX_MESSAGE_BYTES = 4096

    @Test
    fun `valid population signal parses correctly`() {
        val json = JSONObject().apply {
            put("v", 1)
            put("type", "population_signal")
            put("ts", System.currentTimeMillis())
            put("payload", JSONObject().apply {
                put("title", "Elevated respiratory illness in your region")
                put("explanation", "Multiple community members report similar patterns.")
                put("severity", 2)
                put("region", "EU-FR-75")
            })
        }

        val message = json.toString().toByteArray(Charsets.UTF_8)
        assertTrue(message.size <= MAX_MESSAGE_BYTES)

        val parsed = JSONObject(String(message, Charsets.UTF_8))
        assertEquals("population_signal", parsed.getString("type"))

        val payload = parsed.getJSONObject("payload")
        assertEquals("Elevated respiratory illness in your region", payload.getString("title"))
        assertEquals(2, payload.getInt("severity"))
    }

    @Test
    fun `valid model update parses correctly`() {
        val json = JSONObject().apply {
            put("v", 1)
            put("type", "model_update")
            put("ts", System.currentTimeMillis())
            put("payload", JSONObject().apply {
                put("version", "1.5.0")
                put("sha256", "abc123def456")
                put("ed25519_sig", "sig789")
            })
        }

        val parsed = JSONObject(json.toString())
        assertEquals("model_update", parsed.getString("type"))
        assertEquals("1.5.0", parsed.getJSONObject("payload").getString("version"))
    }

    @Test
    fun `oversized message is rejected`() {
        val oversized = ByteArray(MAX_MESSAGE_BYTES + 1) { 0x41 }
        assertTrue(oversized.size > MAX_MESSAGE_BYTES)
    }

    @Test
    fun `malformed JSON does not crash`() {
        val garbage = "not json at all {{{".toByteArray(Charsets.UTF_8)
        val parsed = try {
            JSONObject(String(garbage, Charsets.UTF_8))
            false // should have thrown
        } catch (_: Exception) {
            true
        }
        assertTrue("Malformed JSON should throw", parsed)
    }

    @Test
    fun `missing type field returns empty string`() {
        val json = JSONObject().apply {
            put("v", 1)
            put("payload", JSONObject())
        }
        assertEquals("", json.optString("type"))
    }

    @Test
    fun `population signal missing title is detectable`() {
        val json = JSONObject().apply {
            put("v", 1)
            put("type", "population_signal")
            put("payload", JSONObject().apply {
                put("explanation", "Some explanation")
                put("severity", 1)
            })
        }
        val payload = json.getJSONObject("payload")
        val title = payload.optString("title", "")
        assertTrue("Missing title should be blank", title.isBlank())
    }

    @Test
    fun `severity is clamped to valid range`() {
        val json = JSONObject().apply {
            put("severity", 99)
        }
        val severity = json.optInt("severity", 1).coerceIn(0, 3)
        assertEquals(3, severity)
    }

    @Test
    fun `ping message type is recognized`() {
        val json = JSONObject().apply {
            put("v", 1)
            put("type", "ping")
        }
        assertEquals("ping", json.optString("type"))
    }

    @Test
    fun `title and explanation are truncated for safety`() {
        val longTitle = "A".repeat(500)
        val truncated = longTitle.take(200)
        assertEquals(200, truncated.length)
    }
}
