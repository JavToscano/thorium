package com.thorium.data.sources

import com.thorium.core.model.GameSource
import com.thorium.core.model.OpenedStream
import com.thorium.core.model.RemoteEntry
import com.thorium.core.model.SourceException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * An Internet Archive item or collection, identified by the identifier the user types (or pastes
 * as an archive.org `details` link). Nothing is preconfigured: which collection to use is entirely
 * the user's choice. Optional access keys (the archive.org "S3-like" keys) unlock items that need
 * an account.
 *
 * Top level of a collection lists its items as folders (first 100, with a "More" entry); the top
 * level of an item lists its original files.
 */
class InternetArchiveSource(
    identifier: String,
    accessKey: String = "",
    secretKey: String = "",
    private val baseUrl: String = "https://archive.org",
    allowInsecure: Boolean = false,
    client: OkHttpClient = HttpTransport.defaultClient(),
) : GameSource {

    private val id = normalizeIdentifier(identifier)
    private val root: HttpUrl
    private val http: HttpTransport

    init {
        if (id.isEmpty() || !id.all { it.isLetterOrDigit() || it in "._-" }) {
            throw SourceException.InvalidLocation("Not a valid archive.org identifier: $identifier")
        }
        root = HttpTransport(client, allowInsecure, "", "", null).parse(baseUrl)
        val auth = if (accessKey.isNotBlank() && secretKey.isNotBlank()) {
            mapOf("Authorization" to "LOW $accessKey:$secretKey")
        } else {
            emptyMap()
        }
        http = HttpTransport(client, allowInsecure, "", "", root.host, auth)
    }

    override fun testConnection(): Result<Unit> = runCatching {
        if (fetchMetadata(id).files.isEmpty() && fetchMetadata(id).metadata == null) throw SourceException.NotFound()
    }

    override fun list(ref: String?): List<RemoteEntry> {
        // "item:<id>" opens a sub-item; "page:<n>" is the next page of the collection's items.
        if (ref != null && ref.startsWith("item:")) return filesOf(ref.removePrefix("item:"))
        val page = ref?.removePrefix("page:")?.toIntOrNull() ?: 1
        val meta = fetchMetadata(id)
        if (meta.metadata?.mediatype == "collection") return collectionItems(page)
        if (meta.metadata == null && meta.files.isEmpty()) throw SourceException.NotFound()
        return meta.files.toEntries(id)
    }

    override fun open(entry: RemoteEntry, offset: Long): OpenedStream = http.open(http.parse(entry.ref), offset)

    private fun filesOf(itemId: String): List<RemoteEntry> = fetchMetadata(itemId).files.toEntries(itemId)

    private fun List<MetaFile>.toEntries(itemId: String): List<RemoteEntry> =
        filter { it.source == "original" && !it.name.endsWith("_meta.xml") && !it.name.endsWith("_files.xml") && !it.name.endsWith("_meta.sqlite") }
            .map {
                RemoteEntry(
                    name = it.name,
                    ref = root.newBuilder().addPathSegment("download").addPathSegment(itemId)
                        .apply { it.name.split('/').forEach(::addPathSegment) }.build().toString(),
                    isDirectory = false,
                    sizeBytes = it.size?.toLongOrNull(),
                    sha1 = it.sha1?.lowercase(),
                )
            }
            .sortedBy { it.name.lowercase() }

    private fun collectionItems(page: Int): List<RemoteEntry> {
        val url = root.newBuilder().addPathSegment("advancedsearch.php")
            .addQueryParameter("q", "collection:$id")
            .addQueryParameter("fl[]", "identifier").addQueryParameter("fl[]", "title")
            .addQueryParameter("rows", PAGE_SIZE.toString()).addQueryParameter("page", page.toString())
            .addQueryParameter("output", "json").build()
        val docs = try {
            json.decodeFromString<SearchResponse>(http.getText(url)).response.docs
        } catch (e: Exception) {
            throw SourceException.BadResponse("Unexpected answer from archive.org")
        }
        val entries = docs.map { RemoteEntry(name = it.title ?: it.identifier, ref = "item:${it.identifier}", isDirectory = true) }
        // A full page means there may be more.
        return if (docs.size == PAGE_SIZE) entries + RemoteEntry(name = "…", ref = "page:${page + 1}", isDirectory = true) else entries
    }

    private fun fetchMetadata(itemId: String): Metadata = try {
        json.decodeFromString<Metadata>(http.getText(root.newBuilder().addPathSegment("metadata").addPathSegment(itemId).build()))
    } catch (e: SourceException) {
        throw e
    } catch (e: Exception) {
        throw SourceException.BadResponse("Unexpected answer from archive.org")
    }

    @Serializable
    private class Metadata(val metadata: ItemInfo? = null, val files: List<MetaFile> = emptyList())

    @Serializable
    private class ItemInfo(val mediatype: String? = null)

    @Serializable
    private class MetaFile(val name: String, val source: String? = null, val size: String? = null, val sha1: String? = null)

    @Serializable
    private class SearchResponse(val response: SearchBody)

    @Serializable
    private class SearchBody(val docs: List<Doc> = emptyList())

    @Serializable
    private class Doc(val identifier: String, val title: String? = null)

    companion object {
        private const val PAGE_SIZE = 100
        private val json = Json { ignoreUnknownKeys = true }

        /** Accepts a bare identifier or an archive.org link (`.../details/<identifier>`). */
        fun normalizeIdentifier(input: String): String {
            val text = input.trim().trimEnd('/')
            return if ("/details/" in text) text.substringAfter("/details/").substringBefore('/').substringBefore('?') else text
        }
    }
}
