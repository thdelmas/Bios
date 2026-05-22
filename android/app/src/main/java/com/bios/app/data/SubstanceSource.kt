package com.bios.app.data

/**
 * Substance vocabulary sources for [com.bios.app.model.MedicationAnnotation].
 * Issue #194 — converged ask from eleven traditional / functional medicine
 * audits (Ayurveda, TCM, Kampo, Korean, Siddha, Unani, Sowa Rigpa, African
 * Traditional, Indigenous Americas, Modern Non-Allopathic, Other Asian
 * Systems).
 *
 * **Why an enum.** The audits identified the same gap from eleven angles:
 * traditional and folk substances need a *vocabulary layer* so they are
 * **recordable without endorsement**. Bios's existing free-text medication
 * surface accepts any string but cannot distinguish "metoprolol" (RxNorm
 * 6918) from "Ashwagandha" (a botanical / Ayurvedic dravya) from "六味地黄丸"
 * (a TCM formula). The enum lets owners — when they want to — tag the
 * vocabulary their substance is drawn from so downstream surfaces (Beers
 * filtering, CredibleMeds QT context, FHIR export, clinician share) can
 * reason about it.
 *
 * **Manifesto-aligned.**
 *  - *Recording is not endorsement.* Bios never validates whether a
 *    substance "works". The enum is descriptive, not evaluative.
 *  - *Default is free text.* [LOCAL_FREE_TEXT] is the implicit default
 *    when [com.bios.app.model.MedicationAnnotation.substanceSource] is
 *    `null`; the structured fields are opt-in.
 *  - *No external lookups.* No code in Bios queries RxNorm, ATC, AYUSH,
 *    or any remote vocabulary service. Codes are owner-entered strings.
 *  - *Owner's preferred script.* Traditional names are recorded verbatim
 *    in Devanagari, Hanzi, Hangul, Tibetan, Arabic, Latin transliteration,
 *    or any other script — Bios does not transliterate or normalise.
 *
 * Persisted as the enum's [name] string so the on-disk format is stable
 * across reorderings; never persisted by ordinal.
 */
enum class SubstanceSource {
    /**
     * RxNorm CUI (US National Library of Medicine). The canonical
     * allopathic-pharmacy vocabulary in the US. Used by CredibleMeds
     * (QT-prolonging drug registry) and the AGS Beers Criteria pickers.
     */
    RXNORM,

    /**
     * WHO ATC (Anatomical Therapeutic Chemical) classification. The
     * international allopathic pharmacy vocabulary. Often coded at class
     * level (e.g. "C07AB02" for metoprolol).
     */
    ATC,

    /**
     * WHO ICD-11 Chapter 26 — Traditional Medicine Module 1 (2019). Covers
     * Ayurveda, Siddha, Unani, traditional Korean, TCM, and other
     * traditional-medicine substance codes recognised by WHO. The audit's
     * primary use of this slot is for AYUSH (Ayurveda / Yoga / Unani /
     * Siddha / Homoeopathy) substance codes; widened to "WHO ICD-11
     * traditional medicine" so Sowa Rigpa and other traditions on the WHO
     * track land here too.
     */
    AYUSH_ICD11,

    /**
     * Chinese State Administration of Traditional Chinese Medicine (国家
     * 中医药管理局) formula codes for compound Chinese medicines (中药). For
     * single-herb identification, the ICD-11 chapter 26 code is preferred
     * via [AYUSH_ICD11]; this slot is the canonical home for *formula*
     * codes (e.g. 六味地黄丸, 桂枝湯).
     */
    TCM_FORMULA_CODE,

    /**
     * Japanese Kampo (漢方) National Health Insurance code. The Japanese
     * MHLW lists ~148 Kampo formulae approved for NHI reimbursement; each
     * has a numeric code (Tsumura's "TJ-" series, Kotaro's "K-" series,
     * Kracie's "EK-" series). Persist the canonical NHI number; the
     * vendor prefix is captured by [com.bios.app.model.MedicationAnnotation.traditionalName].
     */
    TSUMURA_NHI_148,

    /**
     * Korean traditional medicine (한약) substance code. The Ministry of
     * Food and Drug Safety (식품의약품안전처) publishes the Korean
     * Pharmacopoeia (대한민국약전) and the Korean Herbal Pharmacopoeia
     * (대한민국약전외한약(생약)규격집) under which traditional Korean
     * substances are coded.
     */
    KOREAN_AYUSH,

    /**
     * WHO botanical monograph (WHO Monographs on Selected Medicinal
     * Plants, vols 1–5, 1999–2010, plus ongoing series). Useful for
     * African Traditional (e.g. Sutherlandia frutescens, Hypoxis
     * hemerocallidea), Indigenous Americas (curandero / Andean herbs),
     * Vietnamese Thuốc Nam, Indonesian Jamu, Thai phaet phaen boran, and
     * Modern Non-Allopathic botanical practice. Latin binomial in
     * [com.bios.app.model.MedicationAnnotation.traditionalName] is
     * encouraged but not required.
     */
    WHO_BOTANICAL_MONOGRAPH,

    /**
     * Default — owner-entered free text. The substance is recorded as
     * [com.bios.app.model.MedicationAnnotation.name] verbatim; no
     * vocabulary tag, no code, no validation. This is the path every
     * pre-#194 medication landed through, and remains the default for
     * owners who don't care to classify.
     */
    LOCAL_FREE_TEXT;

    /** Stable string used for persistence. */
    fun toPersistenceString(): String = name

    companion object {
        /**
         * Lenient parse from the persisted [name] string. Returns
         * [LOCAL_FREE_TEXT] for `null` or unknown values so a future
         * removal-of-enum-value can't crash an old row at read time.
         */
        fun fromPersistenceString(value: String?): SubstanceSource {
            if (value.isNullOrBlank()) return LOCAL_FREE_TEXT
            return entries.firstOrNull { it.name == value } ?: LOCAL_FREE_TEXT
        }
    }
}
