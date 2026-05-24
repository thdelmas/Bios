package com.bios.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Bios identity hue is Material teal — the quiet, instrument-room register
// for the ecosystem hub (docs/design-tokens/colors.yaml: apps.bios).
val BiosTeal = Color(0xFF009688)
val BiosTealDark = Color(0xFF26A69A)

private val DarkColorScheme = darkColorScheme(
    primary = BiosTealDark,
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFF4DD0E1)
)

private val LightColorScheme = lightColorScheme(
    primary = BiosTeal,
    secondary = Color(0xFF26A69A),
    tertiary = Color(0xFF00BCD4)
)

@Composable
fun BiosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
