package com.bios.app

import com.bios.app.ui.clinical.MeasurementPreset
import com.bios.app.ui.clinical.defaultRowsFor
import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the home-cuff blood-pressure entry preset (#390). Blood pressure is
 * the Vessel Watch's top missing dial; the dedicated `blood_pressure_entry`
 * route lands the owner on this preset so a cuff reading is one tap rather
 * than a full triage form.
 *
 * The rows route through the same [com.bios.app.data.ManualReadingRepo.addBundle]
 * path as every other clinical reading → SELF_REPORTED → engine-isolated, so a
 * manually-typed cuff value never poisons a sensor baseline.
 */
class BloodPressureEntryTest {

    @Test
    fun blood_pressure_preset_is_systolic_diastolic_pulse() {
        // A home cuff reports exactly these three numbers in one measurement.
        // Order matches how a monitor displays them (SYS over DIA, pulse last).
        val expected = listOf(
            MetricType.BLOOD_PRESSURE_SYSTOLIC,
            MetricType.BLOOD_PRESSURE_DIASTOLIC,
            MetricType.HEART_RATE,
        )
        val actual = defaultRowsFor(MeasurementPreset.BLOOD_PRESSURE).map { it.metric }
        assertEquals(expected, actual)
    }

    @Test
    fun blood_pressure_preset_metrics_allow_manual_entry() {
        // ManualReadingRepo.add require()s allowsManualEntry; if any preset row
        // lost the flag the save would throw at runtime instead of failing here.
        for (row in defaultRowsFor(MeasurementPreset.BLOOD_PRESSURE)) {
            assertTrue(
                "${row.metric.key} must allow manual entry — blood pressure preset row",
                row.metric.allowsManualEntry,
            )
        }
    }

    @Test
    fun blood_pressure_units_are_mmhg_and_pulse_is_bpm() {
        // The entry field shows the unit suffix; drift here mislabels the cuff
        // numbers the owner reads back in trends and FHIR export.
        assertEquals(MetricUnit.MMHG, MetricType.BLOOD_PRESSURE_SYSTOLIC.unit)
        assertEquals(MetricUnit.MMHG, MetricType.BLOOD_PRESSURE_DIASTOLIC.unit)
        assertEquals(MetricUnit.BPM, MetricType.HEART_RATE.unit)
    }

    @Test
    fun filled_pressure_rows_convert_to_bundle_readings() {
        // The screen maps rows to BundleReadings before addBundle. A typed
        // 128/82 @ 70 must survive that conversion as three plain numeric
        // readings (no original-scale provenance — that's GCS-only).
        val rows = defaultRowsFor(MeasurementPreset.BLOOD_PRESSURE).mapIndexed { i, row ->
            row.copy(valueText = listOf("128", "82", "70")[i])
        }
        val bundle = rows.mapNotNull { it.toBundleReading() }
        assertEquals(3, bundle.size)
        assertEquals(128.0, bundle[0].value, 0.0)
        assertEquals(82.0, bundle[1].value, 0.0)
        assertEquals(70.0, bundle[2].value, 0.0)
        for (b in bundle) {
            assertNull("BP rows carry no original scale", b.originalScale)
            assertNull("BP rows carry no original value", b.originalValue)
        }
    }

    @Test
    fun empty_pressure_rows_drop_out_of_the_bundle() {
        // Default (untouched) rows have blank valueText and must not produce a
        // zero-valued reading; only filled rows convert.
        val bundle = defaultRowsFor(MeasurementPreset.BLOOD_PRESSURE)
            .mapNotNull { it.toBundleReading() }
        assertTrue("Untouched preset rows must not save", bundle.isEmpty())
    }
}
