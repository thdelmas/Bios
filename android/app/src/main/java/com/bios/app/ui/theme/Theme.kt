package com.bios.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val BiosPink = Color(0xFFE91E63)
val BiosPinkDark = Color(0xFFF48FB1)

private val DarkColorScheme = darkColorScheme(
    primary = BiosPinkDark,
    secondary = Color(0xFFCE93D8),
    tertiary = Color(0xFF80CBC4)
)

private val LightColorScheme = lightColorScheme(
    primary = BiosPink,
    secondary = Color(0xFF9C27B0),
    tertiary = Color(0xFF009688)
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
