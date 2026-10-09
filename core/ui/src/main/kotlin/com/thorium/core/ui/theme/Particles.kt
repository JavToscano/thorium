package com.thorium.core.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Particle(
    val x: Float, val y: Float, val size: Float, val speed: Float,
    val swayAmp: Float, val swayFreq: Float, val phase: Float,
    val spin: Float, val flip: Float, val color: Color,
)

/** A unit-sized cherry-blossom petal with the small notch at its tip. */
private val PetalPath = Path().apply {
    moveTo(0f, -0.8f)
    cubicTo(0.25f, -1.15f, 0.95f, -0.9f, 0.9f, -0.2f)
    cubicTo(0.85f, 0.45f, 0.35f, 0.95f, 0f, 1f)
    cubicTo(-0.35f, 0.95f, -0.85f, 0.45f, -0.9f, -0.2f)
    cubicTo(-0.95f, -0.9f, -0.25f, -1.15f, 0f, -0.8f)
    close()
}

/**
 * Drifting particles over the background: petals that tumble and sway, snow, or soft rising orbs.
 * Positions are computed from the clock inside the draw step, so nothing recomposes per frame.
 */
@Composable
fun ParticleField(spec: ParticleSpec, modifier: Modifier = Modifier) {
    val particles = remember(spec) {
        val random = Random(1234)
        List(spec.count) {
            Particle(
                x = random.nextFloat(), y = random.nextFloat(),
                size = spec.sizeMin + random.nextFloat() * (spec.sizeMax - spec.sizeMin).coerceAtLeast(0),
                speed = 0.6f + random.nextFloat() * 0.8f,
                swayAmp = 0.012f + random.nextFloat() * 0.03f,
                swayFreq = 0.5f + random.nextFloat() * 0.9f,
                phase = random.nextFloat() * (2 * PI).toFloat(),
                spin = (random.nextFloat() - 0.5f) * 90f,
                flip = 0.6f + random.nextFloat() * 1.2f,
                color = Color(spec.colors[random.nextInt(spec.colors.size)]),
            )
        }
    }
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) androidx.compose.runtime.withFrameNanos { clock.floatValue = it / 1_000_000_000f }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = clock.floatValue
        val density = this.density
        particles.forEach { p ->
            val fall = (p.y + t * 0.03f * spec.speed * p.speed) % 1f
            val sway = sin(t * p.swayFreq + p.phase) * p.swayAmp * spec.sway
            val wind = t * 0.006f * spec.speed
            val px = (((p.x + sway + wind) % 1f) + 1f) % 1f * size.width
            when (spec.kind) {
                ParticleKind.Petal -> drawPetal(px, fall * (size.height + 80f) - 40f, p.size * density, p, t)
                ParticleKind.Snow -> drawCircle(p.color.copy(alpha = 0.8f), p.size * density * 0.22f, Offset(px, fall * size.height))
                ParticleKind.Orb -> {
                    val y = (1f - fall) * size.height
                    val r = p.size * density
                    drawCircle(
                        Brush.radialGradient(listOf(p.color.copy(alpha = 0.35f), Color.Transparent), center = Offset(px, y), radius = r),
                        r, Offset(px, y),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawPetal(x: Float, y: Float, size: Float, p: Particle, t: Float) {
    // The petal turns on its own axis, so its width shrinks and grows like a real one tumbling.
    val flipScale = cos(t * p.flip + p.phase).let { if (kotlin.math.abs(it) < 0.25f) 0.25f * (if (it < 0) -1 else 1) else it }
    translate(x, y) {
        rotate(p.spin * t * 0.35f + p.phase * 20f, pivot = Offset.Zero) {
            scale(size * flipScale, size, pivot = Offset.Zero) {
                drawPath(PetalPath, p.color.copy(alpha = 0.85f))
                scale(0.55f, 0.55f, pivot = Offset.Zero) { drawPath(PetalPath, Color.White.copy(alpha = 0.22f)) }
            }
        }
    }
}
