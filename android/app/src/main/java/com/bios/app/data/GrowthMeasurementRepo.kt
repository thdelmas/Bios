package com.bios.app.data

import com.bios.app.model.GrowthMeasurement
import java.util.UUID

/**
 * Thin repository over [com.bios.app.data.dao.GrowthMeasurementDao] for the
 * body measurements screen (#199). [record] derives BMI from height and
 * weight and generates the UUID.
 */
class GrowthMeasurementRepo(private val db: BiosDatabase) {

    private val dao get() = db.growthMeasurementDao()

    /**
     * Records a new anthropometric measurement event.
     *
     * @param ageInDays the owner's age in days at [timestamp]; 0 when not
     *   entered.
     */
    suspend fun record(
        timestamp: Long = System.currentTimeMillis(),
        ageInDays: Int = 0,
        heightCm: Float? = null,
        weightKg: Float? = null,
        headCircumferenceCm: Float? = null,
        leanBodyMassKg: Float? = null,
        fatMassKg: Float? = null,
        note: String? = null,
    ): GrowthMeasurement {
        require(heightCm != null || weightKg != null || headCircumferenceCm != null ||
            leanBodyMassKg != null || fatMassKg != null) {
            "GrowthMeasurement must carry at least one anthropometric value"
        }
        val bmi = GrowthMeasurement.bmiFrom(heightCm, weightKg)
        val measurement = GrowthMeasurement(
            id = UUID.randomUUID().toString(),
            timestamp = timestamp,
            ageInDays = ageInDays.coerceAtLeast(0),
            heightCm = heightCm,
            weightKg = weightKg,
            headCircumferenceCm = headCircumferenceCm,
            bmiKgPerM2 = bmi,
            leanBodyMassKg = leanBodyMassKg,
            fatMassKg = fatMassKg,
            note = note?.takeIf { it.isNotBlank() }?.trim(),
        )
        dao.insert(measurement)
        return measurement
    }

    suspend fun update(measurement: GrowthMeasurement) {
        // Recompute BMI on update — if the owner corrects height or weight,
        // the derived value should track the inputs.
        val withBmi = measurement.copy(
            bmiKgPerM2 = GrowthMeasurement.bmiFrom(measurement.heightCm, measurement.weightKg),
        )
        dao.update(withBmi)
    }

    suspend fun remove(id: String) {
        val existing = dao.fetchById(id) ?: return
        dao.delete(existing)
    }

    suspend fun fetchAll(): List<GrowthMeasurement> = dao.fetchAll()
}
