package com.thorium.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Transaction
    @Query("SELECT * FROM games WHERE missing = 0")
    fun observeGames(): Flow<List<GameWithFiles>>

    @Query("SELECT * FROM games")
    suspend fun allGames(): List<GameEntity>

    @Upsert
    suspend fun upsertGames(games: List<GameEntity>)

    @Query("DELETE FROM game_files WHERE gameId IN (:gameIds)")
    suspend fun deleteFilesOf(gameIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<GameFileEntity>)

    @Query("UPDATE games SET missing = 1 WHERE id IN (:gameIds)")
    suspend fun markMissing(gameIds: List<String>)

    @Query("SELECT gameId FROM favorites ORDER BY addedAt")
    fun observeFavoriteIds(): Flow<List<String>>

    @Upsert
    suspend fun upsertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE gameId = :gameId")
    suspend fun deleteFavorite(gameId: String)
}
