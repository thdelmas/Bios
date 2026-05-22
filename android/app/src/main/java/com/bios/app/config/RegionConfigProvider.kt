package com.bios.app.config

import java.util.Locale

/**
 * Region registry. The actual configs live in the per-continent
 * `RegionConfigs*` files; this object aggregates them and implements the
 * locale-resolution fallback chain.
 *
 * **Fallback ordering** (applied by [forLocale]):
 *  1. **Exact country match** — `Locale("es", "MX") → MX`.
 *  2. **Continental fallback by country** — when the locale carries a
 *     country code we don't enumerate but recognise as belonging to a
 *     continent (e.g. `en_BZ` — Belize is LATAM), fall back to that
 *     continent's anchor: LATAM → MX, Africa → ZA, Asia → JP,
 *     Pacific → AU, Europe → EU. Geography beats language here because
 *     clinical norms track jurisdiction.
 *  3. **Language-only anchor** — for the bare-language case
 *     (`Locale("es", "")`) or when the country code is unrecognised:
 *     `es → MX`, `pt → BR`, `ar → SA`, `sw → TZ`, `mi → NZ`, etc.
 *     The anchor map is intentional, not alphabetic — picked by
 *     population / clinical-guideline relevance.
 *  4. **Ultimate fallback** — EU (GDPR-aligned, metric, conservative
 *     privacy posture). This is the silent-fallback that the
 *     Indigenous-Americas audit (§2.5) flagged as harmful when EU
 *     thresholds substituted for `es_MX` / `pt_BR` clinical norms; with
 *     the LATAM tables now defined, that substitution can no longer
 *     happen silently for those locales.
 */
object RegionConfigProvider {

    /**
     * Continental tables — declared as a list rather than spread inline
     * so per-continent files own their own contents and the registry
     * here stays a list of imports.
     */
    private val configs: Map<String, RegionConfig> = buildMap {
        putAll(RegionConfigsAmericas.configs)
        putAll(RegionConfigsEurope.configs)
        putAll(RegionConfigsAsia.configs)
        putAll(RegionConfigsAfrica.configs)
        putAll(RegionConfigsMiddleEast.configs)
        putAll(RegionConfigsPacific.configs)
    }

    /** EU as the ultimate fallback (see class docs for the full chain). */
    private val ultimateFallback: RegionConfig = RegionConfigsEurope.euFallback

    /**
     * Language → primary-country anchor map. Used by [forLocale] step 2
     * when the device locale provides a language but no country (or the
     * country has no specific config). Chosen by population / clinical-
     * guideline relevance, not alphabetic order.
     */
    private val languageAnchors: Map<String, String> = mapOf(
        "es" to "MX",   // Spanish anchor — MX is the largest Spanish-speaking population
        "pt" to "BR",   // Portuguese anchor — BR dwarfs PT in population
        "fr" to "FR",   // Resolved via continental fallback (FR isn't a defined config; will fall to EU)
        "ar" to "SA",   // Arabic anchor — GCC + Maghreb
        "sw" to "TZ",   // Swahili anchor
        "zh" to "CN",   // Chinese anchor
        "hi" to "IN",
        "bn" to "BD",
        "ur" to "PK",
        "id" to "ID",
        "ms" to "MY",
        "th" to "TH",
        "vi" to "VN",
        "tl" to "PH",
        "ja" to "JP",
        "ko" to "KR",
        "mi" to "NZ",   // Te reo Māori → Aotearoa NZ
        "haw" to "US",  // Hawaiian → US (closest legal jurisdiction)
        "fj" to "FJ",
        "to" to "TO",
        "sm" to "WS",
        "mg" to "MG",
        "am" to "ET",
        "ha" to "NG",
        "yo" to "NG",
        "ig" to "NG",
        "zu" to "ZA",
        "xh" to "ZA",
        "af" to "ZA",
        "fa" to "IR",
        "he" to "IL",
        "tr" to "TR",
        "ru" to "EU",   // Falls through to EU; CIS-specific config not yet defined
        "en" to "GB",   // Anchor English to GB; US still wins via country match
    )

    /**
     * Returns the config for the device's current locale via the full
     * fallback chain (see class docs).
     */
    fun forCurrentLocale(): RegionConfig = forLocale(Locale.getDefault())

    /**
     * Returns config for a specific country code (ISO 3166-1 alpha-2).
     * Falls back to the EU config when the country isn't recognised —
     * preferred entry point when the caller has a country code but no
     * full locale (e.g. a user-selected region in settings).
     */
    fun forRegion(regionCode: String): RegionConfig =
        configs[regionCode] ?: ultimateFallback

    /**
     * Returns config for an arbitrary [Locale] using the documented
     * fallback chain. The chain is deterministic — same locale always
     * resolves to the same config.
     */
    fun forLocale(locale: Locale): RegionConfig {
        // Step 1: exact country match.
        val country = locale.country
        if (country.isNotEmpty()) {
            configs[country]?.let { return it }
        }

        // Step 2: continental fallback by ISO country code. Tried
        // before the language anchor when a country IS specified —
        // geography beats language when both are present, because
        // clinical norms track jurisdiction (BZ in LATAM should use
        // LATAM defaults, not the English-language anchor GB).
        if (country.isNotEmpty()) {
            resolveByContinent(country)?.let { return it }
        }

        // Step 3: language-only anchor. Used when the locale specifies
        // only a language (no country), OR when the country code is
        // present but unrecognised by [resolveByContinent].
        val language = locale.language
        if (language.isNotEmpty()) {
            languageAnchors[language]?.let { anchor ->
                configs[anchor]?.let { return it }
            }
        }

        // Step 4: ultimate fallback.
        return ultimateFallback
    }

    /**
     * Maps an unknown ISO country code to its continental anchor. The
     * mapping is by membership in well-known regional groupings; codes
     * not listed fall through to the ultimate fallback.
     */
    private fun resolveByContinent(country: String): RegionConfig? = when (country) {
        // LATAM countries Bios doesn't enumerate yet (e.g. small
        // Caribbean states): anchor to MX (largest Spanish-speaking
        // population) rather than EU. This is the §2.5 audit fix.
        in LATAM_FALLBACK_COUNTRIES -> configs["MX"]
        in AFRICA_FALLBACK_COUNTRIES -> RegionConfigsAfrica.africaFallback
        in ASIA_FALLBACK_COUNTRIES -> RegionConfigsAsia.asiaFallback
        in PACIFIC_FALLBACK_COUNTRIES -> RegionConfigsPacific.pacificFallback
        in EUROPE_FALLBACK_COUNTRIES -> ultimateFallback
        else -> null
    }

    /** All registered country / territory codes. */
    fun supportedRegions(): Set<String> = configs.keys

    // -- Continental fallback sets (codes NOT enumerated in per-continent --
    // -- tables but still recognisable as belonging to that continent). --

    private val LATAM_FALLBACK_COUNTRIES = setOf(
        "BZ", "JM", "TT", "BB", "BS", "GY", "SR", "GF", "PR",
        "AW", "CW", "AI", "AG", "BM", "DM", "GD", "GP",
        "KY", "KN", "LC", "MQ", "MS", "TC", "VC", "VG", "VI",
    )

    private val AFRICA_FALLBACK_COUNTRIES = setOf(
        // Sub-Saharan + Indian Ocean states not enumerated above
        "BF", "BI", "BJ", "CF", "CG", "DJ", "EH", "ER", "GA",
        "GM", "GN", "GQ", "GW", "KM", "LR", "LS", "ML", "MR",
        "NE", "RE", "SC", "SD", "SL", "SO", "SS", "ST", "SZ",
        "TD", "TG", "YT",
    )

    private val ASIA_FALLBACK_COUNTRIES = setOf(
        "AF", "AM", "AZ", "BH", "CY", "GE", "KG", "KP",
        "KW", "MO", "MV", "OM", "PS", "TJ", "TL", "TM",
    )

    private val PACIFIC_FALLBACK_COUNTRIES = setOf(
        "AS", "GU", "MP", "PN", "TK", "WF",
    )

    private val EUROPE_FALLBACK_COUNTRIES = setOf(
        "AD", "AL", "AT", "BA", "BE", "BG", "BY", "CH", "CZ",
        "DE", "DK", "EE", "ES", "FI", "FO", "FR", "GG", "GI",
        "GR", "HR", "HU", "IE", "IM", "IS", "IT", "JE", "LI",
        "LT", "LU", "LV", "MC", "MD", "ME", "MK", "MT", "NL",
        "NO", "PL", "PT", "RO", "RS", "RU", "SE", "SI", "SJ",
        "SK", "SM", "UA", "VA", "XK",
    )
}
