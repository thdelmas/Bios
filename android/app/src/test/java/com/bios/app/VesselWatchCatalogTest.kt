package com.bios.app

import com.bios.app.ui.vessel.DialAvailability
import com.bios.app.ui.vessel.VesselDial
import com.bios.app.ui.vessel.vesselWatchGroups
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Vessel Watch dial catalog (#389) against the manifesto
 * constraints that the surface must never silently break:
 *  - no composite "vessel score" — dials carry no aggregable numeric field;
 *  - the agency-edge gaps are named (PLANNED), not hidden;
 *  - each epigenetic clock is a standalone dial, never composed into one age.
 *
 * Every key referenced in the catalog is an enum constant, so a renamed or
 * removed MetricType breaks compilation rather than dropping a dial unnoticed.
 */
class VesselWatchCatalogTest {

    @Test
    fun catalog_renders_the_six_spec_groups_in_order() {
        val titles = vesselWatchGroups.map { it.title.first() }
        assertEquals(listOf('A', 'B', 'C', 'D', 'E', 'F'), titles)
    }

    @Test
    fun blood_pressure_dial_reads_systolic_and_diastolic_and_needs_entry() {
        val bp = allDials().first { it.label == "Blood pressure" }
        assertEquals(MetricType.BLOOD_PRESSURE_SYSTOLIC, bp.primaryKey)
        assertEquals(MetricType.BLOOD_PRESSURE_DIASTOLIC, bp.secondaryKey)
        assertEquals(DialAvailability.NEEDS_ENTRY, bp.availability)
    }

    @Test
    fun every_epigenetic_clock_is_a_standalone_dial() {
        // The spec's hard line: clocks are "displayed standalone, never
        // composed." Each must appear as its own dial keyed to its own metric —
        // there must be no single dial that merges several clock keys.
        val clockKeys = setOf(
            MetricType.EPIGENETIC_AGE_DUNEDIN_PACE,
            MetricType.EPIGENETIC_AGE_GRIM,
            MetricType.EPIGENETIC_AGE_PHENO,
            MetricType.EPIGENETIC_AGE_HORVATH,
        )
        val clockDials = allDials().filter { it.primaryKey in clockKeys }
        assertEquals("one dial per clock", clockKeys.size, clockDials.size)
        // No clock dial borrows a second clock as its secondary key.
        for (dial in clockDials) {
            assertTrue(
                "${dial.label} must not compose a second clock",
                dial.secondaryKey == null || dial.secondaryKey !in clockKeys,
            )
        }
    }

    @Test
    fun agency_edge_gaps_are_named_not_hidden() {
        // Group E is the honest gap. Its dials are not yet instrumented, so they
        // must render as explicit PLANNED gaps with no value key — never faked
        // and never omitted.
        val groupE = vesselWatchGroups.first { it.title.startsWith("E") }
        assertTrue("group E must name dials", groupE.dials.isNotEmpty())
        for (dial in groupE.dials) {
            assertEquals(DialAvailability.PLANNED, dial.availability)
            assertNull("planned dials carry no live key", dial.primaryKey)
        }
    }

    @Test
    fun only_blood_pressure_uses_a_secondary_key() {
        // secondaryKey exists solely to render sys/dia. Any other dial carrying
        // a secondary key would be a back-door into composing two metrics.
        val withSecondary = allDials().filter { it.secondaryKey != null }
        assertEquals(listOf("Blood pressure"), withSecondary.map { it.label })
    }

    private fun allDials(): List<VesselDial> = vesselWatchGroups.flatMap { it.dials }
}
