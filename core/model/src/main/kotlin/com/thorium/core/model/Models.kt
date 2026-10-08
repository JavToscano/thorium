package com.thorium.core.model

/** A console / platform as shown in the UI (not tied to any emulator). */
data class GameSystem(
    val id: String,
    val name: String,
    val shortName: String,
    val hue: Float,
)

/** One physical file belonging to a game (a game can have several: discs, patches, archives). */
data class GameFile(
    val path: String,
    val sizeBytes: Long,
    val extension: String,
    val isArchive: Boolean,
    val lastModified: Long,
    /** 1-based disc number for multi-disc games, null otherwise. */
    val disc: Int? = null,
)

data class Game(
    val id: String,
    val title: String,
    val systemId: String,
    val files: List<GameFile>,
    val addedAt: Long,
    val lastPlayedAt: Long? = null,
    val progress: Float? = null,
    /** True when another game with the same title exists on the same system. */
    val isDuplicate: Boolean = false,
) {
    val path: String get() = files.first().path
    val sizeBytes: Long get() = files.sumOf { it.sizeBytes }
}

data class Library(
    val systems: List<GameSystem>,
    val games: List<Game>,
    val initialFavorites: List<String> = emptyList(),
) {
    fun system(id: String): GameSystem = systems.first { it.id == id }

    companion object {
        val Empty = Library(emptyList(), emptyList())
    }
}
