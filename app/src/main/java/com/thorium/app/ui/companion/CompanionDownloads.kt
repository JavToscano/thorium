package com.thorium.app.ui.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thorium.app.R
import com.thorium.app.ui.downloads.DownloadsRow
import com.thorium.app.ui.downloads.ProgressBar
import com.thorium.app.ui.downloads.detail
import com.thorium.app.ui.downloads.stateColor
import com.thorium.app.ui.downloads.stateLabel
import com.thorium.app.ui.main.AppViewModel
import com.thorium.app.ui.settings.SourcesController
import com.thorium.core.model.DownloadItem
import com.thorium.core.model.DownloadState
import com.thorium.core.ui.theme.Palette
import com.thorium.data.library.PlatformCatalog

/**
 * Bottom-screen panel for the Downloads tab: the full queue with live progress and, above it, the
 * details of whatever the top screen has in focus (a transfer, or a source to browse).
 */
@Composable
fun DownloadsCompanion(vm: AppViewModel) {
    val items = vm.downloadItems
    val speeds = vm.downloadSpeeds
    val focused = vm.downloadsUi.focusedRow
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (focused) {
            is DownloadsRow.Item -> ItemDetail(focused.item, speeds[focused.item.id])
            is DownloadsRow.Source -> SourceDetail(focused.config)
            else -> Text(stringResource(R.string.companion_dl_pick), color = Palette.TextSecondary, fontSize = 13.sp)
        }
        Title(stringResource(R.string.dl_header_queue))
        if (items.isEmpty()) {
            Text(stringResource(R.string.companion_dl_none), color = Palette.TextSecondary, fontSize = 12.sp)
        } else {
            val focusedId = (focused as? DownloadsRow.Item)?.item?.id
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.take(MAX_QUEUE_ROWS).forEach { QueueRow(it, speeds[it.id], highlighted = it.id == focusedId) }
            }
        }
    }
}

/** A compact list of the transfers still going, shown on other tabs while something downloads. */
@Composable
fun DownloadsStrip(items: List<DownloadItem>, speeds: Map<Long, Long>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Title(stringResource(R.string.tab_downloads))
        items.take(MAX_STRIP_ROWS).forEach { QueueRow(it, speeds[it.id], highlighted = false) }
    }
}

@Composable
private fun ItemDetail(item: DownloadItem, speed: Long?) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                item.title, modifier = Modifier.weight(1f),
                color = Palette.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(stateLabel(item.state), color = stateColor(item.state), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        item.progress?.let { if (!item.state.isFinished) ProgressBar(it, height = 8.dp) }
        Text(detail(item, speed), color = Palette.TextSecondary, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.dl_saving_to, item.destinationDir),
            color = Palette.TextSecondary, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SourceDetail(source: com.thorium.core.model.SourceConfig) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(source.name, color = Palette.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(stringResource(SourcesController.typeLabel(source.type)), color = Palette.Accent, fontSize = 14.sp)
        Text(source.location, color = Palette.TextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        source.defaultPlatformId?.let { id ->
            val name = PlatformCatalog.Default.platforms.firstOrNull { it.id == id }?.system?.name ?: id
            Text(stringResource(R.string.dl_source_default, name), color = Palette.TextSecondary, fontSize = 12.sp)
        }
        Text(stringResource(R.string.companion_dl_browse), color = Palette.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun QueueRow(item: DownloadItem, speed: Long?, highlighted: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (highlighted) Color(0xFF26324D) else Palette.Panel)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                item.title, modifier = Modifier.weight(1f),
                color = Palette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val percent = item.progress?.let { " ${(it * 100).toInt()}%" }.orEmpty()
            val label = if (item.state == DownloadState.Downloading) percent.trim() else stateLabel(item.state)
            Text(label, color = stateColor(item.state), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        val progress = item.progress
        if (!item.state.isFinished && progress != null) ProgressBar(progress, Modifier.padding(top = 4.dp), height = 4.dp)
    }
}

@Composable
private fun Title(text: String) {
    Text(text.uppercase(), color = Palette.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

private const val MAX_QUEUE_ROWS = 6
private const val MAX_STRIP_ROWS = 2
