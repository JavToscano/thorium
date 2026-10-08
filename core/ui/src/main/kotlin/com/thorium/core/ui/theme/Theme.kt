package com.thorium.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Palette {
    val Background = Brush.verticalGradient(listOf(Color(0xFF10131C), Color(0xFF0A0C12)))
    val Accent = Color(0xFF4FC3F7)
    val Panel = Color(0xFF1B2030)
    val TextPrimary = Color(0xFFF2F4F8)
    val TextSecondary = Color(0xFF9AA3B5)
}

object Dimens {
    val CardWidth = 108.dp
    val CardHeight = 144.dp
    val CardSpacing = 14.dp
    val ScreenPadding = 24.dp
}

@Composable
fun ThoriumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Palette.Accent, background = Color(0xFF0A0C12)),
        content = content,
    )
}
