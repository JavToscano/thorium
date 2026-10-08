package com.thorium.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        GameEntity::class,
        GameFileEntity::class,
        FavoriteEntity::class,
        ScanRootEntity::class,
        SettingEntity::class,
        SourceEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // v1 -> v2: adds scan_roots and settings (new tables only).
        AutoMigration(from = 1, to = 2),
        // v2 -> v3: adds sources (new table only).
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class ThoriumDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun sourcesDao(): SourcesDao
}
