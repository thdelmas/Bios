package com.bios.app

import com.bios.app.engine.HrvAnalyzer
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

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

    @Test
    fun `lnRmssd matches the natural log of RMSSD when positive`() {
        val ibis = listOf(800.0, 815.0, 795.0, 810.0, 805.0, 820.0, 790.0)
        val result = HrvAnalyzer.analyze(ibis)!!
        assertTrue(result.rmssd > 0.0)
        assertEquals(ln(result.rmssd), result.lnRmssd, 1e-9)
    }

    @Test
    fun `lnRmssd is zero when RMSSD is zero`() {
        val ibis = listOf(800.0, 800.0, 800.0, 800.0, 800.0)
        val result = HrvAnalyzer.analyze(ibis)!!
        assertEquals(0.0, result.rmssd, 0.0)
        assertEquals(0.0, result.lnRmssd, 0.0)
    }

    // -- Baevsky stress index --

    @Test
    fun `stress index is zero when all IBIs are identical`() {
        // MxDMn = 0, SI undefined → return 0.
        val ibis = listOf(800.0, 800.0, 800.0, 800.0, 800.0)
        assertEquals(0.0, HrvAnalyzer.computeStressIndex(ibis), 0.0)
    }

    @Test
    fun `stress index matches Baevsky formula for known IBIs`() {
        // 800,805,810,815,820 → all in 50ms bin 16 (covers [800, 850)).
        // Mo bin midpoint = (16 + 0.5) * 50ms = 825ms = 0.825s.
        // AMo = 5/5 × 100 = 100%. MxDMn = (820 - 800)/1000 = 0.020s.
        // SI = 100 / (2 × 0.825 × 0.020) = 100 / 0.033 ≈ 3030.3.
        val ibis = listOf(800.0, 805.0, 810.0, 815.0, 820.0)
        val si = HrvAnalyzer.computeStressIndex(ibis)
        assertEquals(3030.30, si, 0.1)
    }

    @Test
    fun `stress index falls as variability rises`() {
        // Same beat count; the spread-out tachogram has both smaller AMo and
        // larger MxDMn, so the Baevsky SI must drop.
        val tight = listOf(800.0, 802.0, 798.0, 805.0, 795.0, 800.0)
        val spread = listOf(700.0, 950.0, 720.0, 880.0, 750.0, 900.0)
        assertTrue(
            HrvAnalyzer.computeStressIndex(tight) > HrvAnalyzer.computeStressIndex(spread)
        )
    }

    @Test
    fun `stress index is zero for fewer than two IBIs`() {
        assertEquals(0.0, HrvAnalyzer.computeStressIndex(listOf(800.0)), 0.0)
        assertEquals(0.0, HrvAnalyzer.computeStressIndex(emptyList()), 0.0)
    }

    // -- Median-anchored artifact rejection (#367) --

    @Test
    fun `median-anchored filter keeps clean IBIs`() {
        val ibis = listOf(800.0, 810.0, 795.0, 820.0, 805.0)
        val clean = HrvAnalyzer.rejectArtifactsMedianAnchored(ibis)
        assertEquals(5, clean.size)
    }

    @Test
    fun `median-anchored survives initial outlier where sequential collapses`() {
        // The sequential Malik filter accepts the first IBI unconditionally
        // (no predecessor to compare against). If that first IBI is an
        // AE-convergence artifact (short, ~350 ms), the rule anchors on it
        // and rejects every subsequent legitimate ~800 ms IBI as a > 100 %
        // change. The median-anchored rule rejects the artifact itself
        // because the global median is robust to a single outlier.
        val ibis = listOf(350.0, 800.0, 810.0, 795.0, 805.0, 815.0, 800.0, 790.0)
        val sequential = HrvAnalyzer.rejectArtifacts(ibis)
        val medianAnchored = HrvAnalyzer.rejectArtifactsMedianAnchored(ibis)
        // Sequential anchors on 350 and rejects everything after.
        assertEquals("sequential anchors on the first-IBI artifact", 1, sequential.size)
        assertEquals(350.0, sequential.first(), 0.01)
        // Median-anchored rejects only the 350 ms outlier.
        assertEquals(7, medianAnchored.size)
        assertFalse("artifact must be rejected", medianAnchored.contains(350.0))
    }

    @Test
    fun `median-anchored drops physiologically impossible IBIs`() {
        val ibis = listOf(800.0, 200.0, 810.0, 3000.0, 805.0)
        val clean = HrvAnalyzer.rejectArtifactsMedianAnchored(ibis)
        assertEquals(3, clean.size)
        assertFalse(clean.contains(200.0))
        assertFalse(clean.contains(3000.0))
    }

    @Test
    fun `analyze with median-anchored strategy uses the median-anchored filter`() {
        // Same first-IBI artifact scenario — sequential anchors on the
        // outlier and analyse() returns null (too few clean IBIs survive);
        // median-anchored produces a usable result.
        val ibis = listOf(350.0, 800.0, 810.0, 795.0, 805.0, 815.0, 800.0, 790.0)
        val viaSequential = HrvAnalyzer.analyze(
            ibis, HrvAnalyzer.ArtifactRejection.SEQUENTIAL_MALIK
        )
        val viaMedian = HrvAnalyzer.analyze(
            ibis, HrvAnalyzer.ArtifactRejection.MEDIAN_ANCHORED
        )
        assertNull("sequential anchors on the outlier and yields too few IBIs", viaSequential)
        assertNotNull(viaMedian)
        assertTrue(
            "median-anchored should keep 7 of 8 IBIs (cleanIbiCount=${viaMedian!!.cleanIbiCount})",
            viaMedian.cleanIbiCount == 7,
        )
    }

    @Test
    fun `analyze default rejection is sequential Malik (backwards compatible)`() {
        // The historical default — devices on PpgDeviceProfiles.DEFAULT must
        // see no behaviour change relative to pre-#367 builds.
        val ibis = listOf(800.0, 815.0, 795.0, 810.0, 805.0, 820.0, 790.0)
        val defaulted = HrvAnalyzer.analyze(ibis)!!
        val explicit = HrvAnalyzer.analyze(
            ibis, HrvAnalyzer.ArtifactRejection.SEQUENTIAL_MALIK
        )!!
        assertEquals(explicit.cleanIbiCount, defaulted.cleanIbiCount)
        assertEquals(explicit.meanIbiMs, defaulted.meanIbiMs, 1e-9)
    }

    @Test
    fun `analyze populates stressIndex consistent with direct call`() {
        val ibis = listOf(800.0, 815.0, 795.0, 810.0, 805.0, 820.0, 790.0)
        val result = HrvAnalyzer.analyze(ibis)!!
        val direct = HrvAnalyzer.computeStressIndex(
            HrvAnalyzer.rejectArtifacts(ibis)
        )
        assertEquals(direct, result.stressIndex, 1e-9)
        assertTrue(result.stressIndex > 0.0)
    }
}
