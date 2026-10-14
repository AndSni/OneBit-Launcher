package com.asnidev.onebit.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.asnidev.onebit.data.IconStyle

/** Everything that changes how one icon looks. */
data class IconSpec(
    val style: IconStyle,
    /** Pixels per side; ORIGINAL uses the display size instead. */
    val res: Int,
    val ink: Int,
    val paper: Int,
    val outline: Boolean,
    val stripBackground: Boolean,
    /** Drawn inverted (1-bit) or shaded (colour), as while pressed. */
    val selected: Boolean = false,
    /** "1-bit dither": each palette colour's pattern. */
    val patterns: List<Int> = PatternDither.DEFAULT_PATTERNS,
    /** 1-bit styles: draw the app's one-colour icon instead when it has one. */
    val preferMono: Boolean = true,
)

/** Where an icon's picture comes from. */
sealed interface IconSource {
    /** The app's own icon (with its adaptive layers and one-colour layer, if any). */
    class App(val drawable: Drawable) : IconSource

    /** A drawable from an installed icon pack. */
    class Pack(val drawable: Drawable) : IconSource

    /** A bundled 1-bit pixel icon, [side]×[side]. */
    class Glyph(val bits: BooleanArray, val side: Int) : IconSource
}

/** Turns app icons into retro ones and keeps the results in memory. */
class IconRenderer {

    private val cache = LruCache<String, ImageBitmap>(800)

    fun cached(id: String, spec: IconSpec): ImageBitmap? = cache.get("$id|$spec")

    /** Call off the main thread. */
    fun render(id: String, drawable: Drawable, spec: IconSpec): ImageBitmap = render(id, IconSource.App(drawable), spec)

    /** Call off the main thread. */
    fun render(id: String, source: IconSource, spec: IconSpec): ImageBitmap {
        cache.get("$id|$spec")?.let { return it }
        val px = when (source) {
            is IconSource.Glyph -> glyph(source, spec)
            is IconSource.Pack -> if (isOneColour(source.drawable)) oneColour(source.drawable, spec)
                else if (spec.style == IconStyle.ORIGINAL) original(source.drawable, spec)
                else convert(source.drawable, spec.copy(preferMono = false))
            is IconSource.App -> if (spec.style == IconStyle.ORIGINAL) original(source.drawable, spec) else convert(source.drawable, spec)
        }
        val bitmap = Bitmap.createBitmap(px.pixels, px.side, px.side, Bitmap.Config.ARGB_8888).asImageBitmap()
        cache.put("$id|$spec", bitmap)
        return bitmap
    }

    fun clear() = cache.evictAll()

    private class Pixels(val pixels: IntArray, val side: Int)

    private fun original(drawable: Drawable, spec: IconSpec): Pixels {
        val side = ORIGINAL_SIDE
        var px = raster(drawable, side, LayerChoice.WHOLE)
        if (spec.selected) px = RetroPixels.shade(px, 0.4f)
        return Pixels(px, side)
    }

    /**
     * The icon at [IconSpec.res] pixels with hard alpha, before any style:
     * the right layer, supersampled, with a free border for the outline.
     */
    fun prepare(drawable: Drawable, spec: IconSpec): IntArray {
        val n = spec.res
        // With an outline the picture leaves a pixel free all round for it.
        val inner = if (spec.outline && spec.style != IconStyle.MONO) n - 2 else n
        val layer = when {
            spec.style == IconStyle.MONO -> LayerChoice.MONOCHROME
            spec.stripBackground -> LayerChoice.FOREGROUND
            else -> LayerChoice.WHOLE
        }
        val small = RetroPixels.downsample(raster(drawable, inner * SUPERSAMPLE, layer), inner * SUPERSAMPLE, SUPERSAMPLE)
        val px = IntArray(n * n)
        val off = (n - inner) / 2
        for (y in 0 until inner) small.copyInto(px, (y + off) * n + off, y * inner, (y + 1) * inner)
        return RetroPixels.hardAlpha(px)
    }

    /** The one-colour layer (or the foreground, if there is none) in ink, its shaded areas half-toned. */
    fun mono(drawable: Drawable, spec: IconSpec): IntArray =
        RetroPixels.monoTone(raster(drawable, spec.res * SUPERSAMPLE, LayerChoice.MONOCHROME), spec.res * SUPERSAMPLE, SUPERSAMPLE, spec.ink, spec.paper)

    private fun convert(drawable: Drawable, spec: IconSpec): Pixels {
        // The designer's own one-colour icon beats any conversion of the colour one:
        // the 1-bit styles draw it as it is (in ink) whenever the app ships one.
        if (spec.preferMono && spec.style in ONE_BIT && hasMonochrome(drawable)) {
            return convert(drawable, spec.copy(style = IconStyle.MONO))
        }
        val n = spec.res
        if (spec.style == IconStyle.MONO) {
            var px = mono(drawable, spec)
            if (spec.selected) px = RetroPixels.invertTile(px, spec.ink, spec.paper)
            return Pixels(px, n)
        }
        val inner = if (spec.outline && spec.style != IconStyle.MONO) n - 2 else n
        var px = prepare(drawable, spec)
        val ink = spec.ink
        val paper = spec.paper
        px = when (spec.style) {
            IconStyle.DITHER -> PatternDither.dither(px, n, ink, paper, spec.patterns)
            IconStyle.GRAIN -> RetroPixels.dither(px, n, ink, paper)
            IconStyle.THRESHOLD -> RetroPixels.lineArt(px, n, ink, paper)
            IconStyle.MONO -> RetroPixels.silhouette(px, ink)
            IconStyle.PIXEL -> RetroPixels.pixelate(px, null)
            IconStyle.COLORS16 -> RetroPixels.pixelate(px, RetroPixels.COLORS16)
            IconStyle.ORIGINAL -> px
        }
        if (inner < n) px = RetroPixels.outline(px, n, ink)
        if (spec.selected) {
            px = when (spec.style) {
                IconStyle.PIXEL, IconStyle.COLORS16 -> RetroPixels.shade(px, 0.45f)
                else -> RetroPixels.invert(px, ink, paper)
            }
        }
        return Pixels(px, n)
    }

    /**
     * A bundled pixel icon in ink, blown up by a whole number of pixels to fit
     * [IconSpec.res] (or sampled down when it's bigger), centred. It is already
     * 1-bit art, so every style draws it the same.
     */
    private fun glyph(g: IconSource.Glyph, spec: IconSpec): Pixels {
        val n = spec.res
        val k = n / g.side
        val drawn = if (k >= 1) g.side * k else n
        val off = (n - drawn) / 2
        var px = IntArray(n * n)
        for (y in 0 until drawn) for (x in 0 until drawn) {
            val sx = if (k >= 1) x / k else x * g.side / n
            val sy = if (k >= 1) y / k else y * g.side / n
            if (g.bits[sy * g.side + sx]) px[(y + off) * n + x + off] = spec.ink
        }
        if (spec.selected) px = RetroPixels.invertTile(px, spec.ink, spec.paper)
        return Pixels(px, n)
    }

    /**
     * A one-colour pack icon (line art such as Arcticons, often white): its
     * shape in ink whatever its own colour, so it never vanishes on the paper.
     */
    private fun oneColour(drawable: Drawable, spec: IconSpec): Pixels {
        if (spec.style == IconStyle.ORIGINAL) {
            var px = raster(drawable, ORIGINAL_SIDE, LayerChoice.WHOLE)
            px = IntArray(px.size) { i -> (px[i] and 0xFF000000.toInt()) or (spec.ink and 0xFFFFFF) }
            if (spec.selected) px = RetroPixels.shade(px, 0.4f)
            return Pixels(px, ORIGINAL_SIDE)
        }
        val n = spec.res
        var px = RetroPixels.monoTone(raster(drawable, n * SUPERSAMPLE, LayerChoice.WHOLE), n * SUPERSAMPLE, SUPERSAMPLE, spec.ink, spec.paper)
        if (spec.selected) px = RetroPixels.invertTile(px, spec.ink, spec.paper)
        return Pixels(px, n)
    }

    /** Whether nearly all of the drawable is one colour (shading by opacity aside). */
    private fun isOneColour(drawable: Drawable): Boolean {
        val px = raster(drawable, 32, LayerChoice.WHOLE).filter { (it ushr 24) >= 128 }
        if (px.isEmpty()) return true
        val top = px.groupingBy { PatternDither.nearest(it) }.eachCount().maxOf { it.value }
        return top >= px.size * 0.85f
    }

    private fun hasMonochrome(drawable: Drawable): Boolean =
        Build.VERSION.SDK_INT >= 33 && (drawable as? AdaptiveIconDrawable)?.monochrome != null

    private enum class LayerChoice { WHOLE, FOREGROUND, MONOCHROME }

    /** The icon drawn into a [side]×[side] ARGB array. */
    private fun raster(drawable: Drawable, side: Int, choice: LayerChoice): IntArray {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val adaptive = drawable as? AdaptiveIconDrawable
        val layer: Drawable? = when {
            adaptive == null -> null
            choice == LayerChoice.MONOCHROME && Build.VERSION.SDK_INT >= 33 -> adaptive.monochrome ?: adaptive.foreground
            choice == LayerChoice.MONOCHROME || choice == LayerChoice.FOREGROUND -> adaptive.foreground
            else -> null
        }
        if (layer != null) {
            // A layer is 108 units wide and only the middle 72 show through the mask,
            // so it's drawn half as large again to fill the square with that middle.
            val pad = side / 4
            val old = layer.copyBounds()
            layer.setBounds(-pad, -pad, side + pad, side + pad)
            layer.draw(canvas)
            layer.bounds = old
        } else {
            val old = drawable.copyBounds()
            drawable.setBounds(0, 0, side, side)
            drawable.draw(canvas)
            drawable.bounds = old
        }
        val px = IntArray(side * side)
        bitmap.getPixels(px, 0, side, 0, 0, side, side)
        bitmap.recycle()
        return px
    }

    private companion object {
        /** Styles that come out in ink and paper only, where a one-colour icon fits as it is. */
        val ONE_BIT = setOf(IconStyle.DITHER, IconStyle.GRAIN, IconStyle.THRESHOLD)
        const val SUPERSAMPLE = 4
        const val ORIGINAL_SIDE = 144
    }
}
