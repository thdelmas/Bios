package com.bios.app

import com.bios.app.engine.TremorAnalyzer
import com.bios.app.engine.TremorAnalyzer.Posture
import com.bios.app.engine.TremorAnalyzer.TremorClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tests the phone-accelerometer tremor pipeline (#206, NEUROLOGY_POV §2.4).
 *
 * Synthetic fixtures:
 *  - 5 Hz rest tremor: pure sinusoid in the 4–6 Hz Parkinsonian rest sub-band,
 *    REST posture. The peak finder must lock onto 5 Hz; the classifier must
 *    return REST_PARKINSONIAN.
 *  - 8 Hz postural tremor: pure sinusoid in the 6–12 Hz essential tremor band,
 *    POSTURAL_OR_ACTION posture. The classifier must distinguish this from the
 *    Parkinsonian case.
 *  - Phone-on-table noise: low-amplitude noise. The analyzer must score
 *    NON_PATHOLOGIC.
 *  - Too-few samples: must return INSUFFICIENT_DATA, never crash.
 */
class TremorAnalyzerTest {

    /** Generates `durationSec` of a pure cosine tremor signal at `frequencyHz`. */
    private fun syntheticTremor(
        frequencyHz: Double,
        amplitude: Double,
        sampleRateHz: Double = 50.0,
        durationSec: Double = 4.0,
        noise: Double = 0.0,
        seed: Long = 7L,
    ): DoubleArray {
        val n = (sampleRateHz * durationSec).toInt()
        val rng = Random(seed)
        return DoubleArray(n) { i ->
            val t = i / sampleRateHz
            amplitude * sin(2.0 * PI * frequencyHz * t) +
                if (noise > 0.0) (rng.nextDouble() - 0.5) * 2.0 * noise else 0.0
        }
    }

    // ---- spectral lock-on ----

    @Test
    fun detects_5Hz_rest_tremor_as_REST_PARKINSONIAN() {
        // Pure 5 Hz, REST posture, amplitude well above the noise floor.
        val samples = syntheticTremor(frequencyHz = 5.0, amplitude = 0.4)
        val ep = TremorAnalyzer.analyze(samples, sampleRateHz = 50.0, posture = Posture.REST)
        // The Goertzel grid resolution is 0.25 Hz; the peak should land
        // within one bin of 5.0 Hz.
        assertEquals("peak frequency must lock to 5 Hz", 5.0, ep.peakFrequencyHz, 0.3)
        assertTrue("amplitude must rise above the noise floor (${ep.rmsAmplitude})",
            ep.rmsAmplitude > TremorAnalyzer.MIN_RMS_AMPLITUDE)
        assertEquals(
            "5 Hz at REST is the Parkinsonian rest-tremor band",
            TremorClassification.REST_PARKINSONIAN,
            ep.classification,
        )
    }

    @Test
    fun distinguishes_8Hz_essential_from_5Hz_parkinsonian() {
        // 8 Hz lies in the essential-tremor range and above the
        // Parkinsonian rest-tremor 4–6 Hz sub-band.
        val rest = TremorAnalyzer.analyze(
            samples = syntheticTremor(frequencyHz = 5.0, amplitude = 0.4),
            sampleRateHz = 50.0,
            posture = Posture.REST,
        )
        val postural = TremorAnalyzer.analyze(
            samples = syntheticTremor(frequencyHz = 8.0, amplitude = 0.4),
            sampleRateHz = 50.0,
            posture = Posture.POSTURAL_OR_ACTION,
        )

        assertEquals(5.0, rest.peakFrequencyHz, 0.3)
        assertEquals(8.0, postural.peakFrequencyHz, 0.3)
        assertEquals(TremorClassification.REST_PARKINSONIAN, rest.classification)
        assertEquals(TremorClassification.POSTURAL_ESSENTIAL, postural.classification)
        assertFalse(
            "5 Hz Parkinsonian and 8 Hz essential must not collapse to the same verdict",
            rest.classification == postural.classification,
        )
    }

    @Test
    fun classifies_8Hz_at_REST_as_AMBIGUOUS_not_parkinsonian() {
        // 8 Hz is in band but outside the 4–6 Hz Parkinsonian rest
        // sub-band — even with REST posture, the classifier must not
        // assign REST_PARKINSONIAN.
        val ep = TremorAnalyzer.analyze(
            samples = syntheticTremor(frequencyHz = 8.0, amplitude = 0.4),
            sampleRateHz = 50.0,
            posture = Posture.REST,
        )
        assertEquals(TremorClassification.AMBIGUOUS, ep.classification)
    }

    @Test
    fun classifies_5Hz_postural_as_POSTURAL_ESSENTIAL() {
        // 5 Hz at POSTURAL_OR_ACTION posture is essential-tremor-shaped
        // even though the frequency overlaps the Parkinsonian rest band.
        val ep = TremorAnalyzer.analyze(
            samples = syntheticTremor(frequencyHz = 5.0, amplitude = 0.4),
            sampleRateHz = 50.0,
            posture = Posture.POSTURAL_OR_ACTION,
        )
        assertEquals(TremorClassification.POSTURAL_ESSENTIAL, ep.classification)
    }

    // ---- noise rejection ----

    @Test
    fun phone_on_table_noise_is_scored_NON_PATHOLOGIC() {
        // Low-amplitude noise without a structured tremor peak.
        val rng = Random(13L)
        val samples = DoubleArray(200) { (rng.nextDouble() - 0.5) * 2.0 * 0.005 }
        val ep = TremorAnalyzer.analyze(samples, sampleRateHz = 50.0, posture = Posture.REST)
        assertEquals(TremorClassification.NON_PATHOLOGIC, ep.classification)
    }

    @Test
    fun out_of_band_signal_is_NON_PATHOLOGIC() {
        // 1 Hz arm-swing during walking is below the 3 Hz tremor floor;
        // the analyzer must not flag it.
        val samples = syntheticTremor(frequencyHz = 1.0, amplitude = 0.5)
        val ep = TremorAnalyzer.analyze(samples, sampleRateHz = 50.0, posture = Posture.REST)
        assertEquals(TremorClassification.NON_PATHOLOGIC, ep.classification)
    }

    // ---- guards ----

    @Test
    fun too_few_samples_returns_INSUFFICIENT_DATA() {
        val tooFew = DoubleArray(30) { it.toDouble() }
        val ep = TremorAnalyzer.analyze(tooFew, sampleRateHz = 50.0, posture = Posture.REST)
        assertEquals(TremorClassification.INSUFFICIENT_DATA, ep.classification)
    }

    @Test
    fun below_nyquist_sample_rate_returns_INSUFFICIENT_DATA() {
        // 20 Hz sample rate has 10 Hz Nyquist — below the 12 Hz upper
        // band. The analyzer must not pretend to evaluate a window it
        // cannot represent.
        val samples = syntheticTremor(frequencyHz = 5.0, amplitude = 0.4, sampleRateHz = 20.0)
        val ep = TremorAnalyzer.analyze(samples, sampleRateHz = 20.0, posture = Posture.REST)
        assertEquals(TremorClassification.INSUFFICIENT_DATA, ep.classification)
    }

    // ---- classification rules independent of spectral sweep ----

    @Test
    fun classify_4Hz_REST_is_REST_PARKINSONIAN() {
        assertEquals(
            TremorClassification.REST_PARKINSONIAN,
            TremorAnalyzer.classify(4.0, Posture.REST),
        )
    }

    @Test
    fun classify_6Hz_REST_is_REST_PARKINSONIAN() {
        assertEquals(
            TremorClassification.REST_PARKINSONIAN,
            TremorAnalyzer.classify(6.0, Posture.REST),
        )
    }

    @Test
    fun classify_12Hz_POSTURAL_is_POSTURAL_ESSENTIAL() {
        assertEquals(
            TremorClassification.POSTURAL_ESSENTIAL,
            TremorAnalyzer.classify(12.0, Posture.POSTURAL_OR_ACTION),
        )
    }

    @Test
    fun classify_outside_band_is_NON_PATHOLOGIC_regardless_of_posture() {
        assertEquals(TremorClassification.NON_PATHOLOGIC, TremorAnalyzer.classify(2.0, Posture.REST))
        assertEquals(TremorClassification.NON_PATHOLOGIC, TremorAnalyzer.classify(15.0, Posture.POSTURAL_OR_ACTION))
        assertEquals(TremorClassification.NON_PATHOLOGIC, TremorAnalyzer.classify(2.0, Posture.UNKNOWN))
    }

    @Test
    fun unknown_posture_yields_AMBIGUOUS_in_band() {
        assertEquals(
            TremorClassification.AMBIGUOUS,
            TremorAnalyzer.classify(5.0, Posture.UNKNOWN),
        )
    }
}
