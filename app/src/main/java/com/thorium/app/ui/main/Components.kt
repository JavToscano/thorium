package com.thorium.app.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.thorium.core.ui.components.Cover
import com.thorium.core.ui.components.focusBorder
import com.thorium.core.ui.components.focusScale
import com.thorium.core.ui.components.systemColors
import com.thorium.core.ui.theme.Dimens
import com.thorium.core.ui.theme.Palette

@Composable
fun CardRow(row: RowModel, rowFocused: Boolean, itemIndex: Int, vm: AppViewModel) {
    val state = rememberLazyListState()
    LaunchedEffect(itemIndex) { state.animateScrollToItem((itemIndex - 1).coerceAtLeast(0)) }
    Column(Modifier.fillMaxWidth()) {
        if (row.title.isNotEmpty()) {
            Text(
                row.title,
                modifier = Modifier.padding(start = Dimens.ScreenPadding, top = 6.dp, bottom = 2.dp),
                color = if (rowFocused) Palette.TextPrimary else Palette.TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LazyRow(
            state = state,
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
            contentPadding = PaddingValues(
                horizontal = Dimens.ScreenPadding, vertical = 12.dp
            ),
        ) {
            itemsIndexed(row.items, key = { _, c -> c.key }) { i, card ->
                val focused = rowFocused && i == itemIndex
                when (card) {
                    is CardModel.GameCard -> Cover(
                        title = card.game.title,
                        system = card.system,
                        focused = focused,
                        progress = card.game.progress,
                        favorite = vm.isFavorite(card.game.id),
                    )
                    is CardModel.SystemCard -> SystemTile(card, focused)
                }
            }
        }
    }
}

@Composable
private fun SystemTile(card: CardModel.SystemCard, focused: Boolean) {
    val scale = focusScale(focused)
    val border = focusBorder(focused)
    Box(
        Modifier
            .zIndex(if (focused) 1f else 0f)
            .size(Dimens.CardWidth + 24.dp, Dimens.CardHeight - 40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(systemColors(card.system.hue)))
            .border(3.dp, border, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(card.system.shortName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(gameCountLabel(card.gameCount), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
        }
    }
}

@Composable
fun TabBar(selected: Tab) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("L1", color = Palette.TextSecondary, fontSize = 11.sp)
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            val color by animateColorAsState(if (isSelected) Palette.Accent else Palette.TextSecondary, label = "tab")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tab.title, color = color, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Box(Modifier.padding(top = 3.dp).width(if (isSelected) 28.dp else 0.dp).height(3.dp).background(color))
            }
        }
        Text("R1", color = Palette.TextSecondary, fontSize = 11.sp)
    }
}


/** "1 game" / "N games". */
fun gameCountLabel(count: Int): String = if (count == 1) "1 game" else "$count games"
