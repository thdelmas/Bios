package com.bios.app.config

import android.content.Context
import com.bios.app.model.EnvironmentalContext
import com.bios.app.model.PhotoperiodState
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Resolves the current [EnvironmentalContext] for the owner (#197). Three
 * elevation sources, none requiring permission:
 *
 *  1. Owner-set value (Settings → Environmental context). Always wins.
 *  2. Travel-elevation override (owner says "I am in La Paz today"). Wins
 *     when set; cleared when the owner returns home.
 *  3. Region default from [RegionConfig.defaultElevationM] when nothing has
 *     been set. The intentionally lossy fallback so a Dharamsala or Quito
 *     install still gets non-zero elevation without prompting.
 *
 * `Location` permission is **never** required. A future location-aware path
 * may opt in, but the manifesto guard is that nothing leaves the device and
 * nothing is auto-derived from sensors the owner hasn't agreed to feed.
 *
 * Climate zone follows the same precedence — owner override beats region
 * default. Daylight hours are computed analytically from the owner's
 * latitude using the standard solar-declination formula (no external APIs).
 *
 * Singleton lifecycle: the prefs file is small and the context object is
 * trivially constructible on every call — pinning a long-lived cache would
 * just leak the Application context.
 */
class EnvironmentalContextProvider(
    private val appContext: Context,
    private val regionConfig: RegionConfig = RegionConfigProvider.forCurrentLocale(),
    private val clock: () -> LocalDate = { LocalDate.now(ZoneId.systemDefault()) },
) {

    private val prefs = appContext.applicationContext.getSharedPreferences(
        PREFS_FILE, Context.MODE_PRIVATE,
    )

    /** Resolve the current context. Cheap; safe to call from a UI composable. */
    fun current(): EnvironmentalContext {
        val elevation = elevationM()
        val climate = climateZone()
        val latitude = latitudeDeg()
        val date = clock()
        val daylight = daylightHours(latitude, date)
        return EnvironmentalContext(
            elevationM = elevation,
            climateZone = climate,
            photoperiod = PhotoperiodState.forDaylightHours(daylight),
            daylightHours = daylight,
        )
    }

    // -- Owner-set values --

    /** Home elevation (metres). Null when the owner has not set a value. */
    fun homeElevationM(): Double? =
        if (prefs.contains(KEY_HOME_ELEVATION_M)) {
            prefs.getFloat(KEY_HOME_ELEVATION_M, 0f).toDouble()
        } else {
            null
        }

    fun setHomeElevationM(value: Double?) {
        prefs.edit().apply {
            if (value == null) remove(KEY_HOME_ELEVATION_M) else putFloat(KEY_HOME_ELEVATION_M, value.toFloat())
            apply()
        }
    }

    /** Travel override (metres). Null when the owner is at home elevation. */
    fun travelElevationM(): Double? =
        if (prefs.contains(KEY_TRAVEL_ELEVATION_M)) {
            prefs.getFloat(KEY_TRAVEL_ELEVATION_M, 0f).toDouble()
        } else {
            null
        }

    fun setTravelElevationM(value: Double?) {
        prefs.edit().apply {
            if (value == null) remove(KEY_TRAVEL_ELEVATION_M) else putFloat(KEY_TRAVEL_ELEVATION_M, value.toFloat())
            apply()
        }
    }

    /** Owner-set climate zone. Null when relying on region default. */
    fun ownerClimateZone(): ClimateZone? =
        prefs.getString(KEY_CLIMATE_ZONE, null)
            ?.let { runCatching { ClimateZone.valueOf(it) }.getOrNull() }

    fun setOwnerClimateZone(zone: ClimateZone?) {
        prefs.edit().apply {
            if (zone == null) remove(KEY_CLIMATE_ZONE) else putString(KEY_CLIMATE_ZONE, zone.name)
            apply()
        }
    }

    /** Owner-set latitude (degrees north, signed). Null = use region default. */
    fun ownerLatitudeDeg(): Double? =
        if (prefs.contains(KEY_LATITUDE_DEG)) {
            prefs.getFloat(KEY_LATITUDE_DEG, 0f).toDouble()
        } else {
            null
        }

    fun setOwnerLatitudeDeg(value: Double?) {
        prefs.edit().apply {
            if (value == null) remove(KEY_LATITUDE_DEG) else putFloat(KEY_LATITUDE_DEG, value.toFloat())
            apply()
        }
    }

    // -- Resolution helpers --

    /** Travel value > home value > region default. */
    fun elevationM(): Double =
        travelElevationM() ?: homeElevationM() ?: regionConfig.defaultElevationM

    fun climateZone(): ClimateZone =
        ownerClimateZone() ?: regionConfig.climateZone

    fun latitudeDeg(): Double =
        ownerLatitudeDeg() ?: regionConfig.defaultLatitudeDeg

    companion object {
        const val PREFS_FILE = "bios_environmental_context"
        const val KEY_HOME_ELEVATION_M = "home_elevation_m"
        const val KEY_TRAVEL_ELEVATION_M = "travel_elevation_m"
        const val KEY_CLIMATE_ZONE = "climate_zone"
        const val KEY_LATITUDE_DEG = "latitude_deg"

        /**
         * Hours of daylight at [latitudeDeg] on [date]. Standard solar-
         * declination formula:
         *
         *   δ = 23.44° × sin(360/365 × (N − 81))                 — declination
         *   cos(H) = −tan(φ) × tan(δ)                            — sunrise hour-angle
         *   daylight_hours = 2H / 15                             — H in degrees
         *
         * Above the polar circle the `cos(H)` argument exits [-1, 1]; we
         * clamp to 24 h (polar day) or 0 h (polar night) per the photoperiod
         * literature. Accurate to ~10 min, which is far below the resolution
         * the SAAD pattern reads.
         *
         * Reference: Forsythe et al. (1995), "A model comparison for daylength
         * as a function of latitude and day of year." Ecological Modelling.
         */
        fun daylightHours(latitudeDeg: Double, date: LocalDate): Double {
            val n = date.dayOfYear
            val phiRad = latitudeDeg * PI / 180.0
            val declRad = 23.44 * PI / 180.0 *
                sin(2.0 * PI / 365.0 * (n - 81))
            val cosHourAngle = -tan(phiRad) * tan(declRad)
            if (cosHourAngle >= 1.0) return 0.0          // polar night
            if (cosHourAngle <= -1.0) return 24.0        // polar day
            val hourAngleDeg = acos(cosHourAngle) * 180.0 / PI
            return 2.0 * hourAngleDeg / 15.0
        }

        /**
         * Convenience for tests + ad-hoc callers that don't need a
         * preferences-bound provider — pure function of the inputs.
         */
        fun photoperiodFor(latitudeDeg: Double, date: LocalDate): PhotoperiodState =
            PhotoperiodState.forDaylightHours(daylightHours(latitudeDeg, date))
    }
}
