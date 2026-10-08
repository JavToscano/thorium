package com.thorium.data.archive

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveExtractorTest {

    @TempDir
    lateinit var dir: File

    private val extractor = ArchiveExtractor()

    private fun zip(name: String = "a.zip", vararg entries: Pair<String, ByteArray>): File =
        File(dir, name).also { file ->
            ZipOutputStream(file.outputStream()).use { out ->
                entries.forEach { (entryName, data) ->
                    out.putNextEntry(ZipEntry(entryName)); out.write(data); out.closeEntry()
                }
            }
        }

    private fun sevenZ(name: String = "a.7z", vararg entries: Pair<String, ByteArray>): File =
        File(dir, name).also { file ->
            SevenZOutputFile(file).use { out ->
                entries.forEach { (entryName, data) ->
                    val entry = SevenZArchiveEntry().apply { this.name = entryName; size = data.size.toLong() }
                    out.putArchiveEntry(entry); out.write(data); out.closeArchiveEntry()
                }
            }
        }

    private val outDir get() = File(dir, "out")

    @Test
    fun `extracts a zip with nested folders`() {
        val archive = zip(entries = arrayOf("game.gba" to ByteArray(100) { 1 }, "extras/readme.txt" to "hi".toByteArray()))
        val files = extractor.extract(archive, outDir)
        assertEquals(setOf("game.gba", "readme.txt"), files.map { it.name }.toSet())
        assertEquals(100, File(outDir, "game.gba").length())
        assertEquals("hi", File(outDir, "extras/readme.txt").readText())
    }

    @Test
    fun `extracts a 7z`() {
        val archive = sevenZ(entries = arrayOf("disc/game.bin" to ByteArray(2000) { (it % 7).toByte() }, "disc/game.cue" to "FILE".toByteArray()))
        val files = extractor.extract(archive, outDir)
        assertEquals(2, files.size)
        assertEquals(2000, File(outDir, "disc/game.bin").length())
    }

    @Test
    fun `recognises the format by content, not by extension`() {
        val renamed = zip("noext", "x" to ByteArray(1)).also { it.renameTo(File(dir, "weird.dat")) }
        assertEquals(ArchiveKind.Zip, extractor.kind(File(dir, "weird.dat")))
        assertEquals(ArchiveKind.SevenZ, extractor.kind(sevenZ(entries = arrayOf("x" to ByteArray(1)))))
        assertNull(extractor.kind(File(dir, "plain.txt").apply { writeText("not an archive") }))
    }

    @Test
    fun `refuses entries that escape the target folder`() {
        val archive = zip(entries = arrayOf("../evil.txt" to "x".toByteArray()))
        assertThrows(ArchiveException.UnsafePath::class.java) { extractor.extract(archive, outDir) }
        assertFalse(File(dir, "evil.txt").exists())
    }

    @Test
    fun `refuses an absolute path inside a 7z`() {
        val archive = sevenZ(entries = arrayOf("../../escape.txt" to "x".toByteArray()))
        assertThrows(ArchiveException.UnsafePath::class.java) { extractor.extract(archive, outDir) }
    }

    @Test
    fun `stops when the total size passes the limit`() {
        val archive = zip(entries = arrayOf("big.bin" to ByteArray(10_000)))
        val small = ArchiveExtractor(ArchiveExtractor.Limits(maxTotalBytes = 1_000))
        assertThrows(ArchiveException.TooLarge::class.java) { small.extract(archive, outDir) }
    }

    @Test
    fun `stops when there are too many entries`() {
        val archive = zip(entries = Array(20) { "f$it.txt" to ByteArray(1) })
        val few = ArchiveExtractor(ArchiveExtractor.Limits(maxEntries = 10))
        assertThrows(ArchiveException.TooLarge::class.java) { few.extract(archive, outDir) }
    }

    @Test
    fun `reports progress up to the total`() {
        val archive = zip(entries = arrayOf("a.bin" to ByteArray(200_000), "b.bin" to ByteArray(100_000)))
        var last = 0L; var total = 0L
        extractor.extract(archive, outDir, onProgress = { done, t -> last = done; total = t })
        assertEquals(300_000L, last)
        assertEquals(300_000L, total)
    }

    @Test
    fun `can be cancelled while copying`() {
        val archive = zip(entries = arrayOf("a.bin" to ByteArray(500_000)))
        assertThrows(ArchiveException.Cancelled::class.java) { extractor.extract(archive, outDir, isCancelled = { true }) }
    }

    @Test
    fun `a text file is not an archive`() {
        val file = File(dir, "a.zip").apply { writeText("this is not a zip") }
        assertThrows(ArchiveException.Unsupported::class.java) { extractor.extract(file, outDir) }
    }

    @Test
    fun `a truncated zip is reported as corrupt`() {
        val good = zip(entries = arrayOf("a.bin" to ByteArray(5000) { it.toByte() }))
        val broken = File(dir, "broken.zip").apply { writeBytes(good.readBytes().copyOf(60)) }
        assertThrows(ArchiveException::class.java) { extractor.extract(broken, outDir) }
    }

    @Test
    fun `empty archive extracts nothing`() {
        assertTrue(extractor.extract(zip(entries = emptyArray()), outDir).isEmpty())
    }
}
