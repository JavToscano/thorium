package com.thorium.app.ui.downloads

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thorium.app.R
import com.thorium.app.ui.main.Hint
import com.thorium.core.model.DownloadItem
import com.thorium.core.model.DownloadState
import com.thorium.core.model.SourceConfig
import com.thorium.core.ui.input.GamepadAction
import com.thorium.core.ui.keyboard.KeyboardController

enum class DownloadsPage { Main, Browse, PlatformPick }

/** One line of the Downloads tab. Headers and notes are drawn but never take the focus. */
sealed interface DownloadsRow {
    val focusable: Boolean get() = true

    data class Header(@StringRes val title: Int) : DownloadsRow {
        override val focusable get() = false
    }

    data class Note(@StringRes val text: Int) : DownloadsRow {
        override val focusable get() = false
    }

    data class Source(val config: SourceConfig) : DownloadsRow
    data object Manage : DownloadsRow
    data class Item(val item: DownloadItem) : DownloadsRow
}

/** What the Downloads tab needs from the rest of the app. */
interface DownloadsHost : BrowseHost {
    val sources: List<SourceConfig>
    val items: List<DownloadItem>
    fun pause(id: Long)
    fun resume(id: Long)
    fun cancel(id: Long)
    fun remove(id: Long)
    fun clearFinished()

    /** Opens Settings → Sources, where sources are added, edited and removed. */
    fun openSourceSettings()
}

/**
 * The Downloads tab: where games are obtained. The first page lists the sources to browse and,
 * below them, the queue of downloads; A on a source opens it (browsing, filtering and downloading
 * live in [BrowseController]). Adding or editing sources stays in Settings.
 */
class DownloadsController(private val host: DownloadsHost, keyboard: KeyboardController) {

    var page by mutableStateOf(DownloadsPage.Main); private set
    var index by mutableIntStateOf(0); private set

    val browse = BrowseController(host, keyboard) { page = it }

    val rows: List<DownloadsRow>
        get() = buildList {
            add(DownloadsRow.Header(R.string.dl_header_sources))
            if (host.sources.isEmpty()) add(DownloadsRow.Note(R.string.dl_no_sources))
            host.sources.forEach { add(DownloadsRow.Source(it)) }
            add(DownloadsRow.Manage)
            if (host.items.isNotEmpty()) {
                add(DownloadsRow.Header(R.string.dl_header_queue))
                // Unfinished first, newest first inside each group.
                host.items.sortedWith(compareBy({ it.state.isFinished }, { -it.id })).forEach { add(DownloadsRow.Item(it)) }
            }
        }

    val focusedRow: DownloadsRow? get() = rows.getOrNull(index)

    /** Number of items that are still going (for a badge or summary). */
    val activeCount: Int get() = host.items.count { !it.state.isFinished && it.state != DownloadState.Paused }

    val hints: List<Hint>
        get() = when (page) {
            DownloadsPage.Browse -> browse.hints
            DownloadsPage.PlatformPick -> browse.platformHints
            DownloadsPage.Main -> mainHints()
        }

    private fun mainHints(): List<Hint> = when (val row = focusedRow) {
        is DownloadsRow.Item -> listOf(
            when (row.item.state) {
                DownloadState.Paused -> Hint("A", R.string.hint_resume)
                DownloadState.Failed -> Hint("A", R.string.hint_retry)
                DownloadState.Completed, DownloadState.Cancelled -> Hint("A", R.string.hint_select)
                else -> Hint("A", R.string.hint_pause)
            },
            Hint("Y", if (row.item.state.isFinished) R.string.hint_remove else R.string.hint_cancel),
            Hint("SELECT", R.string.hint_clear), Hint("L1/R1", R.string.hint_tabs), Hint("START", R.string.hint_menu),
        )
        is DownloadsRow.Source -> listOf(
            Hint("A", R.string.hint_browse), Hint("L1/R1", R.string.hint_tabs), Hint("START", R.string.hint_menu),
        )
        else -> listOf(
            Hint("A", R.string.hint_open), Hint("SELECT", R.string.hint_clear),
            Hint("L1/R1", R.string.hint_tabs), Hint("START", R.string.hint_menu),
        )
    }

    /** Returns true when the action was used here; false lets the caller handle tabs, menu and back. */
    fun handle(action: GamepadAction): Boolean = when (page) {
        DownloadsPage.Main -> handleMain(action)
        DownloadsPage.Browse -> browse.handle(action)
        DownloadsPage.PlatformPick -> browse.handlePlatform(action)
    }

    private fun handleMain(action: GamepadAction): Boolean {
        val rows = rows
        index = settle(rows, index)
        when (action) {
            GamepadAction.Up -> index = step(rows, index, -1)
            GamepadAction.Down -> index = step(rows, index, +1)
            GamepadAction.Select -> when (val row = rows.getOrNull(index)) {
                is DownloadsRow.Source -> browse.open(row.config)
                DownloadsRow.Manage -> host.openSourceSettings()
                is DownloadsRow.Item -> when (row.item.state) {
                    DownloadState.Paused, DownloadState.Failed -> host.resume(row.item.id)
                    DownloadState.Completed, DownloadState.Cancelled -> Unit
                    else -> host.pause(row.item.id)
                }
                else -> Unit
            }
            GamepadAction.Favorite -> (rows.getOrNull(index) as? DownloadsRow.Item)?.let {
                if (it.item.state.isFinished) host.remove(it.item.id) else host.cancel(it.item.id)
                index = settle(rows, (index - 1).coerceAtLeast(0))
            }
            GamepadAction.Secondary -> host.clearFinished()
            else -> return false
        }
        return true
    }

    /** The nearest focusable row at or after [from]; falls back to the nearest before it. */
    private fun settle(rows: List<DownloadsRow>, from: Int): Int {
        val start = from.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        return (start until rows.size).firstOrNull { rows[it].focusable }
            ?: (start downTo 0).firstOrNull { rows[it].focusable }
            ?: 0
    }

    private fun step(rows: List<DownloadsRow>, from: Int, direction: Int): Int {
        var next = from + direction
        while (next in rows.indices) {
            if (rows[next].focusable) return next
            next += direction
        }
        return from
    }
}
