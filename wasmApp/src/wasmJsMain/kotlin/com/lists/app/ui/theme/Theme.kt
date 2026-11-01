package com.lists.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MinimalDarkColorScheme = darkColorScheme(
    primary = TextWhite,
    onPrimary = PureBlack,
    primaryContainer = DarkCardElevated,
    onPrimaryContainer = TextWhite,
    secondary = TextGrayLight,
    onSecondary = PureBlack,
    background = PureBlack,
    onBackground = TextWhite,
    surface = DarkCardBg,
    onSurface = TextWhite,
    surfaceVariant = DarkCardElevated,
    onSurfaceVariant = TextGrayMedium,
    outline = DarkBorder
)

@Composable
fun ListyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MinimalDarkColorScheme,
        content = content
    )
}
