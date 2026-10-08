package com.thorium.data.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TitleNormalizerTest {

    @Test
    fun `strips region and language tags`() {
        val parsed = TitleNormalizer.parse("Sample Game - Planet (USA) (En,Fr,Es)")
        assertEquals("Sample Game - Planet", parsed.title)
        assertNull(parsed.disc)
    }

    @Test
    fun `strips bracket tags`() {
        assertEquals("Sample Game", TitleNormalizer.parse("Sample Game [!]").title)
    }

    @Test
    fun `extracts disc number and removes it from the title`() {
        val parsed = TitleNormalizer.parse("Sample RPG (Disc 2)")
        assertEquals("Sample RPG", parsed.title)
        assertEquals(2, parsed.disc)
        assertEquals(TitleNormalizer.parse("Sample RPG (Disc 1)").matchKey, parsed.matchKey)
    }

    @Test
    fun `replaces underscores and collapses spaces`() {
        assertEquals("Sample Game Name", TitleNormalizer.parse("Sample_Game__Name").title)
    }

    @Test
    fun `match key ignores case and punctuation`() {
        assertEquals(
            TitleNormalizer.parse("Sample: Game (Europe)").matchKey,
            TitleNormalizer.parse("sample game (USA)").matchKey,
        )
    }

    @Test
    fun `falls back to the raw name when everything is a tag`() {
        assertEquals("(USA)", TitleNormalizer.parse("(USA)").title)
    }
}
