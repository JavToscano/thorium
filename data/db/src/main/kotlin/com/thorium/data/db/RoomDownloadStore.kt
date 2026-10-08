package com.thorium.data.db

import com.thorium.core.model.DownloadError
import com.thorium.core.model.DownloadItem
import com.thorium.core.model.DownloadState
import com.thorium.core.model.DownloadStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The download queue, kept in the database so it survives the app being closed or killed. */
internal class RoomDownloadStore(private val dao: DownloadsDao) : DownloadStore {

    override fun observeAll(): Flow<List<DownloadItem>> = dao.observeAll().map { rows -> rows.map { it.toItem() } }
    override suspend fun get(id: Long) = dao.get(id)?.toItem()
    override suspend fun all() = dao.all().map { it.toItem() }
    override suspend fun insert(item: DownloadItem) = dao.insert(item.toEntity())
    override suspend fun update(item: DownloadItem) = dao.update(item.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)
}

private fun DownloadEntity.toItem() = DownloadItem(
    id = id, sourceId = sourceId, title = title, entryName = entryName, entryRef = entryRef,
    platformId = platformId, destinationDir = destinationDir, workDir = workDir,
    sizeBytes = sizeBytes, sha1 = sha1,
    state = runCatching { DownloadState.valueOf(state) }.getOrDefault(DownloadState.Failed),
    bytesDone = bytesDone,
    error = error?.let { runCatching { DownloadError.valueOf(it) }.getOrNull() },
    retries = retries, createdAt = createdAt, installedPath = installedPath,
)

private fun DownloadItem.toEntity() = DownloadEntity(
    id = id, sourceId = sourceId, title = title, entryName = entryName, entryRef = entryRef,
    platformId = platformId, destinationDir = destinationDir, workDir = workDir,
    sizeBytes = sizeBytes, sha1 = sha1, state = state.name, bytesDone = bytesDone,
    error = error?.name, retries = retries, createdAt = createdAt, installedPath = installedPath,
)
