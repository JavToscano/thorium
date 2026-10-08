package com.thorium.data.db

import android.content.Context
import androidx.room.Room

/** Entry point to persistence: one database shared by the library and settings repositories. */
class ThoriumData private constructor(db: ThoriumDatabase) {

    val library = LibraryRepository(db)
    val settings = SettingsRepository(db)
    val sources = SourcesRepository(db, SecretBox())
    val downloads: com.thorium.core.model.DownloadStore = RoomDownloadStore(db.downloadsDao())

    companion object {
        fun create(context: Context): ThoriumData =
            ThoriumData(Room.databaseBuilder(context.applicationContext, ThoriumDatabase::class.java, "thorium.db").build())
    }
}
