package com.thorium.data.downloads

import com.thorium.core.model.DownloadItem
import com.thorium.core.model.DownloadState
import com.thorium.core.model.DownloadStore
import com.thorium.core.model.GameSource
import com.thorium.core.model.InstallException
import com.thorium.core.model.Installer
import com.thorium.core.model.OpenedStream
import com.thorium.core.model.RemoteEntry
import com.thorium.core.model.SourceException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

class FakeStore : DownloadStore {
    private val items = MutableStateFlow<List<DownloadItem>>(emptyList())
    private val next = java.util.concurrent.atomic.AtomicLong(1)
    override fun observeAll(): Flow<List<DownloadItem>> = items
    override suspend fun get(id: Long) = items.value.firstOrNull { it.id == id }
    override suspend fun all() = items.value
    override suspend fun insert(item: DownloadItem): Long {
        val id = next.getAndIncrement()
        items.update { it + item.copy(id = id) }
        return id
    }
    override suspend fun update(item: DownloadItem) { items.update { list -> list.map { if (it.id == item.id) item else it } } }
    override suspend fun delete(id: Long) { items.update { list -> list.filter { it.id != id } } }
}

/** A source serving [data]; behaviour is tuned per test. */
class FakeSource(
    private val data: ByteArray,
    private val supportsRange: Boolean = true,
    /** Failures to throw, one per call to open, before it starts working. */
    private val failures: MutableList<SourceException> = mutableListOf(),
    /** Pause between chunks so a test can interrupt a transfer. */
    private val chunkDelayMs: Long = 0,
    private val chunk: Int = 1024,
) : GameSource {

    val offsets = mutableListOf<Long>()
    val opens = AtomicInteger()
    val concurrent = AtomicInteger()
    val maxConcurrent = AtomicInteger()

    override fun testConnection() = Result.success(Unit)
    override fun list(ref: String?) = emptyList<RemoteEntry>()

    override fun open(entry: RemoteEntry, offset: Long): OpenedStream {
        opens.incrementAndGet()
        synchronized(offsets) { offsets += offset }
        if (failures.isNotEmpty()) throw failures.removeAt(0)
        val honoured = supportsRange && offset > 0
        val start = if (honoured) offset.toInt() else 0
        val stream = SlowStream(data, start, chunkDelayMs, chunk).also {
            val now = concurrent.incrementAndGet()
            maxConcurrent.updateAndGet { m -> maxOf(m, now) }
            it.onClose = { concurrent.decrementAndGet() }
        }
        return OpenedStream(stream, data.size.toLong(), resumed = honoured || offset == 0L)
    }
}

class SlowStream(private val data: ByteArray, start: Int, private val delayMs: Long, private val chunk: Int) : InputStream() {
    private val inner = ByteArrayInputStream(data, start, data.size - start)
    @Volatile private var closed = false
    var onClose: () -> Unit = {}

    override fun read(): Int = inner.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (closed) throw IOException("closed")
        if (delayMs > 0) Thread.sleep(delayMs)
        if (closed) throw IOException("closed")
        return inner.read(b, off, minOf(len, chunk))
    }
    override fun close() { if (!closed) { closed = true; onClose() } }
}

/** Copies the finished file into [dir]; optionally fails. */
class FakeInstaller(private val dir: File, private val failWith: InstallException? = null) : Installer {
    val installed = mutableListOf<Pair<DownloadItem, ByteArray>>()
    override suspend fun install(item: DownloadItem, downloaded: File, stage: suspend (DownloadState) -> Unit): String {
        stage(DownloadState.Installing)
        failWith?.let { throw it }
        val target = File(dir, item.entryName)
        downloaded.copyTo(target, overwrite = true)
        synchronized(installed) { installed += item to target.readBytes() }
        return target.path
    }
}
