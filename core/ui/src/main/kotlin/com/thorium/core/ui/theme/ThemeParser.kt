package com.thorium.core.ui.theme

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A theme file that cannot be used at all (not JSON, or without an id). */
class ThemeException(message: String) : Exception(message)

@Serializable
private data class ThemeJson(
    val id: String? = null,
    val name: String? = null,
    val author: String? = null,
    val description: String? = null,
    val version: Int? = null,
    val colors: ColorsJson? = null,
    val shapes: ShapesJson? = null,
    val motion: MotionJson? = null,
    val background: BackgroundJson? = null,
    val particles: ParticlesJson? = null,
    val tabs: Map<String, TabJson>? = null,
    val sounds: Map<String, String>? = null,
    val font: FontJson? = null,
)

@Serializable
private data class ColorsJson(
    val backgroundTop: String? = null, val backgroundBottom: String? = null,
    val accent: String? = null, val accentAlt: String? = null,
    val panel: String? = null, val panelFocused: String? = null,
    val textPrimary: String? = null, val textSecondary: String? = null,
    val success: String? = null, val danger: String? = null, val warning: String? = null,
    val onAccent: String? = null,
)

@Serializable
private data class ShapesJson(val cardRadius: Int? = null, val panelRadius: Int? = null, val border: Int? = null)

@Serializable
private data class MotionJson(
    val focusScale: Float? = null, val focusLift: Int? = null,
    val glow: Boolean? = null, val glowPulse: Boolean? = null, val shine: Boolean? = null,
    val entrance: Boolean? = null, val tabSlideMs: Int? = null, val pageMs: Int? = null,
)

@Serializable
private data class LayerJson(val image: String? = null, val parallax: Float? = null, val drift: Float? = null)

@Serializable
private data class BackgroundJson(
    val layers: List<LayerJson>? = null, val vignette: Float? = null, val gradientShift: Boolean? = null,
)

@Serializable
private data class ParticlesJson(
    val kind: String? = null, val count: Int? = null, val speed: Float? = null,
    val sizeMin: Int? = null, val sizeMax: Int? = null, val colors: List<String>? = null, val sway: Float? = null,
)

@Serializable
private data class TabJson(val icon: String? = null, val sub: String? = null)

@Serializable
private data class FontJson(val regular: String? = null, val bold: String? = null)

/**
 * Reads `theme.json`. Every field is optional: what a theme leaves out (or gets wrong) falls back to
 * [base], so a theme can be three lines long and a typo never breaks the app.
 */
object ThemeParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String, base: ThemeSpec = ThemeSpec.Default): ThemeSpec {
        val t = try {
            json.decodeFromString<ThemeJson>(text)
        } catch (e: Exception) {
            throw ThemeException("Not a valid theme file: ${e.message}")
        }
        val id = t.id?.trim().orEmpty()
        if (id.isEmpty() || !id.matches(ID)) throw ThemeException("A theme needs an id of letters, digits, '-' or '_'")
        val c = t.colors
        val colors = base.colors.let {
            ColorSpec(
                backgroundTop = color(c?.backgroundTop, it.backgroundTop), backgroundBottom = color(c?.backgroundBottom, it.backgroundBottom),
                accent = color(c?.accent, it.accent), accentAlt = color(c?.accentAlt, it.accentAlt),
                panel = color(c?.panel, it.panel), panelFocused = color(c?.panelFocused, it.panelFocused),
                textPrimary = color(c?.textPrimary, it.textPrimary), textSecondary = color(c?.textSecondary, it.textSecondary),
                success = color(c?.success, it.success), danger = color(c?.danger, it.danger), warning = color(c?.warning, it.warning),
                onAccent = color(c?.onAccent, it.onAccent),
            )
        }
        val s = t.shapes
        val shapes = ShapeSpec(
            cardRadius = (s?.cardRadius ?: base.shapes.cardRadius).coerceIn(0, 40),
            panelRadius = (s?.panelRadius ?: base.shapes.panelRadius).coerceIn(0, 40),
            border = (s?.border ?: base.shapes.border).coerceIn(0, 8),
        )
        val m = t.motion
        val motion = MotionSpec(
            focusScale = (m?.focusScale ?: base.motion.focusScale).coerceIn(1f, 1.4f),
            focusLift = (m?.focusLift ?: base.motion.focusLift).coerceIn(0, 24),
            glow = m?.glow ?: base.motion.glow,
            glowPulse = m?.glowPulse ?: base.motion.glowPulse,
            shine = m?.shine ?: base.motion.shine,
            entrance = m?.entrance ?: base.motion.entrance,
            tabSlideMs = (m?.tabSlideMs ?: base.motion.tabSlideMs).coerceIn(0, 1500),
            pageMs = (m?.pageMs ?: base.motion.pageMs).coerceIn(0, 1500),
        )
        val b = t.background
        val background = BackgroundSpec(
            layers = b?.layers?.mapNotNull { l ->
                l.image?.takeIf { it.isNotBlank() }?.let { BackgroundLayer(it, (l.parallax ?: 0f).coerceIn(0f, 1f), (l.drift ?: 0f).coerceIn(-1f, 1f)) }
            } ?: base.background.layers,
            vignette = (b?.vignette ?: base.background.vignette).coerceIn(0f, 1f),
            gradientShift = b?.gradientShift ?: base.background.gradientShift,
        )
        val particles = t.particles?.let { p ->
            val kind = when (p.kind?.lowercase()) {
                "petal" -> ParticleKind.Petal
                "snow" -> ParticleKind.Snow
                "orb" -> ParticleKind.Orb
                else -> return@let base.particles
            }
            val colorsList = p.colors?.mapNotNull { parseColor(it) }.orEmpty().ifEmpty { listOf(colors.accent) }
            ParticleSpec(
                kind = kind,
                count = (p.count ?: 24).coerceIn(0, 60),
                speed = (p.speed ?: 1f).coerceIn(0.1f, 4f),
                sizeMin = (p.sizeMin ?: 8).coerceIn(2, 80),
                sizeMax = (p.sizeMax ?: 20).coerceIn(2, 120),
                colors = colorsList,
                sway = (p.sway ?: 1f).coerceIn(0f, 3f),
            )
        } ?: base.particles
        return ThemeSpec(
            id = id,
            name = t.name?.takeIf { it.isNotBlank() } ?: id,
            author = t.author.orEmpty(),
            description = t.description.orEmpty(),
            version = t.version ?: 1,
            colors = colors,
            shapes = shapes,
            motion = motion,
            background = background,
            particles = particles,
            tabs = t.tabs?.mapValues { (_, v) -> TabSpec(v.icon?.takeIf { it.isNotBlank() }, v.sub?.takeIf { it.isNotBlank() }) } ?: base.tabs,
            sounds = t.sounds?.filterValues { it.isNotBlank() } ?: base.sounds,
            font = t.font?.let { FontSpec(it.regular?.takeIf { f -> f.isNotBlank() }, it.bold?.takeIf { f -> f.isNotBlank() }) } ?: base.font,
        )
    }

    private val ID = Regex("[A-Za-z0-9_-]{1,40}")

    private fun color(text: String?, fallback: Long): Long = text?.let(::parseColor) ?: fallback

    /** `#RRGGBB` or `#RRGGBBAA` (web order); null when it is neither. */
    fun parseColor(text: String): Long? {
        val hex = text.trim().removePrefix("#")
        if (!hex.matches(Regex("[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8}"))) return null
        val rgb = hex.take(6).toLong(16)
        val alpha = if (hex.length == 8) hex.substring(6).toLong(16) else 0xFF
        return (alpha shl 24) or rgb
    }
}
