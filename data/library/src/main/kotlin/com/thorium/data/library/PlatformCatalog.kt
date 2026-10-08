package com.thorium.data.library

import com.thorium.core.model.GameSystem

/**
 * A platform the scanner knows how to recognise: which folder names map to it and which file
 * extensions count as games. Contains no game data, only naming conventions.
 */
data class PlatformDefinition(
    val system: GameSystem,
    /** Lower-case folder names that map to this platform (e.g. "3ds", "n3ds"). */
    val folderAliases: Set<String>,
    /** Lower-case extensions (without dot) that are playable files. */
    val extensions: Set<String>,
    /** Lower-case archive extensions accepted for this platform. */
    val archiveExtensions: Set<String> = setOf("zip", "7z"),
) {
    val id: String get() = system.id
}

class PlatformCatalog(val platforms: List<PlatformDefinition>) {

    private val byFolder: Map<String, PlatformDefinition> =
        platforms.flatMap { p -> p.folderAliases.map { it to p } }.toMap()

    fun forFolder(name: String): PlatformDefinition? = byFolder[name.lowercase()]

    /** Folder names that may contain platform folders (e.g. "roms", "Emulation"). */
    val containerNames: Set<String> = setOf("roms", "rom", "games", "emulation", "retro", "retroarch")

    companion object {
        private fun p(
            id: String, name: String, short: String, hue: Float,
            aliases: Set<String>, ext: Set<String>,
        ) = PlatformDefinition(GameSystem(id, name, short, hue), aliases + id, ext)

        val Default = PlatformCatalog(
            listOf(
                p("nes", "Nintendo Entertainment System", "NES", 0f, setOf("famicom"), setOf("nes")),
                p("snes", "Super Nintendo", "SNES", 280f, setOf("sfc", "superfamicom"), setOf("sfc", "smc")),
                p("n64", "Nintendo 64", "N64", 0f, setOf("nintendo64"), setOf("z64", "n64", "v64")),
                p("gb", "Game Boy", "GB", 100f, setOf("gameboy"), setOf("gb")),
                p("gbc", "Game Boy Color", "GBC", 120f, setOf("gameboycolor"), setOf("gbc")),
                p("gba", "Game Boy Advance", "GBA", 265f, setOf("gameboyadvance"), setOf("gba")),
                p("nds", "Nintendo DS", "NDS", 200f, setOf("ds", "nintendods"), setOf("nds")),
                p("3ds", "Nintendo 3DS", "3DS", 350f, setOf("n3ds", "nintendo3ds"), setOf("3ds", "cia", "cci", "cxi")),
                p("gc", "GameCube", "GC", 285f, setOf("gamecube", "ngc"), setOf("iso", "gcm", "rvz", "gcz", "ciso")),
                p("wii", "Wii", "Wii", 190f, setOf("nintendowii"), setOf("iso", "wbfs", "rvz", "wad", "ciso")),
                p("switch", "Nintendo Switch", "NSW", 5f, setOf("nsw", "nintendoswitch"), setOf("nsp", "xci", "nsz", "xcz")),
                p("ps1", "PlayStation", "PS1", 215f, setOf("psx", "psone", "playstation"), setOf("cue", "chd", "pbp", "iso", "bin")),
                p("ps2", "PlayStation 2", "PS2", 235f, setOf("playstation2"), setOf("iso", "chd", "cso", "bin")),
                p("psp", "PlayStation Portable", "PSP", 190f, setOf("playstationportable"), setOf("iso", "cso", "pbp", "chd")),
                p("genesis", "Sega Genesis", "GEN", 20f, setOf("megadrive", "md", "segagenesis"), setOf("md", "gen", "smd", "bin")),
                p("dreamcast", "Sega Dreamcast", "DC", 30f, setOf("dc"), setOf("gdi", "cdi", "chd")),
            )
        )
    }
}
