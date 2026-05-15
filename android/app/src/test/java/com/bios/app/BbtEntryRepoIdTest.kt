package com.bios.app

import com.bios.app.data.BbtEntryRepo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Guards the deterministic CYCLE_PHASE row id that lets re-derivation be
 * idempotent under `OnConflictStrategy.REPLACE`. If the id strategy
 * changes, re-running [com.bios.app.engine.CycleInference] will create
 * duplicate rows instead of updating in place — exactly the failure mode
 * this test catches.
 */
class BbtEntryRepoIdTest {

    @Test
    fun stable_id_is_constant_within_the_same_utc_day() {
        // Build a UTC midnight explicitly so the +6h offset can't cross a day.
        val midnightUtc = 19_737L * 86_400_000L  // 2024-01-15T00:00:00Z
        val laterSameDay = midnightUtc + 6 * 60 * 60 * 1000L
        assertEquals(
            BbtEntryRepo.stableCyclePhaseId(midnightUtc),
            BbtEntryRepo.stableCyclePhaseId(laterSameDay)
        )
    }

    @Test
    fun stable_id_differs_across_utc_days() {
        val midnightUtc = 19_737L * 86_400_000L
        val nextDay = midnightUtc + 86_400_000L
        assertNotEquals(
            BbtEntryRepo.stableCyclePhaseId(midnightUtc),
            BbtEntryRepo.stableCyclePhaseId(nextDay)
        )
    }

    @Test
    fun stable_id_is_namespaced_so_it_cant_collide_with_other_metric_ids() {
        // The id must be specific enough that arbitrary UUID-keyed rows
        // (which look like "550e8400-e29b-41d4-a716-446655440000") don't
        // collide. We check the namespace prefix is present.
        val id = BbtEntryRepo.stableCyclePhaseId(1_700_000_000_000L)
        assert(id.startsWith("cycle_phase_self_reported_"))
    }
}
