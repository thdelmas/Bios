package com.bios.app

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import com.bios.app.ingest.HealthConnectAdapter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Older Health Connect builds (Android 14 platform HC) do not know skin
 * temperature or background read. Requesting them there can never be granted,
 * and onboarding requires the full requested set: the owner was stuck.
 */
class HealthConnectPermissionsTest {

    private val skin = HealthPermission.getReadPermission(SkinTemperatureRecord::class)
    private val background = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    private val heartRate = HealthPermission.getReadPermission(HeartRateRecord::class)

    @Test
    fun `unsupported optional features are not requested`() {
        val perms = HealthConnectAdapter.permissionsFor(skinTempSupported = false, backgroundReadSupported = false)
        assertFalse(skin in perms)
        assertFalse(background in perms)
        assertTrue(heartRate in perms)
    }

    @Test
    fun `supported optional features are requested`() {
        val perms = HealthConnectAdapter.permissionsFor(skinTempSupported = true, backgroundReadSupported = true)
        assertTrue(skin in perms)
        assertTrue(background in perms)
    }
}
