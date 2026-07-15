package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = JunoPrimary,
    onPrimary = JunoBackground,
    secondary = JunoSecondary,
    onSecondary = JunoTextPrimary,
    tertiary = JunoTertiary,
    onTertiary = JunoBackground,
    background = JunoBackground,
    onBackground = JunoTextPrimary,
    surface = JunoSurface,
    onSurface = JunoTextPrimary,
    surfaceVariant = JunoSurfaceVariant,
    onSurfaceVariant = JunoTextSecondary,
    error = JunoDanger,
    onError = JunoTextPrimary,
    outline = JunoBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark industrial theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
