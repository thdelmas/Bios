package com.bios.app

import com.bios.app.physiology.PerioperativeStateStore
import com.bios.app.physiology.PhysiologyState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Day-boundary mapping for the peri-operative state machine (#183,
 * SURGICAL_POV §2.1). Exercises the pure-function variant
 * [PerioperativeStateStore.Companion.stateForProcedureDate] so the test
 * runs in the JVM-only unit-test source set with no Robolectric / Mockito
 * dependency.
 *
 * Boundaries:
 *  - Future date → PREHAB_WINDOW
 *  - Day 0 → POD_0_30
 *  - Day 29 → POD_0_30
 *  - Day 30 → POD_0_30 (inclusive)
 *  - Day 31 → POD_30_90
 *  - Day 90 → POD_30_90 (inclusive)
 *  - Day 91 → POD_90_PLUS
 */
class PerioperativeStateStoreTest {

    private val day: Long = 24L * 3600L * 1000L

    @Test
    fun future_procedure_date_returns_prehab_window() {
        val state = PerioperativeStateStore.stateForProcedureDate(
            procedureDateMillis = 30L * day,
            nowMillis = 0L,
        )
        assertEquals(PhysiologyState.PREHAB_WINDOW, state)
    }

    @Test
    fun day_zero_maps_to_pod_0_30() {
        assertEquals(
            PhysiologyState.POD_0_30,
            PerioperativeStateStore.stateForProcedureDate(0L, 0L),
        )
    }

    @Test
    fun day_seven_maps_to_pod_0_30() {
        assertEquals(
            PhysiologyState.POD_0_30,
            PerioperativeStateStore.stateForProcedureDate(0L, 7L * day),
        )
    }

    @Test
    fun day_thirty_inclusive_maps_to_pod_0_30() {
        // At exactly 30 days the owner is still POD_0_30.
        assertEquals(
            PhysiologyState.POD_0_30,
            PerioperativeStateStore.stateForProcedureDate(0L, 30L * day),
        )
    }

    @Test
    fun day_thirty_one_transitions_to_pod_30_90() {
        // SURGICAL_POV §2.1 — state transition at day 30 → 31.
        assertEquals(
            PhysiologyState.POD_30_90,
            PerioperativeStateStore.stateForProcedureDate(0L, 31L * day),
        )
    }

    @Test
    fun day_sixty_maps_to_pod_30_90() {
        assertEquals(
            PhysiologyState.POD_30_90,
            PerioperativeStateStore.stateForProcedureDate(0L, 60L * day),
        )
    }

    @Test
    fun day_ninety_inclusive_maps_to_pod_30_90() {
        // At exactly 90 days the owner is still POD_30_90.
        assertEquals(
            PhysiologyState.POD_30_90,
            PerioperativeStateStore.stateForProcedureDate(0L, 90L * day),
        )
    }

    @Test
    fun day_ninety_one_transitions_to_pod_90_plus() {
        // SURGICAL_POV §2.1 — state transition at day 90 → 91.
        assertEquals(
            PhysiologyState.POD_90_PLUS,
            PerioperativeStateStore.stateForProcedureDate(0L, 91L * day),
        )
    }

    @Test
    fun far_post_op_remains_pod_90_plus() {
        assertEquals(
            PhysiologyState.POD_90_PLUS,
            PerioperativeStateStore.stateForProcedureDate(0L, 365L * day),
        )
    }
}
