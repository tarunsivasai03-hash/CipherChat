package com.cipherchat.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cipherchat.data.ThemeMode

/**
 * Warm paper-and-ink palette from the Anthropic design reference (hex values taken from it).
 * The reference is achromatic: the brand colour is ink itself. Emerald survives only as the logo colour and as a
 * tiny status accent ([CipherColors.accent]); it is never used for buttons, links or fills.
 */
object Palette {
    val Parchment = Color(0xFFFAF9F5)
    val ClayCream = Color(0xFFF0EEE6)
    val Tan = Color(0xFFE3DACC)
    val Ink = Color(0xFF141413)
    val InkSoft = Color(0xFF30302E)
    val InkRaised = Color(0xFF3D3D3A)
    val Driftwood = Color(0xFF5E5D59)
    val Stone = Color(0xFF73726C)
    val Pebble = Color(0xFF87867F)
    val Ash = Color(0xFFB0AEA5)
    val Hairline = Color(0xFFD1CFC5)
    val Emerald = Color(0xFF2ECC8F)
    val EmeraldDeep = Color(0xFF0B7A55)

    // Dark theme: the original CipherChat charcoal-green family with the emerald accent.
    val Charcoal = Color(0xFF111513)
    val CharcoalSurface = Color(0xFF1A1F1C)
    val CharcoalHigh = Color(0xFF232A26)
    val CharcoalHighest = Color(0xFF2C3530)
    val MintText = Color(0xFFE4EAE6)
    val MintMuted = Color(0xFFB4BFB8)
    val EmeraldContainer = Color(0xFF14513B)
    val EmeraldOnContainer = Color(0xFFBDF3DC)
    val EmeraldInk = Color(0xFF06281C)
}

/** Colours the Material scheme has no slot for: glass layers, bubbles, backdrop blobs, status accent. */
@Immutable
class CipherColors(
    val isDark: Boolean,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val hairline: Color,
    val bubbleOut: Color,
    val onBubbleOut: Color,
    val blobs: List<Color>,
    val accent: Color,
    val warning: Color,
)

private val LightExtras = CipherColors(
    isDark = false,
    glassFill = Color.White.copy(alpha = 0.55f),
    glassFillStrong = Color.White.copy(alpha = 0.74f),
    glassBorderTop = Color.White.copy(alpha = 0.95f),
    glassBorderBottom = Palette.Hairline.copy(alpha = 0.75f),
    hairline = Palette.Hairline,
    bubbleOut = Palette.Ink,
    onBubbleOut = Palette.Parchment,
    blobs = listOf(Palette.Tan.copy(alpha = 0.95f), Color(0xFFEADFCB).copy(alpha = 0.9f), Palette.Emerald.copy(alpha = 0.16f)),
    accent = Palette.EmeraldDeep,
    warning = Color(0xFF8A5A1C),
)

private val DarkExtras = CipherColors(
    isDark = true,
    glassFill = Color.White.copy(alpha = 0.07f),
    glassFillStrong = Color.White.copy(alpha = 0.12f),
    glassBorderTop = Color.White.copy(alpha = 0.24f),
    glassBorderBottom = Color.White.copy(alpha = 0.05f),
    hairline = Color(0xFF3A453F),
    bubbleOut = Palette.EmeraldContainer,
    onBubbleOut = Palette.EmeraldOnContainer,
    blobs = listOf(Palette.EmeraldContainer.copy(alpha = 0.55f), Palette.CharcoalHighest.copy(alpha = 0.9f), Palette.Emerald.copy(alpha = 0.16f)),
    accent = Palette.Emerald,
    warning = Color(0xFFE3B97A),
)

val LocalCipherColors = staticCompositionLocalOf { LightExtras }

object AppTheme {
    val colors: CipherColors
        @Composable @ReadOnlyComposable get() = LocalCipherColors.current
}

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Palette.Ink,
    onPrimary = Palette.Parchment,
    primaryContainer = Palette.ClayCream,
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.Driftwood,
    onSecondary = Palette.Parchment,
    secondaryContainer = Color(0xFFE8E6DC),
    onSecondaryContainer = Palette.Ink,
    tertiary = Color(0xFF8A5A1C),
    onTertiary = Palette.Parchment,
    tertiaryContainer = Palette.Tan,
    onTertiaryContainer = Palette.Ink,
    background = Palette.Parchment,
    onBackground = Palette.Ink,
    surface = Palette.Parchment,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.ClayCream,
    onSurfaceVariant = Palette.Driftwood,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F4ED),
    surfaceContainer = Palette.ClayCream,
    surfaceContainerHigh = Color(0xFFE8E6DC),
    surfaceContainerHighest = Palette.Tan,
    outline = Palette.Pebble,
    outlineVariant = Palette.Hairline,
    error = Color(0xFFB3402F),
    onError = Color.White,
    errorContainer = Color(0xFFF3DDD8),
    onErrorContainer = Color(0xFF5A1A12),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Palette.Emerald,
    onPrimary = Palette.EmeraldInk,
    primaryContainer = Palette.EmeraldContainer,
    onPrimaryContainer = Palette.EmeraldOnContainer,
    secondary = Color(0xFF8FB7A5),
    onSecondary = Palette.EmeraldInk,
    secondaryContainer = Color(0xFF26352D),
    onSecondaryContainer = Color(0xFFD3E8DD),
    tertiary = Color(0xFFE3B97A),
    onTertiary = Color(0xFF2B1D00),
    tertiaryContainer = Color(0xFF4A3410),
    onTertiaryContainer = Color(0xFFFFE3B0),
    background = Palette.Charcoal,
    onBackground = Palette.MintText,
    surface = Palette.Charcoal,
    onSurface = Palette.MintText,
    surfaceVariant = Palette.CharcoalHigh,
    onSurfaceVariant = Palette.MintMuted,
    surfaceContainerLowest = Color(0xFF0D100E),
    surfaceContainerLow = Palette.CharcoalSurface,
    surfaceContainer = Palette.CharcoalSurface,
    surfaceContainerHigh = Palette.CharcoalHigh,
    surfaceContainerHighest = Palette.CharcoalHighest,
    outline = Color(0xFF7D8A82),
    outlineVariant = Color(0xFF3A453F),
    error = Color(0xFFE88A7C),
    onError = Color(0xFF3B0A06),
    errorContainer = Color(0xFF5C1B16),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Radius scale from the reference: radius grows with object size. No pills. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** 4 px base grid. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

@Composable
fun CipherChatTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalCipherColors provides if (dark) DarkExtras else LightExtras) {
        val scheme = if (dark) DarkScheme else LightScheme
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes) {
            // Without this, any Text that does not name a colour falls back to black (there is no Surface at the root).
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
        }
    }
}
