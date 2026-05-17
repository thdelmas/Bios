package com.bios.app.provider

import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Resolves the SHA-256 of an installed package's signing certificate, returned
 * as lowercase hex. Returns null when the package is uninstalled or unsigned.
 *
 * Wrapped behind an interface so [BiosHealthProvider]'s cert-pin path is
 * testable on the JVM without an Android `PackageManager` — tests inject a
 * map-backed fake; the production wiring uses [PackageManagerCertResolver].
 */
internal interface CallerCertResolver {
    fun signingCertSha256(packageName: String): String?
}

internal class PackageManagerCertResolver(
    private val pm: PackageManager,
) : CallerCertResolver {

    @Suppress("NewApi") // minSdk 28 = always covered; the version branch keeps lint happy
    override fun signingCertSha256(packageName: String): String? = try {
        val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signingInfo = info.signingInfo ?: return null
        // `apkContentsSigners` is the post-rotation v3 result: when a key has
        // been rotated, this returns the current signer only. Fall back to
        // `signingCertificateHistory` for builds still using the older
        // v1/v2-signed-only release flow (no rotation, single cert in history).
        val signers = signingInfo.apkContentsSigners
            ?: signingInfo.signingCertificateHistory
            ?: return null
        val first = signers.firstOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(first.toByteArray())
        digest.joinToString("") { "%02x".format(it) }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    companion object {
        // Referenced only to keep Build.VERSION_CODES.P explicitly tied to the
        // minSdk floor — if minSdk ever drops below 28, the GET_SIGNING_CERTIFICATES
        // flag and `apkContentsSigners` API need a guarded older-API fallback.
        @Suppress("unused")
        private const val REQUIRES_API = Build.VERSION_CODES.P
    }
}
