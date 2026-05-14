package com.bios.app.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// `defaultValue` and the `state` index match what MIGRATION_5_6 emits — without
// these, Room's schema check crashes on every upgrade from v5 to v6 because the
// on-disk table has the default + index but the entity declares neither.
@Entity(
    tableName = "companion_grants",
    indices = [Index(value = ["state"])],
)
data class CompanionGrant(
    @PrimaryKey val packageName: String,
    val state: String,
    val firstSeenAt: Long,
    val grantedAt: Long? = null,
    val revokedAt: Long? = null,
    val lastAccessAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val accessCount: Long = 0,
) {
    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_GRANTED = "GRANTED"
        const val STATE_REVOKED = "REVOKED"
    }
}
