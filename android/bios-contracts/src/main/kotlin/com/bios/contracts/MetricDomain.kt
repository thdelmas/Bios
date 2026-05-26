package com.bios.contracts

/**
 * Top-level grouping for a [MetricType]. Used by the UI for category
 * sectioning, by the engine for proxy-relationship lookups, and by the
 * dashboard to auto-discover biomarker keys (`domain == BIOMARKER`).
 *
 * Adding a domain is non-breaking; renaming or removing one breaks every
 * consumer that grouped by it.
 */
enum class MetricDomain {
    CARDIOVASCULAR, RESPIRATORY, TEMPERATURE, SLEEP,
    ACTIVITY, METABOLIC, RECOVERY, WOMENS_HEALTH,
    MENTAL_HEALTH, NEUROLOGICAL, INTAKE, SAFETY, ENVIRONMENT, BIOMARKER,
    /** Length / circumference measurements (height, head circumference, BMI). */
    ANTHROPOMETRY,
    /** Body composition (lean mass, fat mass) — distinct from METABOLIC weight/% scalars. */
    BODY_COMPOSITION,
}
