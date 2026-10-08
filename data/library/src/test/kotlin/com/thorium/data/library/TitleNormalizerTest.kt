package com.thorium.data.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    /** The catalog builder (tools/catalog) applies the same rules; both sides check this shared file. */
    @Test
    fun `matches the shared fixtures used by the catalog builder`() {
        val file = java.io.File("../../tools/catalog/title_fixtures.tsv")
        val cases = file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(cases.isNotEmpty())
        for (line in cases) {
            val cols = line.split("\t")
            val parsed = TitleNormalizer.parse(cols[0])
            assertEquals(cols[1], parsed.title, cols[0])
            assertEquals(cols[2], parsed.matchKey, cols[0])
            assertEquals(cols.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toInt(), parsed.disc, cols[0])
        }
    }
}
