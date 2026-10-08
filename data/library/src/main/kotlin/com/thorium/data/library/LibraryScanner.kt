package com.thorium.data.library

import com.thorium.core.model.Game
import com.thorium.core.model.GameFile
import com.thorium.core.model.Library
import java.io.File

data class ScanStats(
    val filesVisited: Int,
    val filesIgnored: Int,
    val games: Int,
    val duplicates: Int,
    val durationMs: Long,
    val roots: List<String>,
)

data class ScanResult(val library: Library, val stats: ScanStats)

/**
 * Walks storage roots and builds a [Library].
 *
 * It never walks arbitrary folders: from a root it only enters folders that map to a platform
 * (by alias) or that are known containers such as "roms". That keeps the scan fast on a full SD
 * card and avoids touching Android/, DCIM/ and friends.
 */
class LibraryScanner(private val catalog: PlatformCatalog = PlatformCatalog.Default) {

    private class Candidate(val platform: PlatformDefinition, val file: File, val parsed: ParsedName, val isArchive: Boolean)

    private var visited = 0
    private var ignored = 0

    fun scan(roots: List<File>, maxDepth: Int = 4): ScanResult {
        val start = System.currentTimeMillis()
        visited = 0
        ignored = 0
        val candidates = mutableListOf<Candidate>()
        roots.filter { it.isDirectory }.forEach { collectFromContainer(it, 0, maxDepth, candidates) }
        val games = buildGames(candidates)
        val systems = catalog.platforms.map { it.system }.filter { s -> games.any { it.systemId == s.id } }
        val library = Library(systems, games)
        val stats = ScanStats(
            filesVisited = visited,
            filesIgnored = ignored,
            games = games.size,
            duplicates = games.count { it.isDuplicate },
            durationMs = System.currentTimeMillis() - start,
            roots = roots.map { it.path },
        )
        return ScanResult(library, stats)
    }

    private fun collectFromContainer(dir: File, containerDepth: Int, maxDepth: Int, out: MutableList<Candidate>) {
        val children = dir.listFiles() ?: return
        for (child in children.sortedBy { it.name }) {
            if (!child.isDirectory || isHidden(child.name)) continue
            val platform = catalog.forFolder(child.name)
            when {
                platform != null -> collectFromPlatformDir(child, platform, 0, maxDepth, out)
                containerDepth < MAX_CONTAINER_DEPTH && child.name.lowercase() in catalog.containerNames ->
                    collectFromContainer(child, containerDepth + 1, maxDepth, out)
            }
        }
    }

    private fun collectFromPlatformDir(dir: File, platform: PlatformDefinition, depth: Int, maxDepth: Int, out: MutableList<Candidate>) {
        val children = dir.listFiles() ?: return
        for (child in children.sortedBy { it.name }) {
            if (isHidden(child.name) || child.name in JUNK_NAMES) {
                ignored++
                continue
            }
            if (child.isDirectory) {
                if (depth < maxDepth) collectFromPlatformDir(child, platform, depth + 1, maxDepth, out)
                continue
            }
            visited++
            val ext = child.extension.lowercase()
            val isArchive = ext in platform.archiveExtensions
            if (child.length() == 0L || (ext !in platform.extensions && !isArchive)) {
                ignored++
                continue
            }
            out += Candidate(platform, child, TitleNormalizer.parse(child.nameWithoutExtension), isArchive)
        }
    }

    private fun buildGames(candidates: List<Candidate>): List<Game> {
        val games = mutableListOf<Game>()
        val seenKeys = mutableSetOf<String>()
        // Files of the same game: same platform and title key. Disc-marked files merge into one game.
        candidates.groupBy { it.platform.id to it.parsed.matchKey }.forEach { (key, group) ->
            val (platformId, matchKey) = key
            val withDisc = group.filter { it.parsed.disc != null }
            val withoutDisc = group.filter { it.parsed.disc == null }
            if (withDisc.isNotEmpty()) {
                games += toGame(platformId, matchKey, withDisc, seenKeys, duplicate = false)
            }
            // Several files with the same title and no disc number are duplicates, not one game.
            withoutDisc.forEachIndexed { i, c ->
                val duplicate = i > 0 || withDisc.isNotEmpty()
                games += toGame(platformId, matchKey, listOf(c), seenKeys, duplicate)
            }
        }
        return games.sortedWith(compareBy({ it.systemId }, { it.title.lowercase() }, { it.id }))
    }

    private fun toGame(platformId: String, matchKey: String, group: List<Candidate>, seen: MutableSet<String>, duplicate: Boolean): Game {
        var id = "$platformId/$matchKey"
        var n = 2
        while (!seen.add(id)) id = "$platformId/$matchKey~${n++}"
        val files = group.sortedBy { it.parsed.disc ?: 0 }.map {
            GameFile(
                path = it.file.path,
                sizeBytes = it.file.length(),
                extension = it.file.extension.lowercase(),
                isArchive = it.isArchive,
                lastModified = it.file.lastModified(),
                disc = it.parsed.disc,
            )
        }
        return Game(
            id = id,
            title = group.first().parsed.title,
            systemId = platformId,
            files = files,
            addedAt = files.maxOf { it.lastModified },
            isDuplicate = duplicate,
        )
    }

    // Hidden entries include macOS AppleDouble files ("._Game.3ds") created when copying from a Mac.
    private fun isHidden(name: String) = name.startsWith(".")

    private companion object {
        const val MAX_CONTAINER_DEPTH = 2
        val JUNK_NAMES = setOf("Thumbs.db", "desktop.ini")
    }
}
