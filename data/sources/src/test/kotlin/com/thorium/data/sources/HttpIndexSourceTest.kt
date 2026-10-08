package com.thorium.data.sources

import com.thorium.core.model.RemoteEntry
import com.thorium.core.model.SourceException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class HttpIndexSourceTest {

    private val server = TestServer()

    @AfterEach
    fun stop() = server.close()

    private fun source(path: String = "/", user: String = "", pass: String = "") =
        HttpIndexSource(server.url + path, user, pass, allowInsecure = true)

    private val nginx = """
        <html><body><h1>Index of /roms/</h1><hr><pre><a href="../">../</a>
        <a href="gba/">gba/</a>                                  07-Oct-2026 10:00    -
        <a href="Some%20Game%20%28USA%29.zip">Some Game (USA).zip</a>     07-Oct-2026 10:00   16M
        <a href="other.7z">other.7z</a>                          07-Oct-2026 10:00   1073741824
        </pre><hr></body></html>
    """.trimIndent()

    @Test
    fun `lists an nginx index with folders first, decoded names and sizes`() {
        server.text("/roms/", nginx)
        val entries = source("/roms/").list()
        assertEquals(listOf("gba", "other.7z", "Some Game (USA).zip"), entries.map { it.name })
        assertTrue(entries[0].isDirectory)
        assertEquals(1_073_741_824L, entries[1].sizeBytes)
        assertEquals(16L * 1024 * 1024, entries[2].sizeBytes)
    }

    @Test
    fun `skips the parent link and sort links`() {
        server.text("/roms/", """
            <a href="?C=N;O=D">Name</a> <a href="/">Parent Directory</a> <a href="../">../</a>
            <a href="game.zip">game.zip</a>
        """.trimIndent())
        assertEquals(listOf("game.zip"), source("/roms/").list().map { it.name })
    }

    @Test
    fun `reads an apache table index`() {
        server.text("/roms/", """
            <table><tr><td><a href="game.gba">game.gba</a></td><td align="right">2026-10-07 10:00  </td><td align="right"> 8.0M</td></tr>
            <tr><td><a href="sub/">sub/</a></td><td align="right">2026-10-07 10:00  </td><td align="right">  - </td></tr></table>
        """.trimIndent())
        val entries = source("/roms/").list()
        assertEquals(listOf("sub", "game.gba"), entries.map { it.name })
        assertEquals(8L * 1024 * 1024, entries[1].sizeBytes)
    }

    @Test
    fun `reads a bare python style index without sizes`() {
        server.text("/", """<ul><li><a href="a.zip">a.zip</a></li><li><a href="b.zip">b.zip</a></li></ul>""")
        val entries = source().list()
        assertEquals(listOf("a.zip", "b.zip"), entries.map { it.name })
        assertNull(entries[0].sizeBytes)
    }

    @Test
    fun `listing a folder uses the reference of that folder`() {
        server.text("/roms/", nginx)
        server.text("/roms/gba/", """<a href="x.gba">x.gba</a>""")
        val src = source("/roms/")
        val folder = src.list().first { it.isDirectory }
        assertEquals(listOf("x.gba"), src.list(folder.ref).map { it.name })
    }

    @Test
    fun `sends basic credentials and reports wrong ones`() {
        val good = "Basic " + Base64.getEncoder().encodeToString("me:secret".toByteArray())
        server.text("/", """<a href="a.zip">a.zip</a>""", requireAuth = good)
        assertTrue(source(user = "me", pass = "secret").testConnection().isSuccess)
        val failure = source(user = "me", pass = "nope").testConnection().exceptionOrNull()
        assertTrue(failure is SourceException.Unauthorized)
    }

    @Test
    fun `plain http is refused unless the source allows it`() {
        assertThrows(SourceException.InsecureConnection::class.java) { HttpIndexSource(server.url, allowInsecure = false) }
    }

    @Test
    fun `an address that is not a url is rejected`() {
        assertThrows(SourceException.InvalidLocation::class.java) { HttpIndexSource("not a url", allowInsecure = true) }
    }

    @Test
    fun `a missing page is reported as not found`() {
        val failure = source("/nothing/").testConnection().exceptionOrNull()
        assertTrue(failure is SourceException.NotFound)
    }

    @Test
    fun `an unreachable server is a network error`() {
        val dead = HttpIndexSource("http://127.0.0.1:1", allowInsecure = true)
        assertTrue(dead.testConnection().exceptionOrNull() is SourceException.Network)
    }

    private val payload = ByteArray(1000) { (it % 251).toByte() }
    private fun entry(path: String) = RemoteEntry("f", server.url + path, false)

    @Test
    fun `open starts from zero`() {
        server.file("/f.bin", payload)
        val opened = source().open(entry("/f.bin"))
        assertTrue(opened.resumed)
        assertEquals(1000L, opened.totalSize)
        assertTrue(opened.stream.use { it.readBytes() }.contentEquals(payload))
    }

    @Test
    fun `open resumes with a range request`() {
        server.file("/f.bin", payload)
        val opened = source().open(entry("/f.bin"), offset = 400)
        assertTrue(opened.resumed)
        assertEquals(1000L, opened.totalSize)
        assertTrue(opened.stream.use { it.readBytes() }.contentEquals(payload.copyOfRange(400, 1000)))
    }

    @Test
    fun `open tells the caller when the server ignored the range`() {
        server.file("/f.bin", payload, supportRange = false)
        val opened = source().open(entry("/f.bin"), offset = 400)
        assertFalse(opened.resumed)
        assertEquals(1000, opened.stream.use { it.readBytes() }.size)
    }
}
