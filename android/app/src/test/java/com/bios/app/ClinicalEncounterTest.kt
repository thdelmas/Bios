package com.bios.app

import com.bios.app.data.BiosDatabaseMigrationsExtras
import com.bios.app.model.ClinicalEncounter
import com.bios.app.model.FacilityKind
import com.bios.app.model.FollowUpReferral
import com.bios.app.model.ReferralUrgency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Spec test for ClinicalEncounter / FollowUpReferral entities and the
 * v35 -> v36 migration. Issue #358.
 */
class ClinicalEncounterTest {

    @Test
    fun `encounter generates stable uuid and createdAt`() {
        val e = ClinicalEncounter(admissionAt = 1714000000000L)
        assertNotNull(e.uuid)
        assertEquals(36, e.uuid.length)
        assertNotNull(e.createdAt)
        // Discharge is null while still admitted.
        assertNull(e.dischargeAt)
        assertEquals(FacilityKind.OTHER, e.facilityKind)
    }

    @Test
    fun `encounter round-trips a Sagrat Cor ER visit verbatim`() {
        // The Sagrat Cor case: admitted 06:17, discharged 11:49 same day,
        // facility = ER, reason = headache 6/10 + tunnel-vision episodes.
        // The narrative is Spanish; Bios stores it as-is.
        val e = ClinicalEncounter(
            facility = "Hospital Universitari Sagrat Cor",
            facilityKind = FacilityKind.ER,
            admissionAt = 1713680220000L,
            dischargeAt = 1713700140000L,
            reasonForVisit = "Cefalea holocraneal de 10 días",
            dischargeSummaryText = "Dada ausencia de signos de alarma se da alta a domicilio aconsejando seguimiento por médico de AP y Neurología de zona.",
            dischargeSummaryLanguage = "es",
            followUpInstructions = "Control por medico de AP y especialista en Neurologia de zona.",
        )
        assertEquals(FacilityKind.ER, e.facilityKind)
        assertEquals("es", e.dischargeSummaryLanguage)
        assertNotNull(e.dischargeAt)
    }

    @Test
    fun `two encounters have unique uuids by default`() {
        val a = ClinicalEncounter(admissionAt = 0L)
        val b = ClinicalEncounter(admissionAt = 0L)
        assertNotEquals(a.uuid, b.uuid)
    }

    @Test
    fun `follow-up referral defaults to ROUTINE urgency and carries clinician guidance`() {
        // Sagrat Cor follow-up: "Control by AP doctor and Neurology zone
        // specialist." No timeframe specified — defaults to ROUTINE.
        val r = FollowUpReferral(
            encounterUuid = "enc-1",
            specialty = "Neurology",
        )
        assertEquals(ReferralUrgency.ROUTINE, r.urgency)
        assertNull(r.suggestedTimeframe)
        assertNull(r.facility)
        assertNull(r.reason)
    }

    @Test
    fun `follow-up referral can encode urgency and full clinician guidance`() {
        val r = FollowUpReferral(
            encounterUuid = "enc-1",
            specialty = "Cardiology",
            urgency = ReferralUrgency.URGENT,
            suggestedTimeframe = "within 2 weeks",
            facility = "ESC outpatient",
            reason = "Suspected paroxysmal AFib — Holter requested",
        )
        assertEquals(ReferralUrgency.URGENT, r.urgency)
        assertEquals("within 2 weeks", r.suggestedTimeframe)
    }

    @Test
    fun `MIGRATION_35_36 covers the right schema versions`() {
        val m = BiosDatabaseMigrationsExtras.MIGRATION_35_36
        assertEquals(35, m.startVersion)
        assertEquals(36, m.endVersion)
    }

    @Test
    fun `facility kind and urgency enums cover the expected vocabulary`() {
        // Room persists enum entries by name; renaming would invalidate
        // existing rows. Pin the value-set.
        assertEquals(
            setOf("ER", "INPATIENT", "OUTPATIENT", "TELEHEALTH", "URGENT_CARE", "OTHER"),
            FacilityKind.entries.map { it.name }.toSet(),
        )
        assertEquals(
            setOf("ROUTINE", "URGENT", "EMERGENT"),
            ReferralUrgency.entries.map { it.name }.toSet(),
        )
    }
}
