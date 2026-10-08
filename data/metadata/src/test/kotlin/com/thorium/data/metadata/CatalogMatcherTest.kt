package com.thorium.data.metadata

import com.thorium.core.model.CatalogEntry
import com.thorium.core.model.MatchKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CatalogMatcherTest {

    private fun entry(platform: String, name: String, region: String?, crc: Long? = null) = CatalogEntry(
        platformId = platform,
        name = name,
        title = name.substringBefore(" ("),
        region = region,
        serial = null,
        sizeBytes = 1,
        crc32 = crc,
        md5 = null,
    )

    private val entries = listOf(
        entry("gba", "Super Mario Advance (USA, Europe)", "USA", crc = 0x1E4C6D6A),
        entry("gba", "Super Mario Advance (USA, Europe) (Virtual Console)", "USA", crc = 0x5251F2BF),
        entry("gba", "Super Mario Advance (Japan)", "Japan", crc = 0x11111111),
        entry("gba", "Kirby - Nightmare in Dream Land (USA)", "USA"),
        entry("snes", "Kirby - Nightmare in Dream Land (USA)", "USA"),
        entry("genesis", "Sonic The Hedgehog (USA, Europe)", "USA"),
        entry("nes", "Sonic The Hedgehog (Taiwan) (En) (Pirate)", "Taiwan"),
    )

    private val store = object : CatalogStore {
        override fun byCrc(crc32: Long) = entries.filter { it.crc32 == crc32 }
        override fun byKey(platformId: String?, key: String) =
            entries.filter { (platformId == null || it.platformId == platformId) && simpleKey(it.title) == key }
    }

    private fun simpleKey(text: String) = text.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
    private val matcher = CatalogMatcher(store) { simpleKey(it.substringBefore(" (")) }

    @Test
    fun `a known hash wins over the file name`() {
        val match = matcher.identify("renamed rom", "gba", crc32 = 0x5251F2BF)!!
        assertEquals(MatchKind.Hash, match.kind)
        assertEquals("Super Mario Advance (USA, Europe) (Virtual Console)", match.entry.name)
    }

    @Test
    fun `a hash from another console is ignored`() {
        assertNull(matcher.identify("nothing", "snes", crc32 = 0x1E4C6D6A))
    }

    @Test
    fun `an exact name is recognised as a name match`() {
        val match = matcher.identify("Super Mario Advance (USA, Europe)", "gba")!!
        assertEquals(MatchKind.Name, match.kind)
        assertEquals("USA", match.entry.region)
    }

    @Test
    fun `a bare title picks the plainest release`() {
        val match = matcher.identify("Super Mario Advance", "gba")!!
        assertEquals(MatchKind.Title, match.kind)
        assertEquals("Super Mario Advance (USA, Europe)", match.entry.name)
    }

    @Test
    fun `the region in the file name selects that release`() {
        val match = matcher.identify("Super Mario Advance (Japan) [!]", "gba")!!
        assertEquals("Super Mario Advance (Japan)", match.entry.name)
    }

    @Test
    fun `an unknown hash falls back to the name`() {
        val match = matcher.identify("Super Mario Advance (USA, Europe)", "gba", crc32 = 0x7)!!
        assertEquals(MatchKind.Name, match.kind)
    }

    @Test
    fun `a title on several consoles is ambiguous without a console`() {
        assertNull(matcher.identify("Kirby - Nightmare in Dream Land"))
        assertEquals("gba", matcher.identify("Kirby - Nightmare in Dream Land", "gba")!!.entry.platformId)
    }

    @Test
    fun `an unknown title gives no match`() {
        assertNull(matcher.identify("Does Not Exist", "gba"))
    }

    @Test
    fun `without a console an exact name that exists on one console wins over a shared title`() {
        assertEquals("genesis", matcher.identify("Sonic The Hedgehog (USA, Europe)")!!.entry.platformId)
        assertNull(matcher.identify("Sonic The Hedgehog"))
    }
}
