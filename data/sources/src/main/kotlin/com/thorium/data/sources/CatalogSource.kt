package com.thorium.data.sources

import com.thorium.core.model.GameSource
import com.thorium.core.model.OpenedStream
import com.thorium.core.model.RemoteEntry
import com.thorium.core.model.SourceException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/**
 * A JSON catalog hosted by the user. Format:
 *
 * ```json
 * { "name": "My server",
 *   "items": [ { "title": "Some Game", "platform": "gba",
 *                "url": "https://example.lan/some-game.zip", "size": 16777216, "sha1": "..." } ] }
 * ```
 * `url` may be relative to the catalog's own address; `size` and `sha1` are optional.
 * `list(null)` returns everything; `list("platform:gba")` returns one platform.
 */
class CatalogSource(
    private val catalogUrl: String,
    username: String = "",
    password: String = "",
    allowInsecure: Boolean = false,
    client: OkHttpClient = HttpTransport.defaultClient(),
) : GameSource {

    private val http: HttpTransport
    private val base: HttpUrl

    init {
        base = HttpTransport(client, allowInsecure, username, password, null).parse(catalogUrl)
        http = HttpTransport(client, allowInsecure, username, password, base.host)
    }

    override fun testConnection(): Result<Unit> = runCatching { load(); Unit }

    override fun list(ref: String?): List<RemoteEntry> {
        val platform = ref?.removePrefix("platform:")?.takeIf { ref.startsWith("platform:") }
        return load().filter { platform == null || it.platformId == platform }
    }

    override fun open(entry: RemoteEntry, offset: Long): OpenedStream = http.open(http.parse(entry.ref), offset)

    private fun load(): List<RemoteEntry> {
        val text = http.getText(base)
        val catalog = try {
            json.decodeFromString<Catalog>(text)
        } catch (e: Exception) {
            throw SourceException.BadResponse("This is not a valid Thorium catalog")
        }
        return catalog.items.mapNotNull { item ->
            val target = base.resolve(item.url) ?: return@mapNotNull null
            RemoteEntry(
                name = item.title,
                ref = target.toString(),
                isDirectory = false,
                sizeBytes = item.size,
                platformId = item.platform?.lowercase(),
                sha1 = item.sha1?.lowercase(),
            )
        }
    }

    @Serializable
    private class Catalog(val name: String? = null, val items: List<Item> = emptyList())

    @Serializable
    private class Item(
        val title: String,
        val url: String,
        val platform: String? = null,
        val size: Long? = null,
        val sha1: String? = null,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
