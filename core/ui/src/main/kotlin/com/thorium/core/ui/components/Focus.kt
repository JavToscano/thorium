package com.thorium.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thorium.core.ui.theme.Palette
import com.thorium.core.ui.theme.ThemeState

/** 0 when not focused, 1 when focused, with a springy overshoot so focus changes feel alive. */
@Composable
fun focusProgress(focused: Boolean): Float {
    val progress by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "focus",
    )
    return progress
}

/** Animated scale applied to the focused item, with the strength the theme asks for. */
@Composable
fun focusScale(focused: Boolean): Float {
    val max = ThemeState.spec.motion.focusScale
    return 1f + (max - 1f) * focusProgress(focused)
}

/** Animated border color: accent when focused, transparent otherwise. */
@Composable
fun focusBorder(focused: Boolean): Color {
    val color by animateColorAsState(if (focused) Palette.Accent else Color.Transparent, label = "border")
    return color
}

/** Border brush that fades from transparent to the accent gradient as an item gains focus. */
@Composable
fun focusBorderBrush(focused: Boolean): Brush {
    val p = focusProgress(focused).coerceIn(0f, 1f)
    return Brush.linearGradient(listOf(Palette.Accent.copy(alpha = p), Palette.AccentAlt.copy(alpha = p)))
}

/** A short squeeze on the focused item each time the player confirms something. */
@Composable
private fun pressScale(focused: Boolean): Float {
    val scale = remember { Animatable(1f) }
    val pulse = ThemeState.pulse
    LaunchedEffect(pulse) {
        if (focused && pulse > 0 && ThemeState.animations) {
            scale.snapTo(0.93f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    return scale.value
}

/** Grows and lifts the item when focused, and squeezes it when the player presses A. */
@Composable
fun Modifier.focusLayer(focused: Boolean): Modifier {
    val motion = ThemeState.spec.motion
    val progress = focusProgress(focused)
    val press = pressScale(focused)
    return graphicsLayer {
        val s = (1f + (motion.focusScale - 1f) * progress) * press
        scaleX = s
        scaleY = s
        translationY = -motion.focusLift.dp.toPx() * progress
    }
}

/** A soft accent halo behind the item; it breathes slowly while focused. */
@Composable
fun Modifier.focusGlow(focused: Boolean, corner: Dp): Modifier {
    val motion = ThemeState.spec.motion
    if (!motion.glow) return this
    val progress = focusProgress(focused)
    val pulse = if (focused && motion.glowPulse && ThemeState.animations) {
        val transition = rememberInfiniteTransition(label = "glow")
        val value by transition.animateFloat(
            initialValue = 0.6f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glowPulse",
        )
        value
    } else {
        1f
    }
    val color = Palette.Accent
    return drawBehind {
        if (progress > 0.01f) {
            val layers = 7
            for (i in layers downTo 1) {
                val spread = i * 2.4.dp.toPx()
                drawRoundRect(
                    color = color.copy(alpha = (0.075f * progress * pulse).coerceIn(0f, 1f)),
                    topLeft = Offset(-spread, -spread),
                    size = Size(size.width + 2 * spread, size.height + 2 * spread),
                    cornerRadius = CornerRadius(corner.toPx() + spread),
                )
            }
        }
    }
}

/** A diagonal band of light that sweeps across the item once when it gains focus. */
@Composable
fun Modifier.focusShine(focused: Boolean): Modifier {
    if (!ThemeState.spec.motion.shine || !ThemeState.animations) return this
    val sweep = remember { Animatable(1f) }
    LaunchedEffect(focused) {
        if (focused) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
    }
    return drawWithContent {
        drawContent()
        val t = sweep.value
        if (t > 0.01f && t < 0.99f) {
            val band = size.width * 0.45f
            val x = -band + (size.width + 2 * band) * t
            rotate(degrees = 18f, pivot = center) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.30f), 1f to Color.Transparent,
                        startX = x, endX = x + band,
                    ),
                    topLeft = Offset(x, -size.height * 0.3f),
                    size = Size(band, size.height * 1.6f),
                )
            }
        }
    }
}

/** Slides and fades an item in when it first appears, one after another by [index]. */
@Composable
fun Modifier.entrance(index: Int): Modifier {
    if (!ThemeState.spec.motion.entrance || !ThemeState.animations) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 380, delayMillis = (index.coerceAtMost(9)) * 45, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 22.dp.toPx()
    }
}

/** Linear 0..1 clock that repeats every [millis]; drives ambient motion such as drifting light. */
@Composable
fun rememberLoop(millis: Int): Float {
    val transition = rememberInfiniteTransition(label = "loop")
    val value by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing)),
        label = "loopValue",
    )
    return value
}
