package com.thorium.data.sources

import com.thorium.core.model.GameSource
import com.thorium.core.model.OpenedStream
import com.thorium.core.model.RemoteEntry
import com.thorium.core.model.SourceException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.Channels

/** A folder on the device (for example on a USB drive), treated like any other source. */
class LocalFolderSource(private val folder: File) : GameSource {

    override fun testConnection(): Result<Unit> = runCatching {
        if (!folder.isDirectory) throw SourceException.NotFound()
    }

    override fun list(ref: String?): List<RemoteEntry> {
        val dir = ref?.let(::File) ?: folder
        // Never list outside the configured folder, whatever the reference says.
        if (!dir.canonicalPath.startsWith(folder.canonicalPath)) throw SourceException.InvalidLocation("Outside the source folder")
        val children = dir.listFiles() ?: throw SourceException.NotFound()
        return children.filter { !it.name.startsWith(".") }
            .map { RemoteEntry(it.name, it.path, it.isDirectory, if (it.isFile) it.length() else null) }
            .sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override fun open(entry: RemoteEntry, offset: Long): OpenedStream {
        val file = File(entry.ref)
        if (!file.canonicalPath.startsWith(folder.canonicalPath)) throw SourceException.InvalidLocation("Outside the source folder")
        try {
            val raf = RandomAccessFile(file, "r")
            raf.seek(offset)
            return OpenedStream(Channels.newInputStream(raf.channel), raf.length(), resumed = true)
        } catch (e: IOException) {
            throw SourceException.Network(e)
        }
    }
}
