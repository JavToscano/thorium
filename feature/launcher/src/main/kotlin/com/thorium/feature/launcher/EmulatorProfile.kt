package com.thorium.feature.launcher

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How to start games of some platforms in one emulator. */
@Serializable
data class EmulatorProfile(
    val id: String,
    val name: String,
    @SerialName("package") val packageName: String,
    /** Fully qualified class of the activity that takes a game file. */
    val activity: String,
    /** Platform ids (`3ds`, `gba`...) the emulator can run. */
    val platforms: List<String>,
    val action: String = "android.intent.action.VIEW",
    val mimeType: String = "application/octet-stream",
)

@Serializable
private data class ProfileFile(val emulators: List<EmulatorProfile> = emptyList())

object EmulatorProfiles {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<EmulatorProfile> = json.decodeFromString<ProfileFile>(text).emulators

    fun forPlatform(profiles: List<EmulatorProfile>, platformId: String): List<EmulatorProfile> =
        profiles.filter { platformId in it.platforms }
}

/** Picks the file that starts a game when it has several (a disc image's sheet, not its tracks). */
object PrimaryFile {
    /** Extensions that name the entry point of a multi-file game, best first. */
    private val entryPoints = listOf("m3u", "cue", "gdi", "ccd", "mds")

    fun pick(paths: List<String>): String? {
        for (extension in entryPoints) {
            paths.firstOrNull { it.substringAfterLast('.', "").equals(extension, ignoreCase = true) }?.let { return it }
        }
        return paths.firstOrNull()
    }
}
