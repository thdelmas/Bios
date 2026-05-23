package com.bios.app.data

import com.bios.app.model.EmergencyContact

/**
 * Thin repository wrapper over [com.bios.app.data.dao.EmergencyContactDao]
 * for the Settings → Emergency contacts screen and the URGENT-escalation
 * pipeline (audit gap EM/CC §2.1).
 *
 * Off-by-default semantics: an empty contact list = no escalation. The
 * UrgentAckTimeoutWorker queries [eligibleForTier] before scheduling
 * anything; an empty result means nothing fires.
 */
class EmergencyContactRepo(private val db: BiosDatabase) {

    private val dao get() = db.emergencyContactDao()

    /**
     * Records a new emergency contact.
     *
     * @param name owner-recognisable display name; required, non-blank
     * @param relationship free-text relationship label; required, non-blank
     * @param phoneNumber phone number as the owner enters it; required,
     *   non-blank. Bios doesn't validate format — the owner knows what
     *   reaches their person.
     * @param minTierLevel minimum alert tier at which this contact may
     *   surface (defaults to URGENT). Clamped to ADVISORY..URGENT.
     * @param acknowledgementTimeoutMinutes minutes Bios waits before
     *   composing an SMS to this contact on an unacknowledged URGENT
     *   alert. Null = never escalate (default). When non-null, clamped
     *   to [EmergencyContact.MIN_ACK_TIMEOUT_MIN]..[EmergencyContact.MAX_ACK_TIMEOUT_MIN].
     * @param note optional owner annotation
     */
    suspend fun add(
        name: String,
        relationship: String,
        phoneNumber: String,
        minTierLevel: Int = EmergencyContact.MIN_TIER_URGENT,
        acknowledgementTimeoutMinutes: Int? = null,
        note: String? = null,
    ): Long {
        require(name.isNotBlank()) { "Name cannot be blank" }
        require(relationship.isNotBlank()) { "Relationship cannot be blank" }
        require(phoneNumber.isNotBlank()) { "Phone number cannot be blank" }
        return dao.insert(
            EmergencyContact(
                name = name.trim(),
                relationship = relationship.trim(),
                phoneNumber = phoneNumber.trim(),
                minTierLevel = clampTier(minTierLevel),
                acknowledgementTimeoutMinutes = clampTimeout(acknowledgementTimeoutMinutes),
                note = note?.takeIf { it.isNotBlank() }?.trim(),
            )
        )
    }

    suspend fun update(contact: EmergencyContact) {
        require(contact.name.isNotBlank()) { "Name cannot be blank" }
        require(contact.relationship.isNotBlank()) { "Relationship cannot be blank" }
        require(contact.phoneNumber.isNotBlank()) { "Phone number cannot be blank" }
        dao.update(
            contact.copy(
                name = contact.name.trim(),
                relationship = contact.relationship.trim(),
                phoneNumber = contact.phoneNumber.trim(),
                minTierLevel = clampTier(contact.minTierLevel),
                acknowledgementTimeoutMinutes = clampTimeout(contact.acknowledgementTimeoutMinutes),
                note = contact.note?.takeIf { it.isNotBlank() }?.trim(),
            )
        )
    }

    suspend fun remove(id: Long) {
        val existing = dao.fetchById(id) ?: return
        dao.delete(existing)
    }

    suspend fun fetchAll(): List<EmergencyContact> = dao.fetchAll()

    /**
     * Contacts the [com.bios.app.alerts.UrgentAckTimeoutWorker] may surface
     * for an alert at [tierLevel]. Filters by per-contact tier opt-in and
     * by non-null ack-timeout.
     */
    suspend fun eligibleForTier(tierLevel: Int): List<EmergencyContact> =
        dao.fetchEligibleForTier(tierLevel)

    suspend fun count(): Int = dao.count()

    companion object {

        /** Clamp owner-supplied tier floor to ADVISORY..URGENT inclusive. */
        fun clampTier(level: Int): Int = level.coerceIn(
            EmergencyContact.MIN_TIER_ADVISORY,
            EmergencyContact.MIN_TIER_URGENT,
        )

        /**
         * Clamp owner-supplied timeout to a sensible window. Null passes
         * through (escalation off for this contact).
         */
        fun clampTimeout(minutes: Int?): Int? = minutes?.coerceIn(
            EmergencyContact.MIN_ACK_TIMEOUT_MIN,
            EmergencyContact.MAX_ACK_TIMEOUT_MIN,
        )
    }
}
