package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.app.physiology.OwnerCondition
import com.bios.contracts.MetricType

/**
 * Tropical / endemic-disease pattern library (issue #196, audit-gap §2.6
 * cluster — AFRICAN_TRADITIONAL_POV, INDIGENOUS_AMERICAS_POV,
 * OTHER_ASIAN_SYSTEMS_POV, OCEANIC_ARCTIC_POV, SIDDHA_POV).
 *
 * Bios's pre-existing infection-onset pattern is Mishra/Quer-anchored on a
 * North-American COVID cohort. The signatures of the tropical and
 * subtropical endemic diseases the affected populations actually face are
 * different enough that one pattern cannot cover both — malaria's cyclical
 * fever every 48–72 h, dengue's plasma-leak defervescence + thrombocytopenia
 * window, leptospirosis's biphasic fever + jaundice + AKI, scrub typhus's
 * eschar + multi-organ marker constellation, sickle-cell vaso-occlusive
 * crisis's sudden hypoxia + pain spike. The wearable surface alone is
 * insufficient for several of these — diagnostic confirmation requires
 * laboratory testing (parasitemia, NS1 antigen, IgM/IgG, urine antigen,
 * dark-field microscopy). Where Bios's FHIR-import surface carries the
 * relevant lab (platelets, bilirubin, creatinine, LFTs, hemoglobin), the
 * patterns lean on the [BiomarkerConditionPatterns] shape — lab anchor +
 * wearable corroborators.
 *
 * **Region gating.** Every pattern in this file declares
 * `requiresTropicalRegion = true`. The [com.bios.app.engine.AnomalyDetector]
 * filters them out unless the active [com.bios.app.config.RegionConfig] has
 * `tropicalDiseaseRelevant = true` (sub-Saharan Africa, South / Southeast
 * Asia, Pacific, Caribbean, tropical South America) — or the owner has set
 * a travelling-/lived-in-tropics override. This is what stops the patterns
 * mis-firing on a Stockholm winter resting-tachycardia drift.
 *
 * **Sickle cell gating.** The vaso-occlusive crisis screen is gated on
 * [OwnerCondition.KNOWN_SICKLE_CELL_DISEASE] rather than region: SCD
 * affects a global diaspora and the signature is too non-specific in
 * non-SCD owners (a desaturation + tachycardia + pain spike can be
 * infection, anxiety, exertion in the general population — only meaningful
 * when prior probability is high).
 *
 * **Patterns shipped:**
 *  - `malaria_suggestive` — cyclical fever every 48–72 h (skin-temperature
 *    deviation) + sustained tachycardia + low hemoglobin if available.
 *    WHO Guidelines for the Treatment of Malaria (3rd ed. 2015 + 2023
 *    update); CDC Yellow Book 2024 ch. 5.
 *  - `dengue_warning` — defervescence + tachycardia + platelet drop /
 *    hematocrit rise (classic plasma-leak day 3–7 signature). WHO Dengue
 *    Guidelines (2009, 2022 update); CDC Yellow Book 2024.
 *  - `chikungunya_suggestive` — sudden fever + tachycardia + owner-flagged
 *    polyarthralgia symptom marker. PAHO/WHO Chikungunya Guidelines (2015).
 *  - `leptospirosis_suggestive` — fever + bilirubin rise + creatinine rise
 *    (Weil's-disease biphasic). WHO Human Leptospirosis Guidelines (2003);
 *    CDC Yellow Book 2024.
 *  - `scrub_typhus_suggestive` — fever + multi-organ markers (bilirubin,
 *    creatinine, AST/ALT). WHO SEARO Scrub Typhus Guidelines (2015).
 *  - `sickle_cell_crisis_screen` — sudden tachycardia + SpO2 drop +
 *    pain-score spike. NHLBI Evidence-Based Management of Sickle Cell
 *    Disease (2014); ASH 2020 SCD Pain Crisis Guideline.
 *  - `ciguatera_suggestive` — bradycardia + owner-flagged cold-allodynia
 *    + GI marker. Pacific-region context. CDC Yellow Book 2024 ch. 4
 *    (Marine Toxins); SPC Ciguatera Resource (2023).
 *
 * **Deferred.** Ebola / Marburg / Lassa / Crimean-Congo HF — the wearable
 * signature in early viral hemorrhagic fever (mild fever + malaise) is
 * indistinguishable from any other early viral illness, and the
 * differentiating signs (mucosal bleeding, conjunctival injection,
 * hypotension late) sit outside the wearable surface. Adding patterns
 * for these conditions would either duplicate `infection_onset` or
 * trigger only at terminal stages where the alert is unactionable.
 * Schistosomiasis and sleeping sickness (HAT) are subacute / chronic and
 * present with eosinophilia / CSF findings the FHIR-import surface
 * doesn't carry today — deferred until issue-tracking adds those keys.
 *
 * **AlertContentPolicy compliance.** Every pattern's text says
 * "fever pattern suggestive of X — consider diagnostic testing if
 * exposure-risk applies"; none of them claim a diagnosis. The
 * suggested-action language follows the data-statement + professional-
 * referral shape the [AlertContentPolicy] tests enforce.
 *
 * **References (per the audit):**
 *  - World Health Organization (2015 + 2023) — Guidelines for the
 *    Treatment of Malaria, 3rd edition.
 *  - World Health Organization (2009, 2022) — Dengue: Guidelines for
 *    Diagnosis, Treatment, Prevention and Control.
 *  - World Health Organization (2003) — Human Leptospirosis: Guidance
 *    for Diagnosis, Surveillance and Control.
 *  - PAHO/WHO (2015) — Chikungunya: A Guide for Clinicians.
 *  - WHO SEARO (2015) — Guidelines for Diagnosis and Management of
 *    Scrub Typhus.
 *  - CDC (2024) — Yellow Book: Health Information for International
 *    Travel.
 *  - NHLBI (2014) — Evidence-Based Management of Sickle Cell Disease.
 *  - ASH (2020) — Management of Acute and Chronic Pain in Sickle Cell
 *    Disease (Brandow et al., Blood Advances).
 */
object TropicalDiseasePatterns {

    val all by lazy {
        listOf(
            malariaSuggestive,
            dengueWarning,
            chikungunyaSuggestive,
            leptospirosisSuggestive,
            scrubTyphusSuggestive,
            sickleCellCrisisScreen,
            ciguateraSuggestive,
        )
    }

    /**
     * Malaria-suggestive fever pattern. The defining clinical signature
     * of *Plasmodium* infection is the cyclical paroxysmal fever — every
     * 48 h (*P. vivax*, *P. ovale*, *P. falciparum* roughly), every 72 h
     * (*P. malariae*) — with sustained inter-paroxysm tachycardia and
     * progressive anemia as parasitemia rises. The wearable surface
     * captures the temperature spikes via [MetricType.SKIN_TEMPERATURE_DEVIATION]
     * and the tachycardia via [MetricType.RESTING_HEART_RATE]; the
     * cyclical-spacing detection itself is statistical work that doesn't
     * fit the SignalRule shape, so this pattern uses **sustained
     * 72-hour fever deviation + sustained tachycardia** as the wearable
     * gate. Hemoglobin, if available from a recent CBC, anchors the
     * pattern toward malaria-specific differential rather than general
     * febrile illness.
     */
    val malariaSuggestive = ConditionPattern(
        id = "malaria_suggestive",
        title = "Fever and tachycardia pattern suggestive of malaria",
        category = ConditionCategory.INFECTIOUS,
        signalRules = listOf(
            SignalRule(
                MetricType.SKIN_TEMPERATURE_DEVIATION, DeviationDirection.ABOVE, 1.5, 72, 1.5,
                ThresholdSource.LITERATURE,
                "WHO Treatment of Malaria 3rd ed. — paroxysmal fever recurring every 48–72 h is the cardinal sign of plasmodial infection",
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.5, 48, 1.2,
                ThresholdSource.LITERATURE,
                "CDC Yellow Book 2024 — sustained tachycardia accompanies the inter-paroxysm phase of malaria as parasitemia rises",
            ),
            SignalRule(
                MetricType.HEMOGLOBIN, DeviationDirection.BELOW, 0.0, 0, 1.0,
                ThresholdSource.LITERATURE,
                "WHO 2015 — severe-falciparum malaria criterion: Hb <7 g/dL in adults; <12 is the universal anemia threshold that anchors the pattern when CBC is available",
                absoluteBelow = 12.0,
            ),
        ),
        minActiveSignals = 2,
        explanation = "Skin-temperature deviation and resting heart rate have been elevated together over the past 48–72 hours in a pattern compatible with cyclical-fever illnesses endemic to your region. Malaria is one possibility; the differential also includes dengue, chikungunya, leptospirosis, scrub typhus, and many other febrile illnesses. This is a data observation against published clinical patterns — not a diagnosis.",
        suggestedAction = "If exposure-risk applies — recent travel to or residence in a malaria-endemic area, mosquito bites in the past 1–4 weeks — seek diagnostic testing (thick / thin blood film microscopy or rapid diagnostic test for *P. falciparum* HRP-2). Bring the recent temperature and heart-rate trends to share with the assessing clinician. Severe-malaria warning signs (impaired consciousness, severe weakness, respiratory distress, dark urine, repeated vomiting) require emergency care without delay.",
        references = listOf(
            "World Health Organization (2015, 2023) — Guidelines for the Treatment of Malaria, 3rd edition",
            "CDC (2024) — Yellow Book: Health Information for International Travel, ch. 5",
            "World Health Organization (2014) — Severe Malaria, Tropical Medicine & International Health",
        ),
        earlyDetection = "Malaria's wearable signature is dominated by the paroxysmal-fever pattern: sharp temperature spikes separated by 48 (tertian) or 72 (quartan) hours, with inter-paroxysm tachycardia that reflects the parasitemia load. Skin-temperature deviation from baseline tends to swing 1–2 sigma above and back over each cycle, while resting heart rate stays elevated throughout. Hemoglobin drops progressively over days to weeks as red-cell destruction outpaces erythropoiesis. The 72-hour pattern-window in the SignalRule above captures the slowest cycle.",
        risks = "Severe falciparum malaria — cerebral malaria, severe anemia, acute kidney injury, ARDS, hypoglycemia — has high mortality without prompt antimalarial therapy. The Surviving Sepsis Campaign and WHO both anchor every hour of treatment delay in severe malaria to measurable mortality increase. Pregnant owners, children, and immunologically naïve travellers face higher severe-disease risk. Bios's data observation is supportive — diagnostic confirmation by blood film or RDT and timely artemisinin-based combination therapy are the standard of care.",
        requiresTropicalRegion = true,
    )

    /**
     * Dengue warning-phase pattern. WHO Dengue Guidelines describe a
     * classic day-3-to-7 plasma-leak window when defervescence (the
     * temperature dropping from peak fever) is paradoxically the most
     * dangerous moment — thrombocytopenia, hemoconcentration (rising
     * hematocrit), and capillary leak coincide. Wearable surface
     * captures defervescence + compensatory tachycardia; FHIR-imported
     * platelet count and hematocrit anchor the pattern.
     */
    val dengueWarning = ConditionPattern(
        id = "dengue_warning",
        title = "Defervescence pattern suggestive of dengue warning phase",
        category = ConditionCategory.INFECTIOUS,
        signalRules = listOf(
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.5, 24, 1.5,
                ThresholdSource.LITERATURE,
                "WHO Dengue 2009/2022 — persistent tachycardia during defervescence is the compensatory hallmark of plasma leak",
            ),
            SignalRule(
                MetricType.PLATELETS, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "WHO Dengue 2009/2022 — platelet count <100 ×10⁹/L marks the warning phase; <50 raises severe-dengue risk",
                absoluteBelow = 100.0,
            ),
            SignalRule(
                MetricType.HEMATOCRIT, DeviationDirection.ABOVE, 1.0, 168, 1.0,
                ThresholdSource.LITERATURE,
                "WHO Dengue 2009/2022 — rising hematocrit (≥20% above baseline) indicates capillary leak and plasma loss",
            ),
            SignalRule(
                MetricType.SKIN_TEMPERATURE_DEVIATION, DeviationDirection.BELOW, 1.0, 24, 0.8,
                ThresholdSource.LITERATURE,
                "WHO Dengue 2009/2022 — abrupt defervescence on day 3–7 marks the critical phase transition",
            ),
        ),
        minActiveSignals = 2,
        explanation = "Skin-temperature has dropped from a previous fever while resting heart rate remains elevated, in a pattern compatible with the day-3-to-7 critical phase of dengue. If a recent CBC shows falling platelets or rising hematocrit, the pattern is corroborated. This is a data observation; the differential during defervescence is wide and includes many other resolving febrile illnesses.",
        suggestedAction = "If exposure-risk applies — recent travel to or residence in a dengue-endemic area, mosquito bites in the prior 4–14 days, prior febrile illness in this window — seek urgent diagnostic testing (NS1 antigen days 1–7, IgM serology day 5 onward, paired CBC for platelets + hematocrit). Warning signs requiring emergency care: severe abdominal pain, persistent vomiting, mucosal bleeding, lethargy, rapid breathing, cold clammy extremities. The plasma-leak window closes 24–48 hours after onset; supportive fluid management during the critical phase is the standard of care.",
        references = listOf(
            "World Health Organization (2009, 2022) — Dengue: Guidelines for Diagnosis, Treatment, Prevention and Control",
            "CDC (2024) — Yellow Book: Dengue chapter",
            "Halstead SB (2007) — Dengue. Lancet",
        ),
        earlyDetection = "The defining feature of severe dengue is paradoxical: clinical deterioration coincides with the fever falling, not rising. Bios watches for the temperature-falling-but-heart-rate-elevated pattern in the 24-hour window following a prior fever, which is the wearable proxy for defervescence-phase tachycardia. Platelet count falling below 100 ×10⁹/L and hematocrit rising 20% above baseline are the two laboratory anchors that distinguish dengue warning-phase from a resolving viral illness.",
        risks = "Severe dengue — dengue shock syndrome, severe bleeding, severe organ involvement — develops in roughly 5% of symptomatic infections and is the leading cause of pediatric hospital admission in many endemic-region settings. The Pan American Health Organization and WHO both anchor outcome to fluid-management initiation within the warning-phase window. Owners with prior dengue infection face higher severe-disease risk on heterotypic re-infection (antibody-dependent enhancement).",
        severityFloor = AlertTier.URGENT,
        requiresTropicalRegion = true,
    )

    /**
     * Chikungunya-suggestive pattern. The defining clinical signature is
     * sudden-onset high fever + incapacitating polyarthralgia (chikungunya
     * is the Kimakonde word for "to walk bent over"). Polyarthralgia is
     * an owner-reported symptom — Bios has no joint-pain wearable signal,
     * so the pattern leans on sudden fever + tachycardia from the
     * wearable surface and treats the polyarthralgia component as
     * pull-side context the owner annotates after the alert. The
     * differential against dengue is reduced platelet drop and absence
     * of plasma leak; against malaria is non-cyclical fever.
     */
    val chikungunyaSuggestive = ConditionPattern(
        id = "chikungunya_suggestive",
        title = "Sudden fever pattern suggestive of arboviral illness",
        category = ConditionCategory.INFECTIOUS,
        signalRules = listOf(
            SignalRule(
                MetricType.SKIN_TEMPERATURE_DEVIATION, DeviationDirection.ABOVE, 2.0, 12, 1.5,
                ThresholdSource.LITERATURE,
                "PAHO/WHO 2015 — chikungunya presents with abrupt high fever (>38.9 °C); rapid temperature rise is the cardinal wearable signal",
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.5, 12, 1.0,
                ThresholdSource.LITERATURE,
                "PAHO/WHO 2015 — compensatory tachycardia accompanies the acute febrile phase",
            ),
            SignalRule(
                MetricType.PAIN_SCORE, DeviationDirection.ABOVE, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "PAHO/WHO 2015 — incapacitating polyarthralgia (pain score ≥6/10 in multiple joints) is the differentiating clinical feature vs dengue",
                absoluteAbove = 6.0,
            ),
        ),
        minActiveSignals = 2,
        explanation = "A sudden temperature rise and compensatory tachycardia have appeared in a pattern compatible with arboviral illness. If accompanied by severe joint pain (especially in the hands, wrists, ankles, and feet), the pattern is suggestive of chikungunya. The differential during the acute febrile phase includes dengue and Zika; serological testing distinguishes them.",
        suggestedAction = "If exposure-risk applies — recent travel to or residence in a chikungunya-endemic area, mosquito bites in the prior 3–7 days — seek diagnostic testing (RT-PCR in the first 5 days, IgM/IgG serology after day 5). Joint-pain management and supportive care are the standard of care in the acute phase. Chronic-phase arthralgia can persist for months; rheumatology follow-up may be useful if joint pain persists beyond the acute illness.",
        references = listOf(
            "PAHO/WHO (2015) — Chikungunya: A Guide for Clinicians",
            "Burt FJ et al. (2017) — Chikungunya virus: an update on the biology and pathogenesis of this emerging pathogen. Lancet Infectious Diseases",
            "CDC (2024) — Yellow Book: Chikungunya chapter",
        ),
        earlyDetection = "Chikungunya's wearable signature is the *rate* of the temperature rise — a 2-sigma deviation within a 12-hour window — coupled with severe early-onset joint pain that the owner notes via the [MetricType.PAIN_SCORE] manual-entry surface. The pain-score threshold (≥6/10) is what differentiates chikungunya from dengue: dengue's myalgia tends to be diffuse muscular rather than joint-localized, and chikungunya pain reliably reaches the high end of the numerical-rating scale.",
        risks = "Acute chikungunya is rarely fatal but produces severe morbidity through the polyarthralgia phase, which can persist as chronic inflammatory arthritis for months to years in 30–40% of cases. Older owners and those with pre-existing joint disease face higher chronic-phase risk. The differential during the acute febrile phase is critical because dengue treatment (avoid NSAIDs / aspirin pre-platelet-result) differs from chikungunya (NSAIDs are first-line analgesia).",
        requiresTropicalRegion = true,
    )

    /**
     * Leptospirosis-suggestive (Weil's-disease) pattern. Classic biphasic
     * presentation: 4–7 day septicemic phase with fever + myalgia, brief
     * defervescence, then immune phase with jaundice + AKI + hemorrhage
     * in 5–10% of cases (Weil's disease). Wearable surface captures
     * the fever + tachycardia; bilirubin and creatinine from FHIR-import
     * anchor the severe-form pattern. Common exposures: freshwater
     * contact (flooding, agricultural work, recreational water sports).
     */
    val leptospirosisSuggestive = ConditionPattern(
        id = "leptospirosis_suggestive",
        title = "Fever with hepatic and renal markers suggestive of leptospirosis",
        category = ConditionCategory.INFECTIOUS,
        signalRules = listOf(
            SignalRule(
                MetricType.SKIN_TEMPERATURE_DEVIATION, DeviationDirection.ABOVE, 1.5, 48, 1.2,
                ThresholdSource.LITERATURE,
                "WHO 2003 — sustained fever is the cardinal feature of the septicemic phase",
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 48, 0.8,
                ThresholdSource.LITERATURE,
                "WHO 2003 — compensatory tachycardia accompanies the febrile phase",
            ),
            SignalRule(
                // Bilirubin isn't a MetricType key today; AST/ALT are the
                // closest hepatic-injury markers available on the bus.
                // Use AST as the hepatic anchor — Weil's disease produces
                // mixed hepatocellular + cholestatic injury that AST/ALT
                // reflect alongside the bilirubin rise.
                MetricType.AST, DeviationDirection.ABOVE, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "WHO 2003 — hepatic involvement in the immune phase produces transaminase elevation (AST often >50 U/L); jaundice is the hallmark of Weil's disease",
                absoluteAbove = 50.0,
            ),
            SignalRule(
                MetricType.CREATININE, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "WHO 2003 — acute kidney injury (creatinine >1.3 mg/dL) marks the severe form (Weil's disease) and correlates with mortality",
                absoluteAbove = 1.3,
            ),
        ),
        minActiveSignals = 2,
        explanation = "A fever pattern has appeared together with hepatic or renal markers consistent with biphasic spirochete illness. Leptospirosis is one possibility; the differential also includes viral hepatitis, scrub typhus, severe malaria, and dengue. The wearable signature alone is non-specific — the laboratory anchors (AST, creatinine) are what narrow it toward leptospirosis-compatible.",
        suggestedAction = "If exposure-risk applies — recent freshwater contact (flooding, agricultural work, recreational water sports), livestock or rodent exposure — seek diagnostic testing (MAT serology, blood / urine PCR, urine dark-field microscopy). Empirical doxycycline or penicillin is the standard early treatment when clinical suspicion is high; severe-form (Weil's) care typically requires hospital admission for fluid management, dialysis if needed, and exchange transfusion in the most severe presentations.",
        references = listOf(
            "World Health Organization (2003) — Human Leptospirosis: Guidance for Diagnosis, Surveillance and Control",
            "CDC (2024) — Yellow Book: Leptospirosis chapter",
            "Bharti AR et al. (2003) — Leptospirosis: a zoonotic disease of global importance. Lancet Infectious Diseases",
        ),
        earlyDetection = "Leptospirosis is biphasic: 4–7 days of fever + myalgia + conjunctival suffusion in the septicemic phase, a brief 1–3 day asymptomatic window, then the immune phase with jaundice, AKI, and pulmonary hemorrhage in the severe form. The wearable surface catches the fever and tachycardia early; the labs distinguish leptospirosis from the other tropical-fever differentials. Calf-muscle tenderness is a classic exam finding the owner may report alongside the fever.",
        risks = "Weil's disease — severe icteric leptospirosis with AKI and pulmonary hemorrhage — has 5–15% mortality even with modern critical care. The Surviving Sepsis Campaign hour-1 bundle, dialysis for severe AKI, and species-appropriate antibiotic (doxycycline or penicillin G in mild disease; ceftriaxone in severe) are the standard of care. Outbreaks follow flooding events; rural agricultural workers, sewage workers, and adventure-tourism populations face the highest exposure risk.",
        requiresTropicalRegion = true,
    )

    /**
     * Scrub typhus pattern. *Orientia tsutsugamushi* infection following
     * chigger-mite bite. Classic triad: fever + eschar (a painless black
     * crust at the bite site; owner-reported only) + lymphadenopathy.
     * Multi-organ involvement in severe disease — transaminitis, AKI,
     * thrombocytopenia, encephalitis, ARDS. Endemic to the "tsutsugamushi
     * triangle" (India / Pakistan in the west, Japan / Korea / Russia
     * in the north, northern Australia in the south). Wearable surface
     * + FHIR-import labs anchor the multi-organ pattern.
     */
    val scrubTyphusSuggestive = ConditionPattern(
        id = "scrub_typhus_suggestive",
        title = "Fever with multi-organ markers suggestive of scrub typhus",
        category = ConditionCategory.INFECTIOUS,
        signalRules = listOf(
            SignalRule(
                MetricType.SKIN_TEMPERATURE_DEVIATION, DeviationDirection.ABOVE, 1.5, 72, 1.2,
                ThresholdSource.LITERATURE,
                "WHO SEARO 2015 — high-grade fever lasting 5–7 days is the cardinal feature of scrub typhus",
            ),
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 1.0, 48, 0.8,
                ThresholdSource.LITERATURE,
                "WHO SEARO 2015 — compensatory tachycardia accompanies the febrile phase; relative bradycardia is less common than in typhoid fever",
            ),
            SignalRule(
                MetricType.AST, DeviationDirection.ABOVE, 0.0, 0, 1.0,
                ThresholdSource.LITERATURE,
                "WHO SEARO 2015 — transaminitis (AST >50 U/L) is found in 70–90% of scrub typhus cases and marks hepatic involvement",
                absoluteAbove = 50.0,
            ),
            SignalRule(
                MetricType.CREATININE, DeviationDirection.ABOVE, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "WHO SEARO 2015 — acute kidney injury (creatinine >1.3 mg/dL) marks severe scrub typhus and is associated with mortality",
                absoluteAbove = 1.3,
            ),
            SignalRule(
                MetricType.PLATELETS, DeviationDirection.BELOW, 0.0, 0, 1.0,
                ThresholdSource.LITERATURE,
                "WHO SEARO 2015 — thrombocytopenia (platelets <100 ×10⁹/L) is common in scrub typhus and aligns with the multi-organ involvement axis",
                absoluteBelow = 100.0,
            ),
        ),
        minActiveSignals = 2,
        explanation = "A fever pattern has appeared together with multiple organ markers — hepatic, renal, or hematological — in a constellation compatible with rickettsial illness. Scrub typhus is one possibility; the differential includes other rickettsioses, leptospirosis, severe malaria, and dengue. The wearable signature alone is non-specific.",
        suggestedAction = "If exposure-risk applies — recent travel to or residence in the tsutsugamushi triangle (South / Southeast Asia, northern Australia, Pacific), outdoor exposure in scrub or grassland — examine the skin for a painless black-crusted eschar (typically in skin folds: groin, axilla, waistline) and seek diagnostic testing (Weil-Felix test, IFA serology, PCR if available). Empirical doxycycline is the standard early treatment when clinical suspicion is high — delay correlates with mortality.",
        references = listOf(
            "WHO SEARO (2015) — Guidelines for Diagnosis and Management of Scrub Typhus",
            "Watt G, Parola P (2003) — Scrub typhus and tropical rickettsioses. Current Opinion in Infectious Diseases",
            "Taylor AJ et al. (2015) — A systematic review of mortality from untreated scrub typhus (Orientia tsutsugamushi). PLOS Neglected Tropical Diseases",
        ),
        earlyDetection = "Scrub typhus's distinguishing feature is the eschar — a painless 5–15 mm black-crusted ulcer at the chigger-bite site found in 40–70% of cases, often in skin folds the owner doesn't routinely inspect. Bios cannot detect the eschar; the wearable + lab pattern is the trigger for the owner to look. The fever curve tends to be sustained high-grade rather than cyclical (distinguishing from malaria); the multi-organ involvement axis (transaminitis + AKI + thrombocytopenia) distinguishes scrub typhus from uncomplicated viral illness.",
        risks = "Untreated scrub typhus has 6–35% mortality depending on the strain and region. Severe disease produces ARDS, meningoencephalitis, septic shock, and multi-organ failure. The single most important determinant of outcome is *time to doxycycline* — empiric treatment when clinical suspicion is high, before confirmatory serology returns, is the standard of care in endemic regions. Owners with co-morbidities (diabetes, hepatic disease, chronic kidney disease) face higher severe-disease risk.",
        requiresTropicalRegion = true,
    )

    /**
     * Sickle-cell vaso-occlusive crisis (VOC) screen. The signature
     * difference from general pain / infection: sudden onset (not
     * gradual), simultaneous SpO2 drop (suggesting acute chest syndrome
     * or sequestration), tachycardia, and severe pain spike. Gated on
     * [OwnerCondition.KNOWN_SICKLE_CELL_DISEASE] because the pattern is
     * too non-specific in non-SCD owners — anxiety, exertion, infection,
     * and many other states can produce the same wearable signature
     * with prior probability that doesn't warrant an SCD-specific alert.
     *
     * Audit cross-reference: AFRICAN_TRADITIONAL_POV §2.4 — Bios had no
     * SCD-aware pattern; the diaspora is global and the population most
     * affected lives in tropical and sub-tropical regions.
     */
    val sickleCellCrisisScreen = ConditionPattern(
        id = "sickle_cell_crisis_screen",
        title = "Pattern suggestive of sickle cell vaso-occlusive crisis",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.ABOVE, 2.0, 6, 1.5,
                ThresholdSource.LITERATURE,
                "NHLBI 2014 — sudden tachycardia accompanies acute VOC pain crisis and acute chest syndrome",
            ),
            SignalRule(
                MetricType.BLOOD_OXYGEN, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "NHLBI 2014 / ASH 2020 — SpO2 below 92% in an owner with SCD raises concern for acute chest syndrome (ACS) — the leading cause of mortality in adults with SCD",
                absoluteBelow = 92.0,
            ),
            SignalRule(
                MetricType.PAIN_SCORE, DeviationDirection.ABOVE, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "ASH 2020 — vaso-occlusive crisis pain typically scores ≥7/10 on numerical-rating scale; severity is what differentiates VOC from baseline chronic SCD pain",
                absoluteAbove = 7.0,
            ),
        ),
        minActiveSignals = 2,
        explanation = "Resting heart rate has risen sharply, oxygen saturation has dropped, or pain score has spiked in a pattern compatible with sickle cell vaso-occlusive crisis. Acute chest syndrome — the leading cause of mortality in adults with SCD — can present with this combination and develops rapidly. This is a data observation against published SCD-specific clinical patterns; many other causes can produce a similar wearable signature.",
        suggestedAction = "If pain has reached the level usually identified as a crisis, or if breathing has become difficult, seek emergency care — acute chest syndrome can develop within hours and requires hospital management. Bring the recent SpO2, heart-rate, and pain-score trends to the assessing clinician. Standard SCD care includes prompt analgesia (per the SCD care plan), hydration, supplemental oxygen as needed, and incentive spirometry as an ACS-prevention measure.",
        references = listOf(
            "NHLBI (2014) — Evidence-Based Management of Sickle Cell Disease: Expert Panel Report",
            "Brandow AM et al. (2020) — American Society of Hematology 2020 guidelines for sickle cell disease: management of acute and chronic pain. Blood Advances",
            "Vichinsky EP et al. (2000) — Causes and outcomes of the acute chest syndrome in sickle cell disease. NEJM",
        ),
        earlyDetection = "VOC's wearable signature in SCD differs from a general-population pain alert in three ways: it is sudden (within hours rather than days), the pain score reliably reaches the high end of the numerical scale (≥7/10), and SpO2 may drop into the 88–92% range as acute chest syndrome begins. Bios uses a short 6-hour window for the heart-rate signal to catch this acceleration and uses absolute SpO2 / pain-score thresholds rather than personal-baseline z-scores because SCD owners' baselines are typically already shifted.",
        risks = "Acute chest syndrome is the leading cause of mortality in adults with SCD; rates of ACS are highest within 1–3 days after a painful crisis begins. Prompt recognition + supportive care (supplemental oxygen, incentive spirometry, hydration, analgesia, sometimes transfusion) reduces ACS-related mortality. Other VOC complications — splenic sequestration, priapism, hepatic crisis, stroke — also require urgent assessment. Owners should keep an SCD-aware care plan accessible during alerts.",
        severityFloor = AlertTier.URGENT,
        requiredOwnerConditions = setOf(OwnerCondition.KNOWN_SICKLE_CELL_DISEASE),
    )

    /**
     * Ciguatera fish-poisoning pattern. Caused by ciguatoxins
     * bioaccumulating in large reef-fish (barracuda, grouper, snapper,
     * moray eel). Classic presentation: 1–24 h after consumption, GI
     * symptoms (vomiting, diarrhea, abdominal pain), followed within
     * 24–72 h by neurological features — paradoxical cold-allodynia
     * (cold objects feel painful / burning), paresthesias,
     * bradycardia, hypotension. Pacific and Caribbean reef-fish
     * exposure context. Cold-allodynia is owner-reported (no wearable
     * signal); wearable surface picks up the bradycardia.
     */
    val ciguateraSuggestive = ConditionPattern(
        id = "ciguatera_suggestive",
        title = "Bradycardia pattern suggestive of ciguatera fish-poisoning",
        category = ConditionCategory.CARDIOVASCULAR,
        signalRules = listOf(
            SignalRule(
                MetricType.RESTING_HEART_RATE, DeviationDirection.BELOW, 0.0, 0, 1.5,
                ThresholdSource.LITERATURE,
                "CDC Yellow Book 2024 / SPC — ciguatera produces sinus bradycardia (resting HR <50 bpm) via cholinergic stimulation as ciguatoxins target voltage-gated sodium channels",
                absoluteBelow = 50.0,
                absoluteWindowHours = 48,
                absoluteMinReadings = 2,
            ),
            SignalRule(
                MetricType.BLOOD_PRESSURE_SYSTOLIC, DeviationDirection.BELOW, 0.0, 0, 1.2,
                ThresholdSource.LITERATURE,
                "CDC Yellow Book 2024 — hypotension (SBP <100 mmHg) accompanies the bradycardia in moderate-to-severe ciguatera",
                absoluteBelow = 100.0,
                absoluteWindowHours = 48,
                absoluteMinReadings = 1,
            ),
            SignalRule(
                MetricType.PAIN_SCORE, DeviationDirection.ABOVE, 0.0, 0, 1.0,
                ThresholdSource.LITERATURE,
                "CDC Yellow Book 2024 — cold-allodynia (cold objects perceived as painful / burning) is the pathognomonic neurological feature; owner-reported via pain score on cold exposure",
                absoluteAbove = 5.0,
            ),
        ),
        minActiveSignals = 2,
        explanation = "A bradycardia pattern has appeared together with hypotension or owner-reported cold-allodynia in a pattern compatible with marine-toxin envenomation. Ciguatera fish-poisoning is one possibility in the Pacific and Caribbean reef-fish context. The differential includes other marine toxins (tetrodotoxin, palytoxin), medication-related bradycardia, and primary cardiac conduction disease.",
        suggestedAction = "If exposure-risk applies — consumption of large reef-fish (barracuda, grouper, snapper, moray eel, amberjack) in the prior 24–72 hours — seek medical assessment. There is no rapid diagnostic test; diagnosis is clinical, anchored by the cold-allodynia feature. Mannitol within 48 hours of symptom onset has historically been used; supportive care (atropine for severe bradycardia, fluids for hypotension, analgesia for neuropathic pain) is the mainstay. Avoid alcohol, caffeine, fish, nuts, and exercise during recovery — they can reactivate symptoms for weeks to months.",
        references = listOf(
            "CDC (2024) — Yellow Book: Marine Toxins chapter",
            "Friedman MA et al. (2017) — An updated review of ciguatera fish poisoning: clinical, epidemiological, environmental, and public health management. Marine Drugs",
            "Pacific Community SPC (2023) — Ciguatera Online: Resources for Pacific Island health professionals",
        ),
        earlyDetection = "Ciguatera's distinguishing feature is the *cold-allodynia* — touching a cold object (cold drink, refrigerator handle, cold water) produces a burning or electric-shock sensation rather than the expected cold. This is pathognomonic and the owner is the one who reports it; Bios surfaces the bradycardia + hypotension that often accompany it. The wearable bradycardia threshold (RHR <50 bpm sustained over 48 h) is calibrated to flag the cholinergic-stimulation signature without over-firing on endurance athletes (the [com.bios.app.physiology.PhysiologyState.ATHLETE_HIGH_FITNESS] state suppresses this pattern via the existing exclusion shape).",
        risks = "Most ciguatera cases are self-limiting over weeks; severe cases produce cardiovascular collapse, respiratory failure, and prolonged neurological symptoms persisting for months to years. Repeat exposures during the recovery window can precipitate severe relapse. Pacific Island populations face the highest exposure risk; climate-driven coral-reef ciguatoxin distribution is expanding the at-risk geography.",
        requiresTropicalRegion = true,
        // Endurance-athlete RHR baseline is often below the 50 bpm absolute
        // bradycardia threshold this pattern uses; suppress the pattern in
        // that state to avoid the standard false-positive shape.
        excludedStates = setOf(com.bios.app.physiology.PhysiologyState.ATHLETE_HIGH_FITNESS),
    )
}
