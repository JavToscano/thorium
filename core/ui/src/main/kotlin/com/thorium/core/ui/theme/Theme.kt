package com.thorium.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.dp
import java.io.File

/** Images, font and sound files of the active theme, loaded from its folder. */
class ThemeAssets(
    /** Background layers, in the order of [BackgroundSpec.layers] (a layer that failed to load is null). */
    val layers: List<ImageBitmap?>,
    /** Tab icons by tab id. */
    val icons: Map<String, ImageBitmap>,
    val fontFamily: FontFamily?,
    /** Sound files by event name, ready for the sound player. */
    val soundFiles: Map<String, File>,
) {
    companion object {
        val Empty = ThemeAssets(emptyList(), emptyMap(), null, emptyMap())
    }
}

/** The colors of the active theme as Compose values. */
class ColorTokens(spec: ColorSpec) {
    val backgroundTop = Color(spec.backgroundTop)
    val backgroundBottom = Color(spec.backgroundBottom)
    val background: Brush = Brush.verticalGradient(listOf(backgroundTop, backgroundBottom))
    val accent = Color(spec.accent)
    val accentAlt = Color(spec.accentAlt)
    val panel = Color(spec.panel)
    val panelFocused = Color(spec.panelFocused)
    val textPrimary = Color(spec.textPrimary)
    val textSecondary = Color(spec.textSecondary)
    val success = Color(spec.success)
    val danger = Color(spec.danger)
    val warning = Color(spec.warning)
    val onAccent = Color(spec.onAccent)
}

/**
 * The active theme. Everything that reads it from a composable (through [Palette], [ThemeShapes] or
 * [ThemeState.spec]) recomposes by itself when the theme changes.
 */
object ThemeState {
    var spec by mutableStateOf(ThemeSpec.Default); private set
    var tokens by mutableStateOf(ColorTokens(ThemeSpec.Default.colors)); private set
    var assets by mutableStateOf(ThemeAssets.Empty); private set

    /** Turns off particles, pulses and shine (movement itself stays); a user preference. */
    var animations by mutableStateOf(true)

    fun apply(spec: ThemeSpec, assets: ThemeAssets = ThemeAssets.Empty) {
        this.spec = spec
        this.tokens = ColorTokens(spec.colors)
        this.assets = assets
    }
}

/** Colors of the active theme. */
object Palette {
    val Background: Brush get() = ThemeState.tokens.background
    val BackgroundTop: Color get() = ThemeState.tokens.backgroundTop
    val BackgroundBottom: Color get() = ThemeState.tokens.backgroundBottom
    val Accent: Color get() = ThemeState.tokens.accent
    val AccentAlt: Color get() = ThemeState.tokens.accentAlt
    val Panel: Color get() = ThemeState.tokens.panel
    val PanelFocused: Color get() = ThemeState.tokens.panelFocused
    val TextPrimary: Color get() = ThemeState.tokens.textPrimary
    val TextSecondary: Color get() = ThemeState.tokens.textSecondary
    val Success: Color get() = ThemeState.tokens.success
    val Danger: Color get() = ThemeState.tokens.danger
    val Warning: Color get() = ThemeState.tokens.warning
    val OnAccent: Color get() = ThemeState.tokens.onAccent
}

/** Corner radii and border width of the active theme. */
object ThemeShapes {
    val Card: RoundedCornerShape get() = RoundedCornerShape(ThemeState.spec.shapes.cardRadius.dp)
    val Panel: RoundedCornerShape get() = RoundedCornerShape(ThemeState.spec.shapes.panelRadius.dp)
    val Border get() = ThemeState.spec.shapes.border.dp
}

object Dimens {
    val CardWidth = 108.dp
    val CardHeight = 144.dp
    val CardSpacing = 14.dp
    val ScreenPadding = 24.dp
}

@Composable
fun ThoriumTheme(content: @Composable () -> Unit) {
    val font = ThemeState.assets.fontFamily ?: FontFamily.Default
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Palette.Accent, background = Palette.BackgroundBottom),
    ) {
        // Plain Text() reads LocalTextStyle, so providing the font here changes every label.
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(fontFamily = font))) {
            content()
        }
    }
}
