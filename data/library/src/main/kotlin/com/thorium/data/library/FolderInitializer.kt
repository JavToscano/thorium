package com.thorium.data.library

import java.io.File

/** One platform in the "set up folder" list. */
data class SetupEntry(
    val platform: PlatformDefinition,
    /** Name of an existing folder that already maps to this platform, or null when it is missing. */
    val existingName: String?,
)

data class CreateResult(
    /** Folder names that were created. */
    val created: List<String>,
    /** Platforms skipped because a matching folder already existed. */
    val alreadyExisted: List<String>,
    /** Folder names that could not be created (a file with that name, no permission, ...). */
    val failed: List<String>,
)

/**
 * Prepares a games folder by creating one sub-folder per console, named after the platform id
 * (gba, 3ds, switch...), which the scanner recognises without any extra mapping.
 */
class FolderInitializer(private val catalog: PlatformCatalog = PlatformCatalog.Default) {

    /** Lists every known platform and whether [root] already has a folder for it (any alias, any case). */
    fun inspect(root: File): List<SetupEntry> {
        val children = root.listFiles()?.filter { it.isDirectory }.orEmpty()
        return catalog.platforms.map { platform ->
            val existing = children.firstOrNull { it.name.lowercase() in platform.folderAliases }
            SetupEntry(platform, existing?.name)
        }
    }

    /** Creates the folders for [platformIds], leaving existing ones untouched. */
    fun create(root: File, platformIds: Collection<String>): CreateResult {
        val entries = inspect(root).filter { it.platform.id in platformIds }
        val created = mutableListOf<String>()
        val existed = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (entry in entries) {
            val name = entry.platform.id
            when {
                entry.existingName != null -> existed += entry.existingName
                !root.isDirectory -> failed += name
                File(root, name).let { it.mkdir() || it.isDirectory } -> created += name
                else -> failed += name
            }
        }
        return CreateResult(created, existed, failed)
    }
}
