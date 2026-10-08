package com.thorium.data.library

import java.io.File

/** Where a downloaded game goes. [workDir] is hidden scratch space on the same volume. */
data class Destination(val platformDir: File, val workDir: File)

/**
 * Decides which console a file belongs to and which folder it is installed into: the folder that
 * console already has (any name the scanner recognises), or a new one named after the platform id.
 */
class DestinationResolver(
    private val catalog: PlatformCatalog = PlatformCatalog.Default,
    private val freeSpace: (File) -> Long = { it.usableSpace },
) {

    /**
     * Order of evidence: the platform the source states, then a folder name in the source path
     * (`.../gba/Game.zip`), then an extension only one console uses (`.3ds`, `.gba`).
     * Returns null when none of them settles it, so the caller can ask the user.
     */
    fun detectPlatform(declaredId: String?, entryName: String, sourcePath: List<String> = emptyList()): PlatformDefinition? {
        declaredId?.lowercase()?.let { id -> catalog.platforms.firstOrNull { it.id == id }?.let { return it } }
        for (segment in sourcePath.asReversed()) catalog.forFolder(segment)?.let { return it }
        val ext = entryName.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty()) {
            val owners = catalog.platforms.filter { ext in it.extensions }
            if (owners.size == 1) return owners.single()
        }
        return null
    }

    /**
     * @param roots the configured game roots
     * @param preferredRoot used first when several roots could take the game
     * @return the destination, or null when there is no usable root. The folder is created when missing.
     */
    fun resolve(platform: PlatformDefinition, roots: List<File>, preferredRoot: File? = null): Destination? {
        val usable = roots.filter { it.isDirectory }
        if (usable.isEmpty()) return null

        val existing = usable.mapNotNull { root -> findPlatformDir(root, platform)?.let { root to it } }
        val chosen = existing.firstOrNull { preferredRoot != null && it.first == preferredRoot }
            ?: existing.maxByOrNull { freeSpace(it.first) }
        if (chosen != null) return destination(chosen.second)

        // No folder for this console anywhere yet: create one in the preferred (or roomiest) root.
        val root = usable.firstOrNull { it == preferredRoot } ?: usable.maxBy { freeSpace(it) }
        val parent = containerOf(root) ?: root
        val dir = File(parent, platform.id)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        return destination(dir)
    }

    private fun destination(platformDir: File): Destination {
        val work = File(platformDir, WORK_DIR)
        work.mkdirs()
        return Destination(platformDir, work)
    }

    /** The console's folder inside [root]: the root itself, a child, or a child of a "roms"-style container. */
    private fun findPlatformDir(root: File, platform: PlatformDefinition): File? {
        if (root.name.lowercase() in platform.folderAliases) return root
        childDir(root, platform)?.let { return it }
        root.listFiles()?.filter { it.isDirectory && it.name.lowercase() in catalog.containerNames }?.forEach { container ->
            childDir(container, platform)?.let { return it }
        }
        return null
    }

    private fun childDir(parent: File, platform: PlatformDefinition): File? =
        parent.listFiles()?.firstOrNull { it.isDirectory && it.name.lowercase() in platform.folderAliases }

    private fun containerOf(root: File): File? =
        root.listFiles()?.firstOrNull { it.isDirectory && it.name.lowercase() in catalog.containerNames }

    companion object {
        /** Hidden, so the scanner never mistakes a half-finished download for a game. */
        const val WORK_DIR = ".thorium-tmp"
    }
}
