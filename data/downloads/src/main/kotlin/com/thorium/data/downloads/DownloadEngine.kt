package com.thorium.data.downloads

import com.thorium.core.model.DownloadError
import com.thorium.core.model.DownloadItem
import com.thorium.core.model.DownloadState
import com.thorium.core.model.DownloadStore
import com.thorium.core.model.InstallException
import com.thorium.core.model.Installer
import com.thorium.core.model.RemoteEntry
import com.thorium.core.model.SourceException
import com.thorium.core.model.SourceResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Tunables of the engine. */
data class DownloadConfig(
    val maxConcurrent: Int = 2,
    /** Automatic retries for transient failures, with growing waits between them. */
    val maxRetries: Int = 3,
    val backoffMs: List<Long> = listOf(2_000L, 8_000L, 30_000L),
    val progressIntervalMs: Long = 500,
    val bufferSize: Int = 64 * 1024,
)

/**
 * Runs the download queue.
 *
 * Each item goes Queued → Downloading → Verifying → (Extracting → Installing) → Completed, with
 * Paused, Failed and Cancelled on the side. The partial file stays in the item's work folder, so a
 * paused download, a failed one that is retried, or one interrupted by the app dying continues from
 * where it stopped when the source supports resuming. State lives in the [DownloadStore]; the
 * engine keeps only the running jobs.
 */
class DownloadEngine(
    private val store: DownloadStore,
    private val sources: SourceResolver,
    private val installer: Installer,
    private val scope: CoroutineScope,
    private val config: DownloadConfig = DownloadConfig(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val freeSpace: (File) -> Long = { it.usableSpace },
) {

    private val running = ConcurrentHashMap<Long, Job>()
    private val retryAt = ConcurrentHashMap<Long, Long>()
    private val scheduleLock = Mutex()

    /** Call once at startup: anything left mid-flight by a previous run goes back to the queue. */
    suspend fun start() {
        store.all().filter { it.state.isActive }.forEach { store.update(it.copy(state = DownloadState.Queued)) }
        schedule()
    }

    suspend fun enqueue(item: DownloadItem): Long {
        val id = store.insert(item.copy(state = DownloadState.Queued, bytesDone = 0, error = null, retries = 0, createdAt = clock()))
        schedule()
        return id
    }

    /** Stops the transfer but keeps the partial file so [resume] continues from there. */
    suspend fun pause(id: Long) {
        val item = store.get(id) ?: return
        if (item.state.isFinished || item.state == DownloadState.Paused) return
        // State first: late progress writes from the stopping job are ignored once it is not Downloading.
        store.update(item.copy(state = DownloadState.Paused))
        // Wait for the transfer to stop writing before anyone can resume it from the partial file.
        running.remove(id)?.cancelAndJoin()
        retryAt.remove(id)
    }

    /** Resumes a paused download, or retries a failed one from scratch of its retry budget. */
    suspend fun resume(id: Long) {
        val item = store.get(id) ?: return
        if (item.state != DownloadState.Paused && item.state != DownloadState.Failed) return
        store.update(item.copy(state = DownloadState.Queued, error = null, retries = 0))
        schedule()
    }

    /** Stops the transfer and deletes everything it left behind. */
    suspend fun cancel(id: Long) {
        val item = store.get(id) ?: return
        if (item.state.isFinished) return
        store.update(item.copy(state = DownloadState.Cancelled))
        running.remove(id)?.cancelAndJoin()
        retryAt.remove(id)
        partFile(item).delete()
        scope.launch { schedule() }
    }

    /** Removes a finished item from the list. */
    suspend fun remove(id: Long) {
        val item = store.get(id) ?: return
        if (!item.state.isFinished) cancel(id)
        partFile(item).delete()
        store.delete(id)
    }

    suspend fun clearFinished() {
        store.all().filter { it.state.isFinished }.forEach { store.delete(it.id) }
    }

    /** Starts queued items while there is room for them. Safe to call at any time. */
    suspend fun schedule(): Unit = scheduleLock.withLock {
        val now = clock()
        val queued = store.all()
            .filter { it.state == DownloadState.Queued && (retryAt[it.id] ?: 0L) <= now && running[it.id] == null }
            .sortedBy { it.id }
        var free = config.maxConcurrent - running.size
        for (item in queued) {
            if (free <= 0) break
            free--
            val job = scope.launch { run(item.id) }
            running[item.id] = job
            job.invokeOnCompletion {
                running.remove(item.id, job)
                scope.launch { schedule() }
            }
        }
    }

    // ---------------------------------------------------------------- one download

    private suspend fun run(id: Long) {
        var item = store.get(id) ?: return
        if (item.state != DownloadState.Queued) return
        val part = partFile(item)
        try {
            item = save(item, DownloadState.Downloading, error = null) ?: return
            val source = sources.resolve(item.sourceId)
            val entry = RemoteEntry(item.entryName, item.entryRef, false, item.sizeBytes, item.platformId, item.sha1)
            File(item.workDir).mkdirs()

            var offset = if (part.exists()) part.length() else 0L
            val expected = item.sizeBytes
            if (expected != null && offset > expected) { part.delete(); offset = 0 }

            if (expected == null || offset < expected) {
                if (expected != null && freeSpace(File(item.workDir)) < expected - offset) return fail(item, DownloadError.NoSpace)
                download(item, { off -> source.open(entry, off) }, part, offset)
                item = store.get(id) ?: return
                if (item.state != DownloadState.Downloading) return
            }

            item = save(item, DownloadState.Verifying) ?: return
            val verdict = verify(item, part)
            if (verdict != null) {
                // A truncated transfer is worth another try; a wrong checksum is not.
                if (verdict == DownloadError.Network) return transient(item, DownloadError.Network)
                part.delete()
                return fail(item, verdict)
            }

            // The stage change is saved before the installer continues, so it can never land after a failure.
            val installedPath = installer.install(item, part) { stage -> save(item, stage) }
            part.delete()
            store.get(id)?.takeIf { it.state != DownloadState.Cancelled }?.let {
                store.update(it.copy(state = DownloadState.Completed, installedPath = installedPath, bytesDone = it.sizeBytes ?: it.bytesDone, error = null))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SourceException) {
            handleSourceError(item, e)
        } catch (e: InstallException) {
            part.delete()
            fail(item, e.reason)
        } catch (e: IOException) {
            if (isNoSpace(e)) fail(item, DownloadError.NoSpace) else fail(item, DownloadError.Storage)
        } catch (e: Exception) {
            fail(item, DownloadError.Unknown)
        }
    }

    /** Streams the file into [part], appending when the source honoured the resume offset. */
    private suspend fun download(item: DownloadItem, open: (Long) -> com.thorium.core.model.OpenedStream, part: File, requestedOffset: Long) {
        val opened = withContext(io) { open(requestedOffset) }
        val appending = requestedOffset > 0 && opened.resumed
        if (requestedOffset > 0 && !opened.resumed) part.delete()
        val startAt = if (appending) requestedOffset else 0L
        val total = opened.totalSize ?: item.sizeBytes

        coroutineScope {
            // A blocked socket read does not notice cancellation, so closing the stream is what stops it.
            val closer = launch {
                try { awaitCancellation() } finally { withContext(NonCancellable) { runCatching { opened.stream.close() } } }
            }
            try {
                withContext(io) { copy(item, opened.stream, part, appending, startAt, total) }
            } finally {
                closer.cancel()
            }
        }
    }

    private suspend fun copy(item: DownloadItem, input: InputStream, part: File, append: Boolean, startAt: Long, total: Long?) {
        var done = startAt
        var lastReport = clock()
        val buffer = ByteArray(config.bufferSize)
        part.parentFile?.mkdirs()
        java.io.FileOutputStream(part, append).use { out ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                done += read
                currentCoroutineContext().ensureActive()
                val now = clock()
                if (now - lastReport >= config.progressIntervalMs) {
                    lastReport = now
                    reportProgress(item.id, done, total)
                }
            }
        }
        reportProgress(item.id, done, total)
    }

    /** Writes progress only while the item is still downloading, so a pause or cancel is never undone. */
    private suspend fun reportProgress(id: Long, done: Long, total: Long?) {
        val current = store.get(id) ?: return
        if (current.state != DownloadState.Downloading) return
        store.update(current.copy(bytesDone = done, sizeBytes = total ?: current.sizeBytes))
    }

    /** Null when the file is fine; otherwise why it is not. */
    private fun verify(item: DownloadItem, part: File): DownloadError? {
        val expected = item.sizeBytes ?: return checksumOnly(item, part)
        if (part.length() < expected) return DownloadError.Network
        if (part.length() > expected) return DownloadError.Verification
        return checksumOnly(item, part)
    }

    private fun checksumOnly(item: DownloadItem, part: File): DownloadError? {
        val wanted = item.sha1?.lowercase() ?: return null
        val digest = MessageDigest.getInstance("SHA-1")
        part.inputStream().use { input ->
            val buffer = ByteArray(config.bufferSize)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return if (actual == wanted) null else DownloadError.Verification
    }

    // ---------------------------------------------------------------- failures

    private suspend fun handleSourceError(item: DownloadItem, e: SourceException) {
        when (e) {
            is SourceException.Network, is SourceException.BadResponse -> transient(item, DownloadError.Network)
            is SourceException.Unauthorized -> fail(item, DownloadError.Unauthorized)
            is SourceException.NotFound -> fail(item, DownloadError.NotFound)
            is SourceException.InsecureConnection -> fail(item, DownloadError.Insecure)
            is SourceException.InvalidLocation -> fail(item, DownloadError.Unknown)
        }
    }

    /** Puts the item back in the queue after a wait, or gives up once the retry budget is spent. */
    private suspend fun transient(item: DownloadItem, error: DownloadError) {
        val current = store.get(item.id) ?: return
        if (current.state == DownloadState.Paused || current.state == DownloadState.Cancelled) return
        if (current.retries >= config.maxRetries) return fail(current, error)
        val wait = config.backoffMs.getOrElse(current.retries) { config.backoffMs.lastOrNull() ?: 0L }
        retryAt[item.id] = clock() + wait
        store.update(current.copy(state = DownloadState.Queued, retries = current.retries + 1, error = error))
        scope.launch { delay(wait); schedule() }
    }

    private suspend fun fail(item: DownloadItem, error: DownloadError) {
        val current = store.get(item.id) ?: return
        if (current.state == DownloadState.Paused || current.state == DownloadState.Cancelled) return
        store.update(current.copy(state = DownloadState.Failed, error = error))
    }

    private suspend fun save(item: DownloadItem, state: DownloadState, error: DownloadError? = item.error): DownloadItem? {
        val current = store.get(item.id) ?: return null
        // Never override a pause or cancel that arrived in the meantime.
        if (current.state == DownloadState.Paused || current.state == DownloadState.Cancelled) return null
        val updated = current.copy(state = state, error = error)
        store.update(updated)
        return updated
    }

    private fun partFile(item: DownloadItem) = File(item.workDir, "${item.id}.part")

    private fun isNoSpace(e: IOException) = e.message?.contains("No space left", ignoreCase = true) == true ||
        e.message?.contains("ENOSPC", ignoreCase = true) == true
}
