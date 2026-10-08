package com.thorium.data.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LibraryScannerTest {

    @TempDir
    lateinit var root: File

    private fun file(path: String, size: Int = 16): File =
        File(root, path).apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(size) { 1 })
        }

    private fun scan() = LibraryScanner().scan(listOf(root))

    @Test
    fun `detects platform from folder alias and ignores unrelated folders`() {
        file("3ds/Sample One.3ds")
        file("Android/data/junk.3ds")
        file("DCIM/photo.jpg")
        val result = scan()
        assertEquals(listOf("3ds"), result.library.games.map { it.systemId })
        assertEquals("Sample One", result.library.games.single().title)
    }

    @Test
    fun `ignores macOS AppleDouble and other hidden files`() {
        file("3ds/._Sample One.3ds", size = 4096)
        file("3ds/.DS_Store")
        file("3ds/Real Game.3ds")
        val result = scan()
        assertEquals(listOf("Real Game"), result.library.games.map { it.title })
        assertEquals(2, result.stats.filesIgnored)
    }

    @Test
    fun `ignores empty files and unknown extensions`() {
        file("gba/Empty.gba", size = 0)
        file("gba/notes.txt")
        file("gba/Good.gba")
        assertEquals(listOf("Good"), scan().library.games.map { it.title })
    }

    @Test
    fun `marks archives`() {
        file("n64/Sample.zip")
        val game = scan().library.games.single()
        assertTrue(game.files.single().isArchive)
        assertEquals("zip", game.files.single().extension)
    }

    @Test
    fun `finds platform folders inside a roms container`() {
        file("ROMs/psx/Sample.cue")
        file("Emulation/roms/gba/Other.gba")
        val systems = scan().library.games.map { it.systemId }.toSet()
        assertEquals(setOf("ps1", "gba"), systems)
    }

    @Test
    fun `groups discs of one game`() {
        file("ps1/Sample RPG (Disc 1).cue")
        file("ps1/Sample RPG (Disc 2).cue")
        val game = scan().library.games.single()
        assertEquals("Sample RPG", game.title)
        assertEquals(listOf(1, 2), game.files.map { it.disc })
    }

    @Test
    fun `flags duplicates without merging them`() {
        file("gba/Sample Game (USA).gba")
        file("gba/Sample Game (Europe).gba")
        val games = scan().library.games
        assertEquals(2, games.size)
        assertEquals(1, games.count { it.isDuplicate })
        assertEquals(games.size, games.map { it.id }.toSet().size)
    }

    @Test
    fun `same title on different platforms is not a duplicate`() {
        file("gb/Sample.gb")
        file("gbc/Sample.gbc")
        assertFalse(scan().library.games.any { it.isDuplicate })
    }

    @Test
    fun `only platforms with games are listed as systems`() {
        file("wii/Sample.rvz")
        assertEquals(listOf("wii"), scan().library.systems.map { it.id })
    }

    @Test
    fun `respects max depth`() {
        file("gba/a/b/c/d/e/Deep.gba")
        val shallow = LibraryScanner().scan(listOf(root), maxDepth = 2)
        assertTrue(shallow.library.games.isEmpty())
    }

    @Test
    fun `empty or missing roots give an empty library`() {
        val result = LibraryScanner().scan(listOf(root, File(root, "missing")))
        assertTrue(result.library.games.isEmpty())
    }
}
