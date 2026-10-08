package com.thorium.data.db

import com.thorium.core.model.Game
import com.thorium.core.model.GameFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SyncPlannerTest {

    private fun game(id: String, addedAt: Long = 100, duplicate: Boolean = false) = Game(
        id = id,
        title = id,
        systemId = "gba",
        files = listOf(GameFile("/roms/$id.gba", 1, "gba", false, addedAt)),
        addedAt = addedAt,
        isDuplicate = duplicate,
    )

    @Test
    fun `first scan keeps the file modification time as addedAt`() {
        val plan = SyncPlanner.plan(emptyList(), listOf(game("a", addedAt = 42)), now = 1000)
        assertEquals(42, plan.upserts.single().addedAt)
        assertEquals(1, plan.added)
    }

    @Test
    fun `new game found on a later scan gets the current time`() {
        val stored = listOf(StoredGame("a", addedAt = 5, missing = false))
        val plan = SyncPlanner.plan(stored, listOf(game("a"), game("b", addedAt = 42)), now = 1000)
        assertEquals(1000, plan.upserts.single { it.id == "b" }.addedAt)
        assertEquals(1, plan.added)
        assertEquals(1, plan.updated)
    }

    @Test
    fun `known game keeps its original addedAt`() {
        val stored = listOf(StoredGame("a", addedAt = 5, missing = false))
        val plan = SyncPlanner.plan(stored, listOf(game("a", addedAt = 999)), now = 1000)
        assertEquals(5, plan.upserts.single().addedAt)
        assertEquals(1000, plan.upserts.single().lastSeenAt)
    }

    @Test
    fun `game absent from the scan is flagged missing, not deleted`() {
        val stored = listOf(StoredGame("a", 5, false), StoredGame("b", 6, false))
        val plan = SyncPlanner.plan(stored, listOf(game("a")), now = 1000)
        assertEquals(listOf("b"), plan.missingIds)
    }

    @Test
    fun `already missing game is not flagged again`() {
        val stored = listOf(StoredGame("a", 5, true))
        assertEquals(emptyList<String>(), SyncPlanner.plan(stored, emptyList(), now = 1000).missingIds)
    }

    @Test
    fun `game that comes back is no longer missing`() {
        val stored = listOf(StoredGame("a", 5, true))
        val plan = SyncPlanner.plan(stored, listOf(game("a")), now = 1000)
        assertFalse(plan.upserts.single().missing)
        assertEquals(5, plan.upserts.single().addedAt)
    }

    @Test
    fun `duplicate flag is refreshed from the scan`() {
        val stored = listOf(StoredGame("a", 5, false))
        assertEquals(true, SyncPlanner.plan(stored, listOf(game("a", duplicate = true)), now = 1).upserts.single().isDuplicate)
    }
}
