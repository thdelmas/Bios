package com.bios.app.engine

/**
 * Per-substance pharmacokinetic profile consumed by [ConcentrationCalculator].
 *
 * Half-life math is identical regardless of substance — caffeine, nicotine,
 * THC, alcohol, future prescribed drugs all go through the same engine. The
 * substance-specific knowledge sits here: elimination half-life, absorption
 * speed, bioavailability, and (when relevant) volume of distribution.
 *
 * Population averages only. Pharmacogenomic individualisation (CYP1A2
 * metabolism rate varying by genotype, CYP2D6 / 2C19 medication polymorphisms,
 * etc.) is a deliberate non-goal of the first iteration — the issue spec
 * flags it as a future research direction. The owner sees a published-range
 * estimate and reads it themselves; the manifesto's "instrument, not coach"
 * stance holds.
 *
 * Categorisation note: [kinetics] selects first-order vs. zero-order
 * elimination. Alcohol is the canonical zero-order case — for typical doses
 * the body clears a roughly constant amount per hour (~7 g of ethanol /
 * 70 kg adult) until the concentration becomes low enough for the
 * Michaelis-Menten elimination to look first-order. Modelling the
 * crossover is overkill for the dashboard surface this engine serves;
 * we use pure zero-order while concentration > 0 and document the
 * approximation here.
 */
data class Pharmacokinetics(
    val substanceKey: String,
    val kinetics: EliminationKinetics,
    /** Elimination half-life in minutes (first-order substances). */
    val halfLifeMinutes: Double = 0.0,
    /**
     * Zero-order elimination rate in mg/min (zero-order substances).
     * Independent of concentration — body removes this many mg per minute
     * regardless of how much is on board.
     */
    val zeroOrderRateMgPerMin: Double = 0.0,
    /**
     * Absorption half-life in minutes. 0.0 = instantaneous (IV / inhaled
     * onset). Non-zero values trigger the Bateman one-compartment oral
     * absorption model (see [ConcentrationCalculator]).
     */
    val absorptionHalfLifeMinutes: Double = 0.0,
    /** Fraction (0..1) of an oral/inhaled dose that reaches systemic
     *  circulation. 1.0 = fully bioavailable. */
    val bioavailability: Double = 1.0,
    /**
     * Volume of distribution in L/kg, when known. Lets the engine convert
     * amount-in-body to plasma concentration (mg/L). Null when the
     * literature does not support a single representative value or when
     * plasma concentration would be misleading for the substance.
     */
    val volumeOfDistributionLPerKg: Double? = null,
    /** Short citation pinning the values to a literature source. */
    val source: String,
)

enum class EliminationKinetics {
    /** Concentration decays exponentially: dC/dt = -ke × C. Most drugs. */
    FIRST_ORDER,

    /** Constant amount per unit time removed: dC/dt = -k. Saturating
     *  enzyme systems at therapeutic concentration — alcohol the standard
     *  example, also high-dose aspirin and phenytoin. */
    ZERO_ORDER,
}

/**
 * Shipped substance table. Slow-rolling reference data; new entries are
 * added as companion surfaces start writing the corresponding intake key.
 *
 * Each entry pairs with one or more `MetricType.*_INTAKE` keys via
 * `substanceKey`. The default mapping is by metric_type for the simple
 * cases (caffeine_intake → "caffeine"); the generic medication_intake
 * key carries `substance_key` in event_payloads so a future medications
 * companion can manage its own vocabulary without re-allocating contract
 * keys per drug.
 */
object PharmacokineticsReference {

    /** Caffeine. Half-life is the metric most often cited from
     *  Mandel 2002 / Statland 1980 — 3–5 h population mean, longer in
     *  pregnancy and on oral contraceptives, shorter in heavy smokers.
     *  Oral absorption peaks ~45 min after intake. */
    val caffeine = Pharmacokinetics(
        substanceKey = "caffeine",
        kinetics = EliminationKinetics.FIRST_ORDER,
        halfLifeMinutes = 5.0 * 60.0,
        absorptionHalfLifeMinutes = 7.0,
        bioavailability = 1.0,
        volumeOfDistributionLPerKg = 0.6,
        source = "Mandel HG 2002, Statland & Demas 1980 (5h population t½, ~99 % oral bioavailability)",
    )

    /** Nicotine, inhaled (cigarette / vape). Onset is effectively
     *  instantaneous (alveolar transit < 10 s) so we treat absorption
     *  as IV-like. Elimination t½ 2 h (Benowitz 2009). */
    val nicotineInhaled = Pharmacokinetics(
        substanceKey = "nicotine_inhaled",
        kinetics = EliminationKinetics.FIRST_ORDER,
        halfLifeMinutes = 2.0 * 60.0,
        absorptionHalfLifeMinutes = 0.0,
        bioavailability = 1.0,
        volumeOfDistributionLPerKg = 2.6,
        source = "Benowitz NL 2009 (t½ ~ 2 h, V_d 2.6 L/kg, inhaled bioavailability ≈ 100 % of inhaled dose)",
    )

    /** Nicotine, oral (gum / lozenge / pouch). Buccal absorption is slower
     *  and partial — first-pass metabolism applies to swallowed drug, so
     *  net bioavailability lands around 50 %. Elimination t½ unchanged. */
    val nicotineOral = Pharmacokinetics(
        substanceKey = "nicotine_oral",
        kinetics = EliminationKinetics.FIRST_ORDER,
        halfLifeMinutes = 2.0 * 60.0,
        absorptionHalfLifeMinutes = 30.0,
        bioavailability = 0.5,
        volumeOfDistributionLPerKg = 2.6,
        source = "Benowitz NL 2009, Choi 1988 (buccal F ~ 50 %, t½ ~ 2 h)",
    )

    /** THC, inhaled (smoked / vaporised). Population-average elimination
     *  is multi-compartmental; the single-exponential approximation here
     *  uses the 30 h whole-body t½ commonly cited (Huestis 2007). For
     *  heavy chronic users effective t½ extends to several days as
     *  redistribution from fat dominates — the engine is honest about
     *  being a population-average lens, not a forensic estimator. */
    val thcInhaled = Pharmacokinetics(
        substanceKey = "thc_inhaled",
        kinetics = EliminationKinetics.FIRST_ORDER,
        halfLifeMinutes = 30.0 * 60.0,
        absorptionHalfLifeMinutes = 0.0,
        bioavailability = 0.3,
        volumeOfDistributionLPerKg = 10.0,
        source = "Huestis MA 2007 (inhaled F 10–35 %, terminal t½ ~ 30 h, V_d 10 L/kg)",
    )

    /** THC, edible. Oral bioavailability is lower (extensive first-pass
     *  metabolism) and absorption is delayed by GI transit and gradual
     *  hepatic conversion to 11-OH-THC. */
    val thcEdible = Pharmacokinetics(
        substanceKey = "thc_edible",
        kinetics = EliminationKinetics.FIRST_ORDER,
        halfLifeMinutes = 30.0 * 60.0,
        absorptionHalfLifeMinutes = 90.0,
        bioavailability = 0.1,
        volumeOfDistributionLPerKg = 10.0,
        source = "Huestis MA 2007, Vandrey 2017 (oral F 4–12 %, peak 1–3 h post-ingestion)",
    )

    /** Ethanol. Linear (zero-order) elimination over the typical
     *  drinking range — Widmark's original 1932 model. The commonly
     *  cited rate is ~0.015 % BAC/hour, equivalent to ~7 g ethanol /
     *  hour for a 70 kg adult (V_d ~ 0.6 L/kg, body water fraction).
     *  Absorption is fast (~ 30 min on empty stomach, longer with food). */
    val alcohol = Pharmacokinetics(
        substanceKey = "alcohol",
        kinetics = EliminationKinetics.ZERO_ORDER,
        zeroOrderRateMgPerMin = ALCOHOL_ELIMINATION_MG_PER_MIN,
        absorptionHalfLifeMinutes = 15.0,
        bioavailability = 0.9,
        volumeOfDistributionLPerKg = 0.6,
        source = "Widmark EMP 1932, Holford NHG 1987 (zero-order ~ 0.015 % BAC/h ≈ 7 g/h for 70 kg adult)",
    )

    /** Built-in substances, keyed by [Pharmacokinetics.substanceKey]. */
    val all: Map<String, Pharmacokinetics> by lazy {
        listOf(
            caffeine, nicotineInhaled, nicotineOral,
            thcInhaled, thcEdible, alcohol,
        ).associateBy { it.substanceKey }
    }

    /**
     * Default substance key for a `*_INTAKE` metric key. Returns null for
     * `medication_intake` — that key requires per-event `substance_key`
     * lookup in event_payloads since multiple drugs share the metric.
     */
    fun substanceKeyForMetric(metricKey: String): String? = when (metricKey) {
        "caffeine_intake" -> caffeine.substanceKey
        "alcohol_intake" -> alcohol.substanceKey
        else -> null
    }

    /** ~7 g/h ÷ 60 min = ~117 mg/min, expressed in milligrams of ethanol
     *  to match `ALCOHOL_INTAKE` storage (grams of ethanol → ×1000 mg
     *  internally so the engine works in a single mass unit). */
    internal const val ALCOHOL_ELIMINATION_MG_PER_MIN = 7000.0 / 60.0
}
