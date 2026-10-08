package com.thorium.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SourcesDao {

    @Query("SELECT * FROM sources ORDER BY createdAt, id")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun get(id: Long): SourceEntity?

    @Insert
    suspend fun insert(source: SourceEntity): Long

    @Update
    suspend fun update(source: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sources SET lastCheckOk = :ok, lastCheckedAt = :at WHERE id = :id")
    suspend fun recordCheck(id: Long, ok: Int, at: Long)
}
