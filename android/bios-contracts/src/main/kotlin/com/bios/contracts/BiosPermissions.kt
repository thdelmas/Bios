package com.bios.contracts

/**
 * Permission names declared by Bios's manifest and required by companions to
 * talk to [BiosHealthProvider]. Both are `dangerous` protection level — the
 * OS prompts the owner before granting.
 *
 * A companion declaring [READ_HEALTH] and/or [WRITE_COMPANION] satisfies the
 * Android-layer gate; Bios's per-package allowlist (Settings → Companion Apps)
 * is the second gate. See `docs/PRIVACY_ARCHITECTURE.md` for the rationale.
 */
object BiosPermissions {
    const val READ_HEALTH = "com.bios.app.permission.READ_HEALTH"
    const val WRITE_COMPANION = "com.bios.app.permission.WRITE_COMPANION"
}
