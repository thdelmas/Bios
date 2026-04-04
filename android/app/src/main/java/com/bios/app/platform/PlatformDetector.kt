package com.bios.app.platform

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Detects the runtime platform: LETHE (embedded system app) vs stock Android.
 *
 * Detection order:
 * 1. LETHE system property (ro.lethe.version) — set by LETHE overlay
 * 2. LETHE agent package presence (org.osmosis.lethe.agent)
 * 3. Fallback: stock Android
 */
object PlatformDetector {

    private var cachedPlatform: Platform? = null

    fun detect(context: Context): Platform {
        cachedPlatform?.let { return it }

        val platform = when {
            hasLetheSystemProperty() -> Platform.LETHE
            hasLetheAgent(context) -> Platform.LETHE
            else -> Platform.STOCK_ANDROID
        }

        cachedPlatform = platform
        return platform
    }

    fun isLethe(context: Context): Boolean = detect(context) == Platform.LETHE

    fun capabilities(context: Context): PlatformCapabilities {
        return when (detect(context)) {
            Platform.LETHE -> PlatformCapabilities(
                hasAgentIpc = true,
                hasWipeSignals = true,
                hasLauncherCards = true,
                hasOtaCoordination = true,
                hasSystemSensorAccess = true,
                hasHealthConnect = isHealthConnectAvailable(context),
                hasTorTransparency = true,
                hasPlayServices = false
            )
            Platform.STOCK_ANDROID -> PlatformCapabilities(
                hasAgentIpc = false,
                hasWipeSignals = false,
                hasLauncherCards = false,
                hasOtaCoordination = false,
                hasSystemSensorAccess = false,
                hasHealthConnect = isHealthConnectAvailable(context),
                hasTorTransparency = false,
                hasPlayServices = isPlayServicesAvailable(context)
            )
        }
    }

    private fun hasLetheSystemProperty(): Boolean {
        return try {
            val value = System.getProperty("ro.lethe.version")
            !value.isNullOrEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun hasLetheAgent(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(
                "org.osmosis.lethe.agent",
                PackageManager.GET_META_DATA
            )
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isHealthConnectAvailable(context: Context): Boolean {
        return try {
            val status = androidx.health.connect.client.HealthConnectClient.getSdkStatus(context)
            status == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE
        } catch (_: Exception) {
            false
        }
    }

    private fun isPlayServicesAvailable(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(
                "com.google.android.gms",
                PackageManager.GET_META_DATA
            )
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}

enum class Platform {
    LETHE,
    STOCK_ANDROID
}

data class PlatformCapabilities(
    val hasAgentIpc: Boolean,
    val hasWipeSignals: Boolean,
    val hasLauncherCards: Boolean,
    val hasOtaCoordination: Boolean,
    val hasSystemSensorAccess: Boolean,
    val hasHealthConnect: Boolean,
    val hasTorTransparency: Boolean,
    val hasPlayServices: Boolean
)
