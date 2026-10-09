package com.thorium.core.ui.theme

/**
 * Everything a theme can change, resolved to concrete values. Colors are ARGB integers so this file
 * stays plain Kotlin (the parser is tested on the JVM); the UI turns them into Compose colors.
 */
data class ThemeSpec(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val version: Int,
    val colors: ColorSpec,
    val shapes: ShapeSpec,
    val motion: MotionSpec,
    val background: BackgroundSpec,
    val particles: ParticleSpec?,
    /** Keyed by tab id: home, systems, favorites, downloads, settings. */
    val tabs: Map<String, TabSpec>,
    /** Event name to file path inside the theme folder. */
    val sounds: Map<String, String>,
    val font: FontSpec?,
) {
    companion object {
        /** The look Thorium ships with; every other theme only overrides what it wants to change. */
        val Default = ThemeSpec(
            id = "thorium",
            name = "Thorium",
            author = "Thorium",
            description = "",
            version = 1,
            colors = ColorSpec(
                backgroundTop = 0xFF0E1020, backgroundBottom = 0xFF07080F,
                accent = 0xFF5CC8FF, accentAlt = 0xFF9B8CFF,
                panel = 0xFF1A2033, panelFocused = 0xFF26324D,
                textPrimary = 0xFFF2F4F8, textSecondary = 0xFF9AA3B5,
                success = 0xFF81C784, danger = 0xFFE57373, warning = 0xFFFFD54F,
                onAccent = 0xFF06101A,
            ),
            shapes = ShapeSpec(cardRadius = 12, panelRadius = 12, border = 3),
            motion = MotionSpec(
                focusScale = 1.10f, focusLift = 4, glow = true, glowPulse = true, shine = true,
                entrance = true, tabSlideMs = 260, pageMs = 240,
            ),
            background = BackgroundSpec(layers = emptyList(), vignette = 0.35f, gradientShift = true),
            particles = null,
            tabs = emptyMap(),
            sounds = emptyMap(),
            font = null,
        )
    }
}

data class ColorSpec(
    val backgroundTop: Long, val backgroundBottom: Long,
    val accent: Long, val accentAlt: Long,
    val panel: Long, val panelFocused: Long,
    val textPrimary: Long, val textSecondary: Long,
    val success: Long, val danger: Long, val warning: Long,
    /** Text drawn on top of an accent-colored surface. */
    val onAccent: Long,
)

data class ShapeSpec(val cardRadius: Int, val panelRadius: Int, val border: Int)

data class MotionSpec(
    val focusScale: Float,
    /** How far (dp) the focused card rises. */
    val focusLift: Int,
    val glow: Boolean,
    val glowPulse: Boolean,
    val shine: Boolean,
    /** Cards slide in one after another when a screen opens. */
    val entrance: Boolean,
    val tabSlideMs: Int,
    val pageMs: Int,
)

data class BackgroundLayer(val image: String, val parallax: Float, val drift: Float)

data class BackgroundSpec(val layers: List<BackgroundLayer>, val vignette: Float, val gradientShift: Boolean)

enum class ParticleKind { Petal, Snow, Orb }

data class ParticleSpec(
    val kind: ParticleKind,
    val count: Int,
    val speed: Float,
    val sizeMin: Int,
    val sizeMax: Int,
    val colors: List<Long>,
    val sway: Float,
)

data class TabSpec(val icon: String?, val sub: String?)

data class FontSpec(val regular: String?, val bold: String?)
