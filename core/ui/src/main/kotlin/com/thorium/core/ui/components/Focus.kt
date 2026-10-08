package com.thorium.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.thorium.core.ui.theme.Palette

/** Animated scale applied to the focused item (springy, so focus changes feel alive). */
@Composable
fun focusScale(focused: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.10f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "scale",
    )
    return scale
}

/** Animated border color: accent when focused, transparent otherwise. */
@Composable
fun focusBorder(focused: Boolean): Color {
    val color by animateColorAsState(if (focused) Palette.Accent else Color.Transparent, label = "border")
    return color
}
