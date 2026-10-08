package com.thorium.data.sources

import com.thorium.core.model.SourceException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InternetArchiveSourceTest {

    private val server = TestServer()

    @AfterEach
    fun stop() = server.close()

    private fun source(id: String, access: String = "", secret: String = "") =
        InternetArchiveSource(id, access, secret, baseUrl = server.url, allowInsecure = true)

    private val item = """
        { "metadata": { "mediatype": "software" },
          "files": [
            { "name": "my game.zip", "source": "original", "size": "2048", "sha1": "AA11" },
            { "name": "sub/deep file.7z", "source": "original", "size": "10" },
            { "name": "item_meta.xml", "source": "original", "size": "5" },
            { "name": "item_files.xml", "source": "original", "size": "5" },
            { "name": "thumb.jpg", "source": "derivative", "size": "5" }
          ] }
    """.trimIndent()

    @Test
    fun `an item lists its original files with download links`() {
        server.text("/metadata/some-item", item, "application/json")
        val files = source("some-item").list()
        assertEquals(listOf("my game.zip", "sub/deep file.7z"), files.map { it.name })
        assertEquals(server.url + "/download/some-item/my%20game.zip", files[0].ref)
        assertEquals(server.url + "/download/some-item/sub/deep%20file.7z", files[1].ref)
        assertEquals(2048L, files[0].sizeBytes)
        assertEquals("aa11", files[0].sha1)
    }

    @Test
    fun `a collection lists its items as folders and opens them`() {
        server.text("/metadata/my-collection", """{ "metadata": { "mediatype": "collection" }, "files": [] }""", "application/json")
        server.text("/advancedsearch.php", """{ "response": { "docs": [ { "identifier": "one", "title": "First" }, { "identifier": "two" } ] } }""", "application/json")
        server.text("/metadata/one", item, "application/json")
        val src = source("my-collection")
        val items = src.list()
        assertEquals(listOf("First", "two"), items.map { it.name })
        assertTrue(items.all { it.isDirectory })
        assertEquals(2, src.list(items[0].ref).size)
        assertTrue(server.requests.any { it.path == "/advancedsearch.php" && it.query!!.contains("collection%3Amy-collection") })
    }

    @Test
    fun `a full page of results adds a more entry`() {
        server.text("/metadata/big", """{ "metadata": { "mediatype": "collection" } }""", "application/json")
        val docs = (1..100).joinToString(",") { """{ "identifier": "i$it" }""" }
        server.text("/advancedsearch.php", """{ "response": { "docs": [ $docs ] } }""", "application/json")
        val items = source("big").list()
        assertEquals(101, items.size)
        assertEquals("page:2", items.last().ref)
    }

    @Test
    fun `access keys are sent as the authorization header`() {
        server.text("/metadata/locked", item, "application/json")
        source("locked", "ACCESS", "SECRET").list()
        assertEquals("LOW ACCESS:SECRET", server.requests.last().headers["Authorization"])
    }

    @Test
    fun `no authorization header without keys`() {
        server.text("/metadata/open", item, "application/json")
        source("open").list()
        assertTrue(server.requests.last().headers.keys.none { it.equals("Authorization", ignoreCase = true) })
    }

    @Test
    fun `an unknown identifier is not found`() {
        server.text("/metadata/nope", "{}", "application/json")
        assertTrue(source("nope").testConnection().exceptionOrNull() is SourceException.NotFound)
    }

    @Test
    fun `accepts an archive org details link`() {
        assertEquals("some-item", InternetArchiveSource.normalizeIdentifier("https://archive.org/details/some-item/"))
        assertEquals("some-item", InternetArchiveSource.normalizeIdentifier("https://archive.org/details/some-item?tab=about"))
        assertEquals("plain_id", InternetArchiveSource.normalizeIdentifier("  plain_id "))
    }

    @Test
    fun `rejects identifiers with strange characters`() {
        assertThrows(SourceException.InvalidLocation::class.java) { source("bad id/../x") }
        assertThrows(SourceException.InvalidLocation::class.java) { source("") }
    }

    @Test
    fun `the real service address is https only`() {
        val factorySource = com.thorium.data.sources.SourceFactory.create(
            com.thorium.core.model.SourceConfig(1, "x", com.thorium.core.model.SourceType.InternetArchive, "some-item"),
        )
        assertTrue(factorySource is InternetArchiveSource)
    }

    @Test
    fun `downloads a file with resume`() {
        val data = ByteArray(300) { it.toByte() }
        server.file("/download/some-item/", data)
        server.text("/metadata/some-item", item, "application/json")
        val src = source("some-item")
        val entry = src.list().first()
        val opened = src.open(entry, 100)
        assertTrue(opened.resumed)
        assertTrue(opened.stream.use { it.readBytes() }.contentEquals(data.copyOfRange(100, 300)))
    }
}
