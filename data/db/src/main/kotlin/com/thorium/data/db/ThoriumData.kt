package com.thorium.data.db

import android.content.Context
import androidx.room.Room

/** Entry point to persistence: one database shared by the library and settings repositories. */
class ThoriumData private constructor(db: ThoriumDatabase) {

    val library = LibraryRepository(db)
    val settings = SettingsRepository(db)

    companion object {
        fun create(context: Context): ThoriumData =
            ThoriumData(Room.databaseBuilder(context.applicationContext, ThoriumDatabase::class.java, "thorium.db").build())
    }
}
