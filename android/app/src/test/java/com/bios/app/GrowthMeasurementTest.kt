package com.bios.app

import com.bios.app.model.GrowthMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-logic guards for the [GrowthMeasurement] model — specifically the
 * BMI derivation helper that the repository and the manual-entry surface
 * both depend on (#199, audit gap §2.7).
 */
class GrowthMeasurementTest {

    @Test
    fun bmi_from_height_and_weight_is_kg_over_m_squared() {
        // 70 kg, 175 cm → BMI ≈ 22.86
        val bmi = GrowthMeasurement.bmiFrom(heightCm = 175f, weightKg = 70f)
        assertEquals(22.857f, bmi!!, 0.01f)
    }

    @Test
    fun bmi_handles_paediatric_values() {
        // 12 kg, 85 cm → BMI ≈ 16.61
        val bmi = GrowthMeasurement.bmiFrom(heightCm = 85f, weightKg = 12f)
        assertEquals(16.61f, bmi!!, 0.01f)
    }

    @Test
    fun bmi_returns_null_when_either_input_missing() {
        assertNull(GrowthMeasurement.bmiFrom(heightCm = null, weightKg = 70f))
        assertNull(GrowthMeasurement.bmiFrom(heightCm = 175f, weightKg = null))
        assertNull(GrowthMeasurement.bmiFrom(heightCm = null, weightKg = null))
    }

    @Test
    fun bmi_returns_null_for_non_positive_inputs() {
        assertNull(GrowthMeasurement.bmiFrom(heightCm = 0f, weightKg = 70f))
        assertNull(GrowthMeasurement.bmiFrom(heightCm = 175f, weightKg = 0f))
        assertNull(GrowthMeasurement.bmiFrom(heightCm = -10f, weightKg = 70f))
    }
}
