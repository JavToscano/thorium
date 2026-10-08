package com.thorium.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thorium.app.ui.main.gameCountLabel
import com.thorium.core.ui.theme.Dimens
import com.thorium.core.ui.theme.Palette

@Composable
fun SettingsScreen(controller: SettingsController) {
    when (controller.page) {
        SettingsPage.Main -> MainPage(controller)
        SettingsPage.Folders -> FoldersPage(controller)
        SettingsPage.Browser -> BrowserPage(controller)
    }
}

@Composable
private fun PageFrame(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Palette.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(subtitle, color = Palette.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        content()
    }
}

/** Vertical list that keeps the focused row in view. */
@Composable
private fun <T> FocusList(items: List<T>, focusedIndex: Int, row: @Composable (Int, T, Boolean) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(focusedIndex) { state.animateScrollToItem((focusedIndex - 1).coerceAtLeast(0)) }
    LazyColumn(
        state = state,
        userScrollEnabled = false,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 120.dp),
    ) {
        itemsIndexed(items) { i, item -> row(i, item, i == focusedIndex) }
    }
}

@Composable
private fun ListRow(title: String, detail: String?, focused: Boolean, trailing: String? = null) {
    val bg by animateColorAsState(if (focused) Color(0xFF26324D) else Palette.Panel, label = "row")
    val border by animateColorAsState(if (focused) Palette.Accent else Color.Transparent, label = "rowBorder")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg)
            .border(2.dp, border, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, color = Palette.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Text(trailing, color = Palette.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MainPage(c: SettingsController) {
    PageFrame("Settings", null) {
        FocusList(c.mainItems, c.mainIndex) { _, (label, detail), focused ->
            // "Dual screen" shows its state on the right; the others show a description underneath.
            if (label == "Dual screen") ListRow(label, "Show game details on the second display", focused, trailing = detail)
            else ListRow(label, detail, focused)
        }
    }
}

@Composable
private fun FoldersPage(c: SettingsController) {
    PageFrame(
        "Game folders",
        "Thorium looks for folders named after consoles (gba, 3ds, switch...) inside these locations.",
    ) {
        FocusList(c.folderRows, c.foldersIndex) { _, row, focused ->
            when (row) {
                is FolderRow.AutoDetect -> ListRow(
                    title = "Scan all storage automatically",
                    detail = row.volumes.joinToString(" - ") { "${it.label} (${it.dir.path})" }.ifEmpty { "No storage found" },
                    focused = focused,
                    trailing = if (row.enabled) "On" else "Off",
                )
                is FolderRow.Custom -> ListRow(row.path, gameCountLabel(row.gameCount), focused)
                FolderRow.Add -> ListRow("Add folder...", "Browse storage and pick a folder", focused)
            }
        }
    }
}

@Composable
private fun BrowserPage(c: SettingsController) {
    PageFrame("Pick a folder", c.browserDir?.path ?: "Storage") {
        if (c.browserEntries.isEmpty()) {
            Text(
                "No sub-folders here. Press Y to use this folder, or B to go up.",
                modifier = Modifier.padding(top = 16.dp),
                color = Palette.TextSecondary, fontSize = 14.sp,
            )
        } else {
            FocusList(c.browserEntries, c.browserIndex) { _, entry, focused ->
                ListRow(entry.label, null, focused)
            }
        }
    }
}
