package com.thorium.data.db

import com.thorium.core.model.Game

/** What the database already knows about a game; enough to decide how to sync it. */
data class StoredGame(val id: String, val addedAt: Long, val missing: Boolean)

data class SyncPlan(
    val upserts: List<GameEntity>,
    /** Games that are in the database, not flagged missing yet, and absent from the scan. */
    val missingIds: List<String>,
    val added: Int,
    val updated: Int,
)

/**
 * Pure decision logic for merging a fresh scan into the database, kept free of Room so it can be
 * unit tested on the JVM.
 *
 * Rules:
 *  - known games keep their original `addedAt` and are un-flagged if they were missing;
 *  - new games get `addedAt = now`, except on the very first scan (empty database), where the
 *    files' own modification time is used so "Recently Added" is meaningful from day one;
 *  - games that were not found are flagged missing, never deleted.
 */
object SyncPlanner {

    fun plan(stored: List<StoredGame>, scanned: List<Game>, now: Long): SyncPlan {
        val storedById = stored.associateBy { it.id }
        val firstScan = stored.isEmpty()
        var added = 0
        var updated = 0
        val upserts = scanned.map { game ->
            val known = storedById[game.id]
            if (known == null) added++ else updated++
            GameEntity(
                id = game.id,
                title = game.title,
                systemId = game.systemId,
                addedAt = known?.addedAt ?: if (firstScan) game.addedAt else now,
                lastSeenAt = now,
                isDuplicate = game.isDuplicate,
                missing = false,
            )
        }
        val scannedIds = scanned.mapTo(HashSet()) { it.id }
        val missing = stored.filter { !it.missing && it.id !in scannedIds }.map { it.id }
        return SyncPlan(upserts, missing, added, updated)
    }
}
