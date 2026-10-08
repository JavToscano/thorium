package com.thorium.app.spike

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SpikeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(), content = content)
}

@Composable
fun Mono(text: String, color: Color = Color.Unspecified, size: Int = 11) {
    Text(text, color = color, fontFamily = FontFamily.Monospace, fontSize = size.sp, lineHeight = (size + 3).sp)
}

@Composable
fun LogList(modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        items(SpikeLog.lines) { Mono(it) }
    }
}

@Composable
fun ActionRow(vararg actions: Pair<String, () -> Unit>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.forEach { (label, onClick) -> Button(onClick = onClick) { Text(label, fontSize = 12.sp) } }
    }
}

@Composable
fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) { content() }
}
