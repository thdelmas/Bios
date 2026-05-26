package com.bios.app.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import com.bios.app.model.AlertTier

/**
 * Compose mappings of the ecosystem semantic tokens
 * (see docs/DESIGN_SYSTEM.md and docs/design-tokens/).
 *
 * Universal palette values come from `colors.yaml`; Material 3's
 * `colorScheme.error` is reused for the error role so the platform
 * default carries through. Apps that fork their own palette should
 * override these via the Bios theme rather than redefining values
 * at call sites.
 */
object BiosTokens {

    private val Success = Color(0xFF00E5A0)
    private val Warning = Color(0xFFFFB830)
    // Bios-specific extension of the canonical role set: a deep amber for the
    // tier between warning and error. Lets the 4-tier AlertTier and 4-band
    // probability gradients stay legible without collapsing two tiers onto
    // one color. The design system permits app-level role additions
    // (docs/DESIGN_SYSTEM.md §2).
    private val Attention = Color(0xFFFF8A00)
    private val Pending = Color(0xFFFFF4DA)

    val success: Color
        @Composable
        @ReadOnlyComposable
        get() = Success

    val warning: Color
        @Composable
        @ReadOnlyComposable
        get() = Warning

    val attention: Color
        @Composable
        @ReadOnlyComposable
        get() = Attention

    val error: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.error

    val pending: Color
        @Composable
        @ReadOnlyComposable
        get() = Pending

    val muted: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    /**
     * Instrument-readout style — monospace, +0.04 letter-spacing,
     * mandatory for data-bearing text (times, durations, quantities,
     * biomarker values, scores). Never for body copy or anything the
     * owner wrote themselves.
     */
    val instrument: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalTextStyle.current.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.04.em,
        )

    /**
     * Canonical color for an AlertTier. Shared by AlertCard and TimelineScreen
     * so the same anomaly reads the same wherever it surfaces.
     */
    @Composable
    @ReadOnlyComposable
    fun tierColor(tier: AlertTier): Color = when (tier) {
        AlertTier.OBSERVATION -> muted
        AlertTier.NOTICE -> warning
        AlertTier.ADVISORY -> attention
        AlertTier.URGENT -> error
    }

    /**
     * Canonical color for a diagnostic match probability. Shared by
     * DiagnosticCard and ConditionDetailScreen so a given probability
     * reads the same on the card and on the detail screen.
     */
    @Composable
    @ReadOnlyComposable
    fun probabilityColor(probability: Double): Color = when {
        probability >= 0.75 -> error
        probability >= 0.50 -> attention
        probability >= 0.25 -> warning
        else -> success
    }
}
