package com.bios.app

import com.bios.app.model.HeadacheEventFields
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Frozen snapshot of the [HeadacheEventFields] payload-field-key set
 * (#283 Cut 1). These strings land in the `event_payloads` sidecar of
 * every HEADACHE_ATTACK_EVENT / CLUSTER_HEADACHE_ATTACK_EVENT /
 * MIGRAINE_ATTACK_EVENT row written by the entry surface (#283 Cut 2)
 * and read by the chronic-migraine evaluator (#283 Cut 3), the MOH
 * evaluator update (also Cut 3), and any future clinician-share
 * exporter.
 *
 * **How to update this file.**
 *  - Adding a key: append the new constant + add it to the snapshot.
 *  - Renaming a key: don't. Keep the old constant + add the new one,
 *    or migrate readers first. Renaming silently invalidates every
 *    pre-rename row on disk.
 *  - Same trust-the-wire-format reasoning as
 *    `MetricTypeKeysSnapshotTest`.
 */
class HeadacheEventFieldsTest {

    private val shippedKeys: Map<String, String> = mapOf(
        "SIDE" to HeadacheEventFields.SIDE,
        "SIDE_UNILATERAL_LEFT" to HeadacheEventFields.SIDE_UNILATERAL_LEFT,
        "SIDE_UNILATERAL_RIGHT" to HeadacheEventFields.SIDE_UNILATERAL_RIGHT,
        "SIDE_BILATERAL" to HeadacheEventFields.SIDE_BILATERAL,
        "SIDE_HOLOCRANIAL" to HeadacheEventFields.SIDE_HOLOCRANIAL,
        "CHARACTER" to HeadacheEventFields.CHARACTER,
        "CHARACTER_PULSATING" to HeadacheEventFields.CHARACTER_PULSATING,
        "CHARACTER_PRESSING" to HeadacheEventFields.CHARACTER_PRESSING,
        "CHARACTER_STABBING" to HeadacheEventFields.CHARACTER_STABBING,
        "CHARACTER_BURNING" to HeadacheEventFields.CHARACTER_BURNING,
        "CHARACTER_OTHER" to HeadacheEventFields.CHARACTER_OTHER,
        "PHOTOPHOBIA" to HeadacheEventFields.PHOTOPHOBIA,
        "PHONOPHOBIA" to HeadacheEventFields.PHONOPHOBIA,
        "NAUSEA" to HeadacheEventFields.NAUSEA,
        "AURA" to HeadacheEventFields.AURA,
        "AGGRAVATED_BY_ACTIVITY" to HeadacheEventFields.AGGRAVATED_BY_ACTIVITY,
        "AUTONOMIC_FEATURES" to HeadacheEventFields.AUTONOMIC_FEATURES,
        "TRIGGERS" to HeadacheEventFields.TRIGGERS,
        "ABORTIVE_MEDICATION" to HeadacheEventFields.ABORTIVE_MEDICATION,
        "ABORTIVE_MEDICATION_INTAKE_ID" to HeadacheEventFields.ABORTIVE_MEDICATION_INTAKE_ID,
        "NOTES" to HeadacheEventFields.NOTES,
    )

    @Test
    fun every_field_key_value_is_frozen() {
        // Pinning each constant to its shipped string. Renaming a value
        // here invalidates every row written under the old name — the
        // diary substrate the chronic-migraine and MOH evaluators read
        // depends on these names staying put.
        assertEquals("side", shippedKeys["SIDE"])
        assertEquals("unilateral_left", shippedKeys["SIDE_UNILATERAL_LEFT"])
        assertEquals("unilateral_right", shippedKeys["SIDE_UNILATERAL_RIGHT"])
        assertEquals("bilateral", shippedKeys["SIDE_BILATERAL"])
        assertEquals("holocranial", shippedKeys["SIDE_HOLOCRANIAL"])
        assertEquals("character", shippedKeys["CHARACTER"])
        assertEquals("pulsating", shippedKeys["CHARACTER_PULSATING"])
        assertEquals("pressing", shippedKeys["CHARACTER_PRESSING"])
        assertEquals("stabbing", shippedKeys["CHARACTER_STABBING"])
        assertEquals("burning", shippedKeys["CHARACTER_BURNING"])
        assertEquals("other", shippedKeys["CHARACTER_OTHER"])
        assertEquals("photophobia", shippedKeys["PHOTOPHOBIA"])
        assertEquals("phonophobia", shippedKeys["PHONOPHOBIA"])
        assertEquals("nausea", shippedKeys["NAUSEA"])
        assertEquals("aura", shippedKeys["AURA"])
        assertEquals("aggravated_by_activity", shippedKeys["AGGRAVATED_BY_ACTIVITY"])
        assertEquals("autonomic_features", shippedKeys["AUTONOMIC_FEATURES"])
        assertEquals("triggers", shippedKeys["TRIGGERS"])
        assertEquals("abortive_medication", shippedKeys["ABORTIVE_MEDICATION"])
        assertEquals(
            "abortive_medication_intake_id",
            shippedKeys["ABORTIVE_MEDICATION_INTAKE_ID"],
        )
        assertEquals("notes", shippedKeys["NOTES"])
    }

    @Test
    fun no_field_keys_collide() {
        // Each constant must be a unique string so a payload row keyed
        // by one field can't be misread as another. Catches accidental
        // copy-paste collisions when adding new fields.
        val values = shippedKeys.values.toList()
        assertEquals(
            "Duplicate values in HeadacheEventFields: ${values.groupBy { it }.filter { it.value.size > 1 }.keys}",
            values.size,
            values.toSet().size,
        )
    }
}
