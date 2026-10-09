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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.thorium.app.R
import com.thorium.app.ui.main.LanguageChoice
import com.thorium.core.ui.theme.Dimens
import com.thorium.core.ui.theme.Palette
import com.thorium.data.library.SetupEntry

@Composable
fun SettingsScreen(controller: SettingsController) {
    when (controller.page) {
        SettingsPage.Main -> MainPage(controller)
        SettingsPage.Folders -> FoldersPage(controller)
        SettingsPage.Browser -> BrowserPage(controller)
        SettingsPage.Setup -> SetupPage(controller)
        SettingsPage.Sources -> SourcesPage(controller)
        SettingsPage.SourceForm -> SourceFormPage(controller)
        SettingsPage.SourceConsole -> SourceConsolePage(controller)
        SettingsPage.About -> AboutPage(controller)
        SettingsPage.Themes -> ThemesPage(controller)
    }
}

@Composable
internal fun PageFrame(title: String, subtitle: String?, content: @Composable () -> Unit) {
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
internal fun <T> FocusList(items: List<T>, focusedIndex: Int, row: @Composable (Int, T, Boolean) -> Unit) {
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
internal fun ListRow(title: String, detail: String?, focused: Boolean, trailing: String? = null) {
    val bg by animateColorAsState(if (focused) Palette.PanelFocused else Palette.Panel, label = "row")
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

/** Credits and licenses; each block is a row so the controller can scroll through them. */
@Composable
private fun AboutPage(c: SettingsController) {
    val blocks = listOf(
        R.string.about_app_title to stringResource(R.string.about_app_text, SettingsController.VERSION),
        R.string.about_catalog_title to stringResource(R.string.about_catalog_text),
        R.string.about_covers_title to stringResource(R.string.about_covers_text),
        R.string.about_libs_title to stringResource(R.string.about_libs_text),
    )
    PageFrame(stringResource(R.string.about_title), null) {
        FocusList(blocks, c.aboutIndex) { _, block, focused ->
            val bg by animateColorAsState(if (focused) Palette.PanelFocused else Palette.Panel, label = "aboutRow")
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg)
                    .border(2.dp, if (focused) Palette.Accent else Color.Transparent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(stringResource(block.first), color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(block.second, color = Palette.TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ThemesPage(c: SettingsController) {
    PageFrame(stringResource(R.string.themes_title), stringResource(R.string.themes_subtitle)) {
        FocusList(c.themeRows, c.themesIndex) { _, row, focused ->
            when (row) {
                is ThemeRow.Theme -> {
                    val spec = row.entry.spec
                    val active = spec.id == c.host.activeThemeId
                    val by = if (spec.author.isNotBlank()) stringResource(R.string.themes_by, spec.author) else null
                    ListRow(
                        title = spec.name,
                        detail = listOfNotNull(by, spec.description.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { null },
                        focused = focused,
                        trailing = if (active) stringResource(R.string.themes_active) else null,
                    )
                }
                ThemeRow.Sounds -> ListRow(
                    stringResource(R.string.themes_sounds), stringResource(R.string.themes_sounds_desc), focused,
                    trailing = stringResource(if (c.host.themeSounds) R.string.value_on else R.string.value_off),
                )
                ThemeRow.Animations -> ListRow(
                    stringResource(R.string.themes_animations), stringResource(R.string.themes_animations_desc), focused,
                    trailing = stringResource(if (c.host.animations) R.string.value_on else R.string.value_off),
                )
            }
        }
    }
}

@Composable
private fun MainPage(c: SettingsController) {
    PageFrame(stringResource(R.string.settings_title), null) {
        FocusList(c.mainItems, c.mainIndex) { _, item, focused ->
            val title = stringResource(item.title)
            val version = "0.0.1"
            val description = item.description?.let { stringResource(it, version) }
            when (item) {
                // These two show their current value on the right of the row.
                SettingsItem.DualScreen -> ListRow(
                    title, description, focused,
                    trailing = stringResource(if (c.companionOn) R.string.value_on else R.string.value_off),
                )
                SettingsItem.Covers -> ListRow(
                    title, description, focused,
                    trailing = stringResource(if (c.coversOn) R.string.value_on else R.string.value_off),
                )
                SettingsItem.Themes -> ListRow(title, description, focused, trailing = c.activeThemeName)
                SettingsItem.Language -> ListRow(title, description, focused, trailing = languageName(c.languageChoice))
                else -> ListRow(title, description, focused)
            }
        }
    }
}

@Composable
private fun languageName(choice: LanguageChoice): String = stringResource(
    when (choice) {
        LanguageChoice.System -> R.string.language_system
        LanguageChoice.English -> R.string.language_english
        LanguageChoice.Spanish -> R.string.language_spanish
    }
)

@Composable
private fun FoldersPage(c: SettingsController) {
    PageFrame(stringResource(R.string.folders_title), stringResource(R.string.folders_subtitle)) {
        FocusList(c.folderRows, c.foldersIndex) { _, row, focused ->
            when (row) {
                is FolderRow.AutoDetect -> ListRow(
                    title = stringResource(R.string.folders_auto_title),
                    detail = row.volumes.joinToString(" · ") { "${it.label} (${it.dir.path})" }
                        .ifEmpty { stringResource(R.string.folders_no_storage) },
                    focused = focused,
                    trailing = stringResource(if (row.enabled) R.string.value_on else R.string.value_off),
                )
                is FolderRow.Custom -> ListRow(
                    row.path,
                    stringResource(
                        R.string.folders_custom_detail,
                        pluralStringResource(R.plurals.games_count, row.gameCount, row.gameCount),
                    ),
                    focused,
                )
                FolderRow.Add -> ListRow(
                    stringResource(R.string.folders_add_title), stringResource(R.string.folders_add_desc), focused,
                )
            }
        }
    }
}

@Composable
private fun BrowserPage(c: SettingsController) {
    PageFrame(stringResource(R.string.browser_title), c.browserDir?.path ?: stringResource(R.string.browser_storage)) {
        if (c.browserEntries.isEmpty()) {
            Text(
                stringResource(R.string.browser_empty),
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

@Composable
private fun SetupPage(c: SettingsController) {
    val creatable = c.setupEntries.count { it.existingName == null }
    PageFrame(stringResource(R.string.setup_title), stringResource(R.string.setup_subtitle, c.setupPath)) {
        // Row 0 is the action; the rest are the consoles.
        val rows = listOf<SetupEntry?>(null) + c.setupEntries
        FocusList(rows, c.setupIndex) { _, entry, focused ->
            if (entry == null) {
                ListRow(
                    title = pluralStringResource(R.plurals.setup_create, c.setupSelected.size, c.setupSelected.size),
                    detail = stringResource(if (creatable == 0) R.string.setup_all_exist else R.string.setup_names_hint),
                    focused = focused,
                )
            } else {
                val existing = entry.existingName
                ListRow(
                    title = entry.platform.system.name,
                    detail = if (existing != null) {
                        stringResource(R.string.setup_exists_detail, existing)
                    } else {
                        stringResource(R.string.setup_folder_detail, entry.platform.id)
                    },
                    focused = focused,
                    trailing = when {
                        existing != null -> stringResource(R.string.setup_exists_badge)
                        entry.platform.id in c.setupSelected -> "[x]"
                        else -> "[ ]"
                    },
                )
            }
        }
    }
}
