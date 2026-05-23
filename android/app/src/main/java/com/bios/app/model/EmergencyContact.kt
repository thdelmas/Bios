package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Owner-designated emergency contact (#179, audit gap EM/CC §2.1).
 *
 * The five-audit convergence — EM/CC §2.1, Geriatrics/Palliative §2.13,
 * Primary Care, Cardiology, Ob/Gyn — flagged that URGENT alerts currently
 * terminate at the notification drawer with no escalation path. For owners
 * most likely to benefit (elderly, living alone, post-discharge, on insulin,
 * on opioids) the owner being the only escalation target is structurally
 * the wrong answer when the alert fires while the owner can no longer act.
 *
 * This entity is the owner-set policy half of the answer:
 *
 *  - The owner picks the contact (name + relationship label + phone) while
 *    they are competent — prospective consent.
 *  - The owner picks the per-tier opt-in: which tiers (URGENT only by
 *    default, optionally ADVISORY too) may surface this contact.
 *  - The owner picks the [acknowledgementTimeoutMinutes] — how long Bios
 *    waits for an "I'm OK" tap before composing a local SMS. Null = never
 *    escalate; default for new contacts.
 *
 * No row in this table = no escalation. The feature is off by default. The
 * owner is final — Bios never auto-sends; the SMS is composed locally and
 * handed to the system SMS chooser for owner confirmation.
 *
 * Storage is the main encrypted DB (SQLCipher), so the row wipes alongside
 * everything else when LETHE burner mode, dead man's switch, or the
 * standalone "Delete all data" action fires — see
 * [com.bios.app.platform.DataDestroyer.destroyDatabase].
 *
 * **Not** stored in [com.bios.app.data.ReproductiveDatabase]: emergency
 * contacts apply across all health domains, not the reproductive subset.
 */
@Entity(
    tableName = "emergency_contacts",
    indices = [Index("createdAt")],
)
data class EmergencyContact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Free-text display name (e.g. "Alex" or "Dr. Patel"). Required. */
    val name: String,
    /**
     * Free-text relationship label (e.g. "Partner", "Daughter", "Neighbor",
     * "Cardiologist"). Surfaced for owner recognition only — Bios does not
     * branch on this value.
     */
    val relationship: String,
    /**
     * E.164-or-local-format phone number. Stored verbatim as the owner
     * entered it; the `smsto:` URI handed to the system SMS chooser
     * passes it through unchanged. Bios does not validate the number —
     * the owner knows what number reaches their person.
     */
    val phoneNumber: String,
    /**
     * Minimum alert-tier level (matching [AlertTier.level]) at which this
     * contact may surface. Default = [AlertTier.URGENT].level (3) so the
     * common case is URGENT-only. An owner can opt this contact in at
     * ADVISORY (2) for higher-touch scenarios — never at NOTICE/OBSERVATION
     * (the audit explicitly scopes escalation to alerts that matter).
     */
    val minTierLevel: Int = MIN_TIER_URGENT,
    /**
     * Minutes Bios waits for owner acknowledgement on an URGENT alert
     * before composing a local SMS for this contact. Null = never escalate
     * (notify-the-owner-only). Default for new contacts is null — the
     * owner has to opt in to escalation explicitly per contact.
     *
     * Range when non-null: 1..120. Anything outside is clamped at the
     * repository boundary; the entity itself doesn't enforce it so test
     * harnesses can probe edge values directly.
     */
    val acknowledgementTimeoutMinutes: Int? = null,
    /** Epoch millis when the row was created. Used for stable ordering. */
    val createdAt: Long = System.currentTimeMillis(),
    /** Optional owner note (e.g. "Has spare key", "Works night shift"). */
    val note: String? = null,
) {
    companion object {
        /** Default tier floor: URGENT only. Matches [AlertTier.URGENT.level]. */
        const val MIN_TIER_URGENT = 3

        /** Owner-overridable lower bound: ADVISORY-and-above. */
        const val MIN_TIER_ADVISORY = 2

        /** Smallest accepted ack-timeout, in minutes. */
        const val MIN_ACK_TIMEOUT_MIN = 1

        /** Largest accepted ack-timeout, in minutes. */
        const val MAX_ACK_TIMEOUT_MIN = 120
    }
}
