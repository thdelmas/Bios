package com.bios.app.ui.components

import com.bios.contracts.MetricType

/**
 * Owner-vocabulary synonyms the search bars match against in addition to
 * each metric's `readableName` and `key`. Keys' readableName ("Body mass",
 * "Heart rate variability", "Blood pressure systolic") rarely match what
 * an owner actually types ("weight", "HRV", "BP"). Cover the typed-name
 * gap here rather than renaming canonical keys (which would break every
 * companion pinned to the current contract).
 *
 * Includes the Spanish ER triage abbreviations (TAS/TAD/FC/FR) and the
 * French BMI label (IMC) since CLAUDE.md and the clinical entry surface
 * already adopt that vocabulary.
 */
internal val MetricSynonyms: Map<MetricType, List<String>> = mapOf(
    // Anthropometry / body composition
    MetricType.BODY_MASS to listOf("weight", "poids"),
    MetricType.HEIGHT_CM to listOf("height", "taille"),
    MetricType.BMI_KG_PER_M2 to listOf("bmi", "imc"),
    MetricType.BODY_FAT_PCT to listOf("fat", "body fat", "graisse"),
    MetricType.FAT_MASS_KG to listOf("fat mass"),
    MetricType.LEAN_BODY_MASS_KG to listOf("lean mass", "muscle mass", "lbm"),
    MetricType.BODY_WATER_PCT to listOf("water"),
    MetricType.BONE_MASS to listOf("bone"),

    // Cardiovascular
    MetricType.HEART_RATE to listOf("hr", "pulse", "fc", "pouls"),
    MetricType.RESTING_HEART_RATE to listOf("rhr", "resting hr", "resting"),
    MetricType.HEART_RATE_VARIABILITY to listOf("hrv", "rmssd", "vfc"),
    MetricType.BLOOD_PRESSURE_SYSTOLIC to listOf("bp", "blood pressure", "tas", "systolic"),
    MetricType.BLOOD_PRESSURE_DIASTOLIC to listOf("bp", "blood pressure", "tad", "diastolic"),
    MetricType.BLOOD_OXYGEN to listOf("spo2", "sat", "saturation", "oxygen", "o2"),
    MetricType.VO2_MAX to listOf("vo2"),

    // Respiratory
    MetricType.RESPIRATORY_RATE to listOf("breath", "breathing", "rr", "fr"),
    MetricType.OXYGEN_FLOW_RATE to listOf("o2 flow", "flujo o2"),

    // Temperature
    MetricType.SKIN_TEMPERATURE to listOf("temp", "fever", "temperature"),
    MetricType.BASAL_BODY_TEMPERATURE to listOf("bbt", "basal temp"),

    // Neurology / pain
    MetricType.PAIN_SCORE to listOf("pain", "eva", "vas"),
    MetricType.CONSCIOUSNESS_LEVEL to listOf("gcs", "avpu", "consciousness"),

    // Metabolic / glucose
    MetricType.BLOOD_GLUCOSE to listOf("glucose", "sugar"),
    MetricType.FASTING_GLUCOSE to listOf("fasting sugar"),

    // Lipids
    MetricType.TOTAL_CHOLESTEROL to listOf("cholesterol", "tc"),
    MetricType.LDL_CHOLESTEROL to listOf("ldl"),
    MetricType.HDL_CHOLESTEROL to listOf("hdl"),
    MetricType.APO_B to listOf("apob"),
    MetricType.LIPOPROTEIN_A to listOf("lp(a)", "lipo a"),

    // Common biomarker shorthands
    MetricType.HBA1C to listOf("a1c"),
    MetricType.HSCRP to listOf("crp"),

    // Activity / sleep
    MetricType.STEPS to listOf("step count", "podometer"),
    MetricType.SLEEP_DURATION to listOf("sleep"),
)

/**
 * True when `q` matches any of: the metric's readable name, its canonical
 * key, or any of the owner-vocabulary synonyms. Empty queries match nothing
 * — callers handle the empty-state separately.
 */
internal fun metricMatchesQuery(metric: MetricType, q: String): Boolean {
    val needle = q.trim().lowercase()
    if (needle.isEmpty()) return false
    if (metric.readableName.lowercase().contains(needle)) return true
    if (metric.key.contains(needle)) return true
    val synonyms = MetricSynonyms[metric] ?: return false
    return synonyms.any { it.contains(needle) }
}
