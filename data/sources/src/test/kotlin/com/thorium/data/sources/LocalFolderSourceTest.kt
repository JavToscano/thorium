package com.thorium.data.sources

import com.thorium.core.model.RemoteEntry
import com.thorium.core.model.SourceException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LocalFolderSourceTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `lists folders first and hides dot files`() {
        File(dir, "b.zip").writeBytes(ByteArray(10))
        File(dir, "sub").mkdirs()
        File(dir, ".hidden").writeText("x")
        File(dir, "._junk.zip").writeText("x")
        val names = LocalFolderSource(dir).list().map { it.name }
        assertEquals(listOf("sub", "b.zip"), names)
    }

    @Test
    fun `reads from an offset`() {
        val data = ByteArray(100) { it.toByte() }
        val file = File(dir, "f.bin").apply { writeBytes(data) }
        val opened = LocalFolderSource(dir).open(RemoteEntry("f.bin", file.path, false), 60)
        assertEquals(100L, opened.totalSize)
        assertTrue(opened.stream.use { it.readBytes() }.contentEquals(data.copyOfRange(60, 100)))
    }

    @Test
    fun `refuses to leave the configured folder`() {
        val outside = File(dir.parentFile, "outside-${System.nanoTime()}.txt").apply { writeText("secret"); deleteOnExit() }
        val src = LocalFolderSource(dir)
        assertThrows(SourceException.InvalidLocation::class.java) { src.open(RemoteEntry("x", outside.path, false)) }
        assertThrows(SourceException.InvalidLocation::class.java) { src.list(dir.parentFile.path) }
    }

    @Test
    fun `a missing folder fails the connection test`() {
        assertTrue(LocalFolderSource(File(dir, "nope")).testConnection().exceptionOrNull() is SourceException.NotFound)
    }
}
