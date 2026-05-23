package com.bios.app.engine

import com.bios.app.model.GrowthChartReference

/**
 * Inline subset of LMS (lambda/mu/sigma) reference values for the
 * WHO/CDC growth charts (#199, audit gap §2.7).
 *
 * **What this file ships.** WHO 0–24-month weight-for-age and length-for-age,
 * by sex, monthly rows. This covers the high-yield slice for paediatric
 * failure-to-thrive surveillance: most FTT presentations occur in the first
 * two years of life when the growth velocity is highest and the curves are
 * tightest. Head-circumference, BMI-for-age, the 2–5y rows, the 5–19y WHO
 * extension, the CDC 2–20y reference, and the Fenton premature reference
 * are tracked as a follow-up — the data layer is already shaped to accept
 * them ([GrowthChartReference] enum covers all four references; the LMS
 * resolver below is a simple lookup by reference + sex + indicator + ageInDays).
 *
 * **LMS values are public.** WHO publishes them at
 * `https://www.who.int/tools/child-growth-standards/standards`. CDC growth
 * chart tables are at `https://www.cdc.gov/growthcharts/`. The values
 * below are transcribed from the published tables; no transformation,
 * smoothing, or back-fitting. The transcription is verified against the
 * canonical z-score / percentile pairs in the unit tests.
 *
 * **The LMS method.** A growth curve's distribution at each age is summarised
 * by three parameters — lambda (Box-Cox power), mu (median), sigma
 * (coefficient of variation). The z-score for a measurement is:
 *   z = ((measurement/mu)^lambda - 1) / (lambda * sigma)   when lambda != 0
 *   z = ln(measurement/mu) / sigma                          when lambda == 0
 * The percentile follows from the standard normal CDF.
 *
 * Reference: Cole TJ, Green PJ (1992) — Smoothing reference centile curves:
 * the LMS method and penalized likelihood. Statistics in Medicine 11:1305-19.
 *
 * Lives in its own file so [GrowthChartEngine] stays under the 500-line cap
 * and so the LMS tables can be extended without churning the engine.
 */
data class LmsRow(
    val ageInDays: Int,
    val lambda: Double,
    val mu: Double,
    val sigma: Double,
)

enum class GrowthSex { MALE, FEMALE }

enum class GrowthIndicator { WEIGHT_FOR_AGE, LENGTH_FOR_AGE, HEAD_CIRCUMFERENCE_FOR_AGE, BMI_FOR_AGE }

/**
 * LMS table store. Lookup is by [GrowthChartReference] + [GrowthSex] +
 * [GrowthIndicator]; rows are indexed by age in days, with linear
 * interpolation between the two surrounding rows when an exact match is
 * absent (WHO publishes daily LMS values; the monthly subset shipped here
 * is a coarser grid that interpolation closes).
 */
object GrowthChartLmsTables {

    /**
     * WHO 0–24 month weight-for-age, MALE. Monthly rows.
     * Source: WHO Multicentre Growth Reference Study (2006), tables on
     * `who.int/tools/child-growth-standards/standards/weight-for-age`.
     * Age in days = months × 30.4375 (mean Gregorian month).
     */
    private val WHO_WEIGHT_FOR_AGE_MALE = listOf(
        LmsRow(0, 0.3487, 3.3464, 0.14602),
        LmsRow(30, 0.2297, 4.4709, 0.13395),
        LmsRow(61, 0.1970, 5.5675, 0.12385),
        LmsRow(91, 0.1738, 6.3762, 0.11727),
        LmsRow(122, 0.1553, 7.0023, 0.11316),
        LmsRow(152, 0.1395, 7.5105, 0.11080),
        LmsRow(183, 0.1257, 7.9340, 0.10958),
        LmsRow(213, 0.1134, 8.2970, 0.10902),
        LmsRow(244, 0.1021, 8.6151, 0.10882),
        LmsRow(274, 0.0917, 8.9014, 0.10881),
        LmsRow(305, 0.0820, 9.1649, 0.10891),
        LmsRow(335, 0.0730, 9.4122, 0.10906),
        LmsRow(366, 0.0644, 9.6479, 0.10925),
        LmsRow(396, 0.0563, 9.8749, 0.10949),
        LmsRow(427, 0.0487, 10.0953, 0.10976),
        LmsRow(457, 0.0413, 10.3108, 0.11007),
        LmsRow(488, 0.0343, 10.5228, 0.11041),
        LmsRow(518, 0.0275, 10.7319, 0.11079),
        LmsRow(549, 0.0211, 10.9385, 0.11119),
        LmsRow(579, 0.0148, 11.1430, 0.11164),
        LmsRow(610, 0.0087, 11.3462, 0.11211),
        LmsRow(640, 0.0029, 11.5486, 0.11261),
        LmsRow(671, -0.0028, 11.7504, 0.11314),
        LmsRow(701, -0.0083, 11.9514, 0.11369),
        LmsRow(731, -0.0137, 12.1515, 0.11426),
    )

    /**
     * WHO 0–24 month weight-for-age, FEMALE. Monthly rows.
     * Source: WHO Multicentre Growth Reference Study (2006), tables on
     * `who.int/tools/child-growth-standards/standards/weight-for-age`.
     */
    private val WHO_WEIGHT_FOR_AGE_FEMALE = listOf(
        LmsRow(0, 0.3809, 3.2322, 0.14171),
        LmsRow(30, 0.1714, 4.1873, 0.13724),
        LmsRow(61, 0.0962, 5.1282, 0.13000),
        LmsRow(91, 0.0402, 5.8458, 0.12619),
        LmsRow(122, -0.0050, 6.4237, 0.12402),
        LmsRow(152, -0.0430, 6.8985, 0.12274),
        LmsRow(183, -0.0756, 7.2970, 0.12204),
        LmsRow(213, -0.1039, 7.6422, 0.12178),
        LmsRow(244, -0.1288, 7.9487, 0.12181),
        LmsRow(274, -0.1507, 8.2254, 0.12199),
        LmsRow(305, -0.1700, 8.4800, 0.12223),
        LmsRow(335, -0.1872, 8.7192, 0.12247),
        LmsRow(366, -0.2024, 8.9481, 0.12268),
        LmsRow(396, -0.2158, 9.1699, 0.12283),
        LmsRow(427, -0.2278, 9.3870, 0.12294),
        LmsRow(457, -0.2384, 9.6008, 0.12299),
        LmsRow(488, -0.2478, 9.8124, 0.12303),
        LmsRow(518, -0.2562, 10.0226, 0.12306),
        LmsRow(549, -0.2637, 10.2315, 0.12309),
        LmsRow(579, -0.2703, 10.4393, 0.12315),
        LmsRow(610, -0.2762, 10.6464, 0.12323),
        LmsRow(640, -0.2815, 10.8534, 0.12335),
        LmsRow(671, -0.2862, 11.0608, 0.12350),
        LmsRow(701, -0.2903, 11.2688, 0.12369),
        LmsRow(731, -0.2941, 11.4775, 0.12390),
    )

    /**
     * WHO 0–24 month length-for-age, MALE. Monthly rows.
     * Source: WHO Multicentre Growth Reference Study (2006), tables on
     * `who.int/tools/child-growth-standards/standards/length-height-for-age`.
     * Recumbent length (the WHO 0–2y convention).
     */
    private val WHO_LENGTH_FOR_AGE_MALE = listOf(
        LmsRow(0, 1.0, 49.8842, 0.03795),
        LmsRow(30, 1.0, 54.7244, 0.03557),
        LmsRow(61, 1.0, 58.4249, 0.03424),
        LmsRow(91, 1.0, 61.4292, 0.03328),
        LmsRow(122, 1.0, 63.8860, 0.03257),
        LmsRow(152, 1.0, 65.9026, 0.03204),
        LmsRow(183, 1.0, 67.6236, 0.03165),
        LmsRow(213, 1.0, 69.1645, 0.03139),
        LmsRow(244, 1.0, 70.5994, 0.03124),
        LmsRow(274, 1.0, 71.9687, 0.03117),
        LmsRow(305, 1.0, 73.2812, 0.03118),
        LmsRow(335, 1.0, 74.5388, 0.03125),
        LmsRow(366, 1.0, 75.7488, 0.03137),
        LmsRow(396, 1.0, 76.9186, 0.03154),
        LmsRow(427, 1.0, 78.0497, 0.03174),
        LmsRow(457, 1.0, 79.1458, 0.03197),
        LmsRow(488, 1.0, 80.2113, 0.03222),
        LmsRow(518, 1.0, 81.2487, 0.03250),
        LmsRow(549, 1.0, 82.2587, 0.03279),
        LmsRow(579, 1.0, 83.2418, 0.03310),
        LmsRow(610, 1.0, 84.1996, 0.03342),
        LmsRow(640, 1.0, 85.1348, 0.03376),
        LmsRow(671, 1.0, 86.0477, 0.03410),
        LmsRow(701, 1.0, 86.9410, 0.03445),
        LmsRow(731, 1.0, 87.8161, 0.03479),
    )

    /**
     * WHO 0–24 month length-for-age, FEMALE. Monthly rows.
     * Source: WHO Multicentre Growth Reference Study (2006), tables on
     * `who.int/tools/child-growth-standards/standards/length-height-for-age`.
     */
    private val WHO_LENGTH_FOR_AGE_FEMALE = listOf(
        LmsRow(0, 1.0, 49.1477, 0.03790),
        LmsRow(30, 1.0, 53.6872, 0.03640),
        LmsRow(61, 1.0, 57.0673, 0.03568),
        LmsRow(91, 1.0, 59.8029, 0.03520),
        LmsRow(122, 1.0, 62.0899, 0.03486),
        LmsRow(152, 1.0, 64.0301, 0.03463),
        LmsRow(183, 1.0, 65.7311, 0.03448),
        LmsRow(213, 1.0, 67.2873, 0.03442),
        LmsRow(244, 1.0, 68.7498, 0.03441),
        LmsRow(274, 1.0, 70.1435, 0.03446),
        LmsRow(305, 1.0, 71.4818, 0.03454),
        LmsRow(335, 1.0, 72.7710, 0.03464),
        LmsRow(366, 1.0, 74.0150, 0.03477),
        LmsRow(396, 1.0, 75.2176, 0.03492),
        LmsRow(427, 1.0, 76.3817, 0.03508),
        LmsRow(457, 1.0, 77.5099, 0.03526),
        LmsRow(488, 1.0, 78.6055, 0.03545),
        LmsRow(518, 1.0, 79.6710, 0.03566),
        LmsRow(549, 1.0, 80.7079, 0.03587),
        LmsRow(579, 1.0, 81.7182, 0.03610),
        LmsRow(610, 1.0, 82.7036, 0.03634),
        LmsRow(640, 1.0, 83.6654, 0.03659),
        LmsRow(671, 1.0, 84.6040, 0.03684),
        LmsRow(701, 1.0, 85.5202, 0.03710),
        LmsRow(731, 1.0, 86.4153, 0.03737),
    )

    /**
     * Resolves the LMS row series for a given reference / sex / indicator
     * tuple. Returns null when the requested combination is not in the
     * shipped subset (e.g. CDC tables, 2–5y WHO rows, head-circumference,
     * BMI-for-age — all tracked as follow-ups).
     */
    fun seriesFor(
        reference: GrowthChartReference,
        sex: GrowthSex,
        indicator: GrowthIndicator,
    ): List<LmsRow>? = when (reference) {
        GrowthChartReference.WHO_0_5Y -> when (indicator) {
            GrowthIndicator.WEIGHT_FOR_AGE -> when (sex) {
                GrowthSex.MALE -> WHO_WEIGHT_FOR_AGE_MALE
                GrowthSex.FEMALE -> WHO_WEIGHT_FOR_AGE_FEMALE
            }
            GrowthIndicator.LENGTH_FOR_AGE -> when (sex) {
                GrowthSex.MALE -> WHO_LENGTH_FOR_AGE_MALE
                GrowthSex.FEMALE -> WHO_LENGTH_FOR_AGE_FEMALE
            }
            // Head-circumference + BMI-for-age tracked as a follow-up.
            else -> null
        }
        // WHO 5–19y / CDC 2–20y / Fenton premature tracked as follow-ups.
        else -> null
    }
}
