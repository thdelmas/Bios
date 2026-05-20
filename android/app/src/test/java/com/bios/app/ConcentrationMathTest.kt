package com.bios.app

import com.bios.app.engine.ConcentrationMath
import com.bios.app.engine.ConcentrationMath.DoseEvent
import com.bios.app.engine.EliminationKinetics
import com.bios.app.engine.Pharmacokinetics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pins the pharmacokinetic math layer (#136). Pure functions over PK
 * profile + dose list — no DAO, no time-of-day, no Android dependencies.
 *
 * Exercises every limb of the engine: first-order with and without
 * absorption, first-order at the flip-flop limit, zero-order alcohol,
 * the curve sampler, and the threshold-crossing search. Numerical
 * checks use tolerances calibrated to the underlying half-life so the
 * tests don't flake on rounding inside `exp`.
 */
class ConcentrationMathTest {

    private val midnight = 1_700_000_000_000L // arbitrary epoch ms for reproducibility
    private val minute = 60_000L
    private val hour = 60L * minute

    // ---- first-order, instant absorption ----

    @Test
    fun `first-order instant absorption halves at one half-life`() {
        val pk = pkInstant(halfLifeMinutes = 60.0)
        val doses = listOf(DoseEvent(midnight, 100.0))
        val oneHalfLife = ConcentrationMath.amountInBodyAt(pk, doses, midnight + hour)
        // 100 mg × e^(-ln 2) = 50 mg.
        assertNear(50.0, oneHalfLife, 0.5)
    }

    @Test
    fun `first-order instant absorption decays by 75 percent at two half-lives`() {
        val pk = pkInstant(halfLifeMinutes = 60.0)
        val doses = listOf(DoseEvent(midnight, 100.0))
        val twoHalfLives = ConcentrationMath.amountInBodyAt(pk, doses, midnight + 2L * hour)
        assertNear(25.0, twoHalfLives, 0.5)
    }

    @Test
    fun `doses prior to the query time only superpose`() {
        // Two 100mg doses one half-life apart. At the second dose:
        //   dose1 has decayed to 50, dose2 just landed at 100, total 150.
        val pk = pkInstant(halfLifeMinutes = 60.0)
        val doses = listOf(
            DoseEvent(midnight, 100.0),
            DoseEvent(midnight + hour, 100.0),
        )
        val sum = ConcentrationMath.amountInBodyAt(pk, doses, midnight + hour)
        assertNear(150.0, sum, 1.0)
    }

    @Test
    fun `future doses do not contribute`() {
        val pk = pkInstant(halfLifeMinutes = 60.0)
        val doses = listOf(DoseEvent(midnight + hour, 100.0))
        val atZero = ConcentrationMath.amountInBodyAt(pk, doses, midnight)
        assertEquals(0.0, atZero, 1e-9)
    }

    @Test
    fun `bioavailability scales the contribution`() {
        // 50% bioavailable, 100mg dose → behaves like a 50mg dose.
        val pk = pkInstant(halfLifeMinutes = 60.0, bioavailability = 0.5)
        val doses = listOf(DoseEvent(midnight, 100.0))
        val atZero = ConcentrationMath.amountInBodyAt(pk, doses, midnight)
        assertNear(50.0, atZero, 0.5)
    }

    // ---- first-order with absorption (Bateman) ----

    @Test
    fun `oral absorption climbs to a peak then decays`() {
        // Caffeine-shaped profile: 5h elimination, 7-min absorption.
        val pk = Pharmacokinetics(
            substanceKey = "caffeine_test",
            kinetics = EliminationKinetics.FIRST_ORDER,
            halfLifeMinutes = 5.0 * 60.0,
            absorptionHalfLifeMinutes = 7.0,
            bioavailability = 1.0,
            source = "test",
        )
        val doses = listOf(DoseEvent(midnight, 200.0))
        val atFiveMin = ConcentrationMath.amountInBodyAt(pk, doses, midnight + 5L * minute)
        val atFortyMin = ConcentrationMath.amountInBodyAt(pk, doses, midnight + 40L * minute)
        val atSixHr = ConcentrationMath.amountInBodyAt(pk, doses, midnight + 6L * hour)
        // Climbs through the absorption phase, then descends — Bateman shape.
        assertTrue("absorption phase should climb (got $atFiveMin → $atFortyMin)",
            atFortyMin > atFiveMin)
        assertTrue("post-peak should descend (got $atFortyMin → $atSixHr)",
            atFortyMin > atSixHr)
    }

    @Test
    fun `flip-flop kinetics ka equals ke uses the limit formula`() {
        // ka = ke ⇒ Bateman's denominator vanishes; engine must switch to
        //   A(t) = D × ka × t × e^(−ke·t)
        // which peaks at t = 1/ke. With halfLife = 60 min, ke = ln2/60,
        // so peak is at ~86.6 min. Just need the math to be finite and
        // increasing through that point, decreasing after.
        val pk = Pharmacokinetics(
            substanceKey = "flipflop",
            kinetics = EliminationKinetics.FIRST_ORDER,
            halfLifeMinutes = 60.0,
            absorptionHalfLifeMinutes = 60.0,
            bioavailability = 1.0,
            source = "test",
        )
        val doses = listOf(DoseEvent(midnight, 100.0))
        val a30 = ConcentrationMath.amountInBodyAt(pk, doses, midnight + 30L * minute)
        val a80 = ConcentrationMath.amountInBodyAt(pk, doses, midnight + 80L * minute)
        val a180 = ConcentrationMath.amountInBodyAt(pk, doses, midnight + 180L * minute)
        assertTrue("amount must be finite at flip-flop limit", a80.isFinite())
        assertTrue("rises before peak (a30=$a30, a80=$a80)", a80 > a30)
        assertTrue("falls after peak (a80=$a80, a180=$a180)", a80 > a180)
    }

    // ---- zero-order (alcohol) ----

    @Test
    fun `zero-order amount decays linearly between doses`() {
        // 10000 mg ethanol (≈ one large drink), rate 100 mg/min. After
        // 60 min: 10000 − 100×60 = 4000 mg.
        val pk = pkZeroOrder(rateMgPerMin = 100.0)
        val doses = listOf(DoseEvent(midnight, 10_000.0))
        val atSixtyMin = ConcentrationMath.amountInBodyAt(pk, doses, midnight + 60L * minute)
        assertNear(4_000.0, atSixtyMin, 50.0)
    }

    @Test
    fun `zero-order amount floors at zero between doses`() {
        val pk = pkZeroOrder(rateMgPerMin = 100.0)
        // Single 1000mg dose; rate 100mg/min ⇒ gone after 10 min. At 3h
        // out the amount must be exactly 0, not negative.
        val doses = listOf(DoseEvent(midnight, 1_000.0))
        val later = ConcentrationMath.amountInBodyAt(pk, doses, midnight + 3L * hour)
        assertEquals(0.0, later, 1e-9)
    }

    @Test
    fun `zero-order doses accumulate respecting between-dose decay`() {
        // Two 5000mg doses 30 min apart, rate 100mg/min.
        //   t=0:   +5000 → on-board 5000
        //   t=30m: decay 30×100=3000 → on-board 2000; +5000 → 7000
        // At t=30 min the queried amount is 7000.
        val pk = pkZeroOrder(rateMgPerMin = 100.0)
        val doses = listOf(
            DoseEvent(midnight, 5_000.0),
            DoseEvent(midnight + 30L * minute, 5_000.0),
        )
        val atSecondDose = ConcentrationMath.amountInBodyAt(
            pk, doses, midnight + 30L * minute
        )
        assertNear(7_000.0, atSecondDose, 50.0)
    }

    // ---- curve sampler ----

    @Test
    fun `curve returns one point per step`() {
        val pk = pkInstant(halfLifeMinutes = 60.0)
        val doses = listOf(DoseEvent(midnight, 100.0))
        // 0..60 min inclusive, step 10 min → 7 points.
        val curve = ConcentrationMath.curve(
            pk, doses, midnight, midnight + 60L * minute, stepMinutes = 10
        )
        assertEquals(7, curve.size)
        // First point is at fromMillis, last is at toMillis.
        assertEquals(midnight, curve.first().first)
        assertEquals(midnight + 60L * minute, curve.last().first)
        // Curve is monotonically decreasing for a single instant-absorption
        // dose with no future intake.
        for (i in 1 until curve.size) {
            assertTrue(
                "curve should descend: ${curve[i - 1]} → ${curve[i]}",
                curve[i].second <= curve[i - 1].second
            )
        }
    }

    // ---- timeUntilBelow ----

    @Test
    fun `timeUntilBelow returns zero when already under the threshold`() {
        val pk = pkInstant(halfLifeMinutes = 60.0)
        val doses = listOf(DoseEvent(midnight, 50.0))
        val ms = ConcentrationMath.timeUntilBelow(pk, doses, thresholdMg = 100.0, fromMillis = midnight)
        assertEquals(0L, ms)
    }

    @Test
    fun `timeUntilBelow finds the half-life crossing`() {
        // 100mg dose, t½ = 60 min. Threshold 50mg ≡ one half-life from now.
        // The bisection should land within ~5 min of the analytic answer.
        val pk = pkInstant(halfLifeMinutes = 60.0)
        val doses = listOf(DoseEvent(midnight, 100.0))
        val ms = ConcentrationMath.timeUntilBelow(pk, doses, thresholdMg = 50.0, fromMillis = midnight)
        assertNotNull("expected a crossing time", ms)
        val crossingMin = ms!!.toDouble() / 60_000.0
        // Bisection refines down to a few minutes; tolerate 5 min absolute.
        assertTrue("crossing $crossingMin min should be near 60 min", abs(crossingMin - 60.0) < 5.0)
    }

    @Test
    fun `timeUntilBelow returns null when threshold never crossed in horizon`() {
        // Frequent dosing keeps the steady-state level above threshold for
        // the search horizon. Engine must return null instead of either
        // looping forever or claiming a false crossing past the dose tail.
        val pk = pkInstant(halfLifeMinutes = 60.0)
        // 100 mg every 30 min, t½ 60 min ⇒ steady-state ~241..341 mg. The
        // search window stays inside the dosing schedule so the threshold
        // never crosses.
        val doses = (0..200).map { DoseEvent(midnight + it * 30L * minute, 100.0) }
        val ms = ConcentrationMath.timeUntilBelow(
            pk, doses, thresholdMg = 200.0,
            fromMillis = midnight + 4L * hour,
            maxLookaheadMs = 2L * hour,
        )
        assertNull(ms)
    }

    // ---- helpers ----

    private fun pkInstant(
        halfLifeMinutes: Double,
        bioavailability: Double = 1.0,
    ): Pharmacokinetics = Pharmacokinetics(
        substanceKey = "test",
        kinetics = EliminationKinetics.FIRST_ORDER,
        halfLifeMinutes = halfLifeMinutes,
        absorptionHalfLifeMinutes = 0.0,
        bioavailability = bioavailability,
        source = "test",
    )

    private fun pkZeroOrder(rateMgPerMin: Double): Pharmacokinetics = Pharmacokinetics(
        substanceKey = "ethanol_test",
        kinetics = EliminationKinetics.ZERO_ORDER,
        zeroOrderRateMgPerMin = rateMgPerMin,
        absorptionHalfLifeMinutes = 0.0,
        bioavailability = 1.0,
        source = "test",
    )

    private fun assertNear(expected: Double, actual: Double, tolerance: Double) {
        assertTrue(
            "expected ≈ $expected (±$tolerance), got $actual",
            abs(actual - expected) <= tolerance,
        )
    }
}
