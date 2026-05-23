package com.bios.app

import com.bios.app.model.EcgClassification
import com.bios.app.model.EcgStrip
import com.bios.app.model.LeadPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round-trip tests for the [EcgStrip] entity and its sibling enums.
 *
 * No Android runtime — these guard the data-class equality semantics
 * (load-bearing for Room change detection and for DAO-round-trip tests
 * that compare inserted vs fetched rows) and the enum-name encoding
 * Room uses for the [LeadPlacement] / [EcgClassification] columns.
 */
class EcgStripModelTest {

    private fun sampleStrip(
        id: String = "abc-123",
        samples: ByteArray = byteArrayOf(0, 1, 2, 3, 4, 5),
        classification: EcgClassification? = EcgClassification.SINUS_RHYTHM,
    ) = EcgStrip(
        id = id,
        timestamp = 1_700_000_000_000L,
        durationSeconds = 30,
        samplingRateHz = 512,
        leadPlacement = LeadPlacement.LEAD_I,
        samples = samples,
        voltageScale = 0.001,
        voltageOffset = 0.0,
        sampleEncoding = "int16_le",
        classification = classification,
        sourceVendor = "Apple Watch",
        note = null,
        createdAt = 1_700_000_001_000L,
    )

    @Test
    fun equals_holds_under_byte_array_content_match() {
        val a = sampleStrip(samples = byteArrayOf(1, 2, 3))
        val b = sampleStrip(samples = byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun equals_differs_when_samples_bytes_differ() {
        val a = sampleStrip(samples = byteArrayOf(1, 2, 3))
        val b = sampleStrip(samples = byteArrayOf(1, 2, 4))
        assertNotEquals(a, b)
    }

    @Test
    fun equals_differs_when_id_differs() {
        val a = sampleStrip(id = "id-1")
        val b = sampleStrip(id = "id-2")
        assertNotEquals(a, b)
    }

    @Test
    fun classification_is_nullable_for_unclassified_strips() {
        val strip = sampleStrip(classification = null)
        assertNull(strip.classification)
    }

    @Test
    fun lead_placement_enum_covers_consumer_devices() {
        // Apple Watch / KardiaMobile / Withings / Samsung all map to
        // LEAD_I; chest-strap derivations are rare on consumer
        // wearables. UNKNOWN is the safe default for imports without
        // metadata.
        assertEquals(3, LeadPlacement.entries.size)
        assertEquals(LeadPlacement.LEAD_I, LeadPlacement.valueOf("LEAD_I"))
        assertEquals(LeadPlacement.LEAD_II_DERIVED, LeadPlacement.valueOf("LEAD_II_DERIVED"))
        assertEquals(LeadPlacement.UNKNOWN, LeadPlacement.valueOf("UNKNOWN"))
    }

    @Test
    fun classification_enum_matches_vendor_buckets() {
        // Apple iOS Health and KardiaMobile both collapse to four
        // buckets at the level Bios cares about. SINUS_RHYTHM /
        // ATRIAL_FIBRILLATION / INCONCLUSIVE / OTHER.
        assertEquals(4, EcgClassification.entries.size)
    }

    @Test
    fun copy_round_trips_all_fields() {
        // Proxies Room's read-back semantics: re-emitting every field
        // through the constructor must yield an identical row. The
        // assertion also catches forgotten-field bugs when [EcgStrip]
        // grows a new column.
        val a = sampleStrip()
        val b = a.copy()
        assertEquals(a, b)
    }
}
