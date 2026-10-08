package com.thorium.app.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thorium.app.R
import com.thorium.app.ui.main.AppViewModel
import com.thorium.app.ui.main.formatBytes
import com.thorium.app.ui.settings.FocusList
import com.thorium.app.ui.settings.PageFrame
import com.thorium.core.model.DownloadError
import com.thorium.core.model.DownloadItem
import com.thorium.core.model.DownloadState
import com.thorium.core.ui.theme.Dimens
import com.thorium.core.ui.theme.Palette

@Composable
fun DownloadsScreen(vm: AppViewModel) {
    val items = vm.downloadItems
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.dl_empty),
                modifier = Modifier.padding(horizontal = 80.dp),
                color = Palette.TextSecondary, fontSize = 15.sp,
            )
        }
        return
    }
    PageFrame(stringResource(R.string.tab_downloads), null) {
        FocusList(items, vm.downloadIndex) { _, item, focused -> DownloadRow(item, focused) }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem, focused: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (focused) Color(0xFF26324D) else Palette.Panel)
            .border(2.dp, if (focused) Palette.Accent else Color.Transparent, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.title, modifier = Modifier.weight(1f),
                color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(stateLabel(item.state), color = stateColor(item.state), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(detail(item), color = Palette.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val progress = item.progress
        if (!item.state.isFinished && progress != null) {
            Box(
                Modifier.padding(top = 6.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                    .drawBehind {
                        drawRect(Color.White.copy(alpha = 0.12f))
                        drawRect(Palette.Accent, size = Size(size.width * progress, size.height))
                    }
            )
        }
    }
}

@Composable
private fun detail(item: DownloadItem): String {
    val total = item.sizeBytes
    return when {
        item.state == DownloadState.Failed && item.error != null -> stringResource(errorRes(item.error!!))
        item.state == DownloadState.Completed && item.installedPath != null ->
            stringResource(R.string.dl_installed_in, item.installedPath!!.substringBeforeLast('/').substringAfterLast('/'))
        total != null && total > 0 ->
            stringResource(R.string.dl_progress, item.entryName, formatBytes(item.bytesDone), formatBytes(total))
        else -> stringResource(R.string.dl_progress_unknown, item.entryName, formatBytes(item.bytesDone))
    }
}

@Composable
private fun stateLabel(state: DownloadState): String = stringResource(
    when (state) {
        DownloadState.Queued -> R.string.dl_state_queued
        DownloadState.Downloading -> R.string.dl_state_downloading
        DownloadState.Paused -> R.string.dl_state_paused
        DownloadState.Verifying -> R.string.dl_state_verifying
        DownloadState.Extracting -> R.string.dl_state_extracting
        DownloadState.Installing -> R.string.dl_state_installing
        DownloadState.Completed -> R.string.dl_state_completed
        DownloadState.Failed -> R.string.dl_state_failed
        DownloadState.Cancelled -> R.string.dl_state_cancelled
    }
)

private fun stateColor(state: DownloadState): Color = when (state) {
    DownloadState.Completed -> Color(0xFF81C784)
    DownloadState.Failed -> Color(0xFFE57373)
    DownloadState.Paused, DownloadState.Cancelled, DownloadState.Queued -> Palette.TextSecondary
    else -> Palette.Accent
}

private fun errorRes(error: DownloadError): Int = when (error) {
    DownloadError.Network -> R.string.dl_err_network
    DownloadError.Unauthorized -> R.string.dl_err_unauthorized
    DownloadError.NotFound -> R.string.dl_err_not_found
    DownloadError.Insecure -> R.string.dl_err_insecure
    DownloadError.NoSpace -> R.string.dl_err_no_space
    DownloadError.Verification -> R.string.dl_err_verification
    DownloadError.Extraction -> R.string.dl_err_extraction
    DownloadError.Storage -> R.string.dl_err_storage
    DownloadError.Unknown -> R.string.dl_err_unknown
}
