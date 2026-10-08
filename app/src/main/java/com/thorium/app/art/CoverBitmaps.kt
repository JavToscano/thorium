package com.thorium.app.art

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decodes cover files at card size and keeps the most recent ones in memory. */
object CoverBitmaps {
    private const val TARGET_WIDTH = 400
    private val cache = LruCache<String, ImageBitmap>(48)

    fun load(file: File): ImageBitmap? {
        cache.get(file.path)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap() ?: return null
        cache.put(file.path, bitmap)
        return bitmap
    }
}

/** The decoded cover for [file], or null while it loads or when there is none. */
@Composable
fun rememberCoverArt(file: File?): ImageBitmap? {
    val art by produceState<ImageBitmap?>(null, file) {
        value = if (file == null) null else withContext(Dispatchers.IO) { CoverBitmaps.load(file) }
    }
    return art
}
