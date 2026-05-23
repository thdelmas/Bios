package com.bios.contracts

/**
 * Unit of measure for a [MetricType]. Each enum value pairs the canonical
 * Bios-internal token with the symbol shown to owners and used in exports.
 *
 * Adding a new unit is non-breaking; renaming or removing one is breaking
 * for every consumer that hard-codes the symbol (and for FhirExporter's
 * exhaustive UCUM `when`). See docs/CONSUMER_API.md compatibility rules.
 */
enum class MetricUnit(val symbol: String) {
    BPM("bpm"),
    MILLISECONDS("ms"),
    MMHG("mmHg"),
    PERCENT("%"),
    BREATHS_PER_MIN("breaths/min"),
    CELSIUS("°C"),
    DELTA_CELSIUS("Δ°C"),
    CATEGORY(""),
    SECONDS("s"),
    COUNT(""),
    KCAL("kcal"),
    KILOGRAMS("kg"),
    MG_PER_DL("mg/dL"),
    MG_PER_L("mg/L"),
    NG_PER_ML("ng/mL"),
    NG_PER_DL("ng/dL"),
    UG_PER_DL("µg/dL"),
    PG_PER_ML("pg/mL"),
    MIU_PER_L("mIU/L"),
    MICRO_IU_PER_ML("µIU/mL"),
    G_PER_DL("g/dL"),
    GIGA_PER_L("10⁹/L"),
    TERA_PER_L("10¹²/L"),
    SCORE(""),
    EVENT(""),
    LUX("lx"),
    YEARS("yr"),
    MS_SQUARED("ms²"),
    ML_PER_KG_MIN("mL/kg/min"),
    UG_PER_M3("µg/m³"),
    PPM("ppm"),
    PPB("ppb"),
    LITERS_PER_MIN("L/min"),
    LITERS("L"),
    MILLIGRAMS("mg"),
    GRAMS("g"),
    /** Enzyme activity per litre — ALT, AST, GGT, ALP, amylase, lipase. */
    U_PER_L("U/L"),
    /** Mass per litre — high-sensitivity troponin clinical convention. */
    NG_PER_L("ng/L"),
    /** Cell count per microlitre — absolute neutrophil count clinical convention. */
    PER_MICRO_L("/µL"),
    /** eGFR normalized to body surface area — KDIGO 2024 standard. */
    ML_PER_MIN_PER_173("mL/min/1.73m²"),
    /** Anthropometric length — height, head circumference. */
    CENTIMETERS("cm"),
    /** Body Mass Index — kg/m². */
    KG_PER_M2("kg/m²"),
    /** Linear distance — elevation above sea level. */
    METERS("m"),
    /** Time of day length — daylight hours computed from latitude + date. */
    HOURS("h"),
    /** Presence indicator — value=1.0 means "this artefact exists." */
    BOOLEAN(""),
    /** Substance amount per litre — Lp(a) molar (ESC 2021 preferred), SHBG. */
    NMOL_PER_L("nmol/L"),
    /** Substance amount per litre — homocysteine canonical SI unit. */
    UMOL_PER_L("µmol/L"),
    /** Electrolyte concentration — Na/K/Cl/total CO2 by US CMP convention. */
    MEQ_PER_L("mEq/L"),
    /** Cell volume — MCV, MPV by automated CBC analyzer convention. */
    FEMTOLITERS("fL"),
    /** Cell mass — MCH by automated CBC analyzer convention. */
    PICOGRAMS("pg"),
    /** Pituitary gonadotropin activity — FSH, LH; numerically equal to IU/L. */
    MIU_PER_ML("mIU/mL"),
    /** Antibody titer per millilitre — TPO Ab, Tg Ab thyroid autoimmunity panel. */
    IU_PER_ML("IU/mL"),
}
