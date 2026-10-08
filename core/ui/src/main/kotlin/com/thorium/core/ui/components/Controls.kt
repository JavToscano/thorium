package com.thorium.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thorium.core.ui.theme.Dimens
import com.thorium.core.ui.theme.Palette

/** Button drawn from the logical focus state; it never takes real Compose focus. */
@Composable
fun ActionButton(label: String, focused: Boolean, modifier: Modifier = Modifier) {
    val scale = focusScale(focused)
    val bg by animateColorAsState(if (focused) Palette.Accent else Palette.Panel, label = "btn")
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp)).background(bg)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (focused) Color.Black else Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Bottom legend of controller buttons, e.g. "A Select". */
@Composable
fun HintBar(hints: List<Pair<String, String>>) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        hints.forEach { (button, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    button,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Palette.Panel).padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Palette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
                Text(label, color = Palette.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}
