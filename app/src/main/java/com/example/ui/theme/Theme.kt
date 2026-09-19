package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FootyColorScheme = darkColorScheme(
    primary = FootyAccentGreen,
    onPrimary = Color.Black,
    secondary = FootyAccentGreenDark,
    onSecondary = FootyAccentGreen,
    background = FootyPitchBlack,
    onBackground = FootyTextPrimary,
    surface = FootySurfaceDark,
    onSurface = FootyTextPrimary,
    surfaceVariant = FootySurfaceSubtle,
    onSurfaceVariant = FootyTextSecondary,
    outline = FootySurfaceBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = FootyColorScheme,
        typography = Typography,
        content = content
    )
}
