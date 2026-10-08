package com.thorium.app.art

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thorium.core.model.CatalogMatch
import com.thorium.core.model.CoverArt
import com.thorium.core.model.Game
import com.thorium.core.model.GameCatalog
import com.thorium.data.metadata.FileStems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Recognises the games of the library against the catalog and fetches their covers. Recognition is
 * quick and local; covers already on disk appear straight away, the rest download one at a time
 * in the background (only while [downloadEnabled]).
 */
class GameArt(
    private val scope: CoroutineScope,
    private val catalog: () -> GameCatalog?,
    private val covers: () -> CoverArt?,
) {
    /** What the catalog recognised, by game id. */
    var matches by mutableStateOf<Map<String, CatalogMatch>>(emptyMap()); private set

    /** Covers on disk, by game id. */
    var coverFiles by mutableStateOf<Map<String, File>>(emptyMap()); private set

    var downloadEnabled = true; private set

    private var games: List<Game> = emptyList()
    private var job: Job? = null

    fun update(list: List<Game>) {
        games = list
        restart()
    }

    fun setDownloadEnabled(enabled: Boolean) {
        if (enabled == downloadEnabled) return
        downloadEnabled = enabled
        restart()
    }

    private fun restart() {
        job?.cancel()
        val snapshot = games
        val download = downloadEnabled
        job = scope.launch(Dispatchers.Default) {
            val catalog = catalog() ?: return@launch
            val found = HashMap<String, CatalogMatch>()
            for (game in snapshot) {
                ensureActive()
                val name = FileStems.of(File(game.path).name)
                catalog.identify(name, game.systemId)?.let { found[game.id] = it }
            }
            matches = found

            val art = covers()
            val files = HashMap<String, File>()
            val missing = ArrayList<Pair<String, com.thorium.core.model.CatalogEntry>>()
            for ((id, match) in found) {
                val cached = art?.cached(match.entry)
                if (cached != null) files[id] = cached else missing += id to match.entry
            }
            coverFiles = HashMap(files)

            if (!download || art == null) return@launch
            for ((id, entry) in missing) {
                ensureActive()
                art.fetch(entry)?.let {
                    files[id] = it
                    coverFiles = HashMap(files)
                }
            }
        }
    }
}
