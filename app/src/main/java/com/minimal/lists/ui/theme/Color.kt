package com.minimal.lists.ui.theme

import androidx.compose.ui.graphics.Color
import com.minimal.lists.data.model.Category

// Official Material Design 3 Tone-Based Dark Tokens (Tone 6 Surface, Tone 10 ContainerLow for Elevated Cards)
val StitchPitchBlack = Color(0xFF141218)
val StitchDarkSurface = Color(0xFF141218)
val StitchDarkSurfaceContainerLowest = Color(0xFF0F0D13)
val StitchDarkSurfaceContainerLow = Color(0xFF1D1B20)
val StitchDarkSurfaceContainer = Color(0xFF211F26)
val StitchDarkSurfaceContainerHigh = Color(0xFF2B2930)
val StitchDarkSurfaceContainerHighest = Color(0xFF36343B)
val StitchDarkSurfaceVariant = Color(0xFF49454F)

val StitchDarkOnSurface = Color(0xFFE6E0E9)
val StitchDarkOnSurfaceVariant = Color(0xFFCAC4D0)
val StitchDarkOutline = Color(0xFF938F99)
val StitchDarkOutlineVariant = Color(0xFF49454F)

val StitchDarkPrimary = Color(0xFFD0BCFF)
val StitchDarkOnPrimary = Color(0xFF381E72)
val StitchDarkPrimaryContainer = Color(0xFF4F378B)
val StitchDarkOnPrimaryContainer = Color(0xFFEADDFF)

val StitchDarkSecondary = Color(0xFFCCC2DC)
val StitchDarkOnSecondary = Color(0xFF332D41)
val StitchDarkSecondaryContainer = Color(0xFF4A4458)
val StitchDarkOnSecondaryContainer = Color(0xFFE8DEF8)

val StitchDarkError = Color(0xFFFFB4AB)
val StitchDarkOnError = Color(0xFF690005)

// Official Material Design 3 Tone-Based Light Tokens (Tone 98 Surface, Tone 96 ContainerLow for Elevated Cards)
val StitchLightBackground = Color(0xFFFAF8FD)
val StitchLightSurface = Color(0xFFFAF8FD)
val StitchLightSurfaceContainerLowest = Color(0xFFFFFFFF)
val StitchLightSurfaceContainerLow = Color(0xFFF7F2FA)
val StitchLightSurfaceContainer = Color(0xFFF3EDF7)
val StitchLightSurfaceContainerHigh = Color(0xFFECE6F0)
val StitchLightSurfaceContainerHighest = Color(0xFFE6E0E9)

val StitchLightOnSurface = Color(0xFF1D1B20)
val StitchLightOnSurfaceVariant = Color(0xFF49454F)
val StitchLightOutline = Color(0xFF79747E)
val StitchLightOutlineVariant = Color(0xFFCAC4D0)

val StitchLightPrimary = Color(0xFF6750A4)
val StitchLightOnPrimary = Color(0xFFFFFFFF)
val StitchLightPrimaryContainer = Color(0xFFEADDFF)
val StitchLightOnPrimaryContainer = Color(0xFF21005D)

// Material 3 Accent Colors - Dynamic Inversion between Dark and Light Modes
enum class AppAccentColor(
    val id: String,
    val displayName: String,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val previewColor: Color
) {
    LAVENDER(
        id = "lavender",
        displayName = "Lavender",
        darkPrimary = Color(0xFFD0BCFF),
        darkOnPrimary = Color(0xFF381E72),
        darkPrimaryContainer = Color(0xFFD0BCFF),
        darkOnPrimaryContainer = Color(0xFF381E72),
        lightPrimary = Color(0xFF6750A4),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFF6750A4),
        lightOnPrimaryContainer = Color(0xFFFFFFFF),
        previewColor = Color(0xFFD0BCFF)
    ),
    OCEAN(
        id = "ocean",
        displayName = "Ocean Blue",
        darkPrimary = Color(0xFFA8C7FA),
        darkOnPrimary = Color(0xFF00325B),
        darkPrimaryContainer = Color(0xFFA8C7FA),
        darkOnPrimaryContainer = Color(0xFF00325B),
        lightPrimary = Color(0xFF0061A4),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFF0061A4),
        lightOnPrimaryContainer = Color(0xFFFFFFFF),
        previewColor = Color(0xFFA8C7FA)
    ),
    MINT(
        id = "mint",
        displayName = "Mint Green",
        darkPrimary = Color(0xFFA8DAB5),
        darkOnPrimary = Color(0xFF003919),
        darkPrimaryContainer = Color(0xFFA8DAB5),
        darkOnPrimaryContainer = Color(0xFF003919),
        lightPrimary = Color(0xFF1E6C3A),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFF1E6C3A),
        lightOnPrimaryContainer = Color(0xFFFFFFFF),
        previewColor = Color(0xFFA8DAB5)
    ),
    CORAL(
        id = "coral",
        displayName = "Coral Peach",
        darkPrimary = Color(0xFFFFB4A9),
        darkOnPrimary = Color(0xFF561E18),
        darkPrimaryContainer = Color(0xFFFFB4A9),
        darkOnPrimaryContainer = Color(0xFF561E18),
        lightPrimary = Color(0xFF9C4136),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFF9C4136),
        lightOnPrimaryContainer = Color(0xFFFFFFFF),
        previewColor = Color(0xFFFFB4A9)
    ),
    AMBER(
        id = "amber",
        displayName = "Warm Amber",
        darkPrimary = Color(0xFFFFDF9E),
        darkOnPrimary = Color(0xFF452B00),
        darkPrimaryContainer = Color(0xFFFFDF9E),
        darkOnPrimaryContainer = Color(0xFF452B00),
        lightPrimary = Color(0xFF815600),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFF815600),
        lightOnPrimaryContainer = Color(0xFFFFFFFF),
        previewColor = Color(0xFFFFDF9E)
    ),
    ROSE(
        id = "rose",
        displayName = "Rose Pink",
        darkPrimary = Color(0xFFFFB1C8),
        darkOnPrimary = Color(0xFF541D32),
        darkPrimaryContainer = Color(0xFFFFB1C8),
        darkOnPrimaryContainer = Color(0xFF541D32),
        lightPrimary = Color(0xFF8E3756),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFF8E3756),
        lightOnPrimaryContainer = Color(0xFFFFFFFF),
        previewColor = Color(0xFFFFB1C8)
    ),
    MONOCHROME(
        id = "monochrome",
        displayName = "Black & White",
        darkPrimary = Color(0xFFFFFFFF),
        darkOnPrimary = Color(0xFF000000),
        darkPrimaryContainer = Color(0xFFFFFFFF),
        darkOnPrimaryContainer = Color(0xFF000000),
        lightPrimary = Color(0xFF000000),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFF000000),
        lightOnPrimaryContainer = Color(0xFFFFFFFF),
        previewColor = Color(0xFFFFFFFF)
    );

    val primary: Color get() = darkPrimary
    val onPrimary: Color get() = darkOnPrimary
    val primaryContainer: Color get() = darkPrimaryContainer
    val onPrimaryContainer: Color get() = darkOnPrimaryContainer

    companion object {
        fun fromId(id: String?): AppAccentColor {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: LAVENDER
        }
    }
}

// Category Neutral Grey helper (no neon or category-specific colors)
fun getCategoryColor(category: Category): Color = Color(0xFF8E8E93)
