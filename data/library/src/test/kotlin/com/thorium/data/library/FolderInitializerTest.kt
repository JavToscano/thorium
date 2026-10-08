package com.thorium.data.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FolderInitializerTest {

    @TempDir
    lateinit var root: File

    private val initializer = FolderInitializer()

    @Test
    fun `inspect lists every platform and finds existing folders by alias and any case`() {
        File(root, "PSX").mkdirs()
        File(root, "gba").mkdirs()
        val entries = initializer.inspect(root).associateBy { it.platform.id }
        assertEquals("PSX", entries.getValue("ps1").existingName)
        assertEquals("gba", entries.getValue("gba").existingName)
        assertNull(entries.getValue("3ds").existingName)
        assertEquals(PlatformCatalog.Default.platforms.size, entries.size)
    }

    @Test
    fun `create makes only the requested folders`() {
        val result = initializer.create(root, listOf("gba", "3ds"))
        assertEquals(listOf("3ds", "gba").sorted(), result.created.sorted())
        assertTrue(File(root, "gba").isDirectory)
        assertTrue(File(root, "3ds").isDirectory)
        assertTrue(!File(root, "wii").exists())
    }

    @Test
    fun `create leaves existing folders untouched and reports them`() {
        File(root, "PSX").mkdirs()
        File(root, "PSX/keep.txt").writeText("x")
        val result = initializer.create(root, listOf("ps1", "gba"))
        assertEquals(listOf("gba"), result.created)
        assertEquals(listOf("PSX"), result.alreadyExisted)
        assertTrue(File(root, "PSX/keep.txt").exists())
        assertTrue(!File(root, "ps1").exists())
    }

    @Test
    fun `a file with the folder name is reported as failed`() {
        File(root, "gba").writeText("not a folder")
        val result = initializer.create(root, listOf("gba"))
        assertEquals(listOf("gba"), result.failed)
        assertTrue(result.created.isEmpty())
    }

    @Test
    fun `created folders are recognised by the scanner`() {
        initializer.create(root, listOf("gba"))
        File(root, "gba/Sample.gba").writeBytes(ByteArray(4) { 1 })
        assertEquals(listOf("Sample"), LibraryScanner().scan(listOf(root)).library.games.map { it.title })
    }

    @Test
    fun `a missing root creates nothing`() {
        val result = initializer.create(File(root, "nope"), listOf("gba"))
        assertEquals(listOf("gba"), result.failed)
        assertTrue(!File(root, "nope").exists())
    }
}
