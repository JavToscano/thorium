package com.thorium.app.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.thorium.app.ui.settings.ListRow
import com.thorium.app.ui.settings.SourcesController
import com.thorium.app.ui.main.formatBytes
import com.thorium.app.ui.settings.FocusList
import com.thorium.app.ui.settings.PageFrame
import com.thorium.core.model.DownloadError
import com.thorium.core.model.DownloadItem
import com.thorium.core.model.DownloadState
import com.thorium.core.ui.theme.Dimens
import com.thorium.core.ui.theme.Palette

@Composable
fun DownloadsScreen(c: DownloadsController) {
    when (c.page) {
        DownloadsPage.Main -> MainPage(c)
        DownloadsPage.Browse -> BrowsePage(c)
        DownloadsPage.PlatformPick -> PlatformPickPage(c)
    }
}

@Composable
private fun MainPage(c: DownloadsController) {
    PageFrame(stringResource(R.string.tab_downloads), null) {
        FocusList(c.rows, c.index) { _, row, focused ->
            when (row) {
                is DownloadsRow.Header -> Text(
                    stringResource(row.title).uppercase(),
                    modifier = Modifier.padding(top = 6.dp),
                    color = Palette.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
                is DownloadsRow.Note -> Text(stringResource(row.text), color = Palette.TextSecondary, fontSize = 13.sp)
                is DownloadsRow.Source -> ListRow(
                    title = row.config.name,
                    detail = stringResource(SourcesController.typeLabel(row.config.type)) + " · " + row.config.location,
                    focused = focused,
                    trailing = "›",
                )
                DownloadsRow.Manage -> ListRow(
                    stringResource(R.string.dl_manage_sources), stringResource(R.string.dl_manage_desc), focused,
                )
                is DownloadsRow.Item -> DownloadRow(row.item, focused)
            }
        }
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
