package com.thorium.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thorium.app.storage.StorageVolumeInfo
import com.thorium.core.ui.input.GamepadAction
import java.io.File

enum class SettingsPage { Main, Folders, Browser }

/** One selectable row of the "Game folders" page. */
sealed interface FolderRow {
    data class AutoDetect(val enabled: Boolean, val volumes: List<StorageVolumeInfo>) : FolderRow
    data class Custom(val path: String, val gameCount: Int) : FolderRow
    data object Add : FolderRow
}

/** A folder shown in the folder browser. */
data class BrowserEntry(val label: String, val dir: File)

/** What the settings screens need from the rest of the app. */
interface SettingsHost {
    val autoDetectStorage: Boolean
    val customRoots: List<String>
    val companionEnabled: Boolean
    fun volumes(): List<StorageVolumeInfo>
    fun gamesIn(path: String): Int
    fun setAutoDetectStorage(enabled: Boolean)
    fun addRoot(path: String)
    fun removeRoot(path: String)
    fun setCompanionEnabled(enabled: Boolean)
    fun rescan()
    fun openDiagnostics()
    fun toast(message: String)
}

/**
 * Logical focus and navigation for the Settings tab: main list, game folders and a folder
 * browser. Like the rest of the UI it is driven only by [GamepadAction]s.
 */
class SettingsController(private val host: SettingsHost) {

    var page by mutableStateOf(SettingsPage.Main); private set
    var mainIndex by mutableIntStateOf(0); private set
    var foldersIndex by mutableIntStateOf(0); private set
    var browserIndex by mutableIntStateOf(0); private set

    /** Folder being browsed; null means the list of storage volumes. */
    var browserDir by mutableStateOf<File?>(null); private set
    var browserEntries by mutableStateOf<List<BrowserEntry>>(emptyList()); private set

    val mainItems: List<Pair<String, String?>>
        get() = listOf(
            "Game folders" to "Choose where Thorium looks for games",
            "Dual screen" to if (host.companionEnabled) "On" else "Off",
            "Rescan library" to null,
            "Diagnostics" to "Displays and controller tester",
            "About" to "Thorium 0.0.1",
        )

    val folderRows: List<FolderRow>
        get() = buildList {
            add(FolderRow.AutoDetect(host.autoDetectStorage, host.volumes()))
            host.customRoots.forEach { add(FolderRow.Custom(it, host.gamesIn(it))) }
            add(FolderRow.Add)
        }

    val hints: List<Pair<String, String>>
        get() = when (page) {
            SettingsPage.Main -> listOf("A" to "Select", "B" to "Home", "L1/R1" to "Tabs", "START" to "Menu")
            SettingsPage.Folders -> listOf("A" to "Select", "Y" to "Remove folder", "B" to "Back")
            SettingsPage.Browser -> listOf("A" to "Open", "Y" to "Use this folder", "B" to "Up")
        }

    /** Leaves any sub-page; called when the tab is entered again from elsewhere. */
    fun reset() {
        page = SettingsPage.Main
    }

    /** Returns true when the action was handled here (so the caller must not also act on it). */
    fun handle(action: GamepadAction): Boolean = when (page) {
        SettingsPage.Main -> handleMain(action)
        SettingsPage.Folders -> handleFolders(action)
        SettingsPage.Browser -> handleBrowser(action)
    }

    private fun handleMain(action: GamepadAction): Boolean {
        val count = mainItems.size
        when (action) {
            GamepadAction.Up -> mainIndex = (mainIndex - 1).coerceAtLeast(0)
            GamepadAction.Down -> mainIndex = (mainIndex + 1).coerceAtMost(count - 1)
            GamepadAction.Select -> when (mainItems[mainIndex].first) {
                "Game folders" -> { page = SettingsPage.Folders; foldersIndex = 0 }
                "Dual screen" -> host.setCompanionEnabled(!host.companionEnabled)
                "Rescan library" -> host.rescan()
                "Diagnostics" -> host.openDiagnostics()
                "About" -> host.toast("Thorium 0.0.1")
            }
            else -> return false
        }
        return true
    }

    private fun handleFolders(action: GamepadAction): Boolean {
        val rows = folderRows
        foldersIndex = foldersIndex.coerceIn(0, rows.size - 1)
        when (action) {
            GamepadAction.Up -> foldersIndex = (foldersIndex - 1).coerceAtLeast(0)
            GamepadAction.Down -> foldersIndex = (foldersIndex + 1).coerceAtMost(rows.size - 1)
            GamepadAction.Select -> when (rows[foldersIndex]) {
                is FolderRow.AutoDetect -> host.setAutoDetectStorage(!host.autoDetectStorage)
                is FolderRow.Custom -> host.toast("Press Y to remove this folder")
                FolderRow.Add -> openBrowser()
            }
            GamepadAction.Favorite -> (rows[foldersIndex] as? FolderRow.Custom)?.let {
                host.removeRoot(it.path)
                foldersIndex = (foldersIndex - 1).coerceAtLeast(0)
            }
            GamepadAction.Back -> page = SettingsPage.Main
            else -> return false
        }
        return true
    }

    private fun handleBrowser(action: GamepadAction): Boolean {
        when (action) {
            GamepadAction.Up -> browserIndex = (browserIndex - 1).coerceAtLeast(0)
            GamepadAction.Down -> browserIndex = (browserIndex + 1).coerceAtMost((browserEntries.size - 1).coerceAtLeast(0))
            GamepadAction.Select -> browserEntries.getOrNull(browserIndex)?.let { showDir(it.dir) }
            GamepadAction.Favorite -> addCurrentSelection()
            GamepadAction.Back -> browserUp()
            else -> return false
        }
        return true
    }

    private fun openBrowser() {
        page = SettingsPage.Browser
        showDir(null)
    }

    private fun showDir(dir: File?) {
        browserDir = dir
        browserIndex = 0
        browserEntries = if (dir == null) {
            host.volumes().map { BrowserEntry(it.label, it.dir) }
        } else {
            dir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                ?.map { BrowserEntry(it.name, it) }
                .orEmpty()
        }
    }

    private fun browserUp() {
        val dir = browserDir
        val isVolumeRoot = dir == null || host.volumes().any { it.dir == dir }
        val parent = dir?.parentFile
        when {
            dir == null -> page = SettingsPage.Folders
            isVolumeRoot || parent == null -> showDir(null)
            else -> showDir(parent)
        }
    }

    /** Adds the highlighted folder, or the folder being browsed when it has no sub-folders. */
    private fun addCurrentSelection() {
        val target = browserEntries.getOrNull(browserIndex)?.dir ?: browserDir ?: return
        if (target.path in host.customRoots) {
            host.toast("Already in the list")
            return
        }
        host.addRoot(target.path)
        page = SettingsPage.Folders
        foldersIndex = 0
    }
}
