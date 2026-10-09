package com.thorium.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thorium.app.R
import com.thorium.app.art.rememberCoverArt
import com.thorium.app.ui.downloads.DownloadsScreen
import com.thorium.app.ui.settings.SettingsScreen
import com.thorium.core.model.Game
import com.thorium.core.ui.components.ActionButton
import com.thorium.core.ui.components.Cover
import com.thorium.core.ui.components.HintBar
import com.thorium.core.ui.keyboard.OnScreenKeyboard
import com.thorium.core.ui.theme.Dimens
import com.thorium.core.ui.text.resolve
import com.thorium.core.ui.theme.Palette
import com.thorium.core.ui.theme.ThemedBackground
import java.util.Locale

@Composable
fun ThoriumApp(vm: AppViewModel) {
    ThemedBackground(position = vm.tab.ordinal / (Tab.entries.size - 1f)) {
        Column(Modifier.fillMaxSize()) {
            TabBar(vm.tab)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (vm.libraryState is LibraryState.NeedsPermission) {
                    PermissionScreen()
                } else if (vm.tab == Tab.Settings) {
                    SettingsScreen(vm.settings)
                } else if (vm.tab == Tab.Downloads) {
                    DownloadsScreen(vm.downloadsUi, vm.downloadSpeeds)
                } else {
                    key(vm.tab) { RowsScreen(vm) }
                }
            }
            HintBar(vm.hints.map { it.button to stringResource(it.label) })
        }

        AnimatedVisibility(
            vm.detail != null,
            enter = fadeIn(tween(240)) + slideInVertically(tween(360, easing = FastOutSlowInEasing)) { it / 14 } + scaleIn(tween(360), initialScale = 0.94f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.96f),
        ) {
            vm.detail?.let { DetailScreen(it, vm) }
        }
        AnimatedVisibility(vm.menuOpen, enter = fadeIn(), exit = fadeOut()) { MenuOverlay(vm) }
        OnScreenKeyboard(vm.settings.keyboard)

        AnimatedVisibility(
            vm.toast != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
            enter = fadeIn(tween(160)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
        ) {
            Text(
                vm.toast?.resolve().orEmpty(),
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Palette.PanelFocused)
                    .border(1.dp, Palette.Accent.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                color = Palette.TextPrimary, fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun RowsScreen(vm: AppViewModel) {
    val rows = vm.rows
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(vm.emptyMessage),
                modifier = Modifier.width(520.dp),
                color = Palette.TextSecondary, fontSize = 15.sp,
            )
        }
        return
    }
    val listState = rememberLazyListState()
    val focusedRow = vm.focusedRow
    LaunchedEffect(focusedRow) { listState.animateScrollToItem(focusedRow) }
    LazyColumn(
        state = listState,
        userScrollEnabled = false,
        contentPadding = PaddingValues(bottom = 220.dp),
    ) {
        itemsIndexed(rows, key = { _, r -> r.id }) { index, row ->
            CardRow(row, rowFocused = index == focusedRow, itemIndex = vm.focusedItem(row), vm = vm)
        }
    }
}

@Composable
private fun DetailScreen(game: Game, vm: AppViewModel) {
    val system = vm.systemOf(game)
    val art = rememberCoverArt(vm.coverOf(game))
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))) {
        // The game's own cover, enlarged and blurred, tints the whole screen with its colors.
        if (art != null) {
            Image(
                art, contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.3f; scaleY = 1.3f; alpha = 0.55f }.blur(36.dp),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.85f)))))
        }
        Row(
            Modifier.align(Alignment.Center).padding(Dimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Cover(game.title, system, focused = false, progress = game.progress, favorite = vm.isFavorite(game.id), width = 150.dp, height = 200.dp, art = art, glow = true)
            Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(game.title, color = Palette.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(system.name, vm.matchOf(game)?.entry?.region).joinToString(" · "), color = Palette.Accent, fontSize = 15.sp)
                Text(formatSize(game.sizeBytes), color = Palette.TextSecondary, fontSize = 13.sp)
                Text(game.path, color = Palette.TextSecondary, fontSize = 12.sp)
                Text(
                    game.progress?.let { stringResource(R.string.detail_progress, (it * 100).toInt()) }
                        ?: stringResource(R.string.detail_not_played),
                    color = Palette.TextSecondary, fontSize = 13.sp,
                )
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    DETAIL_BUTTONS.forEachIndexed { i, label ->
                        val text = if (i == 1 && vm.isFavorite(game.id)) R.string.detail_unfavorite else label
                        ActionButton(stringResource(text), focused = vm.detailFocus == i)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuOverlay(vm: AppViewModel) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(260.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Panel).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.menu_title), color = Palette.TextSecondary, fontSize = 13.sp)
            MenuItem.entries.forEachIndexed { i, item -> ActionButton(stringResource(item.label), focused = vm.menuIndex == i, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

private fun formatSize(bytes: Long): String =
    String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)

@Composable
private fun PermissionScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.perm_title), color = Palette.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.perm_body), color = Palette.TextSecondary, fontSize = 14.sp)
            Text(stringResource(R.string.perm_steps), color = Palette.TextSecondary, fontSize = 14.sp)
            Box(Modifier.padding(top = 8.dp)) { ActionButton(stringResource(R.string.perm_button), focused = true) }
        }
    }
}
