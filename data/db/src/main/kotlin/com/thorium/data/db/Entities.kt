package com.thorium.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * A logical game. Rows are never deleted when a game disappears from disk: they are flagged
 * [missing] so favorites and (later) play history survive an unplugged SD card.
 */
@Entity(tableName = "games", indices = [Index("systemId")])
data class GameEntity(
    @PrimaryKey val id: String,
    val title: String,
    val systemId: String,
    /** When the game first appeared in the library (see [SyncPlanner] for the first-scan rule). */
    val addedAt: Long,
    val lastSeenAt: Long,
    val isDuplicate: Boolean,
    val missing: Boolean,
)

@Entity(
    tableName = "game_files",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class GameFileEntity(
    @PrimaryKey val path: String,
    val gameId: String,
    val sizeBytes: Long,
    val extension: String,
    val isArchive: Boolean,
    val lastModified: Long,
    val disc: Int?,
)

/** Deliberately has no foreign key to games: a favorite must survive a game going missing. */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val gameId: String,
    val addedAt: Long,
)

/** A folder the user added by hand to be scanned, in addition to the auto-detected storage. */
@Entity(tableName = "scan_roots")
data class ScanRootEntity(
    @PrimaryKey val path: String,
    val addedAt: Long,
)

/** Simple key/value preferences (booleans stored as "1" / "0"). */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** A source the user configured. [passwordEnc] is encrypted with a key that never leaves the Android Keystore. */
@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** [com.thorium.core.model.SourceType] name. */
    val type: String,
    val location: String,
    val username: String,
    val passwordEnc: String?,
    val allowInsecure: Boolean,
    val enabled: Boolean,
    val createdAt: Long,
    /** 1 = last test worked, 0 = failed, null = never tested. */
    val lastCheckOk: Int?,
    val lastCheckedAt: Long?,
)

data class GameWithFiles(
    @Embedded val game: GameEntity,
    @Relation(parentColumn = "id", entityColumn = "gameId") val files: List<GameFileEntity>,
)
