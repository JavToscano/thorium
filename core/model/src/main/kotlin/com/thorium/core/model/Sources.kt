package com.thorium.core.model

import java.io.InputStream

/** The kinds of place games can be fetched from. Every one is generic: none points to a specific site. */
enum class SourceType {
    /** A web server with a browsable directory index (Apache, nginx, `python -m http.server`...). */
    Http,

    /** A JSON catalog file hosted by the user (see docs: catalog format). */
    Catalog,

    /** An Internet Archive item or collection, identified by the identifier the user types. */
    InternetArchive,

    /** A folder on the device, for example on a USB drive. */
    Local,
}

/** A source as configured by the user. The password is never part of this model. */
data class SourceConfig(
    val id: Long,
    val name: String,
    val type: SourceType,
    /** URL, identifier or folder path, depending on [type]. */
    val location: String,
    val username: String = "",
    val hasPassword: Boolean = false,
    /** Allows plain `http://`. Off by default; typical for a NAS on the home network. */
    val allowInsecure: Boolean = false,
    val enabled: Boolean = true,
    /** Result of the last connection test: true worked, false failed, null never tested. */
    val healthy: Boolean? = null,
    /**
     * Console id (`gba`, `3ds`...) for sources that only hold games of one console, so downloads from
     * it need no question. The platform a catalog states for a file still wins over this.
     */
    val defaultPlatformId: String? = null,
)

/** One file or folder offered by a source. */
data class RemoteEntry(
    val name: String,
    /** Opaque reference the same source understands in [GameSource.list] and [GameSource.open]. */
    val ref: String,
    val isDirectory: Boolean,
    val sizeBytes: Long? = null,
    /** Platform id (`gba`, `3ds`...) when the source states it. */
    val platformId: String? = null,
    val sha1: String? = null,
)

/**
 * A byte stream opened at some position. [resumed] is false when the server ignored the requested
 * offset and the stream starts at byte 0, so the caller must start over instead of appending.
 */
class OpenedStream(
    val stream: InputStream,
    /** Size of the whole file when known. */
    val totalSize: Long?,
    val resumed: Boolean,
)

/**
 * Where games can be listed and read from. All calls block, so they must run off the main thread.
 * Failures are reported as a [SourceException].
 */
interface GameSource {
    fun testConnection(): Result<Unit>

    /** Lists [ref] (null means the source's top level). */
    fun list(ref: String? = null): List<RemoteEntry>

    /** Opens [entry] starting at [offset] bytes. */
    fun open(entry: RemoteEntry, offset: Long = 0): OpenedStream
}

/** Why a source call failed, in terms the UI can explain. */
sealed class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidLocation(message: String) : SourceException(message)
    class InsecureConnection : SourceException("Plain HTTP is not allowed for this source")
    class Unauthorized : SourceException("Wrong or missing credentials")
    class NotFound : SourceException("Not found")
    class BadResponse(message: String) : SourceException(message)
    class Network(cause: Throwable) : SourceException(cause.message ?: "Network error", cause)
}
