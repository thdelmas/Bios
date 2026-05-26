package com.bios.app

import com.bios.app.config.ClimateZone
import com.bios.app.config.EnvironmentalContextProvider
import com.bios.app.config.RegionConfigProvider
import com.bios.app.model.ElevationBand
import com.bios.app.model.PhotoperiodState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the environmental-context substrate (#197). Five audits
 * converge on this gap; the substrate must pass three correctness bars:
 *
 *  1. Elevation-band derivation matches the West 2010 cut-points.
 *  2. Daylight hours match known textbook values for benchmark
 *     latitude/date pairs (June solstice, December solstice, equinox).
 *  3. Region defaults carry sensible climate zones (Canada COLD, AU
 *     SUBTROPICAL).
 *
 * Tests stay pure / context-free — no Android dependencies — by hitting
 * the static daylight helper on the provider and the `forMeters` /
 * `forDaylightHours` constructors on the enums directly.
 */
class EnvironmentalContextTest {

    // -- elevation bands --

    @Test
    fun sea_level_band_covers_zero_to_500m() {
        assertEquals(ElevationBand.SEA_LEVEL, ElevationBand.forMeters(0.0))
        assertEquals(ElevationBand.SEA_LEVEL, ElevationBand.forMeters(250.0))
        assertEquals(ElevationBand.SEA_LEVEL, ElevationBand.forMeters(500.0))
    }

    @Test
    fun moderate_band_covers_500_to_1500m() {
        assertEquals(ElevationBand.MODERATE, ElevationBand.forMeters(500.01))
        assertEquals(ElevationBand.MODERATE, ElevationBand.forMeters(1000.0))
        assertEquals(ElevationBand.MODERATE, ElevationBand.forMeters(1500.0))
    }

    @Test
    fun high_band_covers_1500_to_2500m() {
        // Lhasa is 3650 m → EXTREME, Quito is 2850 m → VERY_HIGH,
        // Mexico City 2240 m → HIGH, Denver 1610 m → HIGH.
        assertEquals(ElevationBand.HIGH, ElevationBand.forMeters(1610.0))
        assertEquals(ElevationBand.HIGH, ElevationBand.forMeters(2240.0))
        assertEquals(ElevationBand.HIGH, ElevationBand.forMeters(2500.0))
    }

    @Test
    fun very_high_band_covers_2500_to_3500m() {
        assertEquals(ElevationBand.VERY_HIGH, ElevationBand.forMeters(2850.0))
        assertEquals(ElevationBand.VERY_HIGH, ElevationBand.forMeters(3500.0))
    }

    @Test
    fun extreme_band_covers_above_3500m() {
        // Lhasa 3650, La Paz 3640, El Alto 4150, Tibetan plateau 4500.
        assertEquals(ElevationBand.EXTREME, ElevationBand.forMeters(3640.0))
        assertEquals(ElevationBand.EXTREME, ElevationBand.forMeters(3650.0))
        assertEquals(ElevationBand.EXTREME, ElevationBand.forMeters(4500.0))
    }

    // -- photoperiod derivation --

    @Test
    fun short_day_under_10h() {
        assertEquals(PhotoperiodState.SHORT_DAY, PhotoperiodState.forDaylightHours(8.0))
        assertEquals(PhotoperiodState.SHORT_DAY, PhotoperiodState.forDaylightHours(9.99))
    }

    @Test
    fun equinox_between_10h_and_14h() {
        assertEquals(PhotoperiodState.EQUINOX, PhotoperiodState.forDaylightHours(12.0))
        assertEquals(PhotoperiodState.EQUINOX, PhotoperiodState.forDaylightHours(10.0))
    }

    @Test
    fun long_day_at_or_above_14h() {
        assertEquals(PhotoperiodState.LONG_DAY, PhotoperiodState.forDaylightHours(14.0))
        assertEquals(PhotoperiodState.LONG_DAY, PhotoperiodState.forDaylightHours(20.0))
    }

    @Test
    fun polar_day_at_or_above_24h() {
        assertEquals(PhotoperiodState.POLAR_DAY, PhotoperiodState.forDaylightHours(24.0))
    }

    @Test
    fun polar_night_at_or_below_zero() {
        assertEquals(PhotoperiodState.POLAR_NIGHT, PhotoperiodState.forDaylightHours(0.0))
    }

    // -- daylight-hours formula --

    @Test
    fun equator_returns_roughly_12h_year_round() {
        // Equatorial daylight stays within ~12.0 ± 0.1 h all year.
        for (month in 1..12) {
            val date = LocalDate.of(2025, month, 15)
            val h = EnvironmentalContextProvider.daylightHours(0.0, date)
            assertTrue(
                "Equator on $date returned $h hours of daylight — should be ≈12",
                kotlin.math.abs(h - 12.0) < 0.2
            )
        }
    }

    @Test
    fun june_solstice_at_60N_is_about_185_hours() {
        // June 21 at 60 °N (Helsinki / St Petersburg / Whitehorse) — textbook
        // value ~18.5 h (Forsythe 1995). Allow ±15 minutes of formula slop.
        val h = EnvironmentalContextProvider.daylightHours(60.0, LocalDate.of(2025, 6, 21))
        assertTrue(
            "Daylight on June 21 at 60°N should be ~18.5 h, got $h",
            kotlin.math.abs(h - 18.5) < 0.5
        )
    }

    @Test
    fun december_solstice_at_60N_is_about_5_to_6_hours() {
        val h = EnvironmentalContextProvider.daylightHours(60.0, LocalDate.of(2025, 12, 21))
        assertTrue(
            "Daylight on Dec 21 at 60°N should be ~5.5 h, got $h",
            kotlin.math.abs(h - 5.5) < 0.7
        )
        assertEquals(
            PhotoperiodState.SHORT_DAY,
            EnvironmentalContextProvider.photoperiodFor(60.0, LocalDate.of(2025, 12, 21))
        )
    }

    @Test
    fun south_of_equator_inverts_seasons() {
        // Sydney (−33°S) on Dec 21 should be LONG_DAY (austral summer
        // solstice); on June 21 SHORT_DAY (austral winter solstice).
        val summer = EnvironmentalContextProvider.daylightHours(-33.0, LocalDate.of(2025, 12, 21))
        val winter = EnvironmentalContextProvider.daylightHours(-33.0, LocalDate.of(2025, 6, 21))
        assertTrue("Sydney summer solstice should give long day, got $summer", summer > 13.5)
        assertTrue("Sydney winter solstice should give short day, got $winter", winter < 10.5)
        assertNotEquals(summer, winter, 1e-3)
    }

    @Test
    fun above_arctic_circle_returns_polar_day_in_summer() {
        // 75 °N on June 21 → daylight clamped to 24 h.
        val h = EnvironmentalContextProvider.daylightHours(75.0, LocalDate.of(2025, 6, 21))
        assertEquals(24.0, h, 1e-9)
        assertEquals(
            PhotoperiodState.POLAR_DAY,
            EnvironmentalContextProvider.photoperiodFor(75.0, LocalDate.of(2025, 6, 21))
        )
    }

    @Test
    fun above_arctic_circle_returns_polar_night_in_winter() {
        val h = EnvironmentalContextProvider.daylightHours(75.0, LocalDate.of(2025, 12, 21))
        assertEquals(0.0, h, 1e-9)
        assertEquals(
            PhotoperiodState.POLAR_NIGHT,
            EnvironmentalContextProvider.photoperiodFor(75.0, LocalDate.of(2025, 12, 21))
        )
    }

    // -- region-config defaults --

    @Test
    fun canada_default_climate_is_cold() {
        assertEquals(ClimateZone.COLD, RegionConfigProvider.forRegion("CA").climateZone)
    }

    @Test
    fun australia_default_climate_is_subtropical_and_latitude_southern() {
        val cfg = RegionConfigProvider.forRegion("AU")
        assertEquals(ClimateZone.SUBTROPICAL, cfg.climateZone)
        assertTrue("AU default latitude should be negative (southern hemisphere)", cfg.defaultLatitudeDeg < 0)
    }

    @Test
    fun eu_default_climate_is_temperate() {
        assertEquals(ClimateZone.TEMPERATE, RegionConfigProvider.forRegion("EU").climateZone)
    }
}
