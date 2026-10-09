package com.thorium.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import com.thorium.app.R
import com.thorium.app.storage.StorageVolumeInfo
import com.thorium.app.ui.main.Hint
import com.thorium.app.ui.main.LanguageChoice
import com.thorium.app.ui.main.LanguageController
import com.thorium.core.ui.text.UiText
import com.thorium.core.ui.input.GamepadAction
import com.thorium.core.ui.keyboard.KeyboardController
import com.thorium.data.library.FolderInitializer
import com.thorium.data.library.SetupEntry
import java.io.File

enum class SettingsPage { Main, Folders, Browser, Setup, Sources, SourceForm, SourceConsole, About, Themes }

/** One selectable row of the "Game folders" page. */
sealed interface FolderRow {
    data class AutoDetect(val enabled: Boolean, val volumes: List<StorageVolumeInfo>) : FolderRow
    data class Custom(val path: String, val gameCount: Int) : FolderRow
    data object Add : FolderRow
}

/** Rows of the main Settings page. */
enum class SettingsItem(@StringRes val title: Int, @StringRes val description: Int?) {
    Folders(R.string.settings_folders_title, R.string.settings_folders_desc),
    Sources(R.string.settings_sources_title, R.string.settings_sources_desc),
    DualScreen(R.string.settings_dual_title, R.string.settings_dual_desc),
    Covers(R.string.settings_covers_title, R.string.settings_covers_desc),
    Themes(R.string.settings_themes_title, R.string.settings_themes_desc),
    Language(R.string.settings_language_title, R.string.settings_language_desc),
    Rescan(R.string.settings_rescan_title, null),
    About(R.string.settings_about_title, R.string.settings_about_desc),
}

/** One selectable row of the Themes page. */
sealed interface ThemeRow {
    data class Theme(val entry: com.thorium.app.theme.ThemeEntry) : ThemeRow
    data object Sounds : ThemeRow
    data object Animations : ThemeRow
}

/** A folder shown in the folder browser. */
data class BrowserEntry(val label: String, val dir: File)

/** What the settings screens need from the rest of the app. */
interface SettingsHost : SourcesHost {
    val autoDetectStorage: Boolean
    val customRoots: List<String>
    val companionEnabled: Boolean
    val coversEnabled: Boolean
    val themeEntries: List<com.thorium.app.theme.ThemeEntry>
    val activeThemeId: String
    fun selectTheme(id: String)
    val themeSounds: Boolean
    fun setThemeSounds(enabled: Boolean)
    val animations: Boolean
    fun setAnimations(enabled: Boolean)
    fun setCoversEnabled(enabled: Boolean)
    fun volumes(): List<StorageVolumeInfo>
    fun gamesIn(path: String): Int
    fun setAutoDetectStorage(enabled: Boolean)
    fun addRoot(path: String)
    fun removeRoot(path: String)
    fun setCompanionEnabled(enabled: Boolean)
    fun rescan()
    val language: LanguageController?
    fun cycleLanguage()
}

/**
 * Logical focus and navigation for the Settings tab: main list, game folders and a folder
 * browser. Like the rest of the UI it is driven only by [GamepadAction]s.
 */
class SettingsController(internal val host: SettingsHost) {

    companion object {
        const val VERSION = "0.0.1"
        /** Number of blocks on the About page (app, catalog, covers, components). */
        const val ABOUT_BLOCKS = 4
    }

    private val initializer = FolderInitializer()

    /** Controller-driven text entry, shared by every settings page that needs typing. */
    val keyboard = KeyboardController()

    /** Sources list and add/edit form. */
    internal val sourcesUi = SourcesController(host, keyboard) { page = it }

    var page by mutableStateOf(SettingsPage.Main); private set
    var mainIndex by mutableIntStateOf(0); private set
    var foldersIndex by mutableIntStateOf(0); private set
    var browserIndex by mutableIntStateOf(0); private set
    var aboutIndex by mutableIntStateOf(0); private set
    var themesIndex by mutableIntStateOf(0); private set

    /** Folder being browsed; null means the list of storage volumes. */
    var browserDir by mutableStateOf<File?>(null); private set
    var browserEntries by mutableStateOf<List<BrowserEntry>>(emptyList()); private set

    // "Set up folder" page: which console folders to create inside [setupPath].
    var setupPath by mutableStateOf(""); private set
    var setupEntries by mutableStateOf<List<SetupEntry>>(emptyList()); private set
    var setupSelected by mutableStateOf<Set<String>>(emptySet()); private set
    var setupIndex by mutableIntStateOf(0); private set

    val companionOn: Boolean get() = host.companionEnabled
    val coversOn: Boolean get() = host.coversEnabled
    val activeThemeName: String get() = host.themeEntries.firstOrNull { it.spec.id == host.activeThemeId }?.spec?.name.orEmpty()

    val themeRows: List<ThemeRow>
        get() = host.themeEntries.map { ThemeRow.Theme(it) } + ThemeRow.Sounds + ThemeRow.Animations
    val languageChoice: LanguageChoice get() = host.language?.current() ?: LanguageChoice.System

    /** Rows of the main page, in order; the Language row only exists when the system supports it. */
    val mainItems: List<SettingsItem>
        get() = buildList {
            add(SettingsItem.Folders)
            add(SettingsItem.Sources)
            add(SettingsItem.DualScreen)
            add(SettingsItem.Covers)
            add(SettingsItem.Themes)
            if (host.language != null) add(SettingsItem.Language)
            add(SettingsItem.Rescan)
            add(SettingsItem.About)
        }

    val folderRows: List<FolderRow>
        get() = buildList {
            add(FolderRow.AutoDetect(host.autoDetectStorage, host.volumes()))
            host.customRoots.forEach { add(FolderRow.Custom(it, host.gamesIn(it))) }
            add(FolderRow.Add)
        }

    val hints: List<Hint>
        get() = if (keyboard.active) keyboardHints else when (page) {
            SettingsPage.Main -> listOf(
                Hint("A", R.string.hint_select), Hint("B", R.string.hint_home),
                Hint("L1/R1", R.string.hint_tabs), Hint("START", R.string.hint_menu),
            )
            SettingsPage.Folders -> listOf(
                Hint("A", R.string.hint_select), Hint("Y", R.string.hint_remove_folder), Hint("B", R.string.hint_back),
            )
            SettingsPage.Browser -> listOf(
                Hint("A", R.string.hint_open), Hint("Y", R.string.hint_use_folder), Hint("B", R.string.hint_up),
            )
            SettingsPage.Setup -> listOf(
                Hint("A", R.string.hint_toggle_create), Hint("Y", R.string.hint_all_none), Hint("B", R.string.hint_back),
            )
            SettingsPage.Sources -> sourcesUi.listHints
            SettingsPage.SourceForm -> sourcesUi.formHints
            SettingsPage.SourceConsole -> sourcesUi.consoleHints
            SettingsPage.About -> listOf(Hint("B", R.string.hint_back))
            SettingsPage.Themes -> listOf(Hint("A", R.string.hint_apply), Hint("B", R.string.hint_back))
        }

    private val keyboardHints = listOf(
        Hint("A", R.string.hint_type), Hint("Y", R.string.hint_delete), Hint("SELECT", R.string.hint_shift),
        Hint("START", R.string.hint_done), Hint("B", R.string.hint_cancel),
    )

    /** Jumps straight to the sources list (used by the Downloads tab). */
    fun openSources() {
        page = SettingsPage.Sources
    }

    /** Leaves any sub-page; called when the tab is entered again from elsewhere. */
    fun reset() {
        page = SettingsPage.Main
    }

    /** Returns true when the action was handled here (so the caller must not also act on it). */
    fun handle(action: GamepadAction): Boolean = if (keyboard.handle(action)) true else when (page) {
        SettingsPage.Main -> handleMain(action)
        SettingsPage.Folders -> handleFolders(action)
        SettingsPage.Browser -> handleBrowser(action)
        SettingsPage.Setup -> handleSetup(action)
        SettingsPage.Sources -> sourcesUi.handleList(action)
        SettingsPage.SourceForm -> sourcesUi.handleForm(action)
        SettingsPage.SourceConsole -> sourcesUi.handleConsole(action)
        SettingsPage.About -> handleAbout(action)
        SettingsPage.Themes -> handleThemes(action)
    }

    private fun handleMain(action: GamepadAction): Boolean {
        val count = mainItems.size
        when (action) {
            GamepadAction.Up -> mainIndex = (mainIndex - 1).coerceAtLeast(0)
            GamepadAction.Down -> mainIndex = (mainIndex + 1).coerceAtMost(count - 1)
            GamepadAction.Select -> when (mainItems[mainIndex]) {
                SettingsItem.Folders -> { page = SettingsPage.Folders; foldersIndex = 0 }
                SettingsItem.Sources -> page = SettingsPage.Sources
                SettingsItem.DualScreen -> host.setCompanionEnabled(!host.companionEnabled)
                SettingsItem.Covers -> host.setCoversEnabled(!host.coversEnabled)
                SettingsItem.Themes -> {
                    page = SettingsPage.Themes
                    themesIndex = host.themeEntries.indexOfFirst { it.spec.id == host.activeThemeId }.coerceAtLeast(0)
                }
                SettingsItem.Language -> host.cycleLanguage()
                SettingsItem.Rescan -> host.rescan()
                SettingsItem.About -> { page = SettingsPage.About; aboutIndex = 0 }
            }
            else -> return false
        }
        return true
    }

    private fun handleThemes(action: GamepadAction): Boolean {
        val rows = themeRows
        themesIndex = themesIndex.coerceIn(0, rows.size - 1)
        when (action) {
            GamepadAction.Up -> themesIndex = (themesIndex - 1).coerceAtLeast(0)
            GamepadAction.Down -> themesIndex = (themesIndex + 1).coerceAtMost(rows.size - 1)
            GamepadAction.Select -> when (val row = rows[themesIndex]) {
                is ThemeRow.Theme -> host.selectTheme(row.entry.spec.id)
                ThemeRow.Sounds -> host.setThemeSounds(!host.themeSounds)
                ThemeRow.Animations -> host.setAnimations(!host.animations)
            }
            GamepadAction.Back -> page = SettingsPage.Main
            else -> return false
        }
        return true
    }

    private fun handleAbout(action: GamepadAction): Boolean {
        when (action) {
            GamepadAction.Up -> aboutIndex = (aboutIndex - 1).coerceAtLeast(0)
            GamepadAction.Down -> aboutIndex = (aboutIndex + 1).coerceAtMost(ABOUT_BLOCKS - 1)
            GamepadAction.Back -> page = SettingsPage.Main
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
            GamepadAction.Select -> when (val row = rows[foldersIndex]) {
                is FolderRow.AutoDetect -> host.setAutoDetectStorage(!host.autoDetectStorage)
                is FolderRow.Custom -> openSetup(row.path)
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
            host.toast(UiText.res(R.string.toast_already_listed))
            return
        }
        host.addRoot(target.path)
        // Offer to create the console folders right away; B leaves it untouched.
        openSetup(target.path)
    }

    private fun handleSetup(action: GamepadAction): Boolean {
        val creatable = setupEntries.filter { it.existingName == null }.map { it.platform.id }
        val rowCount = 1 + setupEntries.size
        when (action) {
            GamepadAction.Up -> setupIndex = (setupIndex - 1).coerceAtLeast(0)
            GamepadAction.Down -> setupIndex = (setupIndex + 1).coerceAtMost(rowCount - 1)
            GamepadAction.Select ->
                if (setupIndex == 0) {
                    createSelectedFolders()
                } else {
                    val entry = setupEntries[setupIndex - 1]
                    if (entry.existingName == null) {
                        val id = entry.platform.id
                        setupSelected = if (id in setupSelected) setupSelected - id else setupSelected + id
                    }
                }
            GamepadAction.Favorite ->
                setupSelected = if (setupSelected.size == creatable.size) emptySet() else creatable.toSet()
            GamepadAction.Back -> page = SettingsPage.Folders
            else -> return false
        }
        return true
    }

    fun openSetup(path: String) {
        setupPath = path
        setupEntries = initializer.inspect(File(path))
        setupSelected = setupEntries.filter { it.existingName == null }.map { it.platform.id }.toSet()
        setupIndex = 0
        page = SettingsPage.Setup
    }

    private fun createSelectedFolders() {
        if (setupSelected.isEmpty()) {
            host.toast(UiText.res(R.string.toast_nothing_to_create))
            return
        }
        val result = initializer.create(File(setupPath), setupSelected)
        host.toast(
            if (result.failed.isNotEmpty()) {
                UiText.res(R.string.toast_created_partial, result.created.size, result.failed.joinToString())
            } else {
                UiText.plural(R.plurals.toast_created_folders, result.created.size)
            }
        )
        page = SettingsPage.Folders
    }
}
