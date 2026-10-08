package com.thorium.app.downloads

import com.thorium.core.model.DownloadError
import com.thorium.core.model.DownloadItem
import com.thorium.core.model.DownloadState
import com.thorium.core.model.InstallException
import com.thorium.core.model.Installer
import com.thorium.data.archive.ArchiveException
import com.thorium.data.archive.ArchiveExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Turns a finished download into a game inside the folder of its console: archives are extracted,
 * plain files are moved. Nothing is overwritten; a name that is taken gets a " (2)" suffix.
 */
class GameInstaller(
    private val extractor: ArchiveExtractor = ArchiveExtractor(),
    /** Called with the console folder once the game is in place, so the library can pick it up. */
    private val afterInstall: suspend (File) -> Unit = {},
) : Installer {

    override suspend fun install(item: DownloadItem, downloaded: File, stage: suspend (DownloadState) -> Unit): String {
        val destination = File(item.destinationDir)
        val scratch = File(item.workDir, "${item.id}-x")
        try {
            val placed = withContext(Dispatchers.IO) {
                val cancelled = currentCoroutineContext()[Job]
                destination.mkdirs()
                if (extractor.kind(downloaded) != null) {
                    stage(DownloadState.Extracting)
                    scratch.deleteRecursively()
                    extractor.extract(downloaded, scratch, isCancelled = { cancelled?.isActive == false })
                    stage(DownloadState.Installing)
                    placeExtracted(scratch, destination, item)
                } else {
                    stage(DownloadState.Installing)
                    moveInto(downloaded, destination, cleanName(item.entryName))
                }
            }
            afterInstall(destination)
            return placed.path
        } catch (e: ArchiveException) {
            throw InstallException(DownloadError.Extraction, e.message ?: "Could not extract", e)
        } catch (e: IOException) {
            throw InstallException(DownloadError.Storage, e.message ?: "Could not write the game", e)
        } finally {
            scratch.deleteRecursively()
        }
    }

    /**
     * One file goes straight into the console folder, one folder keeps its name, and several loose
     * files (a .cue with its .bin tracks, for example) are grouped in a folder named after the game.
     */
    private fun placeExtracted(scratch: File, destination: File, item: DownloadItem): File {
        val top = scratch.listFiles()?.filter { !it.name.startsWith(".") && !it.name.startsWith("__MACOSX") }.orEmpty()
        val files = top.filter { it.isFile }
        val dirs = top.filter { it.isDirectory }
        return when {
            files.size == 1 && dirs.isEmpty() -> moveInto(files.single(), destination, cleanName(files.single().name))
            dirs.size == 1 && files.isEmpty() -> moveInto(dirs.single(), destination, cleanName(dirs.single().name))
            else -> {
                val folder = uniqueTarget(destination, cleanName(item.title)).also { it.mkdirs() }
                top.forEach { moveInto(it, folder, it.name) }
                folder
            }
        }
    }

    private fun moveInto(source: File, directory: File, name: String): File {
        val target = uniqueTarget(directory, name)
        if (!source.renameTo(target)) {
            // Different volumes cannot be renamed across; copy then delete.
            if (source.isDirectory) source.copyRecursively(target) else source.copyTo(target)
            source.deleteRecursively()
        }
        return target
    }

    companion object {
        private val forbidden = Regex("""[\\:*?"<>|\u0000-\u001F]""")

        /** A name that is safe on every file system an SD card might use. */
        fun cleanName(name: String): String =
            name.substringAfterLast('/').replace(forbidden, "_").trim().trim('.').ifEmpty { "game" }

        /** [name] inside [dir], or "name (2)", "name (3)"... when it is taken. */
        fun uniqueTarget(dir: File, name: String): File {
            var target = File(dir, name)
            if (!target.exists()) return target
            val base = name.substringBeforeLast('.', name)
            val ext = if ('.' in name) "." + name.substringAfterLast('.') else ""
            var n = 2
            while (target.exists()) target = File(dir, "$base ($n)$ext").also { n++ }
            return target
        }
    }
}
