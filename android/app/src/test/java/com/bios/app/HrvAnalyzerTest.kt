package com.bios.app

import com.bios.app.engine.HrvAnalyzer
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class HrvAnalyzerTest {

    // -- Artifact rejection --

    @Test
    fun `physiologically valid IBIs are kept`() {
        val ibis = listOf(800.0, 810.0, 795.0, 820.0, 805.0)
        val clean = HrvAnalyzer.rejectArtifacts(ibis)
        assertEquals(5, clean.size)
    }

    @Test
    fun `IBIs below 300ms are rejected`() {
        val ibis = listOf(800.0, 200.0, 810.0)
        val clean = HrvAnalyzer.rejectArtifacts(ibis)
        assertEquals(2, clean.size)
        assertFalse(clean.contains(200.0))
    }

    @Test
    fun `IBIs above 2000ms are rejected`() {
        val ibis = listOf(800.0, 2500.0, 810.0)
        val clean = HrvAnalyzer.rejectArtifacts(ibis)
        // 810 is >20% jump from 800 if the 2500 was skipped? No, 810 vs 800 = 1.25% change, ok
        assertEquals(2, clean.size)
    }

    @Test
    fun `Malik threshold rejects sudden jumps`() {
        // 800ms → 1100ms = 37.5% change, exceeds 20% Malik threshold
        val ibis = listOf(800.0, 1100.0, 810.0)
        val clean = HrvAnalyzer.rejectArtifacts(ibis)
        // 1100 rejected (37.5% change from 800), then 810 vs 800 = 1.25% ok
        assertEquals(2, clean.size)
        assertEquals(800.0, clean[0], 0.01)
        assertEquals(810.0, clean[1], 0.01)
    }

    // -- RMSSD --

    @Test
    fun `RMSSD of constant IBIs is zero`() {
        val ibis = listOf(800.0, 800.0, 800.0, 800.0)
        assertEquals(0.0, HrvAnalyzer.computeRmssd(ibis), 0.001)
    }

    @Test
    fun `RMSSD computed correctly for known values`() {
        // IBIs: 800, 810, 790, 820
        // Successive diffs: 10, -20, 30
        // Squares: 100, 400, 900
        // Mean of squares: 466.67
        // RMSSD = sqrt(466.67) ≈ 21.6
        val ibis = listOf(800.0, 810.0, 790.0, 820.0)
        val rmssd = HrvAnalyzer.computeRmssd(ibis)
        assertEquals(21.6, rmssd, 0.1)
    }

    // -- SDNN --

    @Test
    fun `SDNN of constant IBIs is zero`() {
        val ibis = listOf(800.0, 800.0, 800.0)
        assertEquals(0.0, HrvAnalyzer.computeSdnn(ibis), 0.001)
    }

    @Test
    fun `SDNN computed correctly`() {
        // IBIs: 800, 900 → mean=850, variance=(2500+2500)/2=2500, sdnn=50
        val ibis = listOf(800.0, 900.0)
        assertEquals(50.0, HrvAnalyzer.computeSdnn(ibis), 0.01)
    }

    // -- pNN50 --

    @Test
    fun `pNN50 is zero when all diffs under 50ms`() {
        val ibis = listOf(800.0, 810.0, 820.0, 815.0)
        assertEquals(0.0, HrvAnalyzer.computePnn50(ibis), 0.01)
    }

    @Test
    fun `pNN50 computed correctly`() {
        // Diffs: 60, 10, 70 → 2 out of 3 > 50ms → 66.67%
        val ibis = listOf(800.0, 860.0, 850.0, 920.0)
        assertEquals(66.67, HrvAnalyzer.computePnn50(ibis), 0.1)
    }

    // -- Full analysis --

    @Test
    fun `analyze returns null for too few IBIs`() {
        val result = HrvAnalyzer.analyze(listOf(800.0, 810.0))
        assertNull(result)
    }

    @Test
    fun `analyze returns valid result for clean data`() {
        val ibis = listOf(800.0, 815.0, 795.0, 810.0, 805.0, 820.0, 790.0)
        val result = HrvAnalyzer.analyze(ibis)
        assertNotNull(result)
        result!!

        assertTrue("RMSSD should be positive", result.rmssd > 0)
        assertTrue("SDNN should be positive", result.sdnn > 0)
        assertTrue("pNN50 should be 0-100", result.pnn50 in 0.0..100.0)
        assertTrue("Mean IBI should be ~800ms", abs(result.meanIbiMs - 805.0) < 20)
        assertTrue("Mean HR should be ~75bpm", abs(result.meanHrBpm - 75.0) < 5)
        assertEquals(0, result.artifactsRejected)
    }

    @Test
    fun `analyze reports rejected artifacts`() {
        val ibis = listOf(800.0, 200.0, 810.0, 3000.0, 795.0, 820.0, 805.0)
        val result = HrvAnalyzer.analyze(ibis)
        assertNotNull(result)
        assertTrue("Should have rejected artifacts", result!!.artifactsRejected > 0)
    }
}
