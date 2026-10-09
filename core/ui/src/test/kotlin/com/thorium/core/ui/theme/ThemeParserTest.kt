package com.thorium.core.ui.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThemeParserTest {

    @Test
    fun `a theme can be tiny and inherits everything else`() {
        val t = ThemeParser.parse("""{"id":"mini","name":"Mini","colors":{"accent":"#FF8FB8"}}""")
        assertEquals("Mini", t.name)
        assertEquals(0xFFFF8FB8, t.colors.accent)
        assertEquals(ThemeSpec.Default.colors.panel, t.colors.panel)
        assertEquals(ThemeSpec.Default.motion, t.motion)
        assertNull(t.particles)
    }

    @Test
    fun `colors accept web order with alpha`() {
        assertEquals(0xFF112233, ThemeParser.parseColor("#112233"))
        assertEquals(0x80112233, ThemeParser.parseColor("#11223380"))
        assertNull(ThemeParser.parseColor("red"))
        assertNull(ThemeParser.parseColor("#12345"))
    }

    @Test
    fun `a wrong value is ignored instead of breaking the theme`() {
        val t = ThemeParser.parse("""{"id":"x","colors":{"accent":"not a color"},"shapes":{"cardRadius":999},"motion":{"focusScale":9}}""")
        assertEquals(ThemeSpec.Default.colors.accent, t.colors.accent)
        assertEquals(40, t.shapes.cardRadius)
        assertEquals(1.4f, t.motion.focusScale)
    }

    @Test
    fun `a theme without a usable id or not json is rejected`() {
        assertThrows(ThemeException::class.java) { ThemeParser.parse("""{"name":"No id"}""") }
        assertThrows(ThemeException::class.java) { ThemeParser.parse("""{"id":"bad id!"}""") }
        assertThrows(ThemeException::class.java) { ThemeParser.parse("nope") }
    }

    @Test
    fun `particles, tabs, sounds, font and background layers are read`() {
        val t = ThemeParser.parse(
            """
            {"id":"full","background":{"layers":[{"image":"a.png","parallax":0.2},{"image":""}],"vignette":0.5},
             "particles":{"kind":"petal","count":99,"colors":["#FFB7D0","bad"]},
             "tabs":{"home":{"icon":"i/h.png","sub":"ホーム"}},
             "sounds":{"move":"s/m.wav","select":""},
             "font":{"regular":"f/r.ttf"}}
            """.trimIndent(),
        )
        assertEquals(listOf(BackgroundLayer("a.png", 0.2f, 0f)), t.background.layers)
        assertEquals(0.5f, t.background.vignette)
        assertEquals(ParticleKind.Petal, t.particles!!.kind)
        assertEquals(60, t.particles!!.count)
        assertEquals(listOf(0xFFFFB7D0), t.particles!!.colors)
        assertEquals("ホーム", t.tabs["home"]!!.sub)
        assertEquals(mapOf("move" to "s/m.wav"), t.sounds)
        assertEquals("f/r.ttf", t.font!!.regular)
        assertNull(t.font!!.bold)
    }

    @Test
    fun `an unknown particle kind means no particles`() {
        assertNull(ThemeParser.parse("""{"id":"p","particles":{"kind":"lasers"}}""").particles)
    }

    @Test
    fun `unknown fields are ignored so newer themes still load`() {
        assertTrue(ThemeParser.parse("""{"id":"f","future":{"a":1},"version":7}""").version == 7)
    }
}
