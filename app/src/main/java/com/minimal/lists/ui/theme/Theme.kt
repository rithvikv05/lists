package com.minimal.lists.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

fun getDarkColorScheme(accent: AppAccentColor = AppAccentColor.LAVENDER) = darkColorScheme(
    primary = accent.darkPrimary,
    onPrimary = accent.darkOnPrimary,
    primaryContainer = accent.darkPrimaryContainer,
    onPrimaryContainer = accent.darkOnPrimaryContainer,
    secondary = StitchDarkSecondary,
    onSecondary = StitchDarkOnSecondary,
    secondaryContainer = StitchDarkSecondaryContainer,
    onSecondaryContainer = StitchDarkOnSecondaryContainer,
    background = StitchPitchBlack,
    onBackground = StitchDarkOnSurface,
    surface = StitchDarkSurface,
    onSurface = StitchDarkOnSurface,
    surfaceVariant = StitchDarkSurfaceVariant,
    onSurfaceVariant = StitchDarkOnSurfaceVariant,
    surfaceContainerLowest = StitchDarkSurfaceContainerLowest,
    surfaceContainerLow = StitchDarkSurfaceContainerLow,
    surfaceContainer = StitchDarkSurfaceContainer,
    surfaceContainerHigh = StitchDarkSurfaceContainerHigh,
    surfaceContainerHighest = StitchDarkSurfaceContainerHighest,
    outline = StitchDarkOutline,
    outlineVariant = StitchDarkOutlineVariant,
    error = StitchDarkError,
    onError = StitchDarkOnError
)

fun getLightColorScheme(accent: AppAccentColor = AppAccentColor.LAVENDER) = lightColorScheme(
    primary = accent.lightPrimary,
    onPrimary = accent.lightOnPrimary,
    primaryContainer = accent.lightPrimaryContainer,
    onPrimaryContainer = accent.lightOnPrimaryContainer,
    background = StitchLightBackground,
    onBackground = StitchLightOnSurface,
    surface = StitchLightSurface,
    onSurface = StitchLightOnSurface,
    surfaceVariant = StitchLightSurfaceContainerHigh,
    onSurfaceVariant = StitchLightOnSurfaceVariant,
    surfaceContainerLowest = StitchLightSurfaceContainerLowest,
    surfaceContainerLow = StitchLightSurfaceContainerLow,
    surfaceContainer = StitchLightSurfaceContainer,
    surfaceContainerHigh = StitchLightSurfaceContainerHigh,
    surfaceContainerHighest = StitchLightSurfaceContainerHighest,
    outline = StitchLightOutline,
    outlineVariant = StitchLightOutlineVariant
)

enum class AppThemeMode(val id: String, val displayName: String) {
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    AUTO("auto", "System");

    companion object {
        fun fromId(id: String?): AppThemeMode {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: AUTO
        }
    }
}

val LocalAppAccent = androidx.compose.runtime.staticCompositionLocalOf { AppAccentColor.LAVENDER }

@Composable
fun ListsTheme(
    darkTheme: Boolean = true,
    accentColor: AppAccentColor = AppAccentColor.LAVENDER,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) getDarkColorScheme(accentColor) else getLightColorScheme(accentColor)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAppAccent provides accentColor
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}
