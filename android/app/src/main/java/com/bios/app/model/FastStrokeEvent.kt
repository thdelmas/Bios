package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Owner-recorded FAST stroke-recognition check. Closes
 * [docs/audits/NEUROLOGY_POV.md] §2.15 (issue #207). The AHA / ASA FAST
 * mnemonic — **F**acial drooping, **A**rm weakness, **S**peech difficulty,
 * **T**ime — is the public-health stroke-recognition tool the National
 * Stroke Association, the WHO, and the American Heart Association all
 * teach for lay-bystander screening.
 *
 * Owner-input only. The neurology audit §3.3 and the Bios manifesto
 * explicitly prohibit automated voice / face stroke detection — every
 * field on this entity is set by the owner (or by someone tapping through
 * the FAST screen on the owner's behalf when the owner can't operate
 * the phone). When **any** of the three observable items is `true`, the
 * record represents a positive FAST screen and the alert pipeline raises
 * an URGENT-tier event with a one-tap "Call emergency services" action.
 *
 * Manifesto guards:
 *  - **Owner-input only.** No inference. Voice / face / handwriting
 *    inference is explicitly out of scope per neurology audit §3.3.
 *  - **Time-stamped.** Stroke care is time-critical (Saver 2006 — every
 *    minute of LVO ischaemia destroys ~1.9M neurons); the timestamp is
 *    what a clinician needs to bound the stroke-onset window.
 *  - **Stays on-device** unless the owner explicitly invokes the FHIR
 *    exporter, which emits a flag observation for the suspected event.
 */
@Entity(
    tableName = "fast_stroke_events",
    indices = [Index("timestamp")],
)
data class FastStrokeEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /**
     * Epoch millis when the FAST check was performed. Stroke care anchors
     * to last-known-well time; the timestamp here is when the owner
     * observed the deficit, which is the closest available proxy.
     */
    val timestamp: Long,
    /**
     * **F**ace — sudden facial drooping when the person smiles. AHA/ASA
     * FAST item 1.
     */
    val facialDrooping: Boolean,
    /**
     * **A**rms — arm weakness when both arms are raised, with one drifting
     * downward. AHA/ASA FAST item 2.
     */
    val armWeakness: Boolean,
    /**
     * **S**peech — slurred speech or difficulty repeating a simple
     * sentence. AHA/ASA FAST item 3.
     */
    val speechDifficulty: Boolean,
    /** Optional owner free-text note (which side, additional symptoms). */
    val notes: String? = null,
) {
    /**
     * True when **any** of the three FAST items is positive. AHA/ASA
     * teaching: even a single positive item warrants immediate emergency
     * services contact — the "T" in FAST is *Time to call*. Identifying
     * any single deficit is sensitive (~80 %) for large-vessel-occlusion
     * stroke in the Cincinnati Prehospital Stroke Scale (Kothari 1999,
     * Annals of EM); the FAST mnemonic is the lay-bystander adaptation.
     */
    val isPositive: Boolean
        get() = facialDrooping || armWeakness || speechDifficulty
}
