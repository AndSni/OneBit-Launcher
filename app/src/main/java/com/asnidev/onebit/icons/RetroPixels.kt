package com.asnidev.onebit.icons

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The icon conversions, on plain ARGB arrays (not premultiplied, row-major,
 * [n]×[n]) so they run in unit tests. Every result has hard alpha: each pixel
 * is fully opaque or fully transparent, as on a one-bit screen.
 */
object RetroPixels {

    /** The 16-colour palette of the old PC graphics cards. */
    val COLORS16 = intArrayOf(
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xAA5500, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
    ).map { it or OPAQUE }.toIntArray()

    private const val OPAQUE = 0xFF000000.toInt()

    /**
     * Averages each [factor]×[factor] block of [src] (side [srcSide]) into one
     * pixel, weighting colour by alpha so transparent pixels don't darken edges.
     */
    fun downsample(src: IntArray, srcSide: Int, factor: Int): IntArray {
        val n = srcSide / factor
        val out = IntArray(n * n)
        val area = factor * factor
        for (y in 0 until n) for (x in 0 until n) {
            var a = 0L; var r = 0L; var g = 0L; var b = 0L
            for (dy in 0 until factor) for (dx in 0 until factor) {
                val c = src[(y * factor + dy) * srcSide + x * factor + dx]
                val ca = (c ushr 24) and 0xFF
                a += ca
                r += ((c shr 16) and 0xFF) * ca
                g += ((c shr 8) and 0xFF) * ca
                b += (c and 0xFF) * ca
            }
            out[y * n + x] = if (a == 0L) 0 else
                ((a / area).toInt() shl 24) or ((r / a).toInt() shl 16) or ((g / a).toInt() shl 8) or (b / a).toInt()
        }
        return out
    }

    fun isOpaque(c: Int): Boolean = (c ushr 24) >= 128

    /** Relative luminance, 0..1 (Rec. 601 weights; good enough for picking ink or paper). */
    fun luminance(c: Int): Float =
        (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f

    /** Every half-or-more opaque pixel fully opaque, the rest transparent. */
    fun hardAlpha(px: IntArray): IntArray = IntArray(px.size) { i ->
        val c = px[i]
        if (isOpaque(c)) c or OPAQUE else 0
    }

    /**
     * How strongly each opaque pixel should be ink, 0..1 (NaN for transparent):
     * brightness stretched over the icon's own range, then turned around when the
     * ink is darker than the paper, so dark shapes print as ink on a light theme
     * and bright shapes glow as ink on a dark one.
     */
    fun inkness(px: IntArray, ink: Int, paper: Int): FloatArray {
        val lum = FloatArray(px.size) { if (isOpaque(px[it])) luminance(px[it]) else Float.NaN }
        val opaque = lum.filter { !it.isNaN() }.sorted()
        val lo = if (opaque.isEmpty()) 0f else opaque[(opaque.size * 0.05f).toInt().coerceAtMost(opaque.lastIndex)]
        val hi = if (opaque.isEmpty()) 1f else opaque[(opaque.size * 0.95f).toInt().coerceAtMost(opaque.lastIndex)]
        // A nearly flat icon (one colour) isn't stretched: it would turn into noise.
        val range = (hi - lo).takeIf { it > 0.2f }
        val inkDark = luminance(ink) <= luminance(paper)
        return FloatArray(px.size) { i ->
            val l = lum[i]
            if (l.isNaN()) Float.NaN else {
                val s = if (range == null) l else ((l - lo) / range).coerceIn(0f, 1f)
                if (inkDark) 1f - s else s
            }
        }
    }

    /** Atkinson dithering: 6/8 of the error spreads to six neighbours, keeping contrast high. */
    fun dither(px: IntArray, n: Int, ink: Int, paper: Int): IntArray {
        val v = inkness(px, ink, paper)
        val out = IntArray(px.size)
        for (y in 0 until n) for (x in 0 until n) {
            val i = y * n + x
            val value = v[i]
            if (value.isNaN()) continue
            val on = value >= 0.5f
            out[i] = if (on) ink else paper
            val err = (value - if (on) 1f else 0f) / 8f
            spread(v, n, x + 1, y, err); spread(v, n, x + 2, y, err)
            spread(v, n, x - 1, y + 1, err); spread(v, n, x, y + 1, err); spread(v, n, x + 1, y + 1, err)
            spread(v, n, x, y + 2, err)
        }
        return out
    }

    private fun spread(v: FloatArray, n: Int, x: Int, y: Int, err: Float) {
        if (x < 0 || x >= n || y >= n) return
        val i = y * n + x
        if (!v[i].isNaN()) v[i] += err
    }

    /**
     * Line art, the way icons were drawn by hand: paper fill, ink where the
     * picture has an edge (a jump in brightness) and where it's darkest (or
     * brightest, on a dark theme).
     */
    fun lineArt(px: IntArray, n: Int, ink: Int, paper: Int): IntArray {
        val v = inkness(px, ink, paper)
        return IntArray(px.size) { i ->
            val value = v[i]
            if (value.isNaN()) return@IntArray 0
            val x = i % n
            val y = i / n
            val right = if (x + 1 < n) v[i + 1] else Float.NaN
            val below = if (y + 1 < n) v[i + n] else Float.NaN
            val edge = (!right.isNaN() && abs(right - value) > EDGE) || (!below.isNaN() && abs(below - value) > EDGE)
            if (value > 0.8f || edge) ink else paper
        }
    }

    private const val EDGE = 0.3f

    /**
     * A one-colour (themed) icon, given at [factor]× its size in [big] (side
     * [bigSide]), in ink and paper. Designers shade inside such icons with
     * opacity, so each sub-pixel is solid (≥ 80%), shaded (20..80%) or empty,
     * and each pixel goes by majority: solid is ink, shaded a half-tone pattern
     * (the designer's lighter areas), otherwise transparent. Edge blending is a
     * sliver of sub-pixels and doesn't sway the vote, so it adds no fringe.
     */
    fun monoTone(big: IntArray, bigSide: Int, factor: Int, ink: Int, paper: Int): IntArray {
        val n = bigSide / factor
        val half = factor * factor / 2
        return IntArray(n * n) { i ->
            val x = i % n
            val y = i / n
            var solid = 0
            var shaded = 0
            for (dy in 0 until factor) for (dx in 0 until factor) {
                val a = big[(y * factor + dy) * bigSide + x * factor + dx] ushr 24
                if (a >= 204) solid++ else if (a >= 51) shaded++
            }
            when {
                solid >= half -> ink
                solid + shaded >= half -> if ((x + y) % 2 == 0) ink else paper
                else -> 0
            }
        }
    }

    /** The opaque shape in ink. */
    fun silhouette(px: IntArray, ink: Int): IntArray = IntArray(px.size) { if (isOpaque(px[it])) ink else 0 }

    /** Hard-edged copy, each colour snapped to the nearest one in [palette] (null keeps colours). */
    fun pixelate(px: IntArray, palette: IntArray?): IntArray = IntArray(px.size) { i ->
        val c = px[i]
        when {
            !isOpaque(c) -> 0
            palette == null -> c or OPAQUE
            else -> nearest(c, palette)
        }
    }

    fun nearest(c: Int, palette: IntArray): Int {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        var best = palette[0]
        var bestD = Int.MAX_VALUE
        for (p in palette) {
            val dr = r - ((p shr 16) and 0xFF); val dg = g - ((p shr 8) and 0xFF); val db = b - (p and 0xFF)
            // Weighted toward green like the eye; plain RGB distance turns skin tones purple.
            val d = 3 * dr * dr + 4 * dg * dg + 2 * db * db
            if (d < bestD) { bestD = d; best = p }
        }
        return best
    }

    /** A one-pixel [ink] line around the outside of the shape (into transparent pixels). */
    fun outline(px: IntArray, n: Int, ink: Int): IntArray {
        val out = px.copyOf()
        for (y in 0 until n) for (x in 0 until n) {
            val i = y * n + x
            if (px[i] != 0) continue
            val touches = (x > 0 && px[i - 1] != 0) || (x + 1 < n && px[i + 1] != 0) ||
                (y > 0 && px[i - n] != 0) || (y + 1 < n && px[i + n] != 0)
            if (touches) out[i] = ink
        }
        return out
    }

    /** Swaps [ink] and [paper]: how a selected icon looks. Other colours are inverted. */
    fun invert(px: IntArray, ink: Int, paper: Int): IntArray = IntArray(px.size) { i ->
        when (val c = px[i]) {
            0 -> 0
            ink -> paper
            paper -> ink
            else -> (c and OPAQUE) or (c.inv() and 0xFFFFFF)
        }
    }

    /**
     * A selected one-colour icon: the whole square inverted, so the shape shows
     * in paper on an ink tile (flipping ink to paper alone would leave paper on paper).
     */
    fun invertTile(px: IntArray, ink: Int, paper: Int): IntArray = IntArray(px.size) { i ->
        when (px[i]) {
            ink -> paper
            else -> ink
        }
    }

    /** Darkens every pixel by [amount] (0..1); how a pressed colour icon looks. */
    fun shade(px: IntArray, amount: Float): IntArray = IntArray(px.size) { i ->
        val c = px[i]
        if (c == 0) 0 else {
            val k = 1f - amount
            (c and OPAQUE) or
                (((c shr 16) and 0xFF) * k).roundToInt().shl(16) or
                (((c shr 8) and 0xFF) * k).roundToInt().shl(8) or
                ((c and 0xFF) * k).roundToInt()
        }
    }
}
