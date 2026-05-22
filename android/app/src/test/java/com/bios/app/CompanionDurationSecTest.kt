package com.bios.app

import com.bios.app.provider.companionDurationSecFor
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the companion-insert duration derivation behind the
 * BiosHealthProvider. SleepRegularityCalculator filters rows by
 * `(durationSec ?: 0) > 0`, so a W2F-pushed sleep_duration that lands
 * with durationSec = null gets silently dropped from the regularity
 * window. The provider derives durationSec from value at insert time;
 * these tests pin that contract so the derivation can't quietly
 * regress and break the regularity score for phone-only owners.
 */
class CompanionDurationSecTest {

    @Test
    fun `sleep_duration positive value seeds durationSec`() {
        // 7h * 3600 = 25200 seconds. The value comes from W2F's screen-off
        // estimate or a wearable's seconds field; either way the
        // derivation must round-trip cleanly.
        val out = companionDurationSecFor(MetricType.SLEEP_DURATION.key, 25_200.0)
        assertEquals(25_200, out)
    }

    @Test
    fun `sleep_duration zero or negative value yields null durationSec`() {
        // Defensive against bad pushes — a zero/negative value can't
        // describe a real night and shouldn't feed regularity as if it
        // were one second of sleep.
        assertNull(companionDurationSecFor(MetricType.SLEEP_DURATION.key, 0.0))
        assertNull(companionDurationSecFor(MetricType.SLEEP_DURATION.key, -1.0))
    }

    @Test
    fun `fractional seconds truncate to whole seconds`() {
        // The Int column can't hold sub-second precision. Truncation
        // (not rounding) is the conservative choice — undercount over
        // overstate.
        val out = companionDurationSecFor(MetricType.SLEEP_DURATION.key, 25_200.9)
        assertEquals(25_200, out)
    }

    @Test
    fun `non sleep_duration keys never get a durationSec`() {
        // Other companion-written keys are event markers (tobacco_use,
        // fall_event) or scalar samples (typing_cadence) — duration has
        // no defined meaning there. Only sleep_duration carries seconds
        // as its value semantics.
        val intakeOrEvent = listOf(
            MetricType.TOBACCO_USE.key,
            MetricType.CANNABIS_USE.key,
            MetricType.FALL_EVENT.key,
            MetricType.TYPING_CADENCE.key,
            MetricType.MOOD_DRIFT_SCORE.key,
            MetricType.CIRCADIAN_PHASE_SHIFT.key,
            MetricType.CAFFEINE_INTAKE.key,
        )
        for (k in intakeOrEvent) {
            assertNull(
                "durationSec must be null for $k",
                companionDurationSecFor(k, 100.0)
            )
        }
    }
}
