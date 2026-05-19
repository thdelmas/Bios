package com.bios.app

import com.bios.app.ingest.parseWithingsGroupMeasures
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic coverage for the Withings `measuregrps[].measures[]`
 * group walk (#27). The HTTP fetch itself is covered by build-and-
 * eyeball; what fails first under regression is the group math —
 * especially the BODY_WATER_PCT derivation, which needs the same
 * group's weight (Withings type 1) as a denominator.
 *
 * Fixture format mirrors what Withings's `/measure?action=getmeas`
 * actually returns: each measure carries a `type`, a `value`, and a
 * `unit` that is the base-10 exponent applied to value (e.g.
 * value=78250, unit=-3 → 78.250 kg).
 */
class WithingsBodyCompositionTest {

    private val sourceId = "withings-scale"
    private val ts = 1_700_000_000_000L

    @Test
    fun `full impedance event emits weight, lean, fat, water-pct, bone`() {
        // Realistic scale event: 78.250 kg total, 60.0 kg lean,
        // 20.0% fat, 45.0 kg hydration (→ 57.5% BWP), 3.2 kg bone.
        val measures = measuresJson(
            1 to (78_250 to -3),    // weight 78.250 kg
            5 to (60_000 to -3),    // lean 60.000 kg
            6 to (20_000 to -3),    // fat ratio 20.000 %
            76 to (45_000 to -3),   // hydration 45.000 kg
            88 to (3_200 to -3),    // bone 3.200 kg
        )
        val out = parseWithingsGroupMeasures(measures, ts, sourceId)
        val byKey = out.associateBy { it.metricType }

        assertEquals(78.250, byKey[MetricType.BODY_MASS.key]!!.value, 1e-9)
        assertEquals(60.0, byKey[MetricType.LEAN_MASS.key]!!.value, 1e-9)
        assertEquals(20.0, byKey[MetricType.BODY_FAT_PCT.key]!!.value, 1e-9)
        assertEquals(3.2, byKey[MetricType.BONE_MASS.key]!!.value, 1e-9)
        // 45.0 / 78.250 × 100 = 57.5079...
        assertEquals(57.508, byKey[MetricType.BODY_WATER_PCT.key]!!.value, 1e-3)
        assertEquals(ts, byKey[MetricType.BODY_WATER_PCT.key]!!.timestamp)
        assertEquals(sourceId, byKey[MetricType.BODY_WATER_PCT.key]!!.sourceId)
    }

    @Test
    fun `BODY_WATER_PCT is dropped when weight is missing from the group`() {
        // Hydration alone — without weight there's no denominator. Don't
        // fabricate, don't fall back to a stale weight from another event.
        val measures = measuresJson(
            76 to (45_000 to -3),
        )
        val out = parseWithingsGroupMeasures(measures, ts, sourceId)
        assertNull(out.firstOrNull { it.metricType == MetricType.BODY_WATER_PCT.key })
    }

    @Test
    fun `BODY_WATER_PCT is dropped when weight is non-positive`() {
        // Defensive against corrupted scale uploads — division by zero
        // (or near-zero) would produce a nonsensical percent.
        val measures = measuresJson(
            1 to (0 to 0),
            76 to (45_000 to -3),
        )
        val out = parseWithingsGroupMeasures(measures, ts, sourceId)
        assertNull(out.firstOrNull { it.metricType == MetricType.BODY_WATER_PCT.key })
    }

    @Test
    fun `weight-only event emits BODY_MASS without the impedance derivatives`() {
        // Some Withings scales (Body, Body+) report only weight. The
        // group walk shouldn't fabricate lean / fat / water / bone rows.
        val measures = measuresJson(1 to (78_250 to -3))
        val out = parseWithingsGroupMeasures(measures, ts, sourceId)
        val keys = out.map { it.metricType }.toSet()
        assertEquals(setOf(MetricType.BODY_MASS.key), keys)
    }

    @Test
    fun `unit exponent scaling works on positive and zero exponents too`() {
        // The Withings unit field can be 0 (integer ppm-style) or even
        // positive (rare but allowed). 1500 × 10⁰ = 1500.
        val measures = measuresJson(9 to (78 to 0))   // BP diastolic 78 mmHg
        val out = parseWithingsGroupMeasures(measures, ts, sourceId)
        assertEquals(78.0, out.single().value, 1e-9)
    }

    // --- Cross-file contract bindings ---

    @Test
    fun `the three new MetricTypes live on the METABOLIC domain with expected units`() {
        assertEquals(MetricDomain.METABOLIC, MetricType.LEAN_MASS.domain)
        assertEquals(MetricUnit.KILOGRAMS, MetricType.LEAN_MASS.unit)

        assertEquals(MetricDomain.METABOLIC, MetricType.BODY_WATER_PCT.domain)
        assertEquals(MetricUnit.PERCENT, MetricType.BODY_WATER_PCT.unit)

        assertEquals(MetricDomain.METABOLIC, MetricType.BONE_MASS.domain)
        assertEquals(MetricUnit.KILOGRAMS, MetricType.BONE_MASS.unit)
    }

    @Test
    fun `LEAN_MASS has a LOINC mapping while BODY_WATER_PCT and BONE_MASS are deferred`() {
        // LEAN_MASS has a widely-adopted code (LOINC 91557-9 for
        // BIA-derived lean mass). Hydration % and impedance-derived bone
        // mass don't have well-adopted clinical codes, so they're
        // deliberately deferred — mirrors the epigenetic-age decision.
        assertEquals("91557-9", com.bios.app.export.loincCode(MetricType.LEAN_MASS)!!.first)
        assertNull(com.bios.app.export.loincCode(MetricType.BODY_WATER_PCT))
        assertNull(com.bios.app.export.loincCode(MetricType.BONE_MASS))
    }

    // --- fixture helpers ---

    /**
     * Builds a Withings-shaped measures JSON array. Each pair is
     * `(type, value × 10^unit)` — same encoding the live API uses.
     */
    private fun measuresJson(vararg entries: Pair<Int, Pair<Int, Int>>): JSONArray {
        val arr = JSONArray()
        for ((type, valueUnit) in entries) {
            val (value, unit) = valueUnit
            arr.put(JSONObject().apply {
                put("type", type)
                put("value", value)
                put("unit", unit)
            })
        }
        return arr
    }

    @Suppress("unused")
    private fun unused(@Suppress("UNUSED_PARAMETER") r: MetricReading) = Unit
}
