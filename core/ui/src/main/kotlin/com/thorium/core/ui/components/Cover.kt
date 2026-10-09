package com.thorium.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.thorium.core.model.GameSystem
import com.thorium.core.ui.theme.Dimens
import com.thorium.core.ui.theme.Palette

/** Two-stop gradient derived from a system hue; used by covers and system tiles. */
fun systemColors(hue: Float): List<Color> = listOf(
    Color.hsv(hue, 0.55f, 0.62f),
    Color.hsv((hue + 25f) % 360f, 0.70f, 0.30f),
)

/** Placeholder cover art: gradient, system badge, title, optional favorite star and progress bar. */
@Composable
fun Cover(
    title: String,
    system: GameSystem,
    focused: Boolean,
    progress: Float? = null,
    favorite: Boolean = false,
    width: Dp = Dimens.CardWidth,
    height: Dp = Dimens.CardHeight,
    /** The real cover when there is one; replaces the placeholder badge and title. */
    art: ImageBitmap? = null,
) {
    val scale = focusScale(focused)
    val border = focusBorder(focused)
    Box(
        Modifier
            .zIndex(if (focused) 1f else 0f)
            .size(width, height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(systemColors(system.hue)))
            .border(3.dp, border, RoundedCornerShape(10.dp))
    ) {
        if (art != null) {
            // Whole cover, never cropped: box art comes in different shapes.
            Image(
                art, contentDescription = title,
                modifier = Modifier.fillMaxSize().background(Palette.Panel),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                system.shortName,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (favorite) {
            Text("★", modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), color = Palette.Warning, fontSize = 14.sp)
        }
        if (art == null) {
            Text(
                title,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (progress != null) {
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)
                    .drawBehind {
                        drawRect(Color.Black.copy(alpha = 0.5f))
                        drawRect(Palette.Accent, Offset.Zero, Size(this.size.width * progress, this.size.height))
                    }
            )
        }
    }
}
