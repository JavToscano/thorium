package com.thorium.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.thorium.core.model.Game
import com.thorium.core.model.GameFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SyncSummary(val added: Int, val updated: Int, val missing: Int)

/** The only entry point to persistence; no Room type leaks out of this module. */
class LibraryRepository private constructor(private val db: ThoriumDatabase) {

    private val dao = db.libraryDao()

    /** Games currently present on disk, with their files. Emits again whenever the data changes. */
    fun observeGames(): Flow<List<Game>> =
        dao.observeGames().map { rows -> rows.mapNotNull { it.toGame() } }

    fun observeFavoriteIds(): Flow<List<String>> = dao.observeFavoriteIds()

    suspend fun setFavorite(gameId: String, favorite: Boolean, now: Long = System.currentTimeMillis()) {
        if (favorite) dao.upsertFavorite(FavoriteEntity(gameId, now)) else dao.deleteFavorite(gameId)
    }

    /** Merges a fresh scan into the database in one transaction. */
    suspend fun sync(scanned: List<Game>, now: Long = System.currentTimeMillis()): SyncSummary =
        db.withTransaction {
            val plan = SyncPlanner.plan(
                stored = dao.allGames().map { StoredGame(it.id, it.addedAt, it.missing) },
                scanned = scanned,
                now = now,
            )
            plan.upserts.chunked(CHUNK).forEach { dao.upsertGames(it) }
            scanned.map { it.id }.chunked(CHUNK).forEach { dao.deleteFilesOf(it) }
            scanned.flatMap { game -> game.files.map { it.toEntity(game.id) } }
                .chunked(CHUNK).forEach { dao.insertFiles(it) }
            plan.missingIds.chunked(CHUNK).forEach { dao.markMissing(it) }
            SyncSummary(plan.added, plan.updated, plan.missingIds.size)
        }

    companion object {
        // SQLite limits the number of bound variables per statement.
        private const val CHUNK = 400

        fun create(context: Context): LibraryRepository =
            LibraryRepository(
                Room.databaseBuilder(context.applicationContext, ThoriumDatabase::class.java, "thorium.db").build()
            )
    }
}

private fun GameWithFiles.toGame(): Game? {
    if (files.isEmpty()) return null
    return Game(
        id = game.id,
        title = game.title,
        systemId = game.systemId,
        files = files.sortedWith(compareBy({ it.disc ?: 0 }, { it.path })).map {
            GameFile(it.path, it.sizeBytes, it.extension, it.isArchive, it.lastModified, it.disc)
        },
        addedAt = game.addedAt,
        isDuplicate = game.isDuplicate,
    )
}

private fun GameFile.toEntity(gameId: String) =
    GameFileEntity(path, gameId, sizeBytes, extension, isArchive, lastModified, disc)
