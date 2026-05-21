package com.bios.app.ui.immunisations

/**
 * Catalog of common adult vaccines surfaced in the manual-entry picker.
 * Each entry pairs the display name shown to the owner with the CDC CVX
 * code (the canonical vaccine identifier — flu = 88, Tdap = 115, etc.)
 * so the screening-cadence engine (#155) can match against it without
 * relying on free-text equality.
 *
 * Owners can also choose **Other (free-text)**, which omits the CVX code
 * and stores only the typed name — useful for vaccines outside this short
 * catalog (e.g. yellow fever, JE, rabies pre-exposure for travel).
 *
 * Sources:
 *  - CDC CVX code set (canonical vaccine identifiers, public domain)
 *  - ACIP adult immunization schedule (display-name conventions)
 *
 * The list is intentionally short — it covers the common screening-engine
 * targets. Paediatric vaccines and the long tail (anthrax, rabies booster,
 * etc.) are out of scope until ACIP-specific paediatric work lands.
 */
data class VaccineCatalogEntry(
    val displayName: String,
    val cvxCode: String,
)

object VaccineCatalog {

    /** Curated adult vaccine list, alphabetised within rough clinical category. */
    val entries: List<VaccineCatalogEntry> = listOf(
        // Respiratory annual / boosters
        VaccineCatalogEntry("COVID-19 (mRNA)", "213"),
        VaccineCatalogEntry("COVID-19 (protein subunit)", "227"),
        VaccineCatalogEntry("Influenza (seasonal, inactivated)", "88"),
        VaccineCatalogEntry("Influenza (high-dose, ≥65)", "135"),

        // Adult routine / catch-up
        VaccineCatalogEntry("Tdap (tetanus, diphtheria, pertussis)", "115"),
        VaccineCatalogEntry("Td (tetanus, diphtheria booster)", "113"),
        VaccineCatalogEntry("MMR (measles, mumps, rubella)", "03"),
        VaccineCatalogEntry("Varicella (chickenpox)", "21"),

        // HPV (catch-up to age 26, shared decision 27-45)
        VaccineCatalogEntry("HPV (9-valent)", "165"),

        // Age 50+ shingles
        VaccineCatalogEntry("Shingles (Shingrix, recombinant)", "187"),

        // Age 65+ / high-risk pneumococcal
        VaccineCatalogEntry("Pneumococcal (PCV20)", "228"),
        VaccineCatalogEntry("Pneumococcal (PCV15)", "215"),
        VaccineCatalogEntry("Pneumococcal (PPSV23)", "33"),

        // Hepatitis
        VaccineCatalogEntry("Hepatitis A", "85"),
        VaccineCatalogEntry("Hepatitis B (recombinant)", "08"),
        VaccineCatalogEntry("Hepatitis A+B combination", "104"),

        // Meningococcal (high-risk / college)
        VaccineCatalogEntry("Meningococcal ACWY", "147"),
        VaccineCatalogEntry("Meningococcal B", "163"),

        // RSV (adult)
        VaccineCatalogEntry("RSV (recombinant, adult)", "303"),

        // Travel
        VaccineCatalogEntry("Yellow fever", "37"),
        VaccineCatalogEntry("Typhoid (inactivated)", "101"),
        VaccineCatalogEntry("Japanese encephalitis", "120"),
    )
}
