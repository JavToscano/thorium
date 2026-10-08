package com.thorium.data.metadata

import com.thorium.core.model.ArtKind
import com.thorium.core.model.CatalogEntry
import com.thorium.core.model.CoverArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Images (box art, title screens and game snaps) from the libretro thumbnail server
 * (thumbnails.libretro.com), which names its images after the No-Intro release names the catalog already holds. No account or key is needed.
 *
 * Images are downloaded one game at a time on request and kept under [dir]. A game with no image
 * leaves a small marker so the server is not asked again until [missRetryMs] has passed; network
 * failures leave nothing, so they are retried next time.
 */
class LibretroCovers internal constructor(
    private val dir: File,
    private val client: OkHttpClient,
    private val baseUrl: String = "https://thumbnails.libretro.com/",
    private val clock: () -> Long = System::currentTimeMillis,
    private val missRetryMs: Long = 30L * 24 * 60 * 60 * 1000,
    parallel: Int = 2,
) : CoverArt {

    private val gate = Semaphore(parallel)

    override fun cached(entry: CatalogEntry, kind: ArtKind): File? =
        fileFor(entry, kind)?.takeIf { it.isFile && it.length() > 0 }

    override suspend fun fetch(entry: CatalogEntry, kind: ArtKind): File? {
        cached(entry, kind)?.let { return it }
        val target = fileFor(entry, kind) ?: return null
        val miss = File(target.path + ".none")
        if (miss.exists() && clock() - miss.lastModified() < missRetryMs) return null
        val url = urlFor(entry, kind) ?: return null
        return gate.withPermit { withContext(Dispatchers.IO) { download(url, target, miss) } }
    }

    private fun download(url: okhttp3.HttpUrl, target: File, miss: File): File? {
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                when {
                    response.code == 404 -> {
                        target.parentFile?.mkdirs()
                        miss.writeBytes(ByteArray(0))
                        miss.setLastModified(clock())
                        null
                    }
                    !response.isSuccessful -> null
                    else -> {
                        val bytes = response.body.byteStream().use { it.readNBytes(MAX_BYTES + 1) }
                        if (bytes.size > MAX_BYTES || !isPng(bytes)) return null
                        target.parentFile?.mkdirs()
                        val temp = File(target.path + ".part")
                        temp.writeBytes(bytes)
                        if (!temp.renameTo(target)) { temp.delete(); return null }
                        miss.delete()
                        target
                    }
                }
            }
        } catch (e: IOException) {
            null
        }
    }

    /** Box art keeps the original layout (`<console>/<name>.png`); the other kinds get a sub-folder. */
    private fun fileFor(entry: CatalogEntry, kind: ArtKind): File? {
        if (SYSTEMS[entry.platformId] == null) return null
        val base = File(dir, entry.platformId)
        val folder = when (kind) {
            ArtKind.Boxart -> base
            ArtKind.Snap -> File(base, "snaps")
            ArtKind.Title -> File(base, "titles")
        }
        return File(folder, sanitize(entry.name) + ".png")
    }

    private fun urlFor(entry: CatalogEntry, kind: ArtKind): okhttp3.HttpUrl? {
        val system = SYSTEMS[entry.platformId] ?: return null
        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(system)
            .addPathSegment(kind.folder)
            .addPathSegment(sanitize(entry.name) + ".png")
            .build()
    }

    private fun isPng(bytes: ByteArray) =
        bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()

    companion object {
        private const val MAX_BYTES = 8 * 1024 * 1024

        /** Thumbnail server folder for each platform the catalog covers. */
        private val SYSTEMS = mapOf(
            "nes" to "Nintendo - Nintendo Entertainment System",
            "snes" to "Nintendo - Super Nintendo Entertainment System",
            "n64" to "Nintendo - Nintendo 64",
            "gb" to "Nintendo - Game Boy",
            "gbc" to "Nintendo - Game Boy Color",
            "gba" to "Nintendo - Game Boy Advance",
            "nds" to "Nintendo - Nintendo DS",
            "3ds" to "Nintendo - Nintendo 3DS",
            "genesis" to "Sega - Mega Drive - Genesis",
        )

        private val unsafe = Regex("""[&*/:`<>?\\|"]""")

        /** The thumbnail server replaces characters that file systems reject with an underscore. */
        internal fun sanitize(name: String) = unsafe.replace(name, "_").trim('.', ' ')

        fun create(dir: File): LibretroCovers = LibretroCovers(dir, OkHttpClient())
    }
}
