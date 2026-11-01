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
fun ListsTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MinimalDarkColorScheme,
        content = content
    )
}

@Deprecated("Renamed to ListsTheme", ReplaceWith("ListsTheme(darkTheme, content)"))
@Composable
fun ListyTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) = ListsTheme(darkTheme, content)
