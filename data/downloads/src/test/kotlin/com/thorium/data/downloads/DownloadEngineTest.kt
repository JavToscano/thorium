package com.thorium.data.downloads

import com.thorium.core.model.DownloadError
import com.thorium.core.model.DownloadItem
import com.thorium.core.model.DownloadState
import com.thorium.core.model.GameSource
import com.thorium.core.model.InstallException
import com.thorium.core.model.SourceException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest

class DownloadEngineTest {

    @TempDir
    lateinit var dir: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = FakeStore()

    @AfterEach
    fun stop() = scope.cancel()

    private val payload = ByteArray(200_000) { (it * 31 % 251).toByte() }
    private fun sha1(bytes: ByteArray) = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private val workDir get() = File(dir, "work").path
    private val gameDir get() = File(dir, "gba").apply { mkdirs() }

    private fun item(name: String = "Game.gba", size: Long? = payload.size.toLong(), sha1: String? = null) = DownloadItem(
        sourceId = 1, title = name, entryName = name, entryRef = "ref/$name", platformId = "gba",
        destinationDir = gameDir.path, workDir = workDir, sizeBytes = size, sha1 = sha1,
    )

    private fun engine(
        source: GameSource,
        installer: FakeInstaller = FakeInstaller(gameDir),
        config: DownloadConfig = DownloadConfig(backoffMs = listOf(20, 20, 20), progressIntervalMs = 0, bufferSize = 4096),
        freeSpace: (File) -> Long = { Long.MAX_VALUE },
    ) = DownloadEngine(store, { source }, installer, scope, config, Dispatchers.IO, freeSpace = freeSpace)

    /** Polls until [condition] holds for the item, failing the test after a few seconds. */
    private fun await(id: Long, timeoutMs: Long = 8_000, condition: (DownloadItem) -> Boolean): DownloadItem = runBlocking {
        withTimeout(timeoutMs) {
            while (true) {
                store.get(id)?.takeIf(condition)?.let { return@withTimeout it }
                delay(10)
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
    }

    private fun awaitState(id: Long, state: DownloadState) = await(id) { it.state == state }

    @Test
    fun `downloads, verifies and installs a file`() = runBlocking {
        val installer = FakeInstaller(gameDir)
        val e = engine(FakeSource(payload), installer)
        val id = e.enqueue(item())
        val done = awaitState(id, DownloadState.Completed)
        assertEquals(payload.size.toLong(), done.bytesDone)
        assertEquals(1, installer.installed.size)
        assertTrue(installer.installed.single().second.contentEquals(payload))
        assertEquals(File(gameDir, "Game.gba").path, done.installedPath)
        assertFalse(File(workDir, "$id.part").exists())
    }

    @Test
    fun `a matching sha1 passes`() = runBlocking {
        val id = engine(FakeSource(payload)).enqueue(item(sha1 = sha1(payload).uppercase()))
        awaitState(id, DownloadState.Completed)
        Unit
    }

    @Test
    fun `a wrong sha1 fails without retrying and removes the file`() = runBlocking {
        val source = FakeSource(payload)
        val installer = FakeInstaller(gameDir)
        val id = engine(source, installer).enqueue(item(sha1 = "0".repeat(40)))
        val failed = awaitState(id, DownloadState.Failed)
        assertEquals(DownloadError.Verification, failed.error)
        assertEquals(1, source.opens.get())
        assertTrue(installer.installed.isEmpty())
        assertFalse(File(workDir, "$id.part").exists())
    }

    @Test
    fun `pause keeps the partial file and resume continues from it`() = runBlocking {
        val source = FakeSource(payload, chunkDelayMs = 5, chunk = 2048)
        val installer = FakeInstaller(gameDir)
        val e = engine(source, installer)
        val id = e.enqueue(item())
        await(id) { it.bytesDone > 20_000 }
        e.pause(id)
        val paused = awaitState(id, DownloadState.Paused)
        val partial = File(workDir, "$id.part").length()
        assertTrue(partial in 1 until payload.size, "partial=$partial")
        e.resume(id)
        awaitState(id, DownloadState.Completed)
        assertTrue(source.offsets.last() > 0, "second open must ask for an offset: ${source.offsets}")
        assertTrue(installer.installed.single().second.contentEquals(payload))
    }

    @Test
    fun `when the server ignores the range the download starts over`() = runBlocking {
        val source = FakeSource(payload, supportsRange = false, chunkDelayMs = 5, chunk = 2048)
        val installer = FakeInstaller(gameDir)
        val e = engine(source, installer)
        val id = e.enqueue(item())
        await(id) { it.bytesDone > 20_000 }
        e.pause(id); awaitState(id, DownloadState.Paused)
        e.resume(id); awaitState(id, DownloadState.Completed)
        assertTrue(installer.installed.single().second.contentEquals(payload))
    }

    @Test
    fun `transient failures are retried until it works`() = runBlocking {
        val failures = mutableListOf<SourceException>(SourceException.Network(java.io.IOException("x")), SourceException.BadResponse("503"))
        val source = FakeSource(payload, failures = failures)
        val id = engine(source).enqueue(item())
        val done = awaitState(id, DownloadState.Completed)
        assertEquals(3, source.opens.get())
        assertEquals(2, done.retries)
    }

    @Test
    fun `gives up after the retry budget`() = runBlocking {
        val failures = MutableList<SourceException>(10) { SourceException.Network(java.io.IOException("down")) }
        val source = FakeSource(payload, failures = failures)
        val id = engine(source).enqueue(item())
        val failed = awaitState(id, DownloadState.Failed)
        assertEquals(DownloadError.Network, failed.error)
        assertEquals(4, source.opens.get())   // first try + 3 retries
    }

    @Test
    fun `permanent errors are not retried`() = runBlocking {
        for ((error, expected) in listOf(
            SourceException.Unauthorized() to DownloadError.Unauthorized,
            SourceException.NotFound() to DownloadError.NotFound,
            SourceException.InsecureConnection() to DownloadError.Insecure,
        )) {
            val source = FakeSource(payload, failures = mutableListOf(error))
            val id = engine(source).enqueue(item())
            assertEquals(expected, awaitState(id, DownloadState.Failed).error)
            assertEquals(1, source.opens.get())
        }
    }

    @Test
    fun `a failed download can be retried by hand`() = runBlocking {
        val source = FakeSource(payload, failures = mutableListOf(SourceException.Unauthorized()))
        val e = engine(source)
        val id = e.enqueue(item())
        awaitState(id, DownloadState.Failed)
        e.resume(id)
        awaitState(id, DownloadState.Completed)
        Unit
    }

    @Test
    fun `a truncated transfer is treated as a network problem and retried`() = runBlocking {
        // First answer ends early (size says 200000, only a part arrives), then a good one.
        val half = payload.copyOf(50_000)
        var call = 0
        val source = object : GameSource by FakeSource(payload) {
            override fun open(entry: com.thorium.core.model.RemoteEntry, offset: Long) =
                if (call++ == 0) {
                    com.thorium.core.model.OpenedStream(java.io.ByteArrayInputStream(half), payload.size.toLong(), resumed = offset == 0L)
                } else {
                    FakeSource(payload).open(entry, offset)
                }
        }
        val installer = FakeInstaller(gameDir)
        val id = engine(source, installer).enqueue(item())
        awaitState(id, DownloadState.Completed)
        assertTrue(installer.installed.single().second.contentEquals(payload))
    }

    @Test
    fun `cancel stops the transfer and deletes the partial file`() = runBlocking {
        val source = FakeSource(payload, chunkDelayMs = 5, chunk = 2048)
        val e = engine(source)
        val id = e.enqueue(item())
        await(id) { it.bytesDone > 10_000 }
        e.cancel(id)
        awaitState(id, DownloadState.Cancelled)
        delay(200)
        assertFalse(File(workDir, "$id.part").exists())
        assertEquals(DownloadState.Cancelled, store.get(id)!!.state)
    }

    @Test
    fun `no more than the configured number run at once`() = runBlocking {
        val source = FakeSource(payload, chunkDelayMs = 3, chunk = 4096)
        val e = engine(source, config = DownloadConfig(maxConcurrent = 2, progressIntervalMs = 0, bufferSize = 4096))
        val ids = (1..5).map { e.enqueue(item("Game$it.gba")) }
        ids.forEach { awaitState(it, DownloadState.Completed) }
        assertTrue(source.maxConcurrent.get() <= 2, "max concurrent was ${source.maxConcurrent.get()}")
        assertTrue(source.maxConcurrent.get() >= 2, "should actually use both slots")
    }

    @Test
    fun `not enough space fails before downloading`() = runBlocking {
        val source = FakeSource(payload)
        val id = engine(source, freeSpace = { 10L }).enqueue(item())
        assertEquals(DownloadError.NoSpace, awaitState(id, DownloadState.Failed).error)
        assertEquals(0, source.opens.get())
    }

    @Test
    fun `an install failure is reported with its reason`() = runBlocking {
        val installer = FakeInstaller(gameDir, InstallException(DownloadError.Extraction, "bad archive"))
        val id = engine(FakeSource(payload), installer).enqueue(item())
        assertEquals(DownloadError.Extraction, awaitState(id, DownloadState.Failed).error)
        assertFalse(File(workDir, "$id.part").exists())
    }

    @Test
    fun `items interrupted by a previous run go back to the queue and finish`() = runBlocking {
        val stale = store.insert(item().copy(state = DownloadState.Downloading, bytesDone = 5))
        val e = engine(FakeSource(payload))
        e.start()
        awaitState(stale, DownloadState.Completed)
        Unit
    }

    @Test
    fun `an item with an unknown size downloads and completes`() = runBlocking {
        val id = engine(FakeSource(payload)).enqueue(item(size = null))
        val done = awaitState(id, DownloadState.Completed)
        assertEquals(payload.size.toLong(), done.sizeBytes ?: done.bytesDone)
    }

    @Test
    fun `clearFinished removes only finished items`() = runBlocking {
        val e = engine(FakeSource(payload, chunkDelayMs = 20, chunk = 1024))
        val doneId = e.enqueue(item("done.gba")); awaitState(doneId, DownloadState.Completed)
        val slow = e.enqueue(item("slow.gba")); await(slow) { it.state == DownloadState.Downloading }
        e.clearFinished()
        assertNull(store.get(doneId))
        assertTrue(store.get(slow) != null)
        e.cancel(slow)
    }
}
