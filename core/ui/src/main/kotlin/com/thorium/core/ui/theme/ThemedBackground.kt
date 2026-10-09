package com.thorium.core.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale

/** The theme's backdrop: gradient, image layers and an edge vignette, with [content] on top. */
@Composable
fun ThemedBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val spec = ThemeState.spec
    val assets = ThemeState.assets
    Box(modifier.fillMaxSize().background(Palette.Background)) {
        assets.layers.forEach { layer ->
            if (layer != null) {
                Image(layer, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
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
