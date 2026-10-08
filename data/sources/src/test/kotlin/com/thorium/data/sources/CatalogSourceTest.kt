package com.thorium.data.sources

import com.thorium.core.model.RemoteEntry
import com.thorium.core.model.SourceException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CatalogSourceTest {

    private val server = TestServer()

    @AfterEach
    fun stop() = server.close()

    private val catalog = """
        { "name": "Mine", "extra": "ignored",
          "items": [
            { "title": "Alpha", "platform": "GBA", "url": "files/alpha.zip", "size": 100, "sha1": "ABCDEF" },
            { "title": "Beta", "platform": "n64", "url": "${'$'}HOST/abs/beta.z64" },
            { "title": "Gamma", "url": "gamma.bin" }
          ] }
    """.trimIndent()

    private fun source(): CatalogSource {
        server.text("/cat/catalog.json", catalog.replace("\$HOST", server.url), "application/json")
        return CatalogSource(server.url + "/cat/catalog.json", allowInsecure = true)
    }

    @Test
    fun `lists every item with its details`() {
        val items = source().list()
        assertEquals(listOf("Alpha", "Beta", "Gamma"), items.map { it.name })
        assertEquals("gba", items[0].platformId)
        assertEquals(100L, items[0].sizeBytes)
        assertEquals("abcdef", items[0].sha1)
        assertEquals(null, items[2].platformId)
    }

    @Test
    fun `relative urls are resolved against the catalog address`() {
        val items = source().list()
        assertEquals(server.url + "/cat/files/alpha.zip", items[0].ref)
        assertEquals(server.url + "/abs/beta.z64", items[1].ref)
        assertEquals(server.url + "/cat/gamma.bin", items[2].ref)
    }

    @Test
    fun `can be filtered by platform`() {
        assertEquals(listOf("Beta"), source().list("platform:n64").map { it.name })
    }

    @Test
    fun `an invalid catalog is a bad response, not a crash`() {
        server.text("/bad.json", "this is not json", "application/json")
        val failure = CatalogSource(server.url + "/bad.json", allowInsecure = true).testConnection().exceptionOrNull()
        assertTrue(failure is SourceException.BadResponse)
    }

    @Test
    fun `an empty catalog lists nothing`() {
        server.text("/empty.json", "{}", "application/json")
        assertTrue(CatalogSource(server.url + "/empty.json", allowInsecure = true).list().isEmpty())
    }

    @Test
    fun `open downloads an item from the catalog`() {
        val data = ByteArray(50) { it.toByte() }
        server.file("/cat/files/alpha.zip", data)
        val entry = source().list().first()
        assertTrue(source().open(entry).stream.use { it.readBytes() }.contentEquals(data))
    }

    @Test
    fun `plain http needs the source to allow it`() {
        assertThrows(SourceException.InsecureConnection::class.java) { CatalogSource(server.url + "/c.json") }
    }
}
