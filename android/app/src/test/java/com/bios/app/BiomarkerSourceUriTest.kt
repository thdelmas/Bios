package com.bios.app

import com.bios.app.data.BiomarkerPayloadKeys
import com.bios.app.ui.biomarkers.LAB_REPORT_MIME_TYPES
import com.bios.app.ui.biomarkers.formatBiomarkerDate
import com.bios.app.ui.biomarkers.formatBiomarkerValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Pure-logic tests for the source-URI capture surface (#106).
 *
 *  - The lab-report MIME policy (PDF, JPEG, PNG) is the picker's only
 *    contract with the rest of the OS — pin the literal so it can't drift
 *    silently and silently widen what the entry surface accepts.
 *  - [BiomarkerPayloadKeys.SOURCE_URI] is the storage key — pinning it
 *    here catches accidental renames that would orphan every existing
 *    attached lab on upgrade.
 *  - Row-side formatters (extracted into BiomarkerEntryRows.kt) round-trip
 *    representative values cleanly.
 *
 * The capture-side `takePersistableUriPermission` flow and the wipe-side
 * `releaseAllPersistableUriPermissions` paths both require an Android
 * `ContentResolver` and are covered by build-and-eyeball, not unit tests.
 */
class BiomarkerSourceUriTest {

    init {
        // Pin dates so format assertions pass in every CI timezone.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun `LAB_REPORT_MIME_TYPES is exactly PDF + JPEG + PNG`() {
        // What the picker accepts is part of the entry-surface contract.
        // Widening (e.g. "*/*") would let the owner accidentally attach a
        // .docx, .heic, or .zip and then hit a viewer-not-found wall at
        // open time. Narrowing (e.g. dropping PNG) would silently break
        // every PNG attached on a previous version.
        assertEquals(
            setOf("application/pdf", "image/jpeg", "image/png"),
            LAB_REPORT_MIME_TYPES.toSet(),
        )
        assertEquals("No duplicates", LAB_REPORT_MIME_TYPES.size, LAB_REPORT_MIME_TYPES.toSet().size)
    }

    @Test
    fun `source URI payload key is the stable string declared in BiomarkerContext`() {
        // Renaming this key would orphan every attached lab on upgrade —
        // the row would still be in event_payloads but `rowsToContext`
        // wouldn't find it. Pin the literal so a refactor can't slip past.
        assertEquals("source_uri", BiomarkerPayloadKeys.SOURCE_URI)
    }

    @Test
    fun `formatBiomarkerValue collapses whole numbers and keeps two decimals otherwise`() {
        // Whole numbers (HbA1c lab integers, platelet counts) shouldn't
        // drag a trailing ".0" through every row.
        assertEquals("5", formatBiomarkerValue(5.0))
        assertEquals("200", formatBiomarkerValue(200.0))
        assertEquals("5.40", formatBiomarkerValue(5.4))
        assertEquals("0.85", formatBiomarkerValue(0.85))
    }

    @Test
    fun `formatBiomarkerDate renders human-readable date`() {
        // 2026-01-15 00:00:00 UTC → "Jan 15, 2026"
        val rendered = formatBiomarkerDate(1768435200000L)
        assertTrue("Expected human-readable date, got '$rendered'", rendered.contains("2026"))
        assertTrue(rendered.contains("Jan"))
        assertTrue(rendered.contains("15"))
    }
}
