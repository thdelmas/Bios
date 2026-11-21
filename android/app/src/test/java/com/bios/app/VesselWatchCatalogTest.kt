package com.bios.app

import com.bios.app.ui.vessel.DialAvailability
import com.bios.app.ui.vessel.VesselDial
import com.bios.app.ui.vessel.vesselWatchGroups
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Vessel Watch dial catalog: every dial is one measurement with
 * a measurement's name, and no dial composes two of them.
 */
class VesselWatchCatalogTest {

    @Test
    fun every_dial_reads_its_own_metric() {
        val keys = allDials().map { it.primaryKey }
        assertEquals("no metric appears on two dials", keys.size, keys.toSet().size)
    }

    @Test
    fun blood_pressure_dial_reads_systolic_and_diastolic_and_needs_entry() {
        val bp = allDials().first { it.label == "Blood pressure" }
        assertEquals(MetricType.BLOOD_PRESSURE_SYSTOLIC, bp.primaryKey)
        assertEquals(MetricType.BLOOD_PRESSURE_DIASTOLIC, bp.secondaryKey)
        assertEquals(DialAvailability.NEEDS_ENTRY, bp.availability)
    }

    @Test
    fun only_blood_pressure_uses_a_secondary_key() {
        // secondaryKey exists solely to render sys/dia. Any other dial carrying
        // a secondary key would be a back-door into composing two metrics.
        val withSecondary = allDials().filter { it.secondaryKey != null }
        assertEquals(listOf("Blood pressure"), withSecondary.map { it.label })
    }

    @Test
    fun no_title_label_or_note_says_what_a_value_means() {
        val text = vesselWatchGroups.joinToString(" ") { g ->
            g.title + " " + g.dials.joinToString(" ") { "${it.label} ${it.note}" }
        }.lowercase()
        for (word in listOf("risk", "aging", "reserve", "stiffness", "substrate", "biological age", "symptom", "planned")) {
            assertFalse("'$word' in the catalog", text.contains(word))
        }
    }

    @Test
    fun a_score_made_elsewhere_names_who_made_it() {
        val recovery = allDials().first { it.primaryKey == MetricType.RECOVERY_SCORE }
        assertTrue(recovery.label, recovery.label.contains("reported by your wearable"))
    }

    private fun allDials(): List<VesselDial> = vesselWatchGroups.flatMap { it.dials }
}
