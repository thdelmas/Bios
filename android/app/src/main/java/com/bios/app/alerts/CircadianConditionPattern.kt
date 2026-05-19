package com.bios.app.alerts

import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType

/**
 * Circadian-disruption pattern. Lifted out of [ConditionPatterns] so the
 * host file stays under the 500-line cap once more longevity-science
 * patterns (#28 glucose variability and friends) wire their corroborating
 * signal rules into the existing patterns. The pattern itself is unchanged
 * from its in-place definition; it's surfaced through the same
 * `ConditionPatterns.all` aggregate so detection sees no behavior change.
 */
object CircadianConditionPattern {

    val all: List<ConditionPattern> by lazy { listOf(circadianDisruption) }

    /**
     * Circadian disruption: erratic light-exposure pattern over a 7-day window
     * paired with sleep degradation. The phone's ambient-light sensor (sampled
     * on a screen-on duty cycle by SyncWorker) is the only Bios-side proxy for
     * the owner's daily light environment. When the AMBIENT_LIGHT z-score
     * pattern drifts well past baseline — e.g. a shift to night-shift work,
     * heavy evening screen exposure replacing daytime sunlight, or jet-lag
     * misalignment — and sleep architecture concurrently degrades, the
     * convergence is the strongest non-invasive circadian-disruption signal
     * available.
     *
     * This is the §8.1 cross-correlation consumer of `AMBIENT_LIGHT`: required
     * = true on the lux rule so wearable sleep drift alone never fires the
     * pattern (per §8.6 / §8.8 gating discipline).
     */
    val circadianDisruption = ConditionPattern(
        id = "circadian_disruption",
        title = "Erratic light-exposure pattern with sleep degradation",
        category = ConditionCategory.SLEEP,
        signalRules = listOf(
            SignalRule(MetricType.AMBIENT_LIGHT, DeviationDirection.IRREGULAR, 1.5, 168, 1.2,
                ThresholdSource.LITERATURE,
                "Wright KP et al. (2013) — entrainment to the natural light-dark cycle is the dominant circadian zeitgeber; sustained departures from the owner's habitual light pattern are the canonical marker of misalignment",
                required = true),
            SignalRule(MetricType.SLEEP_STAGE, DeviationDirection.BELOW, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Chang AM et al. (2015) — evening light exposure suppresses melatonin, delays sleep onset, and reduces deep-sleep proportion"),
            SignalRule(MetricType.SLEEP_DURATION, DeviationDirection.IRREGULAR, 1.5, 168, 0.8,
                ThresholdSource.LITERATURE,
                "Roenneberg T et al. (2007) — social-jetlag-style variability in sleep timing accompanies circadian misalignment and tracks light-exposure irregularity"),
        ),
        minActiveSignals = 2,
        explanation = "Your ambient-light exposure pattern over the past week has deviated substantially from your established baseline, and at least one sleep signal (depth or timing) has degraded in parallel. The combination is consistent with circadian misalignment — caused by night work, heavy evening screen use, jet-lag, or a sudden change in daily light environment. This is a data observation about your environment, not a judgment about your habits.",
        suggestedAction = "If the disruption is intentional (travel, shift work), no action is needed beyond awareness. Otherwise, increasing morning-daylight exposure and reducing bright-screen exposure within 2 hours of bedtime are the highest-evidence interventions for re-entrainment.",
        references = listOf(
            "Wright KP Jr et al. (2013) — Entrainment of the human circadian clock to the natural light-dark cycle",
            "Chang AM et al. (2015) — Evening use of light-emitting eReaders negatively affects sleep, circadian timing, and next-morning alertness",
            "Roenneberg T et al. (2007) — Epidemiology of the human circadian clock"
        ),
        earlyDetection = "Circadian disruption is silent in the short term — the owner often doesn't notice until sleep quality, mood, or daytime alertness has already degraded. The phone's ambient-light sensor captures the upstream cause (changed light environment) days before downstream symptoms accumulate. Pairing the light-exposure shift with sleep degradation is what distinguishes a meaningful misalignment from a one-off late night.",
        prevention = "Anchor the day with bright outdoor light within the first hour after waking — the strongest circadian zeitgeber. Keep evening environments dim (especially the 2-3 hours before bed); warm-color bulbs and reduced screen brightness help. For shift workers, maintain a consistent sleep schedule even on off-days; intermittent re-entrainment is worse than a stable shifted phase.",
        healing = "Most circadian misalignment self-corrects within 7-10 days of restored light-dark cues. Accelerate re-entrainment with timed bright light (morning if phase-delayed, evening if phase-advanced) and strict avoidance of bright light during the new biological night. Consider melatonin (0.3-0.5 mg) 4-6 hours before target sleep onset for travel adjustment, only short-term. If sleep quality and daytime alertness don't recover within 2 weeks, consult a sleep specialist — chronic circadian misalignment can indicate delayed sleep-wake phase disorder or shift-work disorder.",
        risks = "Chronic circadian disruption is independently associated with metabolic syndrome, cardiovascular disease, mood disorders, and certain cancers (IARC classifies shift work as probably carcinogenic). The mechanism is misaligned hormonal and metabolic rhythms — insulin sensitivity, cortisol, and inflammatory regulation all follow circadian patterns. Short-term mismatches recover; sustained ones compound. Early detection via wearable proxy signals provides the window where lifestyle realignment is highly effective."
    )
}
