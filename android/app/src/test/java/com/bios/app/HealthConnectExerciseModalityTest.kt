package com.bios.app

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.bios.app.ingest.HealthConnectAdapter
import com.bios.app.model.ExerciseModality
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the Health Connect → ExerciseModality mapping. The bucketing is
 * coarse on purpose (CARDIO / STRENGTH / INTERVAL / MOBILITY / OTHER) so
 * the pattern engine can reason about aerobic-vs-anaerobic-vs-mixed load
 * without depending on HC's full sport taxonomy.
 *
 * If HC adds a new exercise type that should map to a non-OTHER bucket,
 * the test for that bucket goes here. Anything not explicitly mapped
 * lands in OTHER — that's the correct default (the engine still sees a
 * session, just without a coarse modality cue).
 */
class HealthConnectExerciseModalityTest {

    @Test
    fun cardio_bucket_covers_endurance_modalities() {
        val cardio = listOf(
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL,
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE,
        )
        for (t in cardio) {
            assertEquals(
                "Exercise type $t should map to CARDIO",
                ExerciseModality.CARDIO,
                HealthConnectAdapter.mapExerciseTypeToModality(t)
            )
        }
    }

    @Test
    fun strength_bucket_covers_resistance_modalities() {
        val strength = listOf(
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
        )
        for (t in strength) {
            assertEquals(
                "Exercise type $t should map to STRENGTH",
                ExerciseModality.STRENGTH,
                HealthConnectAdapter.mapExerciseTypeToModality(t)
            )
        }
    }

    @Test
    fun interval_bucket_covers_hiit() {
        assertEquals(
            ExerciseModality.INTERVAL,
            HealthConnectAdapter.mapExerciseTypeToModality(
                ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
            )
        )
    }

    @Test
    fun mobility_bucket_covers_low_intensity_modalities() {
        val mobility = listOf(
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
            ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING,
        )
        for (t in mobility) {
            assertEquals(
                "Exercise type $t should map to MOBILITY",
                ExerciseModality.MOBILITY,
                HealthConnectAdapter.mapExerciseTypeToModality(t)
            )
        }
    }

    @Test
    fun unknown_or_unmapped_exercise_types_fall_to_other() {
        // EXERCISE_TYPE_OTHER_WORKOUT is the explicit HC catch-all;
        // 0 is a sentinel that shouldn't show up in real records.
        assertEquals(
            ExerciseModality.OTHER,
            HealthConnectAdapter.mapExerciseTypeToModality(
                ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
            )
        )
        assertEquals(
            ExerciseModality.OTHER,
            HealthConnectAdapter.mapExerciseTypeToModality(0)
        )
    }
}
