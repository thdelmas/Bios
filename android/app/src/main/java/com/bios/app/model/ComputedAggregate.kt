package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "computed_aggregates",
    indices = [Index("metricType", "period", "periodStart", unique = true)]
)
data class ComputedAggregate(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val metricType: String,
    val period: String,
    val periodStart: Long,
    val mean: Double,
    val median: Double,
    val min: Double,
    val max: Double,
    val stdDev: Double,
    val p5: Double,
    val p95: Double,
    val sampleCount: Int,
    val primarySourceId: String? = null
)
