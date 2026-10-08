package com.thorium.core.ui.keyboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thorium.core.ui.text.resolve
import com.thorium.core.ui.theme.Palette

/** Keyboard overlay for [controller]; draws nothing while it is closed. */
@Composable
fun OnScreenKeyboard(controller: KeyboardController, modifier: Modifier = Modifier) {
    if (!controller.active) return
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().background(Palette.Panel).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            controller.title?.let {
                Text(it.resolve(), color = Palette.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            TextField(controller)
            controller.rows.forEachIndexed { r, keys ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    keys.forEachIndexed { c, key ->
                        KeyCap(
                            label = label(key, controller.page),
                            focused = controller.row == r && controller.col == c,
                            weight = weight(key),
                            modifier = Modifier.weight(weight(key)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextField(controller: KeyboardController) {
    val shown = if (controller.masked) "•".repeat(controller.text.length) else controller.text
    Box(
        Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0E1220))
            .border(1.dp, Palette.Accent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            "$shown▏",
            color = Palette.TextPrimary, fontSize = 16.sp, maxLines = 1,
            // Long values keep their end visible, which is where the user is typing.
            overflow = TextOverflow.StartEllipsis,
        )
    }
}

@Composable
private fun KeyCap(label: String, focused: Boolean, weight: Float, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (focused) Palette.Accent else Color(0xFF2A3350), label = "key")
    Box(
        modifier.height(34.dp).clip(RoundedCornerShape(7.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (focused) Color.Black else Palette.TextPrimary,
            fontSize = if (weight > 1f) 14.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun label(key: Key, page: KeyboardPage): String = when (key) {
    is Key.Char -> key.value
    Key.Shift -> "⇧"
    Key.Symbols -> if (page == KeyboardPage.Symbols) "abc" else "?123"
    Key.Space -> "␣"
    Key.Backspace -> "⌫"
    Key.Done -> "OK"
}

private fun weight(key: Key): Float = when (key) {
    is Key.Char -> 1f
    Key.Space -> 4f
    else -> 1.5f
}
