package com.bios.app.model

import com.bios.app.config.ClimateZone

/**
 * Owner-set environmental context that modulates absolute clinical thresholds
 * for SpO2 / heart rate / temperature / sleep (#197). Converges five audit
 * findings: SOWA_RIGPA_POV §2.1 (altitude — Tibet/Bhutan/Mongolia/Nepal/
 * Andean Peru/Bolivia/Ecuador), INDIGENOUS_AMERICAS_POV (Andes/Mesoamerica
 * altitude), OCEANIC_ARCTIC_POV (Sápmi/Sakha cold + Pacific heat),
 * AFRICAN_TRADITIONAL_POV (heat-acclimation), and MODERN_NON_ALLOPATHIC_POV
 * (environmental medicine).
 *
 * Bios's personal-baseline path self-corrects for chronic environmental
 * exposure (an acclimatised Lhasa resident's 14-day SpO2 baseline already
 * sits at 88 %); the hard-cutoff path in [com.bios.app.alerts.EmergencyVitalPatterns]
 * does not. This context structure is what the hard-cutoff path reads to
 * stay clinically correct.
 *
 * Manifesto-aligned: every field below is either owner-set (Settings →
 * Environmental context) or derived from a value that is itself owner-set
 * (e.g. [photoperiod] derives from owner-confirmed latitude + the calendar).
 * No GPS, no IP geolocation, no inference from sensor patterns.
 */
data class EnvironmentalContext(
    /** Current elevation above sea level, in metres. Owner-set. */
    val elevationM: Double,
    /** Climate zone (Köppen-derived broad band). Owner-overrides region default. */
    val climateZone: ClimateZone,
    /** Photoperiod state for the owner's current date + latitude. */
    val photoperiod: PhotoperiodState,
    /** Hours of daylight at the owner's latitude on the current date. */
    val daylightHours: Double,
) {
    /** Convenience: which elevation band the [elevationM] value falls in. */
    val elevationBand: ElevationBand get() = ElevationBand.forMeters(elevationM)
}

/**
 * Discrete elevation bands matched to high-altitude medicine literature
 * (West 2010 — *High Altitude Medicine and Physiology*; Bärtsch & Saltin
 * 2008). The bands map to the SpO2 floor adjustments documented in the
 * SOWA_RIGPA audit:
 *
 *  | Band         | Range (m)  | Acclimatised SpO2 floor |
 *  |--------------|-----------:|------------------------:|
 *  | SEA_LEVEL    |      ≤500  |                    95 % |
 *  | MODERATE     |  500–1500  |                    93 % |
 *  | HIGH         | 1500–2500  |                    90 % |
 *  | VERY_HIGH    | 2500–3500  |                    87 % |
 *  | EXTREME      |    >3500   |                    84 % |
 */
enum class ElevationBand(val displayName: String, val maxMeters: Double) {
    SEA_LEVEL("Sea level (≤500 m)", 500.0),
    MODERATE("Moderate altitude (500–1500 m)", 1500.0),
    HIGH("High altitude (1500–2500 m)", 2500.0),
    VERY_HIGH("Very high altitude (2500–3500 m)", 3500.0),
    EXTREME("Extreme altitude (>3500 m)", Double.POSITIVE_INFINITY);

    companion object {
        fun forMeters(m: Double): ElevationBand = when {
            m <= SEA_LEVEL.maxMeters -> SEA_LEVEL
            m <= MODERATE.maxMeters -> MODERATE
            m <= HIGH.maxMeters -> HIGH
            m <= VERY_HIGH.maxMeters -> VERY_HIGH
            else -> EXTREME
        }
    }
}

/**
 * Photoperiod state (#197 — SAAD screen, Lewy 2006 photoperiod hypothesis).
 * Computed from latitude + current date. The categorical mapping uses solar-
 * declination thresholds rather than calendar months so it works in both
 * hemispheres without an `if (southern) swap` branch.
 *
 *  - LONG_DAY: daylight ≥14 h (high-summer for mid-latitudes)
 *  - EQUINOX:  10 h ≤ daylight < 14 h
 *  - SHORT_DAY: daylight < 10 h
 *  - POLAR_DAY: daylight ≥24 h (above the polar circle in summer)
 *  - POLAR_NIGHT: daylight ≤0 h (above the polar circle in winter)
 */
enum class PhotoperiodState(val displayName: String) {
    LONG_DAY("Long day"),
    EQUINOX("Near-equinox"),
    SHORT_DAY("Short day"),
    POLAR_DAY("Polar day"),
    POLAR_NIGHT("Polar night");

    companion object {
        fun forDaylightHours(h: Double): PhotoperiodState = when {
            h >= 24.0 -> POLAR_DAY
            h <= 0.0 -> POLAR_NIGHT
            h >= 14.0 -> LONG_DAY
            h < 10.0 -> SHORT_DAY
            else -> EQUINOX
        }
    }
}
