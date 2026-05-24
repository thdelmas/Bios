package com.bios.app

import com.bios.app.alerts.BiomarkerReferences
import com.bios.app.alerts.Citation
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the contract from #305 — every scientific reference Bios
 * surfaces must carry a stable, alive https URL the owner can follow
 * back to the source themselves.
 *
 * This test pins the URL invariant on every shipped reference. It
 * does not perform a live HEAD check (offline CI), but DOI links
 * resolve through doi.org which redirects to the current publisher
 * URL — so as long as a real DOI is captured, the link survives
 * publisher migrations.
 */
class CitationLinksTest {

    @Test
    fun citation_constructor_rejects_a_non_https_url() {
        assertThrows(IllegalArgumentException::class.java) {
            Citation(text = "anything", url = "http://example.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Citation(text = "anything", url = "")
        }
    }

    @Test
    fun every_biomarker_reference_citation_has_an_https_url() {
        val offenders = BiomarkerReferences.all
            .flatMap { ref -> ref.citations.map { ref.id to it } }
            .filterNot { (_, c) -> c.url.startsWith("https://") && c.text.isNotBlank() }
        assertTrue(
            "Citations missing an https URL or text: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun every_biomarker_reference_has_at_least_one_citation() {
        val empty = BiomarkerReferences.all.filter { it.citations.isEmpty() }
        assertTrue(
            "Biomarker references without any citation: ${empty.map { it.id }}",
            empty.isEmpty(),
        )
    }

    @Test
    fun doi_helper_builds_a_doi_org_url() {
        val c = Citation.doi(text = "Ridker 2003", doi = "10.1161/01.CIR.0000093381.57779.67")
        assertTrue(c.url.startsWith("https://doi.org/"))
        assertTrue(c.url.endsWith("10.1161/01.CIR.0000093381.57779.67"))
    }
}
