package com.thorium.core.model

data class GameSystem(
    val id: String,
    val name: String,
    val shortName: String,
    val hue: Float,
)

data class Game(
    val id: String,
    val title: String,
    val systemId: String,
    val addedAt: Long,
    val lastPlayedAt: Long?,
    val progress: Float?,
    val sizeBytes: Long,
    val path: String,
)

data class Library(
    val systems: List<GameSystem>,
    val games: List<Game>,
    val initialFavorites: List<String>,
) {
    fun system(id: String): GameSystem = systems.first { it.id == id }
}
