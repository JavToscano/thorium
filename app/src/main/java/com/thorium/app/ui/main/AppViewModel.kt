package com.thorium.app.ui.main

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thorium.core.model.Game
import com.thorium.core.model.GameSystem
import com.thorium.core.model.Library
import com.thorium.app.R
import com.thorium.app.storage.StorageVolumeInfo
import com.thorium.app.ui.settings.SettingsController
import com.thorium.app.ui.settings.SettingsHost
import com.thorium.data.db.ScanSettings
import com.thorium.data.db.ThoriumData
import com.thorium.data.library.LibraryScanner
import com.thorium.data.library.PlatformCatalog
import com.thorium.data.library.ScanStats
import com.thorium.core.ui.input.GamepadAction
import com.thorium.core.ui.text.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

enum class Tab(@StringRes val title: Int) {
    Home(R.string.tab_home),
    Systems(R.string.tab_systems),
    Favorites(R.string.tab_favorites),
    Settings(R.string.tab_settings),
}

sealed interface CardModel {
    val key: String
    data class GameCard(val game: Game, val system: GameSystem) : CardModel {
        override val key get() = "g-${game.id}"
    }
    data class SystemCard(val system: GameSystem, val gameCount: Int) : CardModel {
        override val key get() = "s-${system.id}"
    }
}

/** A horizontal row of cards; [title] is null for continuation rows that have no heading. */
data class RowModel(val id: String, val title: UiText?, val items: List<CardModel>)

enum class MenuItem(@StringRes val label: Int) {
    Resume(R.string.menu_resume),
    Rescan(R.string.menu_rescan),
    Settings(R.string.menu_settings),
}

/** One entry of the button legend: the button name is shown as is, the label is translated. */
data class Hint(val button: String, @StringRes val label: Int)
/** Detail screen buttons: Play, then Favorite (its label flips to "Unfavorite" in the UI). */
val DETAIL_BUTTONS = listOf(R.string.detail_play, R.string.detail_favorite)

enum class LanguageChoice { System, English, Spanish }

/** Per-app language, implemented by the application on top of the system's locale manager. */
interface LanguageController {
    fun current(): LanguageChoice
    fun set(choice: LanguageChoice)

    /** Moves to the next choice (System, English, Spanish, back to System). */
    fun cycle() {
        val all = LanguageChoice.entries
        set(all[(current().ordinal + 1) % all.size])
    }
}

sealed interface LibraryState {
    data object NeedsPermission : LibraryState
    data object Scanning : LibraryState
    data class Ready(val stats: ScanStats) : LibraryState
}

/**
 * Logical focus model: the ViewModel owns tab / row / item focus and the UI only draws it.
 * Phase 4 runs on fake data; the library arrives in Phase 5/6.
 */
class AppViewModel : ViewModel() {

    private val scanner = LibraryScanner()
    private var scanJob: Job? = null
    private var library by mutableStateOf(Library.Empty)

    // Favorite game ids in the order they were added; mirrored from the database.
    private var favorites by mutableStateOf<List<String>>(emptyList())

    private var data: ThoriumData? = null

    private var scanSettings by mutableStateOf(ScanSettings())
    private var rescanQueued = false

    var libraryState by mutableStateOf<LibraryState>(LibraryState.Scanning); private set

    /** Wired by the application: storage permission check and the folders to scan. */
    var permissionGranted: () -> Boolean = { true }
    var storageRoots: () -> List<File> = { emptyList() }
    var storageVolumes: () -> List<StorageVolumeInfo> = { emptyList() }
    var onRequestStoragePermission: (() -> Unit)? = null

    /**
     * Connects the persistence layer. The library, favorites and settings shown in the UI come
     * from the database, so the last known library appears instantly on launch, before any scan
     * finishes.
     */
    fun attachData(data: ThoriumData) {
        if (this.data != null) return
        this.data = data
        // Read once up front so a disabled companion never flashes open at launch.
        companionEnabled = runBlocking { data.settings.isCompanionEnabled() }
        viewModelScope.launch {
            data.library.observeGames().collect { games -> library = buildLibrary(games) }
        }
        viewModelScope.launch {
            data.library.observeFavoriteIds().collect { favorites = it }
        }
        viewModelScope.launch {
            data.settings.observeScanSettings().collect { scanSettings = it }
        }
    }

    private fun buildLibrary(games: List<Game>): Library {
        val used = games.mapTo(HashSet()) { it.systemId }
        val systems = PlatformCatalog.Default.platforms.map { it.system }.filter { it.id in used }
        return Library(systems, games)
    }

    var tab by mutableStateOf(Tab.Home); private set
    var detail by mutableStateOf<Game?>(null); private set
    var detailFocus by mutableIntStateOf(0); private set
    var menuOpen by mutableStateOf(false); private set
    var menuIndex by mutableIntStateOf(0); private set
    var toast by mutableStateOf<UiText?>(null); private set

    /** User preference (persisted): show the companion on the secondary display when one exists. */
    var companionEnabled by mutableStateOf(true); private set

    /** False once the main activity is finishing, so the companion closes with it. */
    var mainAlive by mutableStateOf(true); private set

    val companionVisible: Boolean get() = companionEnabled && mainAlive

    fun markMainClosed() { mainAlive = false }
    fun markMainOpen() { mainAlive = true }

    private val rowFocus = mutableStateMapOf<Tab, Int>()
    private val itemFocus = mutableStateMapOf<String, Int>()
    private var toastJob: Job? = null

    /** Wired by the application; null when per-app language is not available (Android < 13). */
    var languageController: LanguageController? = null

    val settings = SettingsController(object : SettingsHost {
        override val autoDetectStorage get() = scanSettings.autoDetectStorage
        override val customRoots get() = scanSettings.customRoots
        override val companionEnabled get() = this@AppViewModel.companionEnabled
        override fun volumes() = storageVolumes()
        override fun gamesIn(path: String) =
            library.games.count { game -> game.files.any { it.path.startsWith("$path/") } }

        override fun setAutoDetectStorage(enabled: Boolean) {
            viewModelScope.launch {
                data?.settings?.setAutoDetectStorage(enabled)
                refreshLibrary(force = true)
            }
        }

        override fun addRoot(path: String) {
            viewModelScope.launch {
                data?.settings?.addScanRoot(path)
                showToast(UiText.res(R.string.toast_root_added, path))
                refreshLibrary(force = true)
            }
        }

        override fun removeRoot(path: String) {
            viewModelScope.launch {
                data?.settings?.removeScanRoot(path)
                showToast(UiText.res(R.string.toast_root_removed, path))
                refreshLibrary(force = true)
            }
        }

        override fun setCompanionEnabled(enabled: Boolean) {
            this@AppViewModel.companionEnabled = enabled
            viewModelScope.launch { data?.settings?.setCompanionEnabled(enabled) }
            showToast(UiText.res(if (enabled) R.string.toast_dual_on else R.string.toast_dual_off))
        }

        override fun rescan() = refreshLibrary(force = true)
        override fun toast(message: UiText) = showToast(message)
        override val language get() = languageController
        override fun cycleLanguage() { languageController?.cycle() }
    })

    /** Button legend for whatever is on screen. */
    val hints: List<Hint> get() = when {
        menuOpen -> listOf(Hint("A", R.string.hint_select), Hint("B", R.string.hint_close))
        libraryState is LibraryState.NeedsPermission ->
            listOf(Hint("A", R.string.hint_open_settings), Hint("START", R.string.hint_menu))
        detail != null -> listOf(
            Hint("A", R.string.hint_select), Hint("B", R.string.hint_back),
            Hint("Y", R.string.hint_favorite), Hint("START", R.string.hint_menu),
        )
        tab == Tab.Settings -> settings.hints
        else -> listOf(
            Hint("A", R.string.hint_select), Hint("B", R.string.hint_back), Hint("Y", R.string.hint_favorite),
            Hint("START", R.string.hint_menu), Hint("SELECT", R.string.hint_options),
        )
    }

    /** Card under the logical focus (or the open detail game); drives the companion screen. */
    val focusedCard: CardModel? get() {
        detail?.let { return CardModel.GameCard(it, systemOf(it)) }
        val row = rows.getOrNull(focusedRow) ?: return null
        return row.items.getOrNull(focusedItem(row))
    }

    fun isFavorite(gameId: String) = gameId in favorites
    fun systemOf(game: Game) = library.system(game.systemId)

    val rows: List<RowModel> get() = buildRows(tab)

    /** Text shown when the current tab has nothing to display. */
    @get:StringRes
    val emptyMessage: Int get() = when {
        libraryState is LibraryState.Scanning -> R.string.empty_scanning
        tab == Tab.Favorites -> R.string.empty_favorites
        else -> R.string.empty_no_games
    }

    /**
     * Checks the storage permission and scans when needed. Cheap to call on every resume: it does
     * nothing if the library is already loaded, unless [force] is set.
     */
    fun refreshLibrary(force: Boolean = false) {
        if (!permissionGranted()) {
            libraryState = LibraryState.NeedsPermission
            return
        }
        if (!force && libraryState is LibraryState.Ready) return
        if (scanJob?.isActive == true) {
            // Settings changed mid-scan: run once more with the new folders when this one ends.
            if (force) rescanQueued = true
            return
        }
        libraryState = LibraryState.Scanning
        scanJob = viewModelScope.launch {
            val roots = scanRoots(data?.settings?.scanSettingsNow() ?: scanSettings)
            val result = withContext(Dispatchers.IO) { scanner.scan(roots) }
            // The UI updates itself through the repository's flows once the sync commits.
            val summary = data?.library?.sync(result.library.games)
            libraryState = LibraryState.Ready(result.stats)
            val added = summary?.added ?: 0
            showToast(
                if (added > 0) {
                    UiText.plural(R.plurals.toast_games_found_new, result.stats.games, added, result.stats.durationMs)
                } else {
                    UiText.plural(R.plurals.toast_games_found, result.stats.games, result.stats.durationMs)
                }
            )
            if (rescanQueued) {
                rescanQueued = false
                refreshLibrary(force = true)
            }
        }
    }

    /** Auto-detected volumes (unless turned off) plus the folders added by hand. */
    private fun scanRoots(settings: ScanSettings): List<File> {
        val auto = if (settings.autoDetectStorage) storageRoots() else emptyList()
        return (auto + settings.customRoots.map(::File)).distinct()
    }

    val focusedRow: Int get() = (rowFocus[tab] ?: 0).coerceIn(0, (rows.size - 1).coerceAtLeast(0))

    fun focusedItem(row: RowModel): Int =
        (itemFocus[itemKey(tab, row)] ?: 0).coerceIn(0, (row.items.size - 1).coerceAtLeast(0))

    private fun itemKey(tab: Tab, row: RowModel) = "${tab.name}/${row.id}"

    private fun buildRows(tab: Tab): List<RowModel> {
        if (tab == Tab.Settings || library.games.isEmpty()) return emptyList()
        fun gameCards(games: List<Game>) = games.map { CardModel.GameCard(it, library.system(it.systemId)) }
        return when (tab) {
            Tab.Home -> buildList {
                val continuePlaying = library.games.filter { it.lastPlayedAt != null }
                    .sortedByDescending { it.lastPlayedAt }.take(6)
                if (continuePlaying.isNotEmpty())
                    add(RowModel("continue", UiText.res(R.string.row_continue), gameCards(continuePlaying)))
                add(RowModel("systems", UiText.res(R.string.row_systems), library.systems.map { s ->
                    CardModel.SystemCard(s, library.games.count { it.systemId == s.id })
                }))
                add(RowModel("recent", UiText.res(R.string.row_recent), gameCards(library.games.sortedByDescending { it.addedAt }.take(10))))
                val favs = favoriteGames()
                if (favs.isNotEmpty()) add(RowModel("favorites", UiText.res(R.string.row_favorites), gameCards(favs)))
            }
            Tab.Systems -> library.systems.mapNotNull { s ->
                val games = library.games.filter { it.systemId == s.id }
                if (games.isEmpty()) null else RowModel("sys-${s.id}", UiText.Raw(s.name), gameCards(games))
            }
            Tab.Favorites -> favoriteGames().chunked(6).mapIndexed { i, chunk ->
                RowModel("fav-$i", if (i == 0) UiText.res(R.string.row_favorites) else null, gameCards(chunk))
            }
            Tab.Settings -> emptyList()
        }
    }

    private fun favoriteGames() = favorites.mapNotNull { id -> library.games.firstOrNull { it.id == id } }

    fun handle(action: GamepadAction) {
        when {
            menuOpen -> handleMenu(action)
            libraryState is LibraryState.NeedsPermission -> handlePermission(action)
            detail != null -> handleDetail(action)
            tab == Tab.Settings -> handleSettings(action)
            else -> handleBrowse(action)
        }
    }

    private fun handlePermission(action: GamepadAction) {
        when (action) {
            GamepadAction.Select -> onRequestStoragePermission?.invoke()
            GamepadAction.Menu -> openMenu()
            else -> Unit
        }
    }

    private fun handleBrowse(action: GamepadAction) {
        val rows = rows
        val rowIndex = focusedRow
        when (action) {
            GamepadAction.Up -> if (rowIndex > 0) rowFocus[tab] = rowIndex - 1
            GamepadAction.Down -> if (rowIndex < rows.size - 1) rowFocus[tab] = rowIndex + 1
            GamepadAction.Left -> rows.getOrNull(rowIndex)?.let { moveItem(it, -1) }
            GamepadAction.Right -> rows.getOrNull(rowIndex)?.let { moveItem(it, +1) }
            GamepadAction.TabLeft -> switchTab(-1)
            GamepadAction.TabRight -> switchTab(+1)
            GamepadAction.Select -> rows.getOrNull(rowIndex)?.let { row ->
                when (val card = row.items.getOrNull(focusedItem(row))) {
                    is CardModel.GameCard -> openDetail(card.game)
                    is CardModel.SystemCard -> openSystem(card.system)
                    null -> Unit
                }
            }
            GamepadAction.Back -> if (tab != Tab.Home) tab = Tab.Home
            GamepadAction.Menu -> openMenu()
            GamepadAction.Favorite -> focusedGame()?.let { toggleFavorite(it) }
            GamepadAction.Secondary -> showToast(UiText.res(R.string.toast_secondary_soon))
        }
    }

    private fun handleSettings(action: GamepadAction) {
        if (settings.handle(action)) return
        when (action) {
            GamepadAction.TabLeft -> if (settings.page == com.thorium.app.ui.settings.SettingsPage.Main) switchTab(-1)
            GamepadAction.TabRight -> if (settings.page == com.thorium.app.ui.settings.SettingsPage.Main) switchTab(+1)
            GamepadAction.Back -> tab = Tab.Home
            GamepadAction.Menu -> openMenu()
            else -> Unit
        }
    }

    private fun handleDetail(action: GamepadAction) {
        val game = detail ?: return
        when (action) {
            GamepadAction.Left -> detailFocus = (detailFocus - 1).coerceAtLeast(0)
            GamepadAction.Right -> detailFocus = (detailFocus + 1).coerceAtMost(DETAIL_BUTTONS.size - 1)
            GamepadAction.Select ->
                if (detailFocus == 0) showToast(UiText.res(R.string.toast_launcher_soon)) else toggleFavorite(game)
            GamepadAction.Favorite -> toggleFavorite(game)
            GamepadAction.Back -> detail = null
            GamepadAction.Menu -> openMenu()
            else -> Unit
        }
    }

    private fun handleMenu(action: GamepadAction) {
        when (action) {
            GamepadAction.Up -> menuIndex = (menuIndex - 1).coerceAtLeast(0)
            GamepadAction.Down -> menuIndex = (menuIndex + 1).coerceAtMost(MenuItem.entries.size - 1)
            GamepadAction.Select -> {
                when (MenuItem.entries[menuIndex]) {
                    MenuItem.Resume -> Unit
                    MenuItem.Rescan -> refreshLibrary(force = true)
                    MenuItem.Settings -> {
                        settings.reset()
                        tab = Tab.Settings
                    }
                }
                menuOpen = false
            }
            GamepadAction.Back, GamepadAction.Menu -> menuOpen = false
            else -> Unit
        }
    }

    private fun moveItem(row: RowModel, delta: Int) {
        val next = (focusedItem(row) + delta).coerceIn(0, row.items.size - 1)
        itemFocus[itemKey(tab, row)] = next
    }

    private fun switchTab(delta: Int) {
        val tabs = Tab.entries
        tab = tabs[(tab.ordinal + delta + tabs.size) % tabs.size]
    }

    private fun openSystem(system: GameSystem) {
        tab = Tab.Systems
        val index = rows.indexOfFirst { it.id == "sys-${system.id}" }
        if (index >= 0) rowFocus[Tab.Systems] = index
    }

    private fun openDetail(game: Game) {
        detail = game
        detailFocus = 0
    }

    private fun openMenu() {
        menuIndex = 0
        menuOpen = true
    }

    private fun focusedGame(): Game? =
        rows.getOrNull(focusedRow)?.let { row ->
            (row.items.getOrNull(focusedItem(row)) as? CardModel.GameCard)?.game
        }

    private fun toggleFavorite(game: Game) {
        val makeFavorite = game.id !in favorites
        viewModelScope.launch { data?.library?.setFavorite(game.id, makeFavorite) }
        showToast(UiText.res(if (makeFavorite) R.string.toast_added_favorite else R.string.toast_removed_favorite))
    }

    private fun showToast(message: UiText) {
        toast = message
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(2000)
            toast = null
        }
    }
}
