package com.thorium.core.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.thorium.core.ui.components.rememberLoop
import kotlin.math.PI
import kotlin.math.sin

/**
 * The theme's backdrop with [content] on top: gradient, slowly moving ambient light, image layers
 * that shift a little as [position] (0 to 1, for instance the selected tab) changes, drifting
 * particles and a vignette. [ambient] turns the moving light and particles off (a second screen can stay calm).
 */
@Composable
fun ThemedBackground(
    modifier: Modifier = Modifier,
    position: Float = 0f,
    ambient: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = ThemeState.spec
    val assets = ThemeState.assets
    val animated = ThemeState.animations
    val smooth by animateFloatAsState(position, tween(spec.motion.tabSlideMs.coerceAtLeast(1) + 200, easing = FastOutSlowInEasing), label = "parallax")
    val loop = if (animated && spec.background.gradientShift) rememberLoop(36_000) else 0f

    Box(
        modifier.fillMaxSize().background(Palette.Background).drawBehind {
            if (spec.background.gradientShift) {
                val angle = (loop * 2 * PI).toFloat()
                drawRect(
                    Brush.radialGradient(
                        listOf(Palette.Accent.copy(alpha = 0.13f), Color.Transparent),
                        center = Offset(size.width * (0.22f + 0.08f * sin(angle)), size.height * 0.18f),
                        radius = size.width * 0.55f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Palette.AccentAlt.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * (0.82f - 0.07f * sin(angle)), size.height * 0.88f),
                        radius = size.width * 0.5f,
                    ),
                )
            }
        },
    ) {
        spec.background.layers.forEachIndexed { index, layer ->
            val image = assets.layers.getOrNull(index) ?: return@forEachIndexed
            Image(
                image, contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    // Drawn 14% larger than the screen: 7% of slack per side covers the parallax (up to 4%)
                    // plus the slow drift (up to 2.5%), so shifting never exposes an edge.
                    scaleX = 1.14f
                    scaleY = 1.14f
                    val drift = if (animated) sin(loop * 2 * PI).toFloat() * layer.drift * size.width * 0.025f else 0f
                    translationX = (0.5f - smooth.coerceIn(0f, 1f)) * 2f * layer.parallax * size.width * 0.04f + drift
                },
                contentScale = ContentScale.Crop,
            )
        }
        if (ambient && animated) spec.particles?.takeIf { it.count > 0 }?.let { ParticleField(it) }
        if (spec.background.vignette > 0f) {
            val strength = spec.background.vignette
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    drawRect(
                        Brush.radialGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = strength),
                            center = center,
                            radius = size.maxDimension * 0.75f,
                        ),
                    )
                },
            )
        }
        content()
    }
}
