package com.thorium.data.library

import com.thorium.core.model.Game
import com.thorium.core.model.GameSystem
import com.thorium.core.model.Library

/** Placeholder data for Phase 4. Replaced by the real library (Phase 5/6). */
object FakeLibrary {

    private val systems = listOf(
        GameSystem("n64", "Nintendo 64", "N64", 0f),
        GameSystem("gba", "Game Boy Advance", "GBA", 265f),
        GameSystem("gbc", "Game Boy Color", "GBC", 120f),
        GameSystem("3ds", "Nintendo 3DS", "3DS", 350f),
        GameSystem("gc", "GameCube", "GC", 285f),
        GameSystem("ps1", "PlayStation", "PS1", 215f),
        GameSystem("ps2", "PlayStation 2", "PS2", 235f),
        GameSystem("psp", "PSP", "PSP", 190f),
        GameSystem("switch", "Switch", "NSW", 5f),
    )

    fun create(): Library {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val games = systems.flatMapIndexed { si, system ->
            (1..6).map { n ->
                val index = si * 6 + n
                Game(
                    id = "${system.id}-$n",
                    title = "${system.shortName} Sample Game $n",
                    systemId = system.id,
                    addedAt = now - index * day / 2,
                    lastPlayedAt = if (n == 1 && si < 5) now - si * day / 3 else null,
                    progress = if (n == 1 && si < 5) 0.15f + si * 0.17f else null,
                    sizeBytes = (50L + index * 37L) * 1_048_576L,
                    path = "/storage/emulated/0/ROMs/${system.shortName}/Sample Game $n.zip",
                )
            }
        }
        return Library(
            systems = systems,
            games = games,
            initialFavorites = listOf("n64-2", "gba-3", "ps2-1", "3ds-4"),
        )
    }
}
