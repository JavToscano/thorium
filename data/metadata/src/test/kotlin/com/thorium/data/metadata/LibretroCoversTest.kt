package com.thorium.data.metadata

import com.sun.net.httpserver.HttpServer
import com.thorium.core.model.CatalogEntry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList

class LibretroCoversTest {

    @TempDir lateinit var tmp: File

    private lateinit var server: HttpServer
    private val requests = CopyOnWriteArrayList<String>()
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 1, 2, 3, 4)
    private var status = 200
    private var body: ByteArray = png
    private var now = 1_000_000L

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            requests += URLDecoder.decode(ex.requestURI.rawPath, "UTF-8")
            val data = if (status == 200) body else ByteArray(0)
            ex.sendResponseHeaders(status, if (status == 200) data.size.toLong() else -1)
            if (status == 200) ex.responseBody.use { it.write(data) }
            ex.close()
        }
        server.start()
    }

    @AfterEach
    fun stop() = server.stop(0)

    private fun covers() = LibretroCovers(
        dir = File(tmp, "covers"),
        client = OkHttpClient(),
        baseUrl = "http://127.0.0.1:${server.address.port}/",
        clock = { now },
        missRetryMs = 1000,
    )

    private fun entry(name: String, platform: String = "gba") = CatalogEntry(
        platformId = platform, name = name, title = name.substringBefore(" ("), region = "USA",
        serial = null, sizeBytes = 1, crc32 = null, md5 = null,
    )

    @Test
    fun `downloads the box art once and serves it from disk afterwards`() = runBlocking {
        val c = covers()
        val game = entry("Super Mario Advance (USA, Europe)")
        assertNull(c.cached(game))
        val file = c.fetch(game)!!
        assertTrue(file.readBytes().contentEquals(png))
        assertEquals(listOf("/Nintendo - Game Boy Advance/Named_Boxarts/Super Mario Advance (USA, Europe).png"), requests.toList())
        assertEquals(file, c.cached(game))
        c.fetch(game)
        assertEquals(1, requests.size)
    }

    @Test
    fun `a missing image is remembered until the retry window passes`() = runBlocking {
        status = 404
        val c = covers()
        val game = entry("Obscure Game (USA)")
        assertNull(c.fetch(game))
        assertNull(c.fetch(game))
        assertEquals(1, requests.size)
        now += 2000
        assertNull(c.fetch(game))
        assertEquals(2, requests.size)
    }

    @Test
    fun `a server error is not remembered`() = runBlocking {
        status = 500
        val c = covers()
        val game = entry("Some Game (USA)")
        assertNull(c.fetch(game))
        status = 200
        assertNotNull(c.fetch(game))
        assertEquals(2, requests.size)
    }

    @Test
    fun `an answer that is not a png is rejected`() = runBlocking {
        body = "<html>oops</html>".toByteArray()
        val c = covers()
        val game = entry("Some Game (USA)")
        assertNull(c.fetch(game))
        assertNull(c.cached(game))
    }

    @Test
    fun `characters the server replaces are replaced in the file name`() = runBlocking {
        val c = covers()
        c.fetch(entry("Pokemon & Co: Blue (USA)"))
        assertEquals("/Nintendo - Game Boy Advance/Named_Boxarts/Pokemon _ Co_ Blue (USA).png", requests.single())
    }

    @Test
    fun `a platform without a thumbnail folder gets no request`() = runBlocking {
        val c = covers()
        assertNull(c.fetch(entry("Some Game (USA)", platform = "ps1")))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a name cannot escape the cache folder`() = runBlocking {
        val c = covers()
        val file = c.fetch(entry("../../evil (USA)"))!!
        assertTrue(file.canonicalPath.startsWith(File(tmp, "covers").canonicalPath))
    }
}
