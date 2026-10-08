package com.thorium.data.metadata

import com.thorium.core.model.CatalogEntry
import com.thorium.core.model.CatalogMatch
import com.thorium.core.model.GameCatalog
import com.thorium.core.model.MatchKind

/** Raw lookups against the catalog data. */
interface CatalogStore {
    fun byCrc(crc32: Long): List<CatalogEntry>

    /** Entries whose title key equals [key]; all platforms when [platformId] is null. */
    fun byKey(platformId: String?, key: String): List<CatalogEntry>
}

/**
 * Recognises a file: first by hash, then by title key. Among releases of the same title it prefers
 * the one whose name matches the file exactly, then the one sharing the file's region tag, then a
 * preferred region, then the plainest release (fewest tags), so "Rev 1" and "Virtual Console" variants lose to the base one.
 *
 * [keyOf] turns a file name into the catalog's match key; it must apply the same rules the catalog
 * builder used (TitleNormalizer).
 */
class CatalogMatcher(
    private val store: CatalogStore,
    /** Regions to favour, best first, when the file name does not say which release it is. */
    private val preferredRegions: List<String> = listOf("World", "USA", "Europe"),
    private val keyOf: (String) -> String,
) : GameCatalog {

    override fun identify(fileName: String, platformId: String?, crc32: Long?): CatalogMatch? {
        if (crc32 != null) {
            val byHash = store.byCrc(crc32).filter { platformId == null || it.platformId == platformId }
            pick(byHash, fileName)?.let { return CatalogMatch(it, MatchKind.Hash) }
        }
        val candidates = store.byKey(platformId, keyOf(fileName))
        if (candidates.isEmpty()) return null
        // Without a known console, a title that exists on several of them is ambiguous.
        if (platformId == null && candidates.map { it.platformId }.distinct().size > 1) return null
        val best = pick(candidates, fileName) ?: return null
        val exact = best.name.equals(fileName, ignoreCase = true)
        return CatalogMatch(best, if (exact) MatchKind.Name else MatchKind.Title)
    }

    private fun pick(entries: List<CatalogEntry>, fileName: String): CatalogEntry? =
        entries.maxWithOrNull(compareBy<CatalogEntry> { score(it, fileName) }.thenBy { -it.name.length })

    private fun score(entry: CatalogEntry, fileName: String): Int {
        var score = 0
        if (entry.name.equals(fileName, ignoreCase = true)) score += 1000
        val region = entry.region
        if (region != null && fileName.contains(region, ignoreCase = true)) score += 100
        val rank = preferredRegions.indexOfFirst { it.equals(region, ignoreCase = true) }
        if (rank >= 0) score += (preferredRegions.size - rank) * 5
        score -= entry.name.count { it == '(' } * 10
        return score
    }
}
