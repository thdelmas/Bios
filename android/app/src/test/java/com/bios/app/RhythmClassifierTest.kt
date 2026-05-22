package com.bios.app

import com.bios.app.engine.RhythmClassifier
import com.bios.app.engine.RhythmClassifier.WindowVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.random.Random

/**
 * Tests the PPG-derived AFib rhythm classifier (#180, cardiology audit §2.1).
 *
 * Synthetic test fixtures:
 *  - Regular sinus rhythm: RR series with mild respiratory sinus arrhythmia
 *    (cosine modulation ±20 ms around 800 ms). SD1/SD2 stays low; sample
 *    entropy stays low; turning-point ratio is low because RSA is smooth.
 *  - Irregularly irregular AFib: RR series with the canonical Lorenz-plot
 *    scatter shape — large random jumps that produce a circular cloud
 *    rather than a cigar. SD1/SD2 → 1, sample entropy elevated, ΔRR
 *    turning-point ratio elevated.
 */
class RhythmClassifierTest {

    // ---- synthetic RR-series fixtures ----

    /**
     * Regular sinus rhythm with respiratory sinus arrhythmia: 60 RR intervals
     * around 800 ms with smooth ±20 ms cosine modulation. SD1/SD2 < 0.5 by
     * construction (RSA is the textbook "cigar" Poincaré plot).
     */
    private fun regularRhythm(n: Int = 60, meanMs: Double = 800.0, rsaAmp: Double = 20.0): List<Double> {
        return (0 until n).map { i ->
            meanMs + rsaAmp * cos(2.0 * Math.PI * i / 12.0)
        }
    }

    /**
     * Irregularly irregular AFib pattern: uniformly random RR intervals
     * across a 400–1200 ms range. This is the Lorenz-plot scatter shape
     * cited in the audit — no temporal autocorrelation, no respiratory
     * structure, SD1 ≈ SD2 (circular cloud), elevated sample entropy,
     * elevated turning-point ratio. The fixed seed keeps the test
     * deterministic.
     */
    private fun afibLikeRhythm(n: Int = 60, seed: Long = 42L): List<Double> {
        val rng = Random(seed)
        return (0 until n).map { 400.0 + rng.nextDouble() * 800.0 }
    }

    // ---- per-window classification ----

    @Test
    fun `regular sinus rhythm with RSA is scored REGULAR`() {
        val result = RhythmClassifier.classify(regularRhythm())
        assertEquals(
            "Smooth RSA modulation must not trip the irregularly-irregular gate (sd1/sd2=${result.sd1OverSd2}, sampEn=${result.sampleEntropy}, tpr=${result.turningPointRatio})",
            WindowVerdict.REGULAR, result.verdict
        )
        assertFalse(result.irregular)
    }

    @Test
    fun `irregularly irregular AFib-like rhythm is scored IRREGULARLY_IRREGULAR`() {
        val result = RhythmClassifier.classify(afibLikeRhythm())
        assertEquals(
            "Uniform-random RR cloud must cross the SD1/SD2 + sample entropy + TPR gates simultaneously (sd1/sd2=${result.sd1OverSd2}, sampEn=${result.sampleEntropy}, tpr=${result.turningPointRatio})",
            WindowVerdict.IRREGULARLY_IRREGULAR, result.verdict
        )
        assertTrue(result.irregular)
    }

    @Test
    fun `short RR series returns INSUFFICIENT_DATA`() {
        val tooFew = listOf(800.0, 810.0, 790.0, 805.0, 800.0)  // 5 intervals
        val result = RhythmClassifier.classify(tooFew)
        assertEquals(WindowVerdict.INSUFFICIENT_DATA, result.verdict)
        assertFalse(result.irregular)
        assertEquals(5, result.intervalCount)
    }

    @Test
    fun `constant RR series is REGULAR (zero variance handled)`() {
        // A perfectly constant series is the maximally regular case; the
        // classifier must not crash on the zero-SD div in the entropy step.
        val constant = List(60) { 800.0 }
        val result = RhythmClassifier.classify(constant)
        assertEquals(WindowVerdict.REGULAR, result.verdict)
        assertEquals(0.0, result.sampleEntropy, 1e-9)
    }

    // ---- feature characteristics ----

    @Test
    fun `Poincare SD1 over SD2 ratio is markedly higher for AFib-like rhythms`() {
        // The Poincaré ratio is the most diagnostic of the three features —
        // AFib flattens the cigar into a cloud. The numerical separation
        // should be obvious even without crossing the absolute cutoff.
        val regular = RhythmClassifier.classify(regularRhythm())
        val afib = RhythmClassifier.classify(afibLikeRhythm())
        assertTrue(
            "AFib SD1/SD2 (${afib.sd1OverSd2}) should be much higher than regular SD1/SD2 (${regular.sd1OverSd2})",
            afib.sd1OverSd2 > regular.sd1OverSd2 + 0.2
        )
    }

    @Test
    fun `sample entropy is markedly higher for AFib-like rhythms`() {
        val regular = RhythmClassifier.classify(regularRhythm())
        val afib = RhythmClassifier.classify(afibLikeRhythm())
        assertTrue(
            "AFib sample entropy (${afib.sampleEntropy}) should exceed regular sample entropy (${regular.sampleEntropy})",
            afib.sampleEntropy > regular.sampleEntropy
        )
    }

    @Test
    fun `turning-point ratio approaches the iid expectation for AFib-like rhythms`() {
        // i.i.d. Gaussian / uniform noise has expected TPR of ~2/3. The
        // AFib-like fixture should be substantially above the smooth-RSA
        // ratio (which has only a handful of true turning points).
        val regular = RhythmClassifier.classify(regularRhythm())
        val afib = RhythmClassifier.classify(afibLikeRhythm())
        assertTrue(
            "AFib TPR (${afib.turningPointRatio}) should exceed regular TPR (${regular.turningPointRatio})",
            afib.turningPointRatio > regular.turningPointRatio
        )
    }

    // ---- burden aggregation ----

    @Test
    fun `burdenPercent is fraction of irregularly-irregular over evaluable windows`() {
        val windows = listOf(
            RhythmClassifier.classify(regularRhythm()),     // REGULAR
            RhythmClassifier.classify(regularRhythm(n = 50)), // REGULAR
            RhythmClassifier.classify(afibLikeRhythm()),     // IRREGULAR
            RhythmClassifier.classify(afibLikeRhythm(seed = 7L)), // IRREGULAR
        )
        // 2 of 4 evaluable windows are irregular → 50 %.
        assertEquals(50.0, RhythmClassifier.burdenPercent(windows), 1e-9)
    }

    @Test
    fun `burdenPercent excludes INSUFFICIENT_DATA windows from both numerator and denominator`() {
        val windows = listOf(
            RhythmClassifier.classify(regularRhythm()),
            RhythmClassifier.classify(afibLikeRhythm()),
            // Below threshold — should be skipped.
            RhythmClassifier.classify(listOf(800.0, 810.0, 790.0)),
        )
        // 1 of 2 evaluable windows irregular → 50 %.
        assertEquals(50.0, RhythmClassifier.burdenPercent(windows), 1e-9)
    }

    @Test
    fun `burdenPercent is zero when no evaluable windows are present`() {
        val windows = listOf(RhythmClassifier.classify(listOf(800.0, 810.0)))
        assertEquals(0.0, RhythmClassifier.burdenPercent(windows), 1e-9)
    }

    @Test
    fun `burdenPercent is 100 when every evaluable window is irregularly irregular`() {
        val windows = listOf(
            RhythmClassifier.classify(afibLikeRhythm(seed = 1L)),
            RhythmClassifier.classify(afibLikeRhythm(seed = 2L)),
            RhythmClassifier.classify(afibLikeRhythm(seed = 3L)),
        )
        assertEquals(100.0, RhythmClassifier.burdenPercent(windows), 1e-9)
    }

    // ---- classifier cutoffs are literature-anchored ----

    @Test
    fun `SD1 over SD2 cutoff is at the Tateno-Glass operating point`() {
        // Documented at 0.5 — the PPG-anchored cutoff in the smartwatch
        // literature, slightly below the ECG-anchored 0.6 from Tateno-Glass.
        assertEquals(0.5, RhythmClassifier.SD1_OVER_SD2_CUTOFF, 1e-9)
    }

    @Test
    fun `sample entropy cutoff is at the AFib literature anchor`() {
        assertEquals(1.4, RhythmClassifier.SAMPLE_ENTROPY_CUTOFF, 1e-9)
    }

    @Test
    fun `turning-point ratio cutoff sits between sinus and random expectations`() {
        // 0.55 — below the i.i.d. expectation (≈0.667) but above what a
        // smoothly modulated RSA series produces.
        assertEquals(0.55, RhythmClassifier.TURNING_POINT_RATIO_CUTOFF, 1e-9)
        assertTrue(
            "TPR cutoff must sit below the i.i.d. expectation of 2/3",
            RhythmClassifier.TURNING_POINT_RATIO_CUTOFF < 2.0 / 3.0
        )
    }

    @Test
    fun `result reports the interval count for diagnostics`() {
        val rr = regularRhythm()
        val result = RhythmClassifier.classify(rr)
        assertEquals(rr.size, result.intervalCount)
    }

    @Test
    fun `verdict and irregular flag stay consistent`() {
        // The irregular convenience flag should equal verdict ==
        // IRREGULARLY_IRREGULAR, not match REGULAR or INSUFFICIENT_DATA.
        val regular = RhythmClassifier.classify(regularRhythm())
        val afib = RhythmClassifier.classify(afibLikeRhythm())
        assertEquals(regular.verdict == WindowVerdict.IRREGULARLY_IRREGULAR, regular.irregular)
        assertEquals(afib.verdict == WindowVerdict.IRREGULARLY_IRREGULAR, afib.irregular)
        // And the two regimes produce different verdicts.
        assertNotEquals(regular.verdict, afib.verdict)
    }
}
