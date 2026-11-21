package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Owner- (or proxy-) recorded body measurement (#199): height, weight,
 * head circumference, lean and fat mass, taken together as one event.
 *
 * **Why a dedicated entity** (rather than separate [MetricReading] rows).
 * A measurement session is one event: the owner steps on a scale and is
 * measured against a wall. Tying the values together, and the derived BMI
 * alongside them, preserves that.
 *
 * **Storage shape**. Mainline encrypted DB.
 *
 * @param id stable UUID for the measurement event.
 * @param timestamp epoch millis the measurement was taken.
 * @param ageInDays owner's age in days at [timestamp], as entered; 0 when
 *   not entered.
 * @param heightCm length / standing height in cm.
 * @param weightKg body weight in kg.
 * @param headCircumferenceCm head circumference in cm.
 * @param bmiKgPerM2 derived BMI. Computed by [bmiFrom] when [heightCm] and
 *   [weightKg] are both present; persisted to keep historical trends
 *   stable if the derivation formula ever changes. Null when either input
 *   is missing.
 * @param leanBodyMassKg lean body mass (kg), e.g. from a smart scale.
 *   Null when not measured.
 * @param fatMassKg fat mass (kg), companion to [leanBodyMassKg].
 * @param growthChartReference column written by earlier versions. Kept so
 *   stored rows stay readable; no longer written or shown.
 * @param note free-text owner annotation. Never parsed.
 */
@Entity(
    tableName = "growth_measurements",
    indices = [Index("timestamp"), Index("ageInDays")],
)
data class GrowthMeasurement(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val ageInDays: Int,
    val heightCm: Float? = null,
    val weightKg: Float? = null,
    val headCircumferenceCm: Float? = null,
    val bmiKgPerM2: Float? = null,
    val leanBodyMassKg: Float? = null,
    val fatMassKg: Float? = null,
    val growthChartReference: String? = null,
    val note: String? = null,
) {
    companion object {
        /**
         * Pure BMI derivation. Returns null when either input is missing
         * or non-positive. Extracted from the data-class constructor so
         * it can be unit-tested without a DB and so callers (the
         * repository, the manual-entry surface) share one definition.
         *
         * BMI = weight (kg) / height (m)². Height is expressed in
         * centimetres on input; the conversion to metres is part of this
         * helper.
         */
        fun bmiFrom(heightCm: Float?, weightKg: Float?): Float? {
            if (heightCm == null || weightKg == null) return null
            if (heightCm <= 0f || weightKg <= 0f) return null
            val heightM = heightCm / 100f
            return weightKg / (heightM * heightM)
        }
    }
}
