package com.bios.app

import com.bios.app.ui.immunisations.VaccineCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the vaccine catalog (#156). The CVX codes pin specific clinical
 * identifiers — drift here would let the screening-cadence engine (#155)
 * silently mismatch records and recommendations. The catalog is a static
 * fixture; tests exercise its shape, not its contents per se, except for
 * the highest-impact codes (flu, COVID, Tdap, shingles) where the code
 * value is load-bearing.
 */
class VaccineCatalogTest {

    @Test
    fun catalog_has_no_duplicate_cvx_codes() {
        // A duplicate CVX would mean two display names compete for the
        // same code — the dropdown could let an owner pick either, but
        // the screening engine would treat them as the same vaccine.
        val codes = VaccineCatalog.entries.map { it.cvxCode }
        assertEquals(
            "Duplicate CVX codes: ${codes - codes.toSet()}",
            codes.size, codes.toSet().size,
        )
    }

    @Test
    fun catalog_has_no_duplicate_display_names() {
        val names = VaccineCatalog.entries.map { it.displayName }
        assertEquals(
            "Duplicate display names: ${names - names.toSet()}",
            names.size, names.toSet().size,
        )
    }

    @Test
    fun catalog_uses_only_numeric_cvx_codes() {
        // CDC CVX codes are always integers; non-numeric entries usually
        // mean someone confused CVX with NDC or SNOMED CT.
        for (entry in VaccineCatalog.entries) {
            assertTrue(
                "${entry.displayName} has non-numeric CVX '${entry.cvxCode}'",
                entry.cvxCode.all { it.isDigit() }
            )
        }
    }

    @Test
    fun catalog_covers_the_canonical_adult_set() {
        // High-impact codes the screening-cadence engine (#155) will rely on.
        // Pinned individually because their identity matters.
        val byCode = VaccineCatalog.entries.associateBy { it.cvxCode }
        assertNotNull("Influenza inactivated should be in catalog (CVX 88)", byCode["88"])
        assertNotNull("COVID-19 mRNA should be in catalog (CVX 213)", byCode["213"])
        assertNotNull("Tdap should be in catalog (CVX 115)", byCode["115"])
        assertNotNull("Shingrix should be in catalog (CVX 187)", byCode["187"])
        assertNotNull("HPV 9-valent should be in catalog (CVX 165)", byCode["165"])
    }

    @Test
    fun catalog_is_non_empty_and_keeps_a_reasonable_floor() {
        // Floor check — if someone accidentally truncates the catalog
        // (refactor, merge resolution) the test catches it before the
        // dropdown ships with only one or two entries.
        assertTrue(
            "Catalog has only ${VaccineCatalog.entries.size} entries — below reasonable floor",
            VaccineCatalog.entries.size >= 15
        )
    }
}
