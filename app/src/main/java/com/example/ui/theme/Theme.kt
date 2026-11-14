package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color(0xFF04131E),
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanPrimary,
    secondary = AmberSecondary,
    onSecondary = Color(0xFF1E1403),
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberSecondary,
    tertiary = EmeraldTertiary,
    onTertiary = Color(0xFF041C13),
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldTertiary,
    background = StudioBackground,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder,
    error = CoralError,
    onError = Color(0xFF280808)
)

val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun GeminiStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content
    )
}
