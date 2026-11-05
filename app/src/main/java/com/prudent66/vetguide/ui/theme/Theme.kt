package com.prudent66.vetguide.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * VETGUIDE brand theme. Dynamic color is intentionally disabled: the forest
 * green brand palette must be stable across devices and clinical contexts.
 */
@Composable
fun VETGUIDETheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

private val LightColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = White,
    primaryContainer = ForestGreenContainer,
    onPrimaryContainer = OnForestGreenContainer,
    secondary = SageGreen,
    onSecondary = White,
    secondaryContainer = SageGreenContainer,
    onSecondaryContainer = OnSageGreenContainer,
    tertiary = EarthAmber,
    onTertiary = White,
    tertiaryContainer = EarthAmberContainer,
    onTertiaryContainer = OnEarthAmberContainer,
)

private val DarkColors = darkColorScheme(
    primary = ForestGreenLight,
    onPrimary = ForestGreenDark,
    primaryContainer = ForestGreen,
    onPrimaryContainer = ForestGreenContainer,
    secondary = SageGreenContainer,
    onSecondary = OnSageGreenContainer,
    secondaryContainer = SageGreen,
    onSecondaryContainer = SageGreenContainer,
    tertiary = EarthAmberContainer,
    onTertiary = OnEarthAmberContainer,
    tertiaryContainer = EarthAmber,
    onTertiaryContainer = EarthAmberContainer,
    background = BarkDark,
    onBackground = OnBarkDark,
    surface = BarkDark,
    onSurface = OnBarkDark,
    surfaceDim = BarkSurfaceDim,
    surfaceContainer = BarkSurfaceContainer,
)
