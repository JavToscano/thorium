package com.thorium.app.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thorium.core.model.Game
import com.thorium.core.model.GameSystem
import com.thorium.core.model.Library
import com.thorium.data.library.LibraryScanner
import com.thorium.data.library.ScanStats
import com.thorium.core.ui.input.GamepadAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Tab(val title: String) { Home("Home"), Systems("Systems"), Favorites("Favorites") }

sealed interface CardModel {
    val key: String
    data class GameCard(val game: Game, val system: GameSystem) : CardModel {
        override val key get() = "g-${game.id}"
    }
    data class SystemCard(val system: GameSystem, val gameCount: Int) : CardModel {
        override val key get() = "s-${system.id}"
    }
}

data class RowModel(val id: String, val title: String, val items: List<CardModel>)

val MENU_ITEMS = listOf("Resume", "Rescan library", "Dual screen", "Diagnostics", "About")
val DETAIL_BUTTONS = listOf("Play", "Favorite")

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

    // Favorites are kept in memory until the database arrives (phase 6).
    private val favorites = mutableStateListOf<String>()

    var libraryState by mutableStateOf<LibraryState>(LibraryState.Scanning); private set

    /** Wired by the application: storage permission check and the folders to scan. */
    var permissionGranted: () -> Boolean = { true }
    var storageRoots: () -> List<File> = { emptyList() }
    var onRequestStoragePermission: (() -> Unit)? = null

    var tab by mutableStateOf(Tab.Home); private set
    var detail by mutableStateOf<Game?>(null); private set
    var detailFocus by mutableIntStateOf(0); private set
    var menuOpen by mutableStateOf(false); private set
    var menuIndex by mutableIntStateOf(0); private set
    var toast by mutableStateOf<String?>(null); private set

    /** User preference: show the companion on the secondary display when one exists. */
    var companionEnabled by mutableStateOf(true); private set

    /** False once the main activity is finishing, so the companion closes with it. */
    var mainAlive by mutableStateOf(true); private set

    val companionVisible: Boolean get() = companionEnabled && mainAlive

    fun markMainClosed() { mainAlive = false }
    fun markMainOpen() { mainAlive = true }

    private val rowFocus = mutableStateMapOf<Tab, Int>()
    private val itemFocus = mutableStateMapOf<String, Int>()
    private var toastJob: Job? = null

    var onOpenDiagnostics: (() -> Unit)? = null

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
    val emptyMessage: String get() = when {
        libraryState is LibraryState.Scanning -> "Scanning your library..."
        tab == Tab.Favorites -> "Nothing here yet. Press Y on a game to add it to Favorites."
        else -> "No games found. Put your games in folders such as 3ds, gba or switch on the SD card " +
            "or internal storage, then choose Rescan library in the menu."
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
        if (scanJob?.isActive == true) return
        libraryState = LibraryState.Scanning
        scanJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { scanner.scan(storageRoots()) }
            library = result.library
            val ids = result.library.games.map { it.id }.toSet()
            favorites.retainAll(ids)
            libraryState = LibraryState.Ready(result.stats)
            showToast("${result.stats.games} games found in ${result.stats.durationMs} ms")
        }
    }

    val focusedRow: Int get() = (rowFocus[tab] ?: 0).coerceIn(0, (rows.size - 1).coerceAtLeast(0))

    fun focusedItem(row: RowModel): Int =
        (itemFocus[itemKey(tab, row)] ?: 0).coerceIn(0, (row.items.size - 1).coerceAtLeast(0))

    private fun itemKey(tab: Tab, row: RowModel) = "${tab.name}/${row.id}"

    private fun buildRows(tab: Tab): List<RowModel> {
        if (library.games.isEmpty()) return emptyList()
        fun gameCards(games: List<Game>) = games.map { CardModel.GameCard(it, library.system(it.systemId)) }
        return when (tab) {
            Tab.Home -> buildList {
                val continuePlaying = library.games.filter { it.lastPlayedAt != null }
                    .sortedByDescending { it.lastPlayedAt }.take(6)
                if (continuePlaying.isNotEmpty())
                    add(RowModel("continue", "Continue Playing", gameCards(continuePlaying)))
                add(RowModel("systems", "Systems", library.systems.map { s ->
                    CardModel.SystemCard(s, library.games.count { it.systemId == s.id })
                }))
                add(RowModel("recent", "Recently Added", gameCards(library.games.sortedByDescending { it.addedAt }.take(10))))
                val favs = favoriteGames()
                if (favs.isNotEmpty()) add(RowModel("favorites", "Favorites", gameCards(favs)))
            }
            Tab.Systems -> library.systems.mapNotNull { s ->
                val games = library.games.filter { it.systemId == s.id }
                if (games.isEmpty()) null else RowModel("sys-${s.id}", s.name, gameCards(games))
            }
            Tab.Favorites -> favoriteGames().chunked(6).mapIndexed { i, chunk ->
                RowModel("fav-$i", if (i == 0) "Favorites" else "", gameCards(chunk))
            }
        }
    }

    private fun favoriteGames() = favorites.mapNotNull { id -> library.games.firstOrNull { it.id == id } }

    fun handle(action: GamepadAction) {
        when {
            menuOpen -> handleMenu(action)
            libraryState is LibraryState.NeedsPermission -> handlePermission(action)
            detail != null -> handleDetail(action)
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
            GamepadAction.Secondary -> showToast("Secondary actions arrive later")
        }
    }

    private fun handleDetail(action: GamepadAction) {
        val game = detail ?: return
        when (action) {
            GamepadAction.Left -> detailFocus = (detailFocus - 1).coerceAtLeast(0)
            GamepadAction.Right -> detailFocus = (detailFocus + 1).coerceAtMost(DETAIL_BUTTONS.size - 1)
            GamepadAction.Select ->
                if (detailFocus == 0) showToast("Launcher arrives in Phase 11") else toggleFavorite(game)
            GamepadAction.Favorite -> toggleFavorite(game)
            GamepadAction.Back -> detail = null
            GamepadAction.Menu -> openMenu()
            else -> Unit
        }
    }

    private fun handleMenu(action: GamepadAction) {
        when (action) {
            GamepadAction.Up -> menuIndex = (menuIndex - 1).coerceAtLeast(0)
            GamepadAction.Down -> menuIndex = (menuIndex + 1).coerceAtMost(MENU_ITEMS.size - 1)
            GamepadAction.Select -> {
                when (MENU_ITEMS[menuIndex]) {
                    "Rescan library" -> refreshLibrary(force = true)
                    "Dual screen" -> {
                        companionEnabled = !companionEnabled
                        showToast(if (companionEnabled) "Dual screen on" else "Dual screen off")
                    }
                    "Diagnostics" -> onOpenDiagnostics?.invoke()
                    "About" -> showToast("Thorium 0.0.1 - Phase 4 UI prototype")
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
        if (game.id in favorites) {
            favorites.remove(game.id)
            showToast("Removed from Favorites")
        } else {
            favorites.add(game.id)
            showToast("Added to Favorites")
        }
    }

    private fun showToast(message: String) {
        toast = message
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(2000)
            toast = null
        }
    }
}
