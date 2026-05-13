package com.bios.app.provider

import com.bios.app.data.dao.CompanionGrantDao
import com.bios.app.model.CompanionGrant

/**
 * Owner-controlled gate for companion access to BiosHealthProvider.
 *
 * Android-level permissions (READ_HEALTH / WRITE_COMPANION) are a coarse filter.
 * This gate is the fine filter: every read/write must come from a package the
 * owner has explicitly approved in the Bios consent UI. Unknown packages are
 * recorded as PENDING so the owner can review and approve them later.
 *
 * State transitions are owner-driven (slice 2 wires this to a Settings screen).
 * This class is pure logic — no Android dependencies — so it can be unit-tested
 * without instrumentation.
 */
class CompanionGate(
    private val dao: CompanionGrantDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val onFirstPending: (String) -> Unit = {}
) {

    sealed interface Decision {
        object Allow : Decision
        data class Deny(val reason: Reason) : Decision

        enum class Reason { PENDING_APPROVAL, REVOKED, NO_CALLING_PACKAGE }
    }

    /**
     * Check whether [callingPackage] is allowed to access the provider.
     * Records access bookkeeping (lastAccessAt, accessCount) as a side effect
     * when the call is allowed; records a PENDING row on first-seen denial.
     */
    suspend fun check(callingPackage: String?): Decision {
        if (callingPackage.isNullOrBlank()) {
            return Decision.Deny(Decision.Reason.NO_CALLING_PACKAGE)
        }

        val existing = dao.find(callingPackage)
        val now = clock()

        return when (existing?.state) {
            CompanionGrant.STATE_GRANTED -> {
                dao.recordAccess(callingPackage, now)
                Decision.Allow
            }
            CompanionGrant.STATE_REVOKED -> {
                Decision.Deny(Decision.Reason.REVOKED)
            }
            CompanionGrant.STATE_PENDING -> {
                dao.upsert(existing.copy(
                    lastAccessAt = now,
                    accessCount = existing.accessCount + 1
                ))
                Decision.Deny(Decision.Reason.PENDING_APPROVAL)
            }
            null -> {
                dao.upsert(CompanionGrant(
                    packageName = callingPackage,
                    state = CompanionGrant.STATE_PENDING,
                    firstSeenAt = now,
                    lastAccessAt = now,
                    accessCount = 1
                ))
                onFirstPending(callingPackage)
                Decision.Deny(Decision.Reason.PENDING_APPROVAL)
            }
            else -> Decision.Deny(Decision.Reason.PENDING_APPROVAL)
        }
    }

    /** Owner approves a package — called from the consent UI. */
    suspend fun approve(pkg: String) {
        val now = clock()
        val existing = dao.find(pkg)
        dao.upsert((existing ?: CompanionGrant(
            packageName = pkg,
            state = CompanionGrant.STATE_PENDING,
            firstSeenAt = now
        )).copy(
            state = CompanionGrant.STATE_GRANTED,
            grantedAt = now,
            revokedAt = null
        ))
    }

    /** Owner revokes a previously granted package. */
    suspend fun revoke(pkg: String) {
        val existing = dao.find(pkg) ?: return
        dao.upsert(existing.copy(
            state = CompanionGrant.STATE_REVOKED,
            revokedAt = clock()
        ))
    }
}
