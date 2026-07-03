package com.linguabridge.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// "Warm reading room": Anthropic-inspired paper-and-clay palette.
// Ivory paper surfaces, Book Cloth terracotta as the single strong accent,
// kraft and manilla as supporting warmth, sage for success, slate for ink.

private val BookCloth = Color(0xFFCC785C)
private val BookClothDeep = Color(0xFF9A4B31)
private val BookClothPale = Color(0xFFF3E0D8)
private val Kraft = Color(0xFFD4A27F)
private val Manilla = Color(0xFFEBDBBC)
private val Sage = Color(0xFF6E7F5C)
private val SagePale = Color(0xFFE3E8D7)
private val Ivory = Color(0xFFFAF9F5)
private val IvoryDim = Color(0xFFF0EEE6)
private val Ink = Color(0xFF191919)
private val InkSoft = Color(0xFF4A4740)
private val ClayRed = Color(0xFFBF4D43)

private val LightColors = lightColorScheme(
    primary = BookCloth,
    onPrimary = Color.White,
    primaryContainer = BookClothPale,
    onPrimaryContainer = Color(0xFF55291B),
    secondary = Color(0xFF9C8468),
    onSecondary = Color.White,
    secondaryContainer = Manilla,
    onSecondaryContainer = Color(0xFF4A3A24),
    tertiary = Sage,
    onTertiary = Color.White,
    tertiaryContainer = SagePale,
    onTertiaryContainer = Color(0xFF313D26),
    error = ClayRed,
    onError = Color.White,
    errorContainer = Color(0xFFF7DCD8),
    onErrorContainer = Color(0xFF6B241D),
    background = Ivory,
    onBackground = Ink,
    surface = Ivory,
    onSurface = Ink,
    surfaceVariant = IvoryDim,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F5F0),
    surfaceContainer = Color(0xFFF3F1EA),
    surfaceContainerHigh = IvoryDim,
    surfaceContainerHighest = Color(0xFFEAE7DC),
    outline = Color(0xFFB9B3A4),
    outlineVariant = Color(0xFFE1DDD0),
    inverseSurface = Color(0xFF32302B),
    inverseOnSurface = Ivory,
    inversePrimary = Kraft,
    scrim = Color(0xFF000000),
)

// Dark theme: candle-lit study — warm charcoal, never pure black.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFE49B7F),
    onPrimary = Color(0xFF44200F),
    primaryContainer = Color(0xFF6E3A26),
    onPrimaryContainer = Color(0xFFF6DED4),
    secondary = Kraft,
    onSecondary = Color(0xFF3F2E1B),
    secondaryContainer = Color(0xFF574330),
    onSecondaryContainer = Manilla,
    tertiary = Color(0xFFAABB92),
    onTertiary = Color(0xFF25301A),
    tertiaryContainer = Color(0xFF46523A),
    onTertiaryContainer = SagePale,
    error = Color(0xFFE8887D),
    onError = Color(0xFF491511),
    errorContainer = Color(0xFF6E2B24),
    onErrorContainer = Color(0xFFF7DCD8),
    background = Color(0xFF1E1D1A),
    onBackground = Color(0xFFECE9E1),
    surface = Color(0xFF1E1D1A),
    onSurface = Color(0xFFECE9E1),
    surfaceVariant = Color(0xFF2C2B26),
    onSurfaceVariant = Color(0xFFCBC6B9),
    surfaceContainerLowest = Color(0xFF191815),
    surfaceContainerLow = Color(0xFF232220),
    surfaceContainer = Color(0xFF272623),
    surfaceContainerHigh = Color(0xFF2C2B26),
    surfaceContainerHighest = Color(0xFF34322D),
    outline = Color(0xFF57544A),
    outlineVariant = Color(0xFF3B3933),
    inverseSurface = Color(0xFFECE9E1),
    inverseOnSurface = Color(0xFF2C2B26),
    inversePrimary = BookClothDeep,
    scrim = Color(0xFF000000),
)

// Soft "bound book" corners: generous but not bubbly.
private val LinguaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun LinguaBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LinguaTypography,
        shapes = LinguaShapes,
        content = content,
    )
}
