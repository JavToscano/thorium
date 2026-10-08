package com.thorium.app.downloads

import com.thorium.core.model.DownloadItem
import com.thorium.core.model.DownloadState
import com.thorium.core.model.RemoteEntry
import com.thorium.core.model.SourceConfig
import com.thorium.core.model.SourceException
import com.thorium.data.db.ThoriumData
import com.thorium.data.downloads.DownloadEngine
import com.thorium.data.library.DestinationResolver
import com.thorium.data.library.LibraryScanner
import com.thorium.data.library.PlatformDefinition
import com.thorium.data.library.TitleNormalizer
import com.thorium.data.sources.SourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.URLDecoder

/** Result of asking for a download. */
sealed interface DownloadRequest {
    data class Queued(val id: Long, val platform: PlatformDefinition) : DownloadRequest

    /** The console could not be worked out; the user has to pick one. */
    data object NeedsPlatform : DownloadRequest

    /** There is no game folder to put it in (no storage, or the folder cannot be created). */
    data object NoFolder : DownloadRequest
}

/**
 * Ties the engine to the app: builds download items (working out the console and its folder),
 * opens the right source for each item, and refreshes the library once a game is installed.
 */
class DownloadManager(
    private val data: ThoriumData,
    /** The folders games live in (auto-detected storage plus the ones the user added). */
    private val gameRoots: suspend () -> List<File>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val destinations = DestinationResolver()
    private val scanner = LibraryScanner()

    private val engine = DownloadEngine(
        store = data.downloads,
        sources = { id -> openSource(id) },
        installer = GameInstaller(afterInstall = ::refreshLibrary),
        scope = scope,
    )

    val items: Flow<List<DownloadItem>> = data.downloads.observeAll()

    /** Resumes whatever a previous run left unfinished. */
    fun start() {
        scope.launch { engine.start() }
    }

    /**
     * Queues [entry] from [source]. The console comes from [platform] when the user chose one,
     * otherwise it is detected; if it cannot be, the caller must ask and call again.
     */
    suspend fun request(source: SourceConfig, entry: RemoteEntry, platform: PlatformDefinition? = null): DownloadRequest {
        val detected = platform ?: destinations.detectPlatform(entry.platformId, entry.name, pathOf(entry.ref))
            ?: return DownloadRequest.NeedsPlatform
        // The same file already waiting or running is not queued twice.
        data.downloads.all()
            .firstOrNull { it.sourceId == source.id && it.entryRef == entry.ref && !it.state.isFinished }
            ?.let { return DownloadRequest.Queued(it.id, detected) }
        val destination = withContext(Dispatchers.IO) { destinations.resolve(detected, gameRoots()) }
            ?: return DownloadRequest.NoFolder
        val id = engine.enqueue(
            DownloadItem(
                sourceId = source.id,
                title = TitleNormalizer.parse(entry.name.substringAfterLast('/').substringBeforeLast('.')).title,
                entryName = entry.name,
                entryRef = entry.ref,
                platformId = detected.id,
                destinationDir = destination.platformDir.path,
                workDir = destination.workDir.path,
                sizeBytes = entry.sizeBytes,
                sha1 = entry.sha1,
            )
        )
        return DownloadRequest.Queued(id, detected)
    }

    fun pause(id: Long) { scope.launch { engine.pause(id) } }
    fun resume(id: Long) { scope.launch { engine.resume(id) } }
    fun cancel(id: Long) { scope.launch { engine.cancel(id) } }
    fun remove(id: Long) { scope.launch { engine.remove(id) } }
    fun clearFinished() { scope.launch { engine.clearFinished() } }

    private suspend fun openSource(id: Long) = withContext(Dispatchers.IO) {
        val config = data.sources.config(id) ?: throw SourceException.NotFound()
        SourceFactory.create(config, data.sources.password(id))
    }

    /** Scans just the console folder that changed and merges it, leaving the rest of the library alone. */
    private suspend fun refreshLibrary(consoleFolder: File) {
        val scanned = withContext(Dispatchers.IO) { scanner.scan(listOf(consoleFolder)) }
        data.library.sync(scanned.library.games, markMissing = false)
    }

    /** The folders in the source path of [ref] (`.../gba/Game.zip` gives `gba`), used to guess the console. */
    private fun pathOf(ref: String): List<String> = try {
        URI(ref).rawPath.orEmpty().split('/').dropLast(1).filter { it.isNotEmpty() }
            .map { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
    } catch (e: Exception) {
        emptyList()
    }
}
