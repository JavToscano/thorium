package com.thorium.app.ui.downloads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thorium.app.R
import com.thorium.app.ui.main.Hint
import com.thorium.app.ui.settings.SourceErrors
import com.thorium.core.model.GameSystem
import com.thorium.core.model.CatalogMatch
import com.thorium.core.model.RemoteEntry
import com.thorium.core.model.SourceConfig
import com.thorium.core.ui.input.GamepadAction
import com.thorium.core.ui.keyboard.KeyboardController
import com.thorium.core.ui.text.UiText
import com.thorium.app.ui.main.formatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** What happened when a download was requested. */
enum class DownloadStart { Queued, NeedsPlatform, NoFolder }

sealed interface BrowseState {
    data object Loading : BrowseState
    data object Ready : BrowseState
    data class Failed(val reason: UiText) : BrowseState
}

/** What browsing a source needs from the rest of the app. */
interface BrowseHost {
    val platforms: List<GameSystem>
    fun list(source: SourceConfig, ref: String?, onResult: (Result<List<RemoteEntry>>) -> Unit)
    fun download(source: SourceConfig, entry: RemoteEntry, platformId: String?, onResult: (DownloadStart) -> Unit)
    fun toast(message: UiText)

    /** Recognises [entry] with the bundled catalog; called off the main thread. */
    fun identify(source: SourceConfig, entry: RemoteEntry): CatalogMatch?
}

/**
 * Walks the contents of one source with the controller: A opens a folder or downloads a file, B
 * goes up, Y filters the current listing by name. When the console of a file cannot be worked out,
 * a second page asks for it before anything is queued.
 */
class BrowseController(
    private val host: BrowseHost,
    private val keyboard: KeyboardController,
    private val goTo: (DownloadsPage) -> Unit,
) {
    var source by mutableStateOf<SourceConfig?>(null); private set
    var state by mutableStateOf<BrowseState>(BrowseState.Loading); private set
    var entries by mutableStateOf<List<RemoteEntry>>(emptyList()); private set
    var filter by mutableStateOf(""); private set
    var index by mutableIntStateOf(0); private set

    /** What the catalog recognised, by entry reference; fills in while the listing is open. */
    var matches by mutableStateOf<Map<String, CatalogMatch>>(emptyMap()); private set

    /** The file waiting for the user to pick its console. */
    var pending by mutableStateOf<RemoteEntry?>(null); private set
    var platformIndex by mutableIntStateOf(0); private set

    private val trail = ArrayDeque<String?>()
    private var token = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var identifying: Job? = null

    val platforms: List<GameSystem> get() = host.platforms

    val visible: List<RemoteEntry>
        get() = if (filter.isBlank()) entries else entries.filter { it.name.contains(filter, ignoreCase = true) }

    /** Region, console and size of a file, as far as they are known. */
    fun detail(entry: RemoteEntry): String? {
        val match = matches[entry.ref]?.entry
        val consoleName = match?.let { m -> platforms.firstOrNull { it.id == m.platformId }?.name }
        return listOfNotNull(match?.region, consoleName, entry.sizeBytes?.let(::formatBytes))
            .joinToString(" · ").ifEmpty { null }
    }

    val hints: List<Hint>
        get() = listOf(Hint("A", R.string.hint_open), Hint("Y", R.string.hint_filter), Hint("B", R.string.hint_back))

    val platformHints: List<Hint>
        get() = listOf(Hint("A", R.string.hint_select), Hint("B", R.string.hint_back))

    fun open(config: SourceConfig) {
        source = config
        trail.clear()
        load(null, push = true)
        goTo(DownloadsPage.Browse)
    }

    fun handle(action: GamepadAction): Boolean {
        index = index.coerceIn(0, (visible.size - 1).coerceAtLeast(0))
        when (action) {
            GamepadAction.Up -> index = (index - 1).coerceAtLeast(0)
            GamepadAction.Down -> index = (index + 1).coerceAtMost((visible.size - 1).coerceAtLeast(0))
            GamepadAction.Select -> visible.getOrNull(index)?.let(::activate)
            GamepadAction.Favorite -> keyboard.open(UiText.res(R.string.browse_filter_title), filter) {
                filter = it.trim()
                index = 0
            }
            GamepadAction.Back -> up()
            else -> return false
        }
        return true
    }

    fun handlePlatform(action: GamepadAction): Boolean {
        val list = platforms
        when (action) {
            GamepadAction.Up -> platformIndex = (platformIndex - 1).coerceAtLeast(0)
            GamepadAction.Down -> platformIndex = (platformIndex + 1).coerceAtMost(list.size - 1)
            GamepadAction.Select -> {
                val entry = pending
                val config = source
                if (entry != null && config != null) {
                    host.download(config, entry, list[platformIndex.coerceIn(0, list.size - 1)].id) { report(it, entry) }
                }
                goTo(DownloadsPage.Browse)
            }
            GamepadAction.Back -> goTo(DownloadsPage.Browse)
            else -> return false
        }
        return true
    }

    private fun activate(entry: RemoteEntry) {
        val config = source ?: return
        if (entry.isDirectory) {
            load(entry.ref, push = true)
        } else {
            host.download(config, entry, null) { result ->
                if (result == DownloadStart.NeedsPlatform) {
                    // Last resort before asking: the console the catalog recognised.
                    val recognised = matches[entry.ref]?.entry?.platformId
                    if (recognised != null) {
                        host.download(config, entry, recognised) { report(it, entry) }
                    } else {
                        pending = entry
                        platformIndex = 0
                        goTo(DownloadsPage.PlatformPick)
                    }
                } else {
                    report(result, entry)
                }
            }
        }
    }

    private fun report(result: DownloadStart, entry: RemoteEntry) {
        when (result) {
            DownloadStart.Queued -> host.toast(UiText.res(R.string.toast_download_added, entry.name))
            DownloadStart.NoFolder -> host.toast(UiText.res(R.string.toast_download_no_folder))
            DownloadStart.NeedsPlatform -> Unit
        }
    }

    private fun up() {
        if (trail.size > 1) {
            trail.removeLast()
            load(trail.last(), push = false)
        } else {
            goTo(DownloadsPage.Main)
        }
    }

    private fun load(ref: String?, push: Boolean) {
        val config = source ?: return
        if (push) trail.addLast(ref)
        val mine = ++token
        state = BrowseState.Loading
        entries = emptyList()
        matches = emptyMap()
        identifying?.cancel()
        filter = ""
        index = 0
        host.list(config, ref) { result ->
            // Ignore an answer that arrives after the user has already moved on.
            if (mine != token) return@list
            result.fold(
                onSuccess = { entries = it; state = BrowseState.Ready; identify(config, it, mine) },
                onFailure = { state = BrowseState.Failed(SourceErrors.explain(it)) },
            )
        }
    }

    /** Looks the files up in the catalog in the background, publishing results in batches. */
    private fun identify(config: SourceConfig, list: List<RemoteEntry>, mine: Int) {
        identifying = scope.launch {
            val found = HashMap<String, CatalogMatch>()
            var sinceLast = 0
            for (entry in list) {
                if (entry.isDirectory) continue
                host.identify(config, entry)?.let { found[entry.ref] = it; sinceLast++ }
                if (sinceLast >= 100) {
                    if (mine == token) matches = HashMap(found)
                    sinceLast = 0
                }
            }
            if (mine == token) matches = found
        }
    }
}
