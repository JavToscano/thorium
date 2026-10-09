package com.thorium.core.ui.theme

import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File

/** The moments a theme can attach a sound to. The key is the name used in `theme.json`. */
enum class UiSound(val key: String) {
    Move("move"),
    Select("select"),
    Back("back"),
    Tab("tab"),
    Favorite("favorite"),
    Menu("menu"),
    Launch("launch"),
    Startup("startup"),
    Done("done"),
    Error("error"),
}

/** Plays the short sounds of the active theme (WAV or OGG files) with low latency. */
class ThemeSounds {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids = HashMap<UiSound, Int>()
    private val ready = HashSet<Int>()
    private var waiting: Int? = null

    /** Sounds are muted without being unloaded, so turning them back on is instant. */
    @Volatile var enabled = true

    @Volatile var volume = 0.8f

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                synchronized(ready) { ready += sampleId }
                if (waiting == sampleId) {
                    waiting = null
                    playId(sampleId)
                }
            }
        }
    }

    /** Replaces the loaded sounds with those of a new theme. */
    fun load(files: Map<String, File>) {
        synchronized(ready) {
            ids.values.forEach { pool.unload(it) }
            ids.clear()
            ready.clear()
        }
        waiting = null
        files.forEach { (name, file) ->
            val sound = UiSound.entries.firstOrNull { it.key == name } ?: return@forEach
            val id = pool.load(file.path, 1)
            if (id != 0) ids[sound] = id
        }
    }

    fun play(sound: UiSound) {
        if (!enabled) return
        val id = ids[sound] ?: return
        if (synchronized(ready) { id in ready }) playId(id)
    }

    /** Plays [sound] as soon as it has finished loading (used for the startup sound). */
    fun playWhenReady(sound: UiSound) {
        if (!enabled) return
        val id = ids[sound] ?: return
        if (synchronized(ready) { id in ready }) playId(id) else waiting = id
    }

    private fun playId(id: Int) {
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() = pool.release()
}
