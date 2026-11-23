package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LiquidGlassDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = MidnightDeep,
    primaryContainer = IndigoAccent,
    onPrimaryContainer = TextPrimary,
    secondary = LuminousPurple,
    onSecondary = MidnightDeep,
    secondaryContainer = MidnightViolet,
    onSecondaryContainer = TextPrimary,
    tertiary = SkyBlue,
    background = MidnightDeep,
    onBackground = TextPrimary,
    surface = GlassSurface,
    onSurface = TextPrimary,
    surfaceVariant = GlassSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = GlassStrokeBright,
    outlineVariant = GlassStrokeSubtle,
    error = CoralDanger,
    onError = MidnightDeep
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun WebVisitTesterTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LiquidGlassDarkColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
