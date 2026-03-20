package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "personal_baselines",
    indices = [Index("metricType", "context", unique = true)]
)
data class PersonalBaseline(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val metricType: String,
    val context: String = BaselineContext.ALL.name,
    val windowDays: Int = 14,
    val computedAt: Long = System.currentTimeMillis(),
    val mean: Double,
    val stdDev: Double,
    val p5: Double,
    val p95: Double,
    val trend: String = TrendDirection.STABLE.name,
    val trendSlope: Double = 0.0
) {
    fun zScore(value: Double): Double {
        if (stdDev <= 0.0) return 0.0
        return (value - mean) / stdDev
    }
}
