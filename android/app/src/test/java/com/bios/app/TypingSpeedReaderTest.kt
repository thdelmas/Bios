package com.bios.app

import com.bios.app.ingest.TypingSpeedReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-only tests for the pure cadence-math helper
 * [TypingSpeedReader.Companion.charsPerMinute]. The Context-bound
 * opt-in path is exercised indirectly via the same helper (the reader
 * delegates its math to this companion). Same pattern as
 * `PerioperativeStateStoreTest`.
 */
class TypingSpeedReaderTest {

    @Test
    fun returns_null_below_minimum_character_count() {
        val cpm = TypingSpeedReader.charsPerMinute(
            characters = TypingSpeedReader.MIN_CHARACTERS - 1,
            elapsedMillis = 10_000L,
        )
        assertNull(cpm)
    }

    @Test
    fun returns_null_below_minimum_elapsed_window() {
        val cpm = TypingSpeedReader.charsPerMinute(
            characters = 100,
            elapsedMillis = TypingSpeedReader.MIN_ELAPSED_MS - 1L,
        )
        assertNull(cpm)
    }

    @Test
    fun returns_null_for_zero_elapsed_millis() {
        // Elapsed = 0 is below MIN_ELAPSED_MS but pin separately — a degenerate
        // input should never produce a division-by-zero or an infinite cadence.
        val cpm = TypingSpeedReader.charsPerMinute(characters = 100, elapsedMillis = 0L)
        assertNull(cpm)
    }

    @Test
    fun computes_60_chars_per_minute_for_60_chars_in_60_seconds() {
        val cpm = TypingSpeedReader.charsPerMinute(
            characters = 60,
            elapsedMillis = 60_000L,
        )
        assertNotNull(cpm)
        assertEquals(60.0, cpm!!, 1e-9)
    }

    @Test
    fun computes_300_chars_per_minute_for_50_chars_in_10_seconds() {
        // 50 chars / (10/60) min = 300 chars/min — a brisk typist.
        val cpm = TypingSpeedReader.charsPerMinute(
            characters = 50,
            elapsedMillis = 10_000L,
        )
        assertNotNull(cpm)
        assertEquals(300.0, cpm!!, 1e-9)
    }

    @Test
    fun accepts_the_minimum_floor_inputs_exactly() {
        // MIN_CHARACTERS = 20, MIN_ELAPSED_MS = 2000 ms.
        // 20 chars / (2/60) min = 600 chars/min (a single-burst implausible value).
        // The fact that the floor accepts it but produces an unrealistically
        // high cadence is *exactly* why the trajectory advisory reads the
        // median over many sessions, not a single sample.
        val cpm = TypingSpeedReader.charsPerMinute(
            characters = TypingSpeedReader.MIN_CHARACTERS,
            elapsedMillis = TypingSpeedReader.MIN_ELAPSED_MS,
        )
        assertNotNull(cpm)
        assertTrue("floor-input cadence should be finite", cpm!!.isFinite())
        assertEquals(600.0, cpm, 1e-9)
    }
}
