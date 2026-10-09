package com.thorium.app.ui.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thorium.app.ui.main.AppViewModel
import com.thorium.app.ui.main.CardModel
import com.thorium.app.ui.main.Tab
import com.thorium.app.R
import com.thorium.app.art.rememberCoverArt
import com.thorium.core.model.Game
import com.thorium.core.model.GameSystem
import com.thorium.core.ui.components.Cover
import com.thorium.core.ui.theme.Palette
import com.thorium.core.ui.theme.ThemedBackground
import java.util.Locale

/** Bottom-screen panel: details of whatever the top screen currently has in focus. */
@Composable
fun CompanionScreen(vm: AppViewModel) {
    ThemedBackground {
        Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
        if (vm.tab == Tab.Downloads) {
            DownloadsCompanion(vm)
        } else {
            // While something downloads, a compact progress list shows on every other tab too.
            val strip: (@Composable () -> Unit)? = vm.unfinishedDownloads.takeIf { it.isNotEmpty() }
                ?.let { active -> { DownloadsStrip(active, vm.downloadSpeeds) } }
            when (val card = vm.focusedCard) {
                is CardModel.GameCard -> {
                    // Ask for the title screen and a snap whenever the focused game (or its match) changes.
                    androidx.compose.runtime.LaunchedEffect(card.game.id, vm.matchOf(card.game)) { vm.requestShots(card.game) }
                    GameInfo(
                    card.game, card.system, vm.isFavorite(card.game.id),
                    rememberCoverArt(vm.coverOf(card.game)), vm.matchOf(card.game)?.entry?.region,
                    vm.shotsOf(card.game), strip,
                    )
                }
                is CardModel.SystemCard -> SystemInfo(card.system, card.gameCount, strip)
                null -> Idle(settings = vm.tab == Tab.Settings, strip = strip)
            }
        }
        }
    }
}

@Composable
private fun GameInfo(
    game: Game, system: GameSystem, favorite: Boolean,
    art: androidx.compose.ui.graphics.ImageBitmap?, region: String?, shots: List<java.io.File>,
    strip: (@Composable () -> Unit)?,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Cover(game.title, system, focused = false, progress = game.progress, favorite = favorite, width = 120.dp, height = 160.dp, art = art)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(game.title, color = Palette.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(system.name, region).joinToString(" · "), color = Palette.Accent, fontSize = 14.sp)
                Text(formatSize(game.sizeBytes), color = Palette.TextSecondary, fontSize = 12.sp)
                Text(
                    game.progress?.let { stringResource(R.string.detail_progress, (it * 100).toInt()) }
                        ?: stringResource(R.string.detail_not_played),
                    color = Palette.TextSecondary, fontSize = 12.sp,
                )
                Text(game.path, color = Palette.TextSecondary, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        // Downloads in progress are summarised here.
        strip?.invoke()
        if (shots.isNotEmpty()) {
            SectionTitle(stringResource(R.string.companion_screenshots))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shots.forEach { file ->
                    val image = rememberCoverArt(file)
                    Box(
                        Modifier.weight(1f).height(110.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Panel),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (image != null) {
                            androidx.compose.foundation.Image(
                                image, contentDescription = null,
                                modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            )
                        }
                    }
                }
            }
        }
        SectionTitle(stringResource(R.string.companion_controls))
        Controls()
    }
}

@Composable
private fun SystemInfo(system: GameSystem, gameCount: Int, strip: (@Composable () -> Unit)?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(system.name, color = Palette.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(pluralStringResource(R.plurals.games_count, gameCount, gameCount), color = Palette.Accent, fontSize = 14.sp)
        Text(stringResource(R.string.companion_browse_system), color = Palette.TextSecondary, fontSize = 12.sp)
        Box(Modifier.weight(1f))
        strip?.invoke()
        SectionTitle(stringResource(R.string.companion_controls))
        Controls()
    }
}

@Composable
private fun Idle(settings: Boolean, strip: (@Composable () -> Unit)?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Thorium", color = Palette.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(if (settings) R.string.companion_settings_top else R.string.companion_select_game),
            color = Palette.TextSecondary, fontSize = 13.sp,
        )
        if (strip != null) {
            Box(Modifier.padding(top = 24.dp).fillMaxWidth()) { strip() }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), color = Palette.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Controls() {
    val rows = listOf(
        listOf("D-pad" to R.string.hint_move, "A" to R.string.hint_select, "B" to R.string.hint_back),
        listOf("L1/R1" to R.string.hint_tabs, "Y" to R.string.hint_favorite, "START" to R.string.hint_menu),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (button, label) ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            button,
                            modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Palette.Panel).padding(horizontal = 5.dp, vertical = 1.dp),
                            color = Palette.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(stringResource(label), color = Palette.TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
