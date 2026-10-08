package com.thorium.data.metadata

/** Turns a file name into the stem the catalog is searched with. */
object FileStems {
    private val archive = setOf("zip", "7z")
    private val extension = Regex("""\.([A-Za-z0-9]{1,5})$""")

    /**
     * Removes the extension, and the one before it when the file is an archive ("Game.gba.zip").
     * A trailing dot-word that is part of the title ("Dr. Mario (USA)") is kept: only short
     * alphanumeric suffixes count as extensions.
     */
    fun of(fileName: String): String {
        var name = fileName
        val last = extension.find(name) ?: return name
        val wasArchive = last.groupValues[1].lowercase() in archive
        name = name.removeRange(last.range)
        if (wasArchive) extension.find(name)?.let { name = name.removeRange(it.range) }
        return name
    }
}
