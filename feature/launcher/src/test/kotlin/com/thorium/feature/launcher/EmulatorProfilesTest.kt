package com.thorium.feature.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class EmulatorProfilesTest {

    private val json = """
        {"emulators":[
          {"id":"a","name":"A","package":"p.a","activity":"p.a.Main","platforms":["3ds"],"future":1},
          {"id":"b","name":"B","package":"p.b","activity":"p.b.Main","platforms":["gba","gb"],
           "action":"android.intent.action.MAIN","mimeType":"application/x-gba"}
        ]}
    """.trimIndent()

    @Test
    fun `parses profiles, applying defaults and ignoring unknown fields`() {
        val profiles = EmulatorProfiles.parse(json)
        assertEquals(2, profiles.size)
        assertEquals("p.a", profiles[0].packageName)
        assertEquals("android.intent.action.VIEW", profiles[0].action)
        assertEquals("application/octet-stream", profiles[0].mimeType)
        assertEquals("application/x-gba", profiles[1].mimeType)
    }

    @Test
    fun `finds the emulators for a platform`() {
        val profiles = EmulatorProfiles.parse(json)
        assertEquals(listOf("b"), EmulatorProfiles.forPlatform(profiles, "gb").map { it.id })
        assertTrue(EmulatorProfiles.forPlatform(profiles, "ps1").isEmpty())
    }

    @Test
    fun `the bundled profile file is valid and includes azahar for the 3ds`() {
        val text = File("src/main/assets/emulators.json").readText()
        val azahar = EmulatorProfiles.parse(text).single { it.id == "azahar" }
        assertEquals("org.azahar_emu.azahar", azahar.packageName)
        assertEquals(listOf("3ds"), azahar.platforms)
    }

    @Test
    fun `a disc game starts from its sheet, not from a track`() {
        val paths = listOf("/g/Game (Track 1).bin", "/g/Game.cue", "/g/Game (Track 2).bin")
        assertEquals("/g/Game.cue", PrimaryFile.pick(paths))
        assertEquals("/g/a.m3u", PrimaryFile.pick(listOf("/g/a.cue", "/g/a.m3u")))
    }

    @Test
    fun `a single file is its own entry point`() {
        assertEquals("/g/Kirby.3ds", PrimaryFile.pick(listOf("/g/Kirby.3ds")))
        assertNull(PrimaryFile.pick(emptyList()))
    }

    @Test
    fun `extras replace the path and address placeholders`() {
        val out = ExtrasTemplate.expand(
            mapOf("ROM" to "{rom_path}", "URI" to "{rom_uri}", "CORE" to "/x/core.so", "FLAG" to ""),
            "/g/Game.gba", "content://a/b",
        )
        assertEquals(mapOf("ROM" to "/g/Game.gba", "URI" to "content://a/b", "CORE" to "/x/core.so", "FLAG" to ""), out)
    }

    @Test
    fun `a path profile is parsed with its delivery and extras`() {
        val text = java.io.File("src/main/assets/emulators.json").readText()
        val ra = EmulatorProfiles.parse(text).single { it.id == "retroarch-gba" }
        assertEquals(Delivery.Path, ra.delivery)
        assertEquals("android.intent.action.MAIN", ra.action)
        assertEquals("{rom_path}", ra.extras["ROM"])
        assertTrue(ra.extras["LIBRETRO"]!!.endsWith("mgba_libretro_android.so"))
        assertEquals(Delivery.Uri, EmulatorProfiles.parse(text).single { it.id == "azahar" }.delivery)
    }
}
