package com.backpapp.hanative.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val background = Color(0xFF0D1117)
val surface = Color(0xFF161B22)
val surfaceElevated = Color(0xFF1C2333)
val surfaceActive = Color(0xFF2D1E06)
val accent = Color(0xFFF59E0B)
val textPrimary = Color(0xFFE6EDF3)
val textSecondary = Color(0x80E6EDF3)
val connected = Color(0xFF3FB950)
val border = Color(0xFF21262D)
val toggleOff = Color(0xFF30363D)

val HaNativeColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = background,
    primaryContainer = surfaceActive,
    onPrimaryContainer = accent,
    secondary = connected,
    onSecondary = background,
    secondaryContainer = surfaceElevated,
    onSecondaryContainer = textPrimary,
    tertiary = textSecondary,
    onTertiary = background,
    tertiaryContainer = surfaceElevated,
    onTertiaryContainer = textPrimary,
    background = background,
    onBackground = textPrimary,
    surface = surface,
    onSurface = textPrimary,
    surfaceVariant = surfaceElevated,
    onSurfaceVariant = textSecondary,
    surfaceTint = accent,
    inverseSurface = textPrimary,
    inverseOnSurface = background,
    inversePrimary = surfaceActive,
    error = Color(0xFFFF453A),
    onError = background,
    errorContainer = Color(0xFF3A0D0A),
    onErrorContainer = Color(0xFFFF453A),
    outline = border,
    outlineVariant = toggleOff,
    scrim = Color(0xCC0D1117),
)
