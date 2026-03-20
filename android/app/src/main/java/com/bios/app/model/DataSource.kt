package com.bios.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "data_sources")
data class DataSource(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceType: String,
    val deviceName: String? = null,
    val deviceModel: String? = null,
    val sensorType: String,
    val connectedAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long = System.currentTimeMillis()
)
