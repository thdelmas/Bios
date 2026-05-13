package com.bios.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "companion_grants")
data class CompanionGrant(
    @PrimaryKey val packageName: String,
    val state: String,
    val firstSeenAt: Long,
    val grantedAt: Long? = null,
    val revokedAt: Long? = null,
    val lastAccessAt: Long? = null,
    val accessCount: Long = 0
) {
    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_GRANTED = "GRANTED"
        const val STATE_REVOKED = "REVOKED"
    }
}
