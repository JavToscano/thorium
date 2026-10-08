package com.thorium.core.model

/** One known release in the bundled catalog. Holds identification data only, never download links. */
data class CatalogEntry(
    val platformId: String,
    /** Full release name as the catalog lists it, e.g. "Super Mario Advance (USA, Europe)". */
    val name: String,
    /** Name without region and version tags, e.g. "Super Mario Advance". */
    val title: String,
    val region: String?,
    val serial: String?,
    val sizeBytes: Long,
    val crc32: Long?,
    /** Upper-case hex. */
    val md5: String?,
)

/** How a file was recognised, from most to least certain. */
enum class MatchKind { Hash, Name, Title }

data class CatalogMatch(val entry: CatalogEntry, val kind: MatchKind)

/** Recognises games against the bundled metadata catalog. */
interface GameCatalog {
    /**
     * [fileName] is the file name without its extension. [platformId] narrows the search when the
     * caller knows the console; [crc32] is the file's CRC-32 when it has been computed.
     * Returns null when nothing matches or the match would be ambiguous.
     */
    fun identify(fileName: String, platformId: String? = null, crc32: Long? = null): CatalogMatch?
}

/** The kinds of image kept for a game. */
enum class ArtKind(val folder: String) {
    Boxart("Named_Boxarts"),
    /** A frame of the game being played. */
    Snap("Named_Snaps"),
    /** The title screen. */
    Title("Named_Titles"),
}

/** Images for recognised games. They are fetched on demand and kept on disk. */
interface CoverArt {
    /** The image when it is already on disk; never touches the network. */
    fun cached(entry: CatalogEntry, kind: ArtKind = ArtKind.Boxart): java.io.File?

    /** Downloads the image if needed. Null when there is none or it could not be fetched now. */
    suspend fun fetch(entry: CatalogEntry, kind: ArtKind = ArtKind.Boxart): java.io.File?
}
