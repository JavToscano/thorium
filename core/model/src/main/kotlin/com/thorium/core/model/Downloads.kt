package com.thorium.core.model

import kotlinx.coroutines.flow.Flow
import java.io.File

enum class DownloadState {
    Queued, Downloading, Paused, Verifying, Extracting, Installing, Completed, Failed, Cancelled;

    /** Work is in progress right now (a worker holds the item). */
    val isActive: Boolean get() = this == Downloading || this == Verifying || this == Extracting || this == Installing

    val isFinished: Boolean get() = this == Completed || this == Failed || this == Cancelled
}

/** Why a download failed, in terms the UI can explain and translate. */
enum class DownloadError { Network, Unauthorized, NotFound, Insecure, NoSpace, Verification, Extraction, Storage, Unknown }

/**
 * A file being fetched from a source and installed into the folder of its console.
 *
 * [destinationDir] is the console folder the game ends up in; [workDir] is a hidden scratch folder on
 * the same volume (so moving the finished file is instant, never a copy).
 */
data class DownloadItem(
    val id: Long = 0,
    val sourceId: Long,
    val title: String,
    val entryName: String,
    /** Reference the source understands when opening the file. */
    val entryRef: String,
    val platformId: String,
    val destinationDir: String,
    val workDir: String,
    val sizeBytes: Long? = null,
    val sha1: String? = null,
    val state: DownloadState = DownloadState.Queued,
    val bytesDone: Long = 0,
    val error: DownloadError? = null,
    /** Failed attempts so far; transient errors are retried automatically. */
    val retries: Int = 0,
    val createdAt: Long = 0,
    /** Where the game ended up, once installed. */
    val installedPath: String? = null,
) {
    /** 0..1, or null when the total size is not known. */
    val progress: Float?
        get() = sizeBytes?.takeIf { it > 0 }?.let { (bytesDone.toFloat() / it).coerceIn(0f, 1f) }
}

/** Persistence the download engine needs; implemented by the database module. */
interface DownloadStore {
    fun observeAll(): Flow<List<DownloadItem>>
    suspend fun get(id: Long): DownloadItem?
    suspend fun all(): List<DownloadItem>
    suspend fun insert(item: DownloadItem): Long
    suspend fun update(item: DownloadItem)
    suspend fun delete(id: Long)
}

/** Finds the configured source (with its password) a download belongs to. */
fun interface SourceResolver {
    /** @throws SourceException.NotFound when the source was deleted. */
    suspend fun resolve(sourceId: Long): GameSource
}

/** Why installing a finished download failed. */
class InstallException(val reason: DownloadError, message: String, cause: Throwable? = null) : Exception(message, cause)

/** Turns a finished download (possibly an archive) into a game in its console folder. */
interface Installer {
    /**
     * @param stage called when work moves to [DownloadState.Extracting] or [DownloadState.Installing].
     * @return the path of the installed game (a file, or a folder for games made of several files).
     * @throws InstallException when it cannot be installed.
     */
    suspend fun install(item: DownloadItem, downloaded: File, stage: suspend (DownloadState) -> Unit): String
}
