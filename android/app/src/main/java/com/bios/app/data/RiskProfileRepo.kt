package com.bios.app.data

import com.bios.app.model.RiskProfile

/**
 * Repository for the owner's single-row risk profile (#160).
 *
 * The screening-cadence engine (#155) reads this to compute risk-
 * adjusted cadences (e.g. colorectal screening from age 40 instead of
 * 45 with first-degree family history). The pattern-explanation builder
 * reads this to add context to alert text. Both integrations are
 * follow-up PRs; this PR ships the data + UI surface.
 */
class RiskProfileRepo(private val db: BiosDatabase) {

    private val dao get() = db.riskProfileDao()

    suspend fun save(profile: RiskProfile) {
        dao.save(profile.copy(id = RiskProfile.SINGLETON_ID, updatedAt = System.currentTimeMillis()))
    }

    suspend fun fetch(): RiskProfile? = dao.fetch()

    suspend fun fetchOrEmpty(): RiskProfile = dao.fetch() ?: RiskProfile()

    suspend fun clear() = dao.clear()
}
