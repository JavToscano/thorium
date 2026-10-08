package com.thorium.data.archive

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Why an archive could not be extracted. */
sealed class ArchiveException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unsupported : ArchiveException("Not a supported archive (ZIP or 7z)")
    class Corrupt(cause: Throwable) : ArchiveException("The archive is damaged", cause)
    class UnsafePath(name: String) : ArchiveException("The archive tries to write outside its folder: $name")
    class TooLarge : ArchiveException("The archive is larger than the allowed limit")
    class Cancelled : ArchiveException("Extraction cancelled")
}

enum class ArchiveKind { Zip, SevenZ }

/**
 * Extracts ZIP and 7z archives into a folder.
 *
 * Archives come from outside, so every entry is checked: paths that would escape the target folder
 * ("zip slip") are refused, links are skipped, and the number of entries and the total size are
 * capped to defuse "zip bombs".
 */
class ArchiveExtractor(private val limits: Limits = Limits()) {

    data class Limits(
        val maxTotalBytes: Long = 64L * 1024 * 1024 * 1024,
        val maxEntries: Int = 20_000,
    )

    /** Recognises the format by its first bytes, so a wrong or missing extension does not matter. */
    fun kind(file: File): ArchiveKind? {
        val head = ByteArray(6)
        val read = try {
            file.inputStream().use { it.read(head) }
        } catch (e: IOException) {
            return null
        }
        if (read >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() && head[2] in listOf<Byte>(3, 5, 7)) return ArchiveKind.Zip
        if (read >= 6 && head.contentEquals(byteArrayOf('7'.code.toByte(), 'z'.code.toByte(), 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C))) return ArchiveKind.SevenZ
        return null
    }

    /**
     * @param onProgress bytes written so far and total uncompressed bytes (0 when unknown).
     * @param isCancelled polled while copying; return true to stop with [ArchiveException.Cancelled].
     * @return the files that were created (not folders).
     */
    fun extract(
        archive: File,
        into: File,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): List<File> {
        val kind = kind(archive) ?: throw ArchiveException.Unsupported()
        into.mkdirs()
        val job = Job(into.canonicalFile, onProgress, isCancelled)
        try {
            when (kind) {
                ArchiveKind.Zip -> extractZip(archive, job)
                ArchiveKind.SevenZ -> extractSevenZ(archive, job)
            }
        } catch (e: ArchiveException) {
            throw e
        } catch (e: IOException) {
            throw ArchiveException.Corrupt(e)
        }
        return job.created
    }

    private inner class Job(
        val root: File,
        val onProgress: (Long, Long) -> Unit,
        val isCancelled: () -> Boolean,
    ) {
        val created = mutableListOf<File>()
        var entries = 0
        var written = 0L
        var total = 0L

        /** The file an entry maps to, or an exception when it would land outside [root]. */
        fun target(name: String): File {
            val file = File(root, name).canonicalFile
            if (file.path != root.path && !file.path.startsWith(root.path + File.separator)) throw ArchiveException.UnsafePath(name)
            return file
        }

        fun countEntry() {
            if (++entries > limits.maxEntries) throw ArchiveException.TooLarge()
        }

        fun write(input: InputStream, target: File) {
            target.parentFile?.mkdirs()
            target.outputStream().buffered().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    if (isCancelled()) throw ArchiveException.Cancelled()
                    val n = input.read(buffer)
                    if (n < 0) break
                    written += n
                    // The declared sizes can lie, so the real bytes are counted as well.
                    if (written > limits.maxTotalBytes) throw ArchiveException.TooLarge()
                    out.write(buffer, 0, n)
                    onProgress(written, total)
                }
            }
            created += target
        }
    }

    private fun extractZip(archive: File, job: Job) {
        ZipFile.builder().setFile(archive).get().use { zip ->
            val entries = zip.entries.toList()
            job.total = entries.filter { !it.isDirectory }.sumOf { it.size.coerceAtLeast(0) }
            if (job.total > limits.maxTotalBytes) throw ArchiveException.TooLarge()
            for (entry in entries) {
                job.countEntry()
                val target = job.target(entry.name)
                when {
                    entry.isDirectory -> target.mkdirs()
                    entry.isUnixSymlink -> Unit
                    else -> zip.getInputStream(entry).use { job.write(it, target) }
                }
            }
        }
    }

    private fun extractSevenZ(archive: File, job: Job) {
        SevenZFile.builder().setFile(archive).get().use { sevenZ ->
            job.total = sevenZ.entries.filter { !it.isDirectory }.sumOf { it.size.coerceAtLeast(0) }
            if (job.total > limits.maxTotalBytes) throw ArchiveException.TooLarge()
            while (true) {
                val entry = sevenZ.nextEntry ?: break
                job.countEntry()
                val target = job.target(entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else if (!entry.isAntiItem) {
                    job.write(sevenZ.getInputStream(entry), target)
                }
            }
        }
    }
}
