package com.thorium.core.ui.theme

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.io.InputStream

/** Where a theme's files come from: the app's assets or a folder on storage. */
interface ThemeSource {
    fun open(path: String): InputStream?

    /** The file as a real file on disk (copied out of the assets when needed), for fonts and sounds. */
    fun file(path: String): File?
}

/** A theme bundled in the app: `assets/<base>/`. Files are copied to [cacheDir] when a real file is needed. */
class AssetThemeSource(
    private val assets: AssetManager,
    private val base: String,
    private val cacheDir: File,
) : ThemeSource {
    override fun open(path: String): InputStream? = try {
        assets.open("$base/${safe(path)}")
    } catch (e: java.io.IOException) {
        null
    }

    override fun file(path: String): File? {
        val clean = safe(path)
        val target = File(cacheDir, "$base/$clean")
        if (!target.isFile) {
            val input = open(clean) ?: return null
            target.parentFile?.mkdirs()
            input.use { src -> target.outputStream().use { src.copyTo(it) } }
        }
        return target
    }
}

/** A theme installed by the user in a folder. */
class FolderThemeSource(private val dir: File) : ThemeSource {
    private fun resolve(path: String): File? {
        val file = File(dir, safe(path))
        // A theme can only read inside its own folder.
        return file.takeIf { it.isFile && it.canonicalPath.startsWith(dir.canonicalPath + File.separator) }
    }

    override fun open(path: String): InputStream? = resolve(path)?.inputStream()
    override fun file(path: String): File? = resolve(path)
}

/** Drops anything that could climb out of the theme folder. */
internal fun safe(path: String): String = path.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }.joinToString("/")

object ThemeLoader {
    fun readSpec(source: ThemeSource): ThemeSpec {
        val text = source.open("theme.json")?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw ThemeException("theme.json is missing")
        return ThemeParser.parse(text)
    }

    /** Decodes the images, font and sound files a theme names. Anything missing is skipped. */
    fun loadAssets(spec: ThemeSpec, source: ThemeSource): ThemeAssets {
        val layers = spec.background.layers.map { decode(source, it.image, MAX_LAYER_WIDTH) }
        val icons = spec.tabs.mapNotNull { (tab, t) -> t.icon?.let { decode(source, it, MAX_ICON_WIDTH) }?.let { tab to it } }.toMap()
        val font = spec.font?.let { f ->
            val fonts = buildList {
                f.regular?.let { source.file(it) }?.let { add(Font(it, FontWeight.Normal)) }
                f.bold?.let { source.file(it) }?.let { add(Font(it, FontWeight.Bold)) }
            }
            fonts.takeIf { it.isNotEmpty() }?.let { FontFamily(it) }
        }
        val sounds = spec.sounds.mapNotNull { (event, path) -> source.file(path)?.let { event to it } }.toMap()
        return ThemeAssets(layers, icons, font, sounds)
    }

    private fun decode(source: ThemeSource, path: String, maxWidth: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source.open(path)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return source.open(path)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
    }

    private const val MAX_LAYER_WIDTH = 1920
    private const val MAX_ICON_WIDTH = 256
}
