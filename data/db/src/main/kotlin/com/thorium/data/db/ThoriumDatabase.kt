package com.thorium.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [GameEntity::class, GameFileEntity::class, FavoriteEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ThoriumDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}
