package com.cipherchat.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Glassmorphism, applied the way the reference restricts depth: only to layers that float above the page
 * (bars, composer, sheets, popups). In-flow content stays flat and hairline-ruled.
 *
 * Real backdrop blur (Haze) on Android 12+; older versions fall back to a near-opaque tint, never to fake blur.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Page chrome shared by every screen: ambient backdrop + the blur source that glass layers sample. */
@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    val haze = rememberHazeState()
    CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize()) {
            AmbientBackdrop(Modifier.fillMaxSize().hazeSource(haze))
            content()
        }
    }
}

/** Mark scrolling/in-flow content so glass bars blur it as it passes underneath. */
@Composable
fun Modifier.glassSource(): Modifier {
    val haze = LocalHazeState.current
    return if (haze != null) this.hazeSource(haze) else this
}

enum class GlassLevel { Regular, Strong }

@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(16.dp),
    level: GlassLevel = GlassLevel.Regular,
    border: Boolean = true,
): Modifier {
    val c = AppTheme.colors
    val haze = LocalHazeState.current
    val fill = if (level == GlassLevel.Strong) c.glassFillStrong else c.glassFill
    val solid = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    val clipped = this.clip(shape)
    val blurred = if (haze != null) {
        clipped.hazeEffect(
            state = haze,
            style = HazeStyle(
                backgroundColor = MaterialTheme.colorScheme.surface,
                tint = HazeTint(fill),
                blurRadius = 28.dp,
                noiseFactor = 0.035f,
                fallbackTint = HazeTint(solid),
            ),
        )
    } else {
        clipped.background(solid)
    }
    return if (border) blurred.border(1.dp, Brush.verticalGradient(listOf(c.glassBorderTop, c.glassBorderBottom)), shape) else blurred
}

/** Slow drifting colour fields behind everything; this is what the glass has to refract. */
@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val base = MaterialTheme.colorScheme.background
    val t = if (LocalMotionEnabled.current) {
        val transition = rememberInfiniteTransition(label = "ambient")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift").value
    } else 0.5f
    Canvas(modifier) {
        drawRect(base)
        val w = size.width
        val h = size.height
        val r = maxOf(w, h)
        fun blob(color: Color, cx: Float, cy: Float, radius: Float) = drawCircle(
            brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = Offset(cx, cy), radius = radius),
            radius = radius,
            center = Offset(cx, cy),
        )
        blob(c.blobs[0], w * (0.15f + 0.25f * t), h * (0.12f + 0.10f * t), r * 0.62f)
        blob(c.blobs[1], w * (0.95f - 0.30f * t), h * (0.55f + 0.08f * t), r * 0.55f)
        blob(c.blobs[2], w * (0.30f + 0.20f * t), h * (0.92f - 0.12f * t), r * 0.50f)
    }
}

/**
 * The CipherChat mark: a speech bubble (circle with one sharp corner) plus the same bubble rotated about the
 * canvas centre. At [rotation] = 180 it is exactly the logo (`branding/logo/masters/cipherchat-symbol.svg`);
 * animating 0 -> 180 plays the ROT13 idea. Even-odd fill keeps the overlap a real hole.
 */
@Composable
fun BubbleMark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface, rotation: Float = 180f) {
    Canvas(modifier) {
        val s = size.minDimension / 256f
        val first = bubblePath(100f, 96f, 56f)
        val second = bubblePath(100f, 96f, 56f).also {
            val m = Matrix()
            m.translate(128f, 128f)
            m.rotateZ(rotation)
            m.translate(-128f, -128f)
            it.transform(m)
        }
        val both = Path().apply {
            fillType = PathFillType.EvenOdd
            addPath(first)
            addPath(second)
        }
        scale(s, s, pivot = Offset.Zero) { drawPath(both, color) }
    }
}

private fun bubblePath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    moveTo(cx - r, cy + r)
    lineTo(cx - r, cy)
    arcTo(Rect(cx - r, cy - r, cx + r, cy + r), 180f, 270f, false)
    close()
}
