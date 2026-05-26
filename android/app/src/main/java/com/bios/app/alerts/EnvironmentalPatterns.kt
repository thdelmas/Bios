package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.contracts.MetricType

/**
 * Environment-driven physiology screens (#197). Five audit POVs converge on
 * this gap: SOWA_RIGPA §2.1 (altitude), INDIGENOUS_AMERICAS_POV (Andes /
 * Mesoamerica altitude + heat), OCEANIC_ARCTIC_POV (Sápmi / Sakha cold +
 * Pacific heat), AFRICAN_TRADITIONAL_POV (heat acclimation), and
 * MODERN_NON_ALLOPATHIC_POV (environmental medicine). The baseline-relative
 * pattern set assumes a sea-level temperate Northern climate; these
 * patterns surface deviations that only make sense once the owner's
 * environmental context is known.
 *
 * Manifesto guard: every pattern below is ADVISORY ceiling unless an
 * unambiguous "this is dangerous now" threshold crosses (e.g. severe
 * hypothermia core temp). The SAAD screen is **always** ADVISORY — Bios
 * surfaces the data shape, not a diagnosis.
 *
 * References:
 *  - West JB (2010) — High Altitude Medicine and Physiology
 *  - Bouchama A & Knochel JP (2002) — Heat stroke. NEJM
 *  - Brown DJA et al. (2012) — Accidental hypothermia. NEJM
 *  - Lewy AJ et al. (2006) — The circadian basis of winter depression. PNAS
 */
object EnvironmentalPatterns {

    val all by lazy {
        listOf(
            heatExhaustionScreen,
            hypothermiaScreen,
            altitudeSicknessScreen,
            saadWinterPattern,
        )
    }

    /**
     * Heat-related illness screen. High ambient temperature + skin-temp
     * elevation + tachycardia + activity drop is the wearable-detectable
     * shape of heat exhaustion. The pattern is ADVISORY by default; the
     * `severityFloor` is left null so a confirmed value at the heat-stroke
     * cutoff (core ≥40 °C — Bouchama 2002) escalates via the separate
     * `emergency_spo2_critical`-style hard-cutoff path, not by re-firing
     * this advisory.
     *
     * Activity drop is the corroborator that distinguishes heat
     * exhaustion from a hot-environment workout (where steps would be
     * elevated, not depressed).
     */
    val heatExhaustionScreen = ConditionPattern(
        id = "heat_exhaustion_screen",
        title = "Heat-stress pattern detected",
        category = ConditionCategory.ENVIRONMENTAL,
        signalRules = listOf(
            // Ambient temperature ≥32 °C is the WHO heat-warning threshold
            // and the lower bound of the "extreme caution" band on the NWS
            // heat index. Latest-reading semantics — ambient temp is sparse
            // and intentional.
            SignalRule(
                MetricType.AMBIENT_TEMPERATURE_C, DeviationDirection.ABOVE, 0.0, 0, 1.0,
                ThresholdSource.LITERATURE,
                "WHO Heat-Health Action Plan — ≥32 °C ambient is the lower bound for heat-illness watchfulness",
                required = true,
                absoluteAbove = 32.0,
            ),
            SignalRule(
                MetricType.SKIN_TEMPERATURE_DEVIATION, DeviationDirection.ABOVE, 1.5, 1, 1.2,
                ThresholdSource.LITERATURE,
                "Bouchama & Knochel (2002) — skin-temperature elevation tracks core-temperature rise in heat illness",
            ),
            SignalRule(
                MetricType.HEART_RATE, DeviationDirection.ABOVE, 1.5, 1, 1.0,
                ThresholdSource.LITERATURE,
                "Bouchama & Knochel (2002) — compensatory tachycardia is the earliest cardiovascular sign of heat strain",
            ),
            SignalRule(
                MetricType.STEPS, DeviationDirection.BELOW, 1.0, 6, 0.6,
                ThresholdSource.LITERATURE,
                "Heat exhaustion presents with activity withdrawal — distinguishes the pattern from hot-environment exertion",
            ),
        ),
        minActiveSignals = 3,
        explanation = "Ambient temperature is elevated, skin temperature is above your personal baseline, heart rate is elevated, and activity has dropped — a pattern consistent with heat strain. Severe heat illness develops when the body cannot dissipate heat fast enough; the wearable-visible signs appear before subjective collapse.",
        suggestedAction = "Move to a cooler environment, hydrate, and rest. If confusion, fainting, vomiting, or core temperature above 39 °C develops, seek emergency medical attention immediately — heat stroke is a medical emergency.",
        references = listOf(
            "Bouchama A & Knochel JP (2002) — Heat stroke. New England Journal of Medicine 346:1978–1988",
            "WHO Regional Office for Europe (2021) — Heat-Health Action Plans",
        ),
        earlyDetection = "Heat strain follows a predictable cardiovascular cascade: as core temperature rises, peripheral blood flow increases to dissipate heat, the heart compensates with tachycardia, and activity becomes intolerable. Wearables catch the sequence — ambient temperature high, skin temperature rising, heart rate elevated, steps falling — hours before heat stroke becomes irreversible. Acclimatised heat-zone residents have a wider tolerance window; unacclimatised travellers entering the tropics are the most vulnerable population.",
        prevention = "Hydration, electrolyte replacement, and acclimatisation are the protective factors. Acclimatisation takes 10–14 days of progressive heat exposure; the cardiovascular and sweat-response adaptations during that window are large and durable. Avoid peak afternoon exertion in unfamiliar tropical climates. Loose, light-coloured clothing and active cooling (shade, water immersion, fans) reduce thermal load. Adults over 65, pregnant owners, and owners on diuretics, anticholinergics, or beta-blockers carry elevated heat-illness risk.",
        healing = "Move to shade or air conditioning. Loosen clothing. Sip cool water or an electrolyte solution. Cool the skin with damp cloths, fanning, or immersion if available. Rest until heart rate returns to baseline and the sense of heat intolerance fades — typically 30–60 minutes for heat exhaustion. Persistent symptoms, confusion, vomiting, or temperature above 39 °C require emergency care; heat stroke carries a 10–20 % mortality rate even with prompt treatment.",
        risks = "Untreated heat exhaustion can progress to heat stroke within minutes — the transition is the loss of thermoregulation. Heat stroke causes multi-organ injury (kidneys, liver, coagulation, central nervous system) and can leave permanent deficits even after recovery. Chronic heat exposure during pregnancy is associated with adverse outcomes. Cardiovascular events also cluster during heat waves: most excess deaths during European heat waves are cardiovascular, not heat-stroke per se.",
    )

    /**
     * Hypothermia screen. Cold ambient + low skin temp + bradycardia is the
     * mirror of the heat pattern. ADVISORY by default; the
     * severe-hypothermia hard-cutoff (core <32 °C, Brown 2012) would
     * escalate independently.
     */
    val hypothermiaScreen = ConditionPattern(
        id = "hypothermia_screen",
        title = "Cold-exposure pattern detected",
        category = ConditionCategory.ENVIRONMENTAL,
        signalRules = listOf(
            SignalRule(
                MetricType.AMBIENT_TEMPERATURE_C, DeviationDirection.BELOW, 0.0, 0, 1.0,
                ThresholdSource.LITERATURE,
                "Brown DJA et al. (2012) — ambient ≤5 °C exposure is the field threshold for hypothermia watchfulness",
                required = true,
                absoluteBelow = 5.0,
            ),
            SignalRule(
                MetricType.SKIN_TEMPERATURE_DEVIATION, DeviationDirection.BELOW, 1.5, 1, 1.2,
                ThresholdSource.LITERATURE,
                "Brown DJA et al. (2012) — skin temperature drops as peripheral vasoconstriction conserves core heat",
            ),
            SignalRule(
                MetricType.HEART_RATE, DeviationDirection.BELOW, 1.5, 1, 1.0,
                ThresholdSource.LITERATURE,
                "Brown DJA et al. (2012) — bradycardia develops as core temperature falls below 33 °C",
            ),
        ),
        minActiveSignals = 2,
        explanation = "Ambient temperature is low, skin temperature is below your personal baseline, and resting heart rate has dropped — a pattern consistent with cold-induced thermoregulatory stress. Mild hypothermia (32–35 °C core) is reversible with passive rewarming; severe hypothermia is a medical emergency.",
        suggestedAction = "Move to a warm environment, remove wet clothing, replace with dry insulating layers, and add an external heat source (warm drink, blanket, body heat). If shivering stops, consciousness is altered, or skin becomes pale and waxy, seek emergency medical attention.",
        references = listOf(
            "Brown DJA, Brugger H, Boyd J, Paal P (2012) — Accidental hypothermia. New England Journal of Medicine 367:1930–1938",
            "Wilderness Medical Society (2019) — Clinical Practice Guidelines for the Out-of-Hospital Evaluation and Treatment of Accidental Hypothermia",
        ),
        earlyDetection = "Hypothermia progresses through stages: mild (shivering, cold extremities, mild bradycardia), moderate (shivering stops, confusion, marked bradycardia), severe (loss of consciousness, arrhythmia risk). Wearables catch the mild stage when ambient is cold, skin temperature has dropped, and heart rate has slowed below personal baseline. The Sápmi and Sakha audits flagged this gap — cold-climate residents acclimatise (resting metabolic rate rises seasonally), but unacclimatised owners and frail elderly carry serious hypothermia risk even at marginal temperatures.",
        prevention = "Layered insulating clothing (base, mid, shell) preserves core heat. Wool and synthetic insulators retain warmth when wet; cotton does not. Hydration matters in cold conditions — cold-induced diuresis depletes volume. Frail elderly, owners on beta-blockers or sedatives, and owners with thyroid dysfunction or undernutrition lose heat faster. In cold-climate regions, indoor temperature management matters: 18 °C is the WHO minimum indoor temperature for adult health.",
        healing = "Passive rewarming is sufficient for mild hypothermia: dry clothes, blankets, shelter, warm sweet drinks (no alcohol — accelerates heat loss). Avoid rapid rewarming or strenuous activity, which can precipitate rewarming shock or cardiac arrhythmia. If the person stops shivering, becomes confused, or has slow weak pulse, treat as moderate-to-severe hypothermia and seek emergency care; passive rewarming alone is inadequate at that stage.",
        risks = "Severe hypothermia (core <28 °C) carries a 40 % mortality rate and frequently presents with ventricular arrhythmias. Even mild hypothermia impairs judgement and coordination, increasing risk of further exposure or accident. Cold exposure is a leading winter mortality factor in unhoused populations and in elderly owners with low body fat and impaired thermoregulation. The audit-highlighted populations — Sápmi reindeer herders, Sakha rural communities, high-altitude pastoralists — face this risk routinely; acclimatisation mitigates but does not eliminate it.",
    )

    /**
     * Altitude sickness screen. Activates only when the owner is in a high-
     * elevation context (the rule requires the EnvironmentalContext path —
     * the elevation gate is enforced upstream by the AnomalyDetector
     * filtering on `elevationBand >= HIGH`, but the pattern's required
     * signal still anchors on a high RHR + sleep disruption + recent
     * elevation gain.
     *
     * Recent elevation gain is read from the owner's EnvironmentalContext
     * — when the travel-elevation override is set, the pattern can fire.
     * Otherwise this pattern stays dormant.
     */
    val altitudeSicknessScreen = ConditionPattern(
        id = "altitude_sickness_screen",
        title = "Altitude-adjustment pattern detected",
        category = ConditionCategory.ENVIRONMENTAL,
        signalRules = listOf(
            SignalRule(
                MetricType.HEART_RATE, DeviationDirection.ABOVE, 1.5, 24, 1.2,
                ThresholdSource.LITERATURE,
                "Bärtsch & Saltin (2008) — sustained tachycardia is the most reliable wearable signal of incomplete altitude acclimatisation",
            ),
            SignalRule(
                MetricType.SLEEP_EFFICIENCY, DeviationDirection.BELOW, 1.0, 24, 1.0,
                ThresholdSource.LITERATURE,
                "Roach RC et al. (2018) — periodic breathing and arousals fragment sleep during altitude exposure; Lake Louise Score includes sleep disturbance as a core item",
            ),
            SignalRule(
                MetricType.RESPIRATORY_RATE, DeviationDirection.ABOVE, 1.0, 24, 0.8,
                ThresholdSource.LITERATURE,
                "West JB (2010) — hypoxic ventilatory response elevates respiratory rate during the first 1–3 days at altitude",
            ),
            SignalRule(
                MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 1.5, 12, 1.0,
                ThresholdSource.LITERATURE,
                "West JB (2010) — SpO2 below the owner's altitude-adjusted baseline tracks acute mountain sickness severity",
            ),
        ),
        minActiveSignals = 3,
        explanation = "Resting heart rate is elevated, sleep is fragmented, respiratory rate is increased, and blood-oxygen saturation has dropped below the owner's altitude-adjusted baseline — a pattern consistent with incomplete acclimatisation to high elevation. Acute mountain sickness typically resolves with rest and time at the current altitude; high-altitude pulmonary or cerebral oedema are medical emergencies.",
        suggestedAction = "Avoid further ascent. Rest, hydrate, and allow 24–48 hours for acclimatisation. If headache, nausea, or breathlessness worsen, or if cough, confusion, or ataxia develop, descend immediately and seek medical attention — high-altitude pulmonary or cerebral oedema can be rapidly fatal.",
        references = listOf(
            "West JB (2010) — High Altitude Medicine and Physiology, 5th ed.",
            "Bärtsch P & Saltin B (2008) — General introduction to altitude adaptation and mountain sickness",
            "Roach RC, Hackett PH, Oelz O et al. (2018) — The 2018 Lake Louise Acute Mountain Sickness Score",
        ),
        earlyDetection = "Acute mountain sickness is dose-response: faster ascent and higher altitude make it more likely. Wearable signals appear within 6–12 hours of arrival at altitude. Resting heart rate elevation reflects the cardiovascular cost of low ambient oxygen; periodic breathing during sleep fragments REM and produces the headache-and-fatigue presentation. Owners who travel from sea level to >2500 m without acclimatisation are the most vulnerable; the Lake Louise Score formalises this into a clinical scale that this pattern's signal set parallels.",
        prevention = "Graded ascent is the most effective prophylaxis: above 3000 m, the rule of thumb is to ascend no more than 500 m per night and to spend an extra rest day every 1000 m. Hydration matters but overhydration does not help. Acetazolamide (250 mg twice daily, prescription-only) accelerates acclimatisation; consult a travel-medicine provider before planned high-altitude exposure. Owners with known sickle-cell disease, pulmonary hypertension, or coronary artery disease should seek medical advice before significant altitude travel.",
        healing = "For mild acute mountain sickness: stop ascending, rest, hydrate, manage headache with acetaminophen or ibuprofen, and stay at the current altitude until symptoms resolve. Most cases resolve within 1–3 days at the same elevation. For moderate-to-severe presentations or any sign of pulmonary oedema (productive cough, breathlessness at rest) or cerebral oedema (confusion, ataxia, altered mental status): descend at least 500–1000 m immediately. Supplemental oxygen and dexamethasone may bridge to evacuation. Severe altitude illness is one of the few wilderness emergencies where descent is the definitive intervention.",
        risks = "High-altitude pulmonary oedema and high-altitude cerebral oedema carry double-digit mortality if descent is delayed. Acute mountain sickness can occur in fit, young, previously-unaffected owners — prior tolerance is not protective. Repeat ascent profiles still produce risk. The audit-highlighted populations — Andean miners, Tibetan and Himalayan pilgrims, ski resort visitors — encounter this regularly; acclimatised residents tolerate altitude that would sicken sea-level visitors, but lose acclimatisation after a few weeks at lower elevation.",
    )

    /**
     * Seasonal affective adjustment (SAAD) screen for the short-day
     * photoperiod state (#197, PSYCHIATRY_POV cross-reference). **Strictly
     * advisory** — Bios does not diagnose seasonal-pattern depression and
     * never will. The pattern surfaces the physiological shape that
     * correlates with winter mood changes in clinical literature so the
     * owner can choose what to do with the information.
     */
    val saadWinterPattern = ConditionPattern(
        id = "saad_winter_pattern",
        title = "Short-day photoperiod pattern detected",
        category = ConditionCategory.ENVIRONMENTAL,
        signalRules = listOf(
            // DAYLIGHT_HOURS is computed from owner's latitude + date.
            // SHORT_DAY photoperiod is daylight <10 h.
            SignalRule(
                MetricType.DAYLIGHT_HOURS, DeviationDirection.BELOW, 0.0, 0, 1.0,
                ThresholdSource.LITERATURE,
                "Lewy AJ et al. (2006) — short photoperiod (<10 h daylight) is the seasonal substrate for winter mood pattern shifts",
                required = true,
                absoluteBelow = 10.0,
            ),
            SignalRule(
                MetricType.SLEEP_DURATION, DeviationDirection.ABOVE, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Wirz-Justice A (2018) — hypersomnia is a defining feature of seasonal-pattern mood shifts",
            ),
            SignalRule(
                MetricType.STEPS, DeviationDirection.BELOW, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "Wirz-Justice A (2018) — winter-pattern depression presents with activity withdrawal",
            ),
            SignalRule(
                MetricType.CIRCADIAN_PHASE_SHIFT, DeviationDirection.IRREGULAR, 1.0, 168, 1.2,
                ThresholdSource.LITERATURE,
                "Lewy AJ et al. (2006) — phase delay (sleep midpoint drifting later) is the chronobiological signature of winter SAAD",
            ),
        ),
        minActiveSignals = 3,
        explanation = "The owner's latitude and the current date place them in a short-day photoperiod (less than 10 hours of daylight). Sleep duration has lengthened, activity has dropped, and circadian phase is shifting later — a pattern that correlates with seasonal-pattern mood shifts in clinical literature. This is a data observation, not a diagnosis; many owners experience this pattern without distress.",
        suggestedAction = "Bright-light exposure in the morning (10,000 lux for 20–30 minutes, or sustained outdoor daylight) is the most-evidence-supported non-medication intervention for winter pattern shifts. If you are experiencing persistent low mood, loss of interest, or hopelessness, speaking with a mental health professional is recommended.",
        references = listOf(
            "Lewy AJ, Lefler BJ, Emens JS, Bauer VK (2006) — The circadian basis of winter depression. PNAS 103:7414–7419",
            "Wirz-Justice A, Benedetti F, Terman M (2018) — Chronotherapeutics for Affective Disorders",
            "Rosenthal NE et al. (1984) — Seasonal affective disorder: a description of the syndrome",
        ),
        earlyDetection = "Winter-pattern mood shifts produce a distinctive chronobiological signature: phase delay (later sleep midpoint), longer total sleep, reduced morning activity, and atypical eating patterns. The physiology precedes conscious awareness by weeks. Bios surfaces the pattern data; the owner reads it. Reporting confines itself strictly to the data shape — Bios is not equipped to distinguish seasonal-pattern depression from other mood disorders, from acute grief, from medication side effects, or from natural seasonal preference for rest.",
        prevention = "Consistent morning bright-light exposure — natural where possible, light-therapy lamps where not — is the single highest-yield protective factor against winter-pattern mood shifts in latitudes where daylight drops below 10 hours. Maintaining outdoor time, regular sleep timing, and physical activity through the dark months supports circadian stability. Vitamin D supplementation during low-sun months has independent skeletal-health value and may have mood-adjacent effects; lab-confirmed deficiency makes the case clearer.",
        healing = "If the pattern is established and distressing: morning bright-light therapy, dawn-simulation alarms, and maintaining a consistent rise time are the highest-evidence non-pharmacological interventions. Cognitive-behavioural therapy adapted for seasonal pattern (CBT-SAD) has comparable durability to light therapy. Pharmacological treatments (SSRIs, bupropion in some prescribing guidelines) are clinician-led decisions. If symptoms persist beyond 4 weeks or include suicidal ideation, mental-health professional support is the right step.",
        risks = "Untreated winter-pattern mood shifts carry the same downstream risks as any sustained depressive episode: relationship strain, occupational impact, increased substance use, and elevated suicide risk during the highest-latitude winter months. Bios reports the physiological signature only — judgement about whether the pattern matters in this owner's life belongs to the owner and, if they choose, to a mental-health professional. The audit cross-references psychiatry's posture: Bios is the instrument, the clinician is the diagnostician.",
    )
}
