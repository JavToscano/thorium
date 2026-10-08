package com.thorium.data.sources

import com.thorium.core.model.GameSource
import com.thorium.core.model.OpenedStream
import com.thorium.core.model.RemoteEntry
import com.thorium.core.model.SourceException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.net.URLDecoder

/**
 * A web server that shows a browsable directory index (Apache, nginx, `python -m http.server`...).
 * The page is read as plain HTML links, so any server with an index works without special support.
 */
class HttpIndexSource(
    private val baseUrl: String,
    username: String = "",
    password: String = "",
    allowInsecure: Boolean = false,
    client: OkHttpClient = HttpTransport.defaultClient(),
) : GameSource {

    private val http: HttpTransport
    private val base: HttpUrl

    init {
        val probe = HttpTransport(client, allowInsecure, username, password, null)
        base = probe.parse(baseUrl)
        http = HttpTransport(client, allowInsecure, username, password, base.host)
    }

    override fun testConnection(): Result<Unit> = runCatching { http.getText(base); Unit }

    override fun list(ref: String?): List<RemoteEntry> {
        val url = ref?.let { http.parse(it) } ?: base
        return parseIndex(http.getText(url), url)
    }

    override fun open(entry: RemoteEntry, offset: Long): OpenedStream = http.open(http.parse(entry.ref), offset)

    internal companion object {
        private val anchor = Regex("""<a\s+[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>(.*?)(?=<a\s|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val tags = Regex("<[^>]*>")
        private val sizeToken = Regex("""(?:^|\s)(\d+(?:\.\d+)?)\s?([KMGT]i?B?|B)?(?=\s|$)""", RegexOption.IGNORE_CASE)

        fun parseIndex(html: String, page: HttpUrl): List<RemoteEntry> {
            val seen = LinkedHashMap<String, RemoteEntry>()
            for (match in anchor.findAll(html)) {
                val href = match.groupValues[1].trim()
                if (href.startsWith("#") || href.startsWith("?") || href.startsWith("mailto:") || href.startsWith("javascript:")) continue
                val target = page.resolve(href) ?: continue
                // Only links that stay inside the listed folder; this drops "Parent Directory" and sort links.
                if (target.host != page.host || !target.encodedPath.startsWith(page.encodedPath) || target.encodedPath == page.encodedPath) continue
                val isDir = target.encodedPath.endsWith("/")
                val name = decode(target.pathSegments.lastOrNull { it.isNotEmpty() } ?: continue)
                seen.putIfAbsent(
                    target.toString(),
                    RemoteEntry(
                        name = name,
                        ref = target.toString(),
                        isDirectory = isDir,
                        sizeBytes = if (isDir) null else parseSize(match.groupValues[3]),
                    ),
                )
            }
            return seen.values.sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        }

        private fun decode(segment: String) = runCatching { URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8") }.getOrDefault(segment)

        /** Best effort: the last "16M" / "1.5 GiB" / "12345" style token after the link, if any. */
        private fun parseSize(afterLink: String): Long? {
            val text = tags.replace(afterLink, " ").replace("&nbsp;", " ")
            val token = sizeToken.findAll(text).lastOrNull() ?: return null
            val number = token.groupValues[1].toDoubleOrNull() ?: return null
            val unit = token.groupValues[2].uppercase().firstOrNull()
            val factor = when (unit) {
                'K' -> 1024.0
                'M' -> 1024.0 * 1024
                'G' -> 1024.0 * 1024 * 1024
                'T' -> 1024.0 * 1024 * 1024 * 1024
                else -> 1.0
            }
            // A bare small number is more likely a date fragment than a byte count.
            if (unit == null && number < 1024) return null
            return (number * factor).toLong()
        }
    }
}
