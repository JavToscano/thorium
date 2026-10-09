package com.thorium.app.theme

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thorium.core.ui.theme.AssetThemeSource
import com.thorium.core.ui.theme.FolderThemeSource
import com.thorium.core.ui.theme.ThemeAssets
import com.thorium.core.ui.theme.ThemeLoader
import com.thorium.core.ui.theme.ThemeSource
import com.thorium.core.ui.theme.ThemeSpec
import com.thorium.core.ui.theme.ThemeState
import com.thorium.data.db.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

/** A theme that can be chosen: where it comes from and what it declares. */
data class ThemeEntry(val spec: ThemeSpec, val source: ThemeSource?, val builtIn: Boolean)

/**
 * Finds the themes (bundled in the app and installed by the user), applies the chosen one and
 * remembers the choice. The colors apply at once; images, font and sounds load in the background.
 */
class ThemeController(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    var entries by mutableStateOf(listOf(DEFAULT)); private set
    var activeId by mutableStateOf(ThemeSpec.Default.id); private set
    var soundsEnabled by mutableStateOf(true); private set
    var animationsEnabled by mutableStateOf(true); private set

    /** Called whenever a theme's files finish loading, so the sound player can pick up its sounds. */
    var onAssets: (ThemeSpec, ThemeAssets) -> Unit = { _, _ -> }

    private var loading: Job? = null

    /** Reads the saved choice and applies it; call before the first screen is drawn. */
    fun start() {
        runBlocking {
            activeId = settings.themeId()
            soundsEnabled = settings.isThemeSoundsEnabled()
            animationsEnabled = settings.isAnimationsEnabled()
        }
        ThemeState.animations = animationsEnabled
        rescan()
        apply(entries.firstOrNull { it.spec.id == activeId } ?: DEFAULT)
    }

    /** Looks again for themes, for instance after the user copied a new folder into place. */
    fun rescan() {
        val found = LinkedHashMap<String, ThemeEntry>()
        found[DEFAULT.spec.id] = DEFAULT
        bundledThemes().forEach { found[it.spec.id] = it }
        installedThemes().forEach { found[it.spec.id] = it }
        entries = found.values.toList()
    }

    fun select(id: String) {
        val entry = entries.firstOrNull { it.spec.id == id } ?: return
        activeId = id
        apply(entry)
        scope.launch { settings.setThemeId(id) }
    }

    fun setSounds(enabled: Boolean) {
        soundsEnabled = enabled
        scope.launch { settings.setThemeSoundsEnabled(enabled) }
    }

    fun setAnimations(enabled: Boolean) {
        animationsEnabled = enabled
        ThemeState.animations = enabled
        scope.launch { settings.setAnimationsEnabled(enabled) }
    }

    private fun apply(entry: ThemeEntry) {
        loading?.cancel()
        // Colors first, so there is no flash of the old look while the images decode.
        ThemeState.apply(entry.spec, ThemeAssets.Empty)
        val source = entry.source
        if (source == null) {
            onAssets(entry.spec, ThemeAssets.Empty)
            return
        }
        loading = scope.launch(Dispatchers.IO) {
            val assets = runCatching { ThemeLoader.loadAssets(entry.spec, source) }.getOrDefault(ThemeAssets.Empty)
            // Compose state may be written from any thread.
            if (activeId == entry.spec.id) {
                ThemeState.apply(entry.spec, assets)
                onAssets(entry.spec, assets)
            }
        }
    }

    private fun bundledThemes(): List<ThemeEntry> {
        val names = runCatching { context.assets.list(BUNDLED_DIR)?.toList() }.getOrNull().orEmpty()
        return names.mapNotNull { name ->
            val source = AssetThemeSource(context.assets, "$BUNDLED_DIR/$name", File(context.cacheDir, "themes"))
            runCatching { ThemeEntry(ThemeLoader.readSpec(source), source, builtIn = true) }.getOrNull()
        }
    }

    private fun installedThemes(): List<ThemeEntry> {
        val root = File(Environment.getExternalStorageDirectory(), USER_DIR)
        val folders = root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty().sortedBy { it.name.lowercase() }
        return folders.mapNotNull { dir ->
            val source = FolderThemeSource(dir)
            runCatching { ThemeEntry(ThemeLoader.readSpec(source), source, builtIn = false) }.getOrNull()
        }
    }

    companion object {
        const val BUNDLED_DIR = "themes"
        /** Where the user drops their own themes, relative to the storage root. */
        const val USER_DIR = "Thorium/themes"
        private val DEFAULT = ThemeEntry(ThemeSpec.Default, null, builtIn = true)
    }
}
