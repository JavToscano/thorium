package com.thorium.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT path FROM scan_roots ORDER BY addedAt")
    fun observeScanRoots(): Flow<List<String>>

    @Query("SELECT path FROM scan_roots ORDER BY addedAt")
    suspend fun getScanRoots(): List<String>

    @Upsert
    suspend fun upsertScanRoot(root: ScanRootEntity)

    @Query("DELETE FROM scan_roots WHERE path = :path")
    suspend fun deleteScanRoot(path: String)

    @Query("SELECT value FROM settings WHERE key = :key")
    fun observeSetting(key: String): Flow<String?>

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun getSetting(key: String): String?

    @Upsert
    suspend fun upsertSetting(setting: SettingEntity)
}
