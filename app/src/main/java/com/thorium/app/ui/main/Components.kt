package com.thorium.app.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.thorium.app.R
import com.thorium.app.art.rememberCoverArt
import com.thorium.core.ui.components.Cover
import com.thorium.core.ui.components.entrance
import com.thorium.core.ui.components.focusBorderBrush
import com.thorium.core.ui.components.focusGlow
import com.thorium.core.ui.components.focusLayer
import com.thorium.core.ui.components.focusShine
import com.thorium.core.ui.components.systemColors
import com.thorium.core.ui.text.resolve
import com.thorium.core.ui.theme.Dimens
import com.thorium.core.ui.theme.Palette
import com.thorium.core.ui.theme.ThemeShapes
import com.thorium.core.ui.theme.ThemeState

@Composable
fun CardRow(row: RowModel, rowFocused: Boolean, itemIndex: Int, vm: AppViewModel) {
    val state = rememberLazyListState()
    LaunchedEffect(itemIndex) { state.animateScrollToItem((itemIndex - 1).coerceAtLeast(0)) }
    Column(Modifier.fillMaxWidth()) {
        row.title?.let { title ->
            val titleColor by animateColorAsState(if (rowFocused) Palette.TextPrimary else Palette.TextSecondary, label = "rowTitle")
            Text(
                title.resolve(),
                modifier = Modifier.padding(start = Dimens.ScreenPadding, top = 6.dp, bottom = 2.dp).entrance(0),
                color = titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LazyRow(
            state = state,
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
            contentPadding = PaddingValues(
                horizontal = Dimens.ScreenPadding, vertical = 14.dp
            ),
        ) {
            itemsIndexed(row.items, key = { _, c -> c.key }) { i, card ->
                val focused = rowFocused && i == itemIndex
                // The wrapper carries the entrance and stacking order so a focused card rises over its neighbours.
                Box(Modifier.zIndex(if (focused) 1f else 0f).entrance(i)) {
                    when (card) {
                        is CardModel.GameCard -> Cover(
                            title = card.game.title,
                            system = card.system,
                            focused = focused,
                            progress = card.game.progress,
                            favorite = vm.isFavorite(card.game.id),
                            art = rememberCoverArt(vm.coverOf(card.game)),
                        )
                        is CardModel.SystemCard -> SystemTile(card, focused)
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemTile(card: CardModel.SystemCard, focused: Boolean) {
    Box(
        Modifier
            .zIndex(if (focused) 1f else 0f)
            .size(Dimens.CardWidth + 24.dp, Dimens.CardHeight - 40.dp)
            .focusLayer(focused)
            .focusGlow(focused, ThemeState.spec.shapes.cardRadius.dp)
            .clip(ThemeShapes.Card)
            .background(Brush.linearGradient(systemColors(card.system.hue)))
            .focusShine(focused)
            .border(ThemeShapes.Border, focusBorderBrush(focused), ThemeShapes.Card),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(card.system.shortName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(pluralStringResource(R.plurals.games_count, card.gameCount, card.gameCount), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
        }
    }
}

/**
 * Tabs with an indicator that glides to the selected one. The theme can add an icon and a small
 * second label (for instance the name in another language) to each tab.
 */
@Composable
fun TabBar(selected: Tab) {
    val spec = ThemeState.spec
    val assets = ThemeState.assets
    var containerX by remember { mutableFloatStateOf(0f) }
    val positions = remember { mutableStateMapOf<Tab, Pair<Float, Float>>() }
    val target = positions[selected]
    val slide = tween<Float>(spec.motion.tabSlideMs.coerceAtLeast(1), easing = FastOutSlowInEasing)
    val indicatorX by animateFloatAsState(target?.first ?: 0f, slide, label = "tabX")
    val indicatorW by animateFloatAsState(target?.second ?: 0f, slide, label = "tabW")
    val density = LocalDensity.current

    Box(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding, vertical = 8.dp)
            .onGloballyPositioned { containerX = it.positionInRoot().x },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("L1", color = Palette.TextSecondary, fontSize = 11.sp)
            Tab.entries.forEach { tab ->
                val isSelected = tab == selected
                val color by animateColorAsState(if (isSelected) Palette.Accent else Palette.TextSecondary, label = "tab")
                val scale by animateFloatAsState(if (isSelected) 1f else 0.94f, label = "tabScale")
                val tabSpec = spec.tabs[tab.name.lowercase()]
                val icon = assets.icons[tab.name.lowercase()]
                Column(
                    Modifier
                        .onGloballyPositioned { positions[tab] = (it.positionInRoot().x - containerX) to it.size.width.toFloat() }
                        .graphicsLayer { scaleX = scale; scaleY = scale },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (icon != null) {
                            Image(icon, contentDescription = null, modifier = Modifier.size(20.dp), colorFilter = ColorFilter.tint(color))
                        }
                        Text(stringResource(tab.title), color = color, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (tabSpec?.sub != null) {
                        Text(tabSpec.sub!!, color = color.copy(alpha = 0.75f), fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                }
            }
            Text("R1", color = Palette.TextSecondary, fontSize = 11.sp)
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset(indicatorX.roundToInt(), 0) }
                .width(with(density) { indicatorW.toDp() })
                .height(3.dp)
                .drawBehind {
                    // A soft glow under the bar, then the bar itself.
                    for (i in 4 downTo 1) {
                        drawRoundRect(
                            Palette.Accent.copy(alpha = 0.08f),
                            topLeft = Offset(-i * 2.dp.toPx(), -i * 2.dp.toPx()),
                            size = Size(size.width + i * 4.dp.toPx(), size.height + i * 4.dp.toPx()),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                        )
                    }
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(Palette.Accent, Palette.AccentAlt)),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                },
        )
    }
}
