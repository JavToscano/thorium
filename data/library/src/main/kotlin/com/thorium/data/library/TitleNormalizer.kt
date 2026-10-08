package com.thorium.data.library

/** Result of cleaning a ROM file name. */
data class ParsedName(
    /** Human-readable title without region/version tags, e.g. "Kirby - Planet Robobot". */
    val title: String,
    /** Lower-case alphanumeric key used to match files of the same game. */
    val matchKey: String,
    /** 1-based disc number when the name marks a disc, null otherwise. */
    val disc: Int?,
)

object TitleNormalizer {

    private val discRegex = Regex("""[\s(\[_-]*\b(?:disc|disk|cd)\s*0*(\d+)\b[)\]]?""", RegexOption.IGNORE_CASE)
    private val bracketRegex = Regex("""\([^)]*\)|\[[^\]]*]""")
    private val spaceRegex = Regex("""\s+""")
    private val nonAlnum = Regex("""[^a-z0-9]+""")

    /** [fileName] must already have its extension removed. */
    fun parse(fileName: String): ParsedName {
        val disc = discRegex.find(fileName)?.groupValues?.get(1)?.toIntOrNull()
        var name = discRegex.replace(fileName, " ")
        name = bracketRegex.replace(name, " ")
        name = name.replace('_', ' ')
        name = spaceRegex.replace(name, " ").trim().trimEnd('-', ' ')
        val title = name.ifEmpty { fileName.trim() }
        val key = nonAlnum.replace(title.lowercase(), "")
        return ParsedName(title, key.ifEmpty { fileName.lowercase() }, disc)
    }
}
