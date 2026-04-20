package com.bios.app

import com.bios.app.engine.PpgValidation
import org.junit.Assert.*
import org.junit.Test

class PpgValidationTest {

    @Test
    fun `perfect agreement yields zero MAE, bias, and RMSE`() {
        val camera = listOf(30.0, 42.0, 55.0, 28.0, 49.0)
        val ref = camera.toList()
        val r = PpgValidation.compare(camera, ref)

        assertEquals(5, r.n)
        assertEquals(0.0, r.mae, 0.001)
        assertEquals(0.0, r.bias, 0.001)
        assertEquals(0.0, r.rmse, 0.001)
        assertEquals(0.0, r.maxAbsError, 0.001)
        assertEquals(PpgValidation.Verdict.GOOD, r.verdict)
        assertEquals(1.0, r.withinThresholdFraction, 0.001)
    }

    @Test
    fun `positive bias means camera over-reads`() {
        // Camera reads +5 ms on every sample.
        val ref = listOf(30.0, 40.0, 50.0)
        val camera = ref.map { it + 5.0 }
        val r = PpgValidation.compare(camera, ref)

        assertEquals(5.0, r.mae, 0.001)
        assertEquals(5.0, r.bias, 0.001)   // signed: camera − reference = +5
        assertEquals(5.0, r.rmse, 0.001)
    }

    @Test
    fun `negative bias means camera under-reads`() {
        val ref = listOf(30.0, 40.0, 50.0)
        val camera = ref.map { it - 5.0 }
        val r = PpgValidation.compare(camera, ref)

        assertEquals(5.0, r.mae, 0.001)
        assertEquals(-5.0, r.bias, 0.001)
    }

    @Test
    fun `MAE is the mean of absolute errors`() {
        val camera = listOf(35.0, 40.0, 55.0)
        val ref = listOf(30.0, 42.0, 50.0)  // abs errors: 5, 2, 5 → MAE 4.0
        val r = PpgValidation.compare(camera, ref)
        assertEquals(4.0, r.mae, 0.001)
    }

    @Test
    fun `RMSE penalises large deviations more than MAE`() {
        // abs errors: 1, 1, 10 → MAE 4.0, RMSE sqrt((1+1+100)/3) ≈ 5.87
        val camera = listOf(31.0, 41.0, 60.0)
        val ref = listOf(30.0, 40.0, 50.0)
        val r = PpgValidation.compare(camera, ref)
        assertEquals(4.0, r.mae, 0.001)
        assertTrue("RMSE ${r.rmse} should exceed MAE ${r.mae}", r.rmse > r.mae)
    }

    @Test
    fun `withinThreshold counts pairs inside the typical threshold`() {
        // abs errors: 2, 5, 20 → 2 of 3 within 8 ms threshold
        val camera = listOf(32.0, 45.0, 70.0)
        val ref = listOf(30.0, 40.0, 50.0)
        val r = PpgValidation.compare(camera, ref)

        assertEquals(2, r.withinThresholdCount)
        assertEquals(2.0 / 3.0, r.withinThresholdFraction, 0.001)
    }

    // -- Verdict thresholds --

    @Test
    fun `verdict is GOOD when MAE under 8 ms`() {
        val camera = listOf(30.0, 40.0, 50.0)
        val ref = listOf(35.0, 43.0, 53.0)  // abs errors: 5, 3, 3 → MAE ~3.67
        assertEquals(PpgValidation.Verdict.GOOD, PpgValidation.compare(camera, ref).verdict)
    }

    @Test
    fun `verdict is ACCEPTABLE when MAE between 8 and 15 ms`() {
        val camera = listOf(30.0, 40.0, 50.0)
        val ref = listOf(40.0, 51.0, 61.0)  // abs errors: 10, 11, 11 → MAE ~10.67
        assertEquals(PpgValidation.Verdict.ACCEPTABLE, PpgValidation.compare(camera, ref).verdict)
    }

    @Test
    fun `verdict is POOR when MAE exceeds 15 ms`() {
        val camera = listOf(30.0, 40.0, 50.0)
        val ref = listOf(50.0, 60.0, 70.0)  // abs errors: 20, 20, 20 → MAE 20
        assertEquals(PpgValidation.Verdict.POOR, PpgValidation.compare(camera, ref).verdict)
    }

    // -- Input validation --

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched list lengths throw`() {
        PpgValidation.compare(listOf(30.0, 40.0), listOf(30.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty lists throw`() {
        PpgValidation.compare(emptyList(), emptyList())
    }
}
