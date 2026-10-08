package com.thorium.app.ui.downloads

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thorium.app.R
import com.thorium.app.ui.main.formatBytes
import com.thorium.app.ui.settings.FocusList
import com.thorium.app.ui.settings.ListRow
import com.thorium.app.ui.settings.PageFrame
import com.thorium.core.ui.text.resolve
import com.thorium.core.ui.theme.Palette

@Composable
internal fun BrowsePage(c: DownloadsController) {
    val b = c.browse
    val title = b.source?.name.orEmpty()
    val subtitle = if (b.filter.isNotBlank()) stringResource(R.string.browse_filtered, b.filter) else stringResource(R.string.browse_subtitle)
    PageFrame(title, subtitle) {
        when (val state = b.state) {
            BrowseState.Loading -> Note(stringResource(R.string.browse_loading))
            is BrowseState.Failed -> Note(state.reason.resolve())
            BrowseState.Ready -> {
                val list = b.visible
                if (list.isEmpty()) {
                    Note(stringResource(if (b.entries.isEmpty()) R.string.browse_empty else R.string.browse_no_match))
                } else {
                    FocusList(list, b.index) { _, entry, focused ->
                        ListRow(
                            title = entry.name,
                            detail = entry.sizeBytes?.let(::formatBytes),
                            focused = focused,
                            trailing = if (entry.isDirectory) "›" else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlatformPickPage(c: DownloadsController) {
    val b = c.browse
    PageFrame(
        stringResource(R.string.platform_title),
        stringResource(R.string.platform_subtitle, b.pending?.name.orEmpty()),
    ) {
        FocusList(b.platforms, b.platformIndex) { _, system, focused ->
            ListRow(system.name, stringResource(R.string.setup_folder_detail, system.id), focused)
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, modifier = Modifier.padding(top = 16.dp), color = Palette.TextSecondary, fontSize = 14.sp)
}
