package com.bios.app

import com.bios.app.data.HeadacheEventWriter
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.HeadacheEventFields
import com.bios.app.model.HeadacheLog
import com.bios.app.model.HeadacheType
import com.bios.app.model.MigraineAttack
import com.bios.app.model.MigraineTrigger
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage of [HeadacheEventWriter] (#283 Cut 2). Pins the
 * shape of the cross-cutting `metric_readings` + `event_payloads`
 * writeset the chronic-migraine (#283 Cut 3), MOH (existing), and
 * future clinician-share exporters read.
 *
 * The entity tables stay the primary store; this writer produces the
 * denormalised view so engines can scan a single `metric_readings`
 * window for headache-days / migraine-days / treatment-days.
 */
class HeadacheEventWriterTest {

    private val sourceId = "self-reported"
    private val now = 1_700_000_000_000L

    // -- MigraineAttack →

    @Test
    fun migraine_attack_writes_parent_with_entity_id_and_intensity() {
        val attack = MigraineAttack(
            onsetTimestamp = 1_000L,
            endTimestamp = 5_000L,
            peakIntensity = 7,
        )
        val ws = HeadacheEventWriter.fromMigraineAttack(attack, sourceId, now)
        assertEquals(attack.id, ws.parent.id)
        assertEquals(MetricType.MIGRAINE_ATTACK_EVENT.key, ws.parent.metricType)
        assertEquals(7.0, ws.parent.value, 1e-9)
        assertEquals(1_000L, ws.parent.timestamp)
        // (5_000 - 1_000) ms = 4 s
        assertEquals(4, ws.parent.durationSec)
        assertEquals(ConfidenceTier.HIGH.level, ws.parent.confidence)
    }

    @Test
    fun migraine_attack_in_progress_has_null_durationSec() {
        val attack = MigraineAttack(
            onsetTimestamp = 1_000L,
            endTimestamp = null,
            peakIntensity = 4,
        )
        val ws = HeadacheEventWriter.fromMigraineAttack(attack, sourceId, now)
        assertNull(ws.parent.durationSec)
    }

    @Test
    fun migraine_aura_writes_a_boolean_payload_field() {
        val attack = MigraineAttack(
            onsetTimestamp = 1_000L,
            peakIntensity = 5,
            aura = true,
        )
        val ws = HeadacheEventWriter.fromMigraineAttack(attack, sourceId, now)
        val auraField = ws.payload.firstOrNull { it.fieldKey == HeadacheEventFields.AURA }
        assertNotNull(auraField)
        assertEquals(1L, auraField!!.longValue)
    }

    @Test
    fun migraine_without_aura_does_not_emit_an_aura_payload_field() {
        // We don't write "owner answered no" rows — the absence of the
        // field encodes "owner did not record it." Saves storage and
        // matches the renderer's "missing = unknown" convention.
        val attack = MigraineAttack(
            onsetTimestamp = 1_000L,
            peakIntensity = 5,
            aura = false,
        )
        val ws = HeadacheEventWriter.fromMigraineAttack(attack, sourceId, now)
        assertTrue(ws.payload.none { it.fieldKey == HeadacheEventFields.AURA })
    }

    @Test
    fun migraine_triggers_are_joined_into_one_string_field() {
        val attack = MigraineAttack(
            onsetTimestamp = 1_000L,
            peakIntensity = 5,
            triggers = setOf(MigraineTrigger.STRESS, MigraineTrigger.SLEEP_DEPRIVATION),
        )
        val ws = HeadacheEventWriter.fromMigraineAttack(attack, sourceId, now)
        val triggersField = ws.payload.first { it.fieldKey == HeadacheEventFields.TRIGGERS }
        // Storage format is the enum names; the parser side splits on comma.
        val stored = triggersField.stringValue!!.split(",").toSet()
        assertEquals(setOf("STRESS", "SLEEP_DEPRIVATION"), stored)
    }

    @Test
    fun empty_triggers_set_does_not_emit_a_payload_field() {
        val attack = MigraineAttack(
            onsetTimestamp = 1_000L,
            peakIntensity = 5,
            triggers = emptySet(),
        )
        val ws = HeadacheEventWriter.fromMigraineAttack(attack, sourceId, now)
        assertTrue(ws.payload.none { it.fieldKey == HeadacheEventFields.TRIGGERS })
    }

    // -- Abortive medication →

    @Test
    fun abortive_medication_creates_a_linked_intake_child_row() {
        val attack = MigraineAttack(
            onsetTimestamp = 1_000L,
            peakIntensity = 6,
            medicationTaken = "sumatriptan 50 mg",
        )
        val ws = HeadacheEventWriter.fromMigraineAttack(attack, sourceId, now)

        // Parent carries the abortive_medication freetext + the back-link
        // to the child intake row.
        val medField = ws.payload.first { it.fieldKey == HeadacheEventFields.ABORTIVE_MEDICATION }
        assertEquals("sumatriptan 50 mg", medField.stringValue)
        val linkField = ws.payload.first {
            it.fieldKey == HeadacheEventFields.ABORTIVE_MEDICATION_INTAKE_ID
        }

        // Child intake row.
        assertNotNull(ws.abortiveIntake)
        val intake = ws.abortiveIntake!!
        assertEquals(MetricType.MEDICATION_INTAKE.key, intake.metricType)
        assertEquals(1.0, intake.value, 1e-9) // presence marker for Cut 2
        assertEquals(attack.onsetTimestamp, intake.timestamp)
        assertEquals(ConfidenceTier.HIGH.level, intake.confidence)
        assertEquals(intake.id, linkField.stringValue)

        // Child carries the medication name and a back-link to the parent.
        val nameField = ws.abortivePayload.first {
            it.fieldKey == HeadacheEventWriter.MEDICATION_NAME_FIELD
        }
        assertEquals("sumatriptan 50 mg", nameField.stringValue)
        val parentLink = ws.abortivePayload.first {
            it.fieldKey == HeadacheEventWriter.PARENT_HEADACHE_EVENT_FIELD
        }
        assertEquals(attack.id, parentLink.stringValue)
    }

    @Test
    fun no_medication_leaves_abortive_intake_null() {
        val attack = MigraineAttack(
            onsetTimestamp = 1_000L,
            peakIntensity = 4,
            medicationTaken = null,
        )
        val ws = HeadacheEventWriter.fromMigraineAttack(attack, sourceId, now)
        assertNull(ws.abortiveIntake)
        assertTrue(ws.abortivePayload.isEmpty())
        assertTrue(ws.payload.none {
            it.fieldKey == HeadacheEventFields.ABORTIVE_MEDICATION_INTAKE_ID
        })
    }

    @Test
    fun blank_medication_string_is_treated_as_none() {
        val attack = MigraineAttack(
            onsetTimestamp = 1_000L,
            peakIntensity = 4,
            medicationTaken = "   ",
        )
        val ws = HeadacheEventWriter.fromMigraineAttack(attack, sourceId, now)
        assertNull(ws.abortiveIntake)
    }

    // -- HeadacheLog routing →

    @Test
    fun headache_log_with_type_TENSION_routes_to_generic_HEADACHE_ATTACK_EVENT() {
        val log = HeadacheLog(
            timestamp = 1_000L,
            intensity = 4,
            type = HeadacheType.TENSION,
        )
        val ws = HeadacheEventWriter.fromHeadacheLog(log, sourceId, now)
        assertEquals(MetricType.HEADACHE_ATTACK_EVENT.key, ws.parent.metricType)
    }

    @Test
    fun headache_log_with_type_OTHER_routes_to_generic_HEADACHE_ATTACK_EVENT() {
        val log = HeadacheLog(
            timestamp = 1_000L,
            intensity = 3,
            type = HeadacheType.OTHER,
        )
        val ws = HeadacheEventWriter.fromHeadacheLog(log, sourceId, now)
        assertEquals(MetricType.HEADACHE_ATTACK_EVENT.key, ws.parent.metricType)
    }

    @Test
    fun headache_log_with_type_MIGRAINE_routes_to_MIGRAINE_ATTACK_EVENT() {
        // The chronic-migraine evaluator counts migraine-days from both
        // MigraineAttack rows and HeadacheLog(type=MIGRAINE) rows — they
        // must converge on the same MetricType key.
        val log = HeadacheLog(
            timestamp = 1_000L,
            intensity = 6,
            type = HeadacheType.MIGRAINE,
        )
        val ws = HeadacheEventWriter.fromHeadacheLog(log, sourceId, now)
        assertEquals(MetricType.MIGRAINE_ATTACK_EVENT.key, ws.parent.metricType)
    }

    @Test
    fun headache_log_with_type_CLUSTER_routes_to_CLUSTER_HEADACHE_ATTACK_EVENT() {
        // The #284 cluster periodicity histogram reads from the cluster
        // event MetricType — tension rows must not leak in.
        val log = HeadacheLog(
            timestamp = 1_000L,
            intensity = 8,
            type = HeadacheType.CLUSTER,
        )
        val ws = HeadacheEventWriter.fromHeadacheLog(log, sourceId, now)
        assertEquals(MetricType.CLUSTER_HEADACHE_ATTACK_EVENT.key, ws.parent.metricType)
    }

    @Test
    fun headache_log_durationMinutes_converts_to_durationSec() {
        val log = HeadacheLog(
            timestamp = 1_000L,
            intensity = 4,
            type = HeadacheType.TENSION,
            durationMinutes = 90,
        )
        val ws = HeadacheEventWriter.fromHeadacheLog(log, sourceId, now)
        assertEquals(90 * 60, ws.parent.durationSec)
    }
}
