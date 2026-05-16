package com.bios.app

import com.bios.app.data.CycleDerivation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Guards the deterministic primary keys used by [CycleDerivation] so
 * re-derivation stays idempotent under `OnConflictStrategy.REPLACE`. The
 * BBT-side `cycle_phase` id is guarded separately by
 * [BbtEntryRepoIdTest]; this file covers the two ids added with the
 * menstruation-onset path: `cycle_day` and `menstruation_onset`.
 */
class CycleDerivationIdTest {

    private val midnightUtc = 19_737L * 86_400_000L  // 2024-01-15T00:00:00Z
    private val laterSameDay = midnightUtc + 6 * 60 * 60 * 1000L
    private val nextDay = midnightUtc + 86_400_000L

    @Test
    fun cycle_day_id_collapses_into_one_per_utc_day() {
        assertEquals(
            CycleDerivation.stableCycleDayId(midnightUtc),
            CycleDerivation.stableCycleDayId(laterSameDay)
        )
        assertNotEquals(
            CycleDerivation.stableCycleDayId(midnightUtc),
            CycleDerivation.stableCycleDayId(nextDay)
        )
    }

    @Test
    fun menstruation_onset_id_collapses_into_one_per_utc_day() {
        assertEquals(
            CycleDerivation.stableMenstruationOnsetId(midnightUtc),
            CycleDerivation.stableMenstruationOnsetId(laterSameDay)
        )
        assertNotEquals(
            CycleDerivation.stableMenstruationOnsetId(midnightUtc),
            CycleDerivation.stableMenstruationOnsetId(nextDay)
        )
    }

    @Test
    fun ids_are_namespaced_so_they_cant_collide_across_metric_types() {
        val ts = 1_700_000_000_000L
        val phaseId = CycleDerivation.stableCyclePhaseId(ts)
        val dayId = CycleDerivation.stableCycleDayId(ts)
        val onsetId = CycleDerivation.stableMenstruationOnsetId(ts)
        assert(phaseId.startsWith("cycle_phase_self_reported_"))
        assert(dayId.startsWith("cycle_day_self_reported_"))
        assert(onsetId.startsWith("menstruation_onset_self_reported_"))
        // Different prefixes → different ids on the same day.
        assertNotEquals(phaseId, dayId)
        assertNotEquals(phaseId, onsetId)
        assertNotEquals(dayId, onsetId)
    }
}
