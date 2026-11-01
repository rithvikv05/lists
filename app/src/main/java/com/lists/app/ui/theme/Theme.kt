package com.lists.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PureBlack.toArgb()
            window.navigationBarColor = PureBlack.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = MinimalDarkColorScheme,
        content = content
    )
}

@Deprecated("Use ListsTheme instead", ReplaceWith("ListsTheme(darkTheme, content)"))
@Composable
fun ListyTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) = ListsTheme(darkTheme, content)
