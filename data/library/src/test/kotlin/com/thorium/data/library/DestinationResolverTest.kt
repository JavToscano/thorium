package com.thorium.data.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DestinationResolverTest {

    @TempDir
    lateinit var tmp: File

    private val catalog = PlatformCatalog.Default
    private fun platform(id: String) = catalog.platforms.first { it.id == id }
    private fun root(name: String) = File(tmp, name).apply { mkdirs() }

    // ---- which console

    @Test
    fun `the platform the source states wins`() {
        val resolver = DestinationResolver()
        assertEquals("n64", resolver.detectPlatform("N64", "game.zip", listOf("gba"))?.id)
    }

    @Test
    fun `a folder name in the source path identifies the console`() {
        val resolver = DestinationResolver()
        assertEquals("gba", resolver.detectPlatform(null, "Some Game.zip", listOf("roms", "gba"))?.id)
    }

    @Test
    fun `the closest folder in the path is used`() {
        val resolver = DestinationResolver()
        assertEquals("snes", resolver.detectPlatform(null, "x.zip", listOf("gba", "stuff", "snes"))?.id)
    }

    @Test
    fun `an extension used by one console identifies it`() {
        val resolver = DestinationResolver()
        assertEquals("3ds", resolver.detectPlatform(null, "Game.3ds")?.id)
        assertEquals("switch", resolver.detectPlatform(null, "Game.nsp")?.id)
    }

    @Test
    fun `a shared or unknown extension is left undecided`() {
        val resolver = DestinationResolver()
        assertNull(resolver.detectPlatform(null, "Game.iso"))
        assertNull(resolver.detectPlatform(null, "Game.zip"))
        assertNull(resolver.detectPlatform(null, "noextension"))
    }

    // ---- which folder

    @Test
    fun `uses the console folder that already exists, whatever its name`() {
        val sd = root("sd").also { File(it, "PSX").mkdirs() }
        val dest = DestinationResolver().resolve(platform("ps1"), listOf(sd))!!
        assertEquals(File(sd, "PSX"), dest.platformDir)
    }

    @Test
    fun `finds the folder inside a roms container`() {
        val sd = root("sd").also { File(it, "ROMs/gba").mkdirs() }
        val dest = DestinationResolver().resolve(platform("gba"), listOf(sd))!!
        assertEquals(File(sd, "ROMs/gba"), dest.platformDir)
    }

    @Test
    fun `creates the folder with the platform id when none exists`() {
        val sd = root("sd")
        val dest = DestinationResolver().resolve(platform("3ds"), listOf(sd))!!
        assertEquals(File(sd, "3ds"), dest.platformDir)
        assertTrue(dest.platformDir.isDirectory)
    }

    @Test
    fun `creates it inside a roms container when the root has one`() {
        val sd = root("sd").also { File(it, "roms").mkdirs() }
        val dest = DestinationResolver().resolve(platform("n64"), listOf(sd))!!
        assertEquals(File(sd, "roms/n64"), dest.platformDir)
    }

    @Test
    fun `a root that is itself the console folder is used as is`() {
        val gba = File(root("games"), "gba").apply { mkdirs() }
        val dest = DestinationResolver().resolve(platform("gba"), listOf(gba))!!
        assertEquals(gba, dest.platformDir)
    }

    @Test
    fun `prefers the chosen root when several have the folder`() {
        val a = root("a").also { File(it, "gba").mkdirs() }
        val b = root("b").also { File(it, "gba").mkdirs() }
        val dest = DestinationResolver().resolve(platform("gba"), listOf(a, b), preferredRoot = b)!!
        assertEquals(File(b, "gba"), dest.platformDir)
    }

    @Test
    fun `without a preference the root with more free space wins`() {
        val small = root("small").also { File(it, "gba").mkdirs() }
        val big = root("big").also { File(it, "gba").mkdirs() }
        val resolver = DestinationResolver(freeSpace = { if (it == big) 1_000L else 10L })
        assertEquals(File(big, "gba"), resolver.resolve(platform("gba"), listOf(small, big))!!.platformDir)
    }

    @Test
    fun `a missing folder is created in the roomiest root`() {
        val small = root("small"); val big = root("big")
        val resolver = DestinationResolver(freeSpace = { if (it == big) 1_000L else 10L })
        assertEquals(File(big, "wii"), resolver.resolve(platform("wii"), listOf(small, big))!!.platformDir)
    }

    @Test
    fun `the work folder is hidden and inside the console folder`() {
        val sd = root("sd")
        val dest = DestinationResolver().resolve(platform("gba"), listOf(sd))!!
        assertEquals(File(dest.platformDir, DestinationResolver.WORK_DIR), dest.workDir)
        assertTrue(dest.workDir.isDirectory)
        assertTrue(dest.workDir.name.startsWith("."))
    }

    @Test
    fun `the scanner ignores the work folder`() {
        val sd = root("sd")
        val dest = DestinationResolver().resolve(platform("gba"), listOf(sd))!!
        File(dest.workDir, "1.part").writeBytes(ByteArray(10) { 1 })
        File(dest.workDir, "half.gba").writeBytes(ByteArray(10) { 1 })
        assertTrue(LibraryScanner().scan(listOf(sd)).library.games.isEmpty())
    }

    @Test
    fun `no usable root means no destination`() {
        assertNull(DestinationResolver().resolve(platform("gba"), emptyList()))
        assertNull(DestinationResolver().resolve(platform("gba"), listOf(File(tmp, "missing"))))
    }

    @Test
    fun `existing folders are not touched`() {
        val sd = root("sd").also { File(it, "gba").mkdirs(); File(it, "gba/keep.gba").writeText("x") }
        DestinationResolver().resolve(platform("gba"), listOf(sd))
        assertNotNull(File(sd, "gba/keep.gba").takeIf { it.exists() })
    }
}
