package com.bios.app

import com.bios.app.config.RegionConfigProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Guards the region coverage added to address the Indigenous Americas,
 * African Traditional, Oceanic / Arctic, Other Asian, and Primary Care
 * audits (issue #195):
 *
 *  - Every newly-added region returns a verified emergency number.
 *  - Indigenous-data-sovereignty flagging surfaces on the regions that
 *    sit under named frameworks (NZ, AU, NACCHO; Canadian territories;
 *    Pacific Island states).
 *  - The locale-fallback chain resolves the locales the
 *    INDIGENOUS_AMERICAS_POV.md §2.5 audit named ("es_MX", "pt_BR")
 *    to their own configs rather than silently substituting the EU
 *    ESC/ESH thresholds.
 */
class RegionConfigCoverageTest {

    // -- Emergency numbers (canonical EMS / ambulance lines) --

    @Test
    fun latam_regions_have_verified_emergency_numbers() {
        assertEquals("911", RegionConfigProvider.forRegion("MX").emergencyNumber)
        assertEquals("107", RegionConfigProvider.forRegion("AR").emergencyNumber)
        assertEquals("192", RegionConfigProvider.forRegion("BR").emergencyNumber)
        assertEquals("131", RegionConfigProvider.forRegion("CL").emergencyNumber)
        assertEquals("123", RegionConfigProvider.forRegion("CO").emergencyNumber)
        assertEquals("106", RegionConfigProvider.forRegion("PE").emergencyNumber)
        assertEquals("171", RegionConfigProvider.forRegion("VE").emergencyNumber)
        assertEquals("911", RegionConfigProvider.forRegion("EC").emergencyNumber)
        assertEquals("118", RegionConfigProvider.forRegion("BO").emergencyNumber)
        assertEquals("911", RegionConfigProvider.forRegion("UY").emergencyNumber)
        assertEquals("911", RegionConfigProvider.forRegion("PY").emergencyNumber)
        assertEquals("911", RegionConfigProvider.forRegion("CR").emergencyNumber)
        assertEquals("911", RegionConfigProvider.forRegion("PA").emergencyNumber)
        assertEquals("911", RegionConfigProvider.forRegion("DO").emergencyNumber)
        assertEquals("185", RegionConfigProvider.forRegion("CU").emergencyNumber)
    }

    @Test
    fun africa_regions_have_verified_emergency_numbers() {
        assertEquals("112", RegionConfigProvider.forRegion("NG").emergencyNumber)
        assertEquals("999", RegionConfigProvider.forRegion("KE").emergencyNumber)
        assertEquals("10177", RegionConfigProvider.forRegion("ZA").emergencyNumber)
        assertEquals("193", RegionConfigProvider.forRegion("GH").emergencyNumber)
        assertEquals("907", RegionConfigProvider.forRegion("ET").emergencyNumber)
        assertEquals("114", RegionConfigProvider.forRegion("TZ").emergencyNumber)
        assertEquals("15", RegionConfigProvider.forRegion("SN").emergencyNumber)
        assertEquals("14", RegionConfigProvider.forRegion("DZ").emergencyNumber)
        assertEquals("15", RegionConfigProvider.forRegion("MA").emergencyNumber)
        assertEquals("123", RegionConfigProvider.forRegion("EG").emergencyNumber)
    }

    @Test
    fun middle_east_regions_have_verified_emergency_numbers() {
        assertEquals("101", RegionConfigProvider.forRegion("IL").emergencyNumber)
        assertEquals("112", RegionConfigProvider.forRegion("TR").emergencyNumber)
        assertEquals("998", RegionConfigProvider.forRegion("AE").emergencyNumber)
        assertEquals("997", RegionConfigProvider.forRegion("SA").emergencyNumber)
        assertEquals("999", RegionConfigProvider.forRegion("QA").emergencyNumber)
        assertEquals("115", RegionConfigProvider.forRegion("IR").emergencyNumber)
        assertEquals("140", RegionConfigProvider.forRegion("LB").emergencyNumber)
    }

    @Test
    fun asia_regions_have_verified_emergency_numbers() {
        assertEquals("120", RegionConfigProvider.forRegion("CN").emergencyNumber)
        assertEquals("119", RegionConfigProvider.forRegion("KR").emergencyNumber)
        assertEquals("108", RegionConfigProvider.forRegion("IN").emergencyNumber)
        assertEquals("115", RegionConfigProvider.forRegion("VN").emergencyNumber)
        assertEquals("1669", RegionConfigProvider.forRegion("TH").emergencyNumber)
        assertEquals("999", RegionConfigProvider.forRegion("MY").emergencyNumber)
        assertEquals("995", RegionConfigProvider.forRegion("SG").emergencyNumber)
        assertEquals("119", RegionConfigProvider.forRegion("ID").emergencyNumber)
        assertEquals("911", RegionConfigProvider.forRegion("PH").emergencyNumber)
        assertEquals("1990", RegionConfigProvider.forRegion("LK").emergencyNumber)
    }

    @Test
    fun pacific_regions_have_verified_emergency_numbers() {
        assertEquals("111", RegionConfigProvider.forRegion("NZ").emergencyNumber)
        assertEquals("000", RegionConfigProvider.forRegion("AU").emergencyNumber)
        assertEquals("911", RegionConfigProvider.forRegion("FJ").emergencyNumber)
        assertEquals("111", RegionConfigProvider.forRegion("PG").emergencyNumber)
        assertEquals("999", RegionConfigProvider.forRegion("SB").emergencyNumber)
        assertEquals("112", RegionConfigProvider.forRegion("VU").emergencyNumber)
        assertEquals("994", RegionConfigProvider.forRegion("KI").emergencyNumber)
    }

    @Test
    fun anchor_regions_still_have_their_existing_emergency_numbers() {
        assertEquals("911", RegionConfigProvider.forRegion("US").emergencyNumber)
        assertEquals("911", RegionConfigProvider.forRegion("CA").emergencyNumber)
        assertEquals("999", RegionConfigProvider.forRegion("GB").emergencyNumber)
        assertEquals("112", RegionConfigProvider.forRegion("EU").emergencyNumber)
        assertEquals("119", RegionConfigProvider.forRegion("JP").emergencyNumber)
    }

    // -- Indigenous data sovereignty flagging --

    @Test
    fun nz_is_flagged_for_te_mana_raraunga() {
        assertTrue(RegionConfigProvider.forRegion("NZ").indigenousFlagged)
    }

    @Test
    fun au_is_flagged_for_naccho() {
        assertTrue(RegionConfigProvider.forRegion("AU").indigenousFlagged)
    }

    @Test
    fun canadian_territories_are_flagged_for_ocap() {
        assertTrue(RegionConfigProvider.forRegion("CA-NU").indigenousFlagged)
        assertTrue(RegionConfigProvider.forRegion("CA-NT").indigenousFlagged)
        assertTrue(RegionConfigProvider.forRegion("CA-YT").indigenousFlagged)
    }

    @Test
    fun pacific_island_states_are_flagged_for_indigenous_sovereignty() {
        assertTrue(RegionConfigProvider.forRegion("FJ").indigenousFlagged)
        assertTrue(RegionConfigProvider.forRegion("PG").indigenousFlagged)
        assertTrue(RegionConfigProvider.forRegion("TO").indigenousFlagged)
        assertTrue(RegionConfigProvider.forRegion("WS").indigenousFlagged)
        assertTrue(RegionConfigProvider.forRegion("MH").indigenousFlagged)
    }

    @Test
    fun federal_canada_is_not_flagged_by_default() {
        // Federal CA is NOT a tribally-governed region — only the
        // territories carry the OCAP flag.
        assertFalse(RegionConfigProvider.forRegion("CA").indigenousFlagged)
    }

    @Test
    fun latam_regions_default_to_no_indigenous_flag() {
        // MX / BR are not flagged by default — pueblos originarios / povos
        // indígenas data-sovereignty frameworks are sub-national, not
        // applied at the country level. (Per-state extension is future
        // work; the audit asks for explicit flagging, not blanket flagging.)
        assertFalse(RegionConfigProvider.forRegion("MX").indigenousFlagged)
        assertFalse(RegionConfigProvider.forRegion("BR").indigenousFlagged)
    }

    // -- Locale fallback chain --

    @Test
    fun es_MX_resolves_to_MX_not_EU() {
        val config = RegionConfigProvider.forLocale(Locale("es", "MX"))
        assertEquals("MX", config.regionCode)
    }

    @Test
    fun pt_BR_resolves_to_BR_not_EU() {
        val config = RegionConfigProvider.forLocale(Locale("pt", "BR"))
        assertEquals("BR", config.regionCode)
    }

    @Test
    fun sw_TZ_resolves_to_TZ() {
        val config = RegionConfigProvider.forLocale(Locale("sw", "TZ"))
        assertEquals("TZ", config.regionCode)
    }

    @Test
    fun mi_NZ_resolves_to_NZ_with_indigenous_flag() {
        val config = RegionConfigProvider.forLocale(Locale("mi", "NZ"))
        assertEquals("NZ", config.regionCode)
        assertTrue(config.indigenousFlagged)
    }

    @Test
    fun bare_spanish_locale_falls_back_to_language_anchor() {
        // Locale("es") with no country should land on MX (the language
        // anchor) rather than EU.
        val config = RegionConfigProvider.forLocale(Locale("es"))
        assertEquals("MX", config.regionCode)
    }

    @Test
    fun bare_portuguese_locale_falls_back_to_language_anchor() {
        val config = RegionConfigProvider.forLocale(Locale("pt"))
        assertEquals("BR", config.regionCode)
    }

    @Test
    fun bare_swahili_locale_falls_back_to_language_anchor() {
        val config = RegionConfigProvider.forLocale(Locale("sw"))
        assertEquals("TZ", config.regionCode)
    }

    @Test
    fun unknown_latam_country_falls_back_to_MX_not_EU() {
        // BZ (Belize) is in LATAM but Bios doesn't enumerate it. The
        // continental fallback should land on MX, not EU.
        val config = RegionConfigProvider.forLocale(Locale("en", "BZ"))
        assertEquals("MX", config.regionCode)
    }

    @Test
    fun unknown_african_country_falls_back_to_ZA() {
        val config = RegionConfigProvider.forLocale(Locale("en", "BW"))
        // BW is enumerated, so it resolves to BW directly.
        assertEquals("BW", config.regionCode)
        // SL (Sierra Leone) is not enumerated — should fall back to ZA.
        val fallback = RegionConfigProvider.forLocale(Locale("en", "SL"))
        assertEquals("ZA", fallback.regionCode)
    }

    @Test
    fun unknown_asian_country_falls_back_to_JP_continental() {
        // AF (Afghanistan) is not enumerated. With "geography beats
        // language" ordering, the continental fallback (AF in
        // ASIA_FALLBACK_COUNTRIES → JP) wins over the language anchor
        // "fa" → IR. This is the new contract added by issue #195.
        val config = RegionConfigProvider.forLocale(Locale("fa", "AF"))
        assertEquals("JP", config.regionCode)
    }

    @Test
    fun bare_persian_language_anchors_to_IR() {
        // No country → language anchor fires (step 3 of the chain).
        val config = RegionConfigProvider.forLocale(Locale("fa"))
        assertEquals("IR", config.regionCode)
    }

    @Test
    fun unknown_asian_country_with_unknown_language_falls_back_to_JP() {
        // Use a country we don't enumerate AND a language with no anchor.
        val config = RegionConfigProvider.forLocale(Locale("en", "BH"))
        assertEquals("JP", config.regionCode)
    }

    @Test
    fun unknown_pacific_country_falls_back_to_AU() {
        // GU (Guam) is not enumerated. Continental fallback (GU in
        // PACIFIC_FALLBACK_COUNTRIES → AU) wins over the English
        // language anchor.
        val config = RegionConfigProvider.forLocale(Locale("en", "GU"))
        assertEquals("AU", config.regionCode)
    }

    @Test
    fun unknown_european_country_falls_back_to_EU() {
        // FR is not enumerated as a specific config; FR is in
        // EUROPE_FALLBACK_COUNTRIES → EU.
        val config = RegionConfigProvider.forLocale(Locale("fr", "FR"))
        assertEquals("EU", config.regionCode)
    }

    @Test
    fun completely_unknown_country_and_language_falls_back_to_EU() {
        // ZZ is the ISO reserved "unknown" code. "qq" is a private-use
        // language not in any anchor map.
        val config = RegionConfigProvider.forLocale(Locale("qq", "ZZ"))
        assertEquals("EU", config.regionCode)
    }

    @Test
    fun forCurrentLocale_returns_a_valid_config() {
        // Doesn't crash, returns something — exact value depends on the
        // test JVM's default locale.
        assertNotNull(RegionConfigProvider.forCurrentLocale())
    }

    // -- Coverage sanity --

    @Test
    fun coverage_includes_at_least_eighty_distinct_locales() {
        // We declared ~100 regions across the continental files. Lower
        // bound 80 leaves room for refactoring without churning this test.
        assertTrue(
            "Coverage too low: ${RegionConfigProvider.supportedRegions().size}",
            RegionConfigProvider.supportedRegions().size >= 80,
        )
    }
}
