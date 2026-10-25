package com.cipherchat.core.designsystem

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The reference system's motion is restrained; these curves/springs are shared so everything moves alike. */
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** False when the user turned system animations off (Settings > Accessibility > Remove animations). */
val LocalMotionEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }.getOrDefault(true)
    }
}

/** Gentle press feedback: shrinks slightly while held, springs back on release. */
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressed: Float = 0.96f): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Clickable row/card with the press feedback and the ripple replaced by the scale. */
fun Modifier.clickableScale(onClick: () -> Unit, enabled: Boolean = true): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this.pressScale(source, 0.98f).clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** Fades and lifts an element in once when it first appears. [index] staggers siblings. */
fun Modifier.appear(index: Int = 0, offsetDp: Int = 14): Modifier = composed {
    val motion = LocalMotionEnabled.current
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) {
            delay((index.coerceAtMost(8) * 35).toLong())
            progress.animateTo(1f, tween(380, easing = EmphasizedEasing))
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetDp.dp.toPx()
    }
}

/** Animated skeleton block shown while a list loads. */
fun Modifier.shimmer(shape: Shape): Modifier = composed {
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val hi = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val x = if (LocalMotionEnabled.current) {
        rememberInfiniteTransition(label = "shimmer").animateFloat(
            -400f, 1200f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "x",
        ).value
    } else 0f
    this.clip(shape).drawBehind {
        drawRect(Brush.linearGradient(listOf(base, hi, base), start = Offset(x, 0f), end = Offset(x + 400f, 200f)))
    }
}

/**
 * Text that "decodes" into its final value: random glyphs resolve left to right. Used for cipher output.
 * The final text is always what is shown once the effect finishes (and immediately if motion is off).
 */
@Composable
fun ScrambleText(target: String, style: TextStyle, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    var shown by remember { mutableStateOf(target) }
    val motion = LocalMotionEnabled.current
    LaunchedEffect(target) {
        if (!motion || target.length < 2) {
            shown = target
            return@LaunchedEffect
        }
        val glyphs = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&*+?"
        val steps = 14
        for (step in 1..steps) {
            val resolved = target.length * step / steps
            shown = buildString {
                for ((i, ch) in target.withIndex()) {
                    append(if (i < resolved || ch.isWhitespace() || !ch.isLetterOrDigit()) ch else glyphs.random())
                }
            }
            delay(26)
        }
        shown = target
    }
    Text(shown, modifier = modifier, style = style, color = color)
}

/** Three softly pulsing dots ("someone is typing"). */
@Composable
fun TypingDots(modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val a by transition.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(520, delayMillis = i * 140, easing = LinearEasing), RepeatMode.Reverse),
                label = "dot$i",
            )
            Spacer(Modifier.size(4.dp))
            Spacer(Modifier.size(6.dp).alpha(a).background(color, CircleShape))
        }
    }
}
