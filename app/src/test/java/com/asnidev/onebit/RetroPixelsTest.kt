package com.asnidev.onebit

import com.asnidev.onebit.icons.RetroPixels
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroPixelsTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val green = 0xFF33FF66.toInt()
    private val dark = 0xFF001A08.toInt()

    private fun gray(v: Int) = black or (v shl 16) or (v shl 8) or v

    @Test
    fun downsampleAveragesAndIgnoresTransparent() {
        // 2x2 block: two white opaque, two transparent -> white at half alpha.
        val px = intArrayOf(white, 0, 0, white)
        val out = RetroPixels.downsample(px, 2, 2)
        assertEquals(1, out.size)
        assertEquals(0x7F, out[0] ushr 24)
        assertEquals(0xFFFFFF, out[0] and 0xFFFFFF)
    }

    @Test
    fun ditherOnlyUsesInkAndPaperAndKeepsTransparency() {
        val n = 8
        val px = IntArray(n * n) { if (it % n == 0) 0 else gray((it * 4) and 0xFF) }
        val out = RetroPixels.dither(px, n, black, white)
        for (i in out.indices) {
            if (px[i] == 0) assertEquals(0, out[i]) else assertTrue(out[i] == black || out[i] == white)
        }
    }

    @Test
    fun ditherOfMidGrayIsAboutHalfInk() {
        val n = 16
        // A gradient so the levels stretch; the middle column band is mid gray.
        val px = IntArray(n * n) { gray(((it % n) * 255) / (n - 1)) }
        val out = RetroPixels.dither(px, n, black, white)
        val ink = out.count { it == black }
        assertTrue("ink $ink", ink in 96..160)
    }

    @Test
    fun darkThemeTurnsBrightIntoInk() {
        val px = intArrayOf(white, black, white, black)
        val out = RetroPixels.dither(px, 2, green, dark)
        assertEquals(green, out[0])
        assertEquals(dark, out[1])
    }

    @Test
    fun outlineGoesAroundTheShape() {
        val n = 3
        val px = IntArray(9).also { it[4] = white }
        val out = RetroPixels.outline(px, n, black)
        assertArrayEquals(intArrayOf(0, black, 0, black, white, black, 0, black, 0), out)
    }

    @Test
    fun silhouetteIsInkWhereOpaque() {
        val px = intArrayOf(0, 0x80FF0000.toInt(), 0x10FF0000)
        assertArrayEquals(intArrayOf(0, black, 0), RetroPixels.silhouette(px, black))
    }

    @Test
    fun sixteenColoursSnapToPalette() {
        val out = RetroPixels.pixelate(intArrayOf(0xFFFE0101.toInt(), 0xFF0101A0.toInt()), RetroPixels.COLORS16)
        assertEquals(0xFFAA0000.toInt(), out[0])
        assertEquals(0xFF0000AA.toInt(), out[1])
    }

    @Test
    fun invertSwapsInkAndPaper() {
        assertArrayEquals(intArrayOf(white, black, 0), RetroPixels.invert(intArrayOf(black, white, 0), black, white))
    }

    @Test
    fun lineArtMarksEdges() {
        val n = 4
        // Left half light gray, right half white: an edge in the middle.
        val px = IntArray(n * n) { if (it % n < 2) gray(0xC0) else white }
        val out = RetroPixels.lineArt(px, n, black, white)
        for (y in 0 until n) assertEquals(black, out[y * n + 1])
        for (y in 0 until n) assertEquals(white, out[y * n + 3])
    }

    @Test
    fun monoToneSolidShadedAndEmpty() {
        // Three pixels at 4x: solid, a designer's 40% shade, and one stray sub-pixel of edge blend.
        val f = 4
        val side = 3 * f
        val big = IntArray(side * side) // square; only the top row of pixels is drawn
        for (y in 0 until f) for (x in 0 until side) {
            big[y * side + x] = when (x / f) {
                0 -> black
                1 -> 0x66000000
                else -> if (x == side - 1 && y == 0) 0x80000000.toInt() else 0
            }
        }
        val out = RetroPixels.monoTone(big, side, f, black, white)
        assertEquals(black, out[0])
        assertEquals(white, out[1]) // shaded: half-tone, and (1 + 0) is odd, so paper here
        assertEquals(0, out[2])     // a sliver of blend is not part of the shape
    }

    @Test
    fun shapeBoundsFindsTheMarkAndIgnoresFaintPixels() {
        val side = 10
        val px = IntArray(side * side)
        px[2 * side + 3] = black            // (3, 2)
        px[5 * side + 6] = black            // (6, 5)
        px[9 * side + 9] = 0x20000000       // below 20% opacity: not part of the shape
        assertArrayEquals(intArrayOf(3, 2, 7, 6), RetroPixels.shapeBounds(px, side))
        assertEquals(null, RetroPixels.shapeBounds(IntArray(side * side), side))
    }

    @Test
    fun fitZoomEnlargesOnlySmallMarks() {
        assertEquals(1f, RetroPixels.fitZoom(intArrayOf(0, 0, 60, 30), 100), 0f)   // already 60% wide
        assertEquals(1f, RetroPixels.fitZoom(null, 100), 0f)
        assertEquals(62f / 20f, RetroPixels.fitZoom(intArrayOf(40, 40, 60, 55), 100), 1e-4f)
    }

    @Test
    fun legibilityRejectsDotsAndHalfTone() {
        val n = 16
        // A solid 6x6 block: 36 of 256 pixels, about 14%.
        val solid = IntArray(n * n) { i -> if (i % n < 6 && i / n < 6) black else 0 }
        assertTrue(RetroPixels.isLegible(solid, black, white))
        // A clock's two hands: a few pixels.
        val dots = IntArray(n * n).also { it[0] = black; it[17] = black; it[34] = black }
        assertEquals(false, RetroPixels.isLegible(dots, black, white))
        // A large half-toned area with a little solid ink.
        val halftone = IntArray(n * n) { i -> if ((i % n + i / n) % 2 == 0) black else white }
        assertEquals(false, RetroPixels.isLegible(halftone, black, white))
    }

    @Test
    fun bodyCountsInkAndPaperButNotTransparency() {
        val n = 10
        val pale = IntArray(n * n) { i -> if (i < 30) white else 0 }   // a pale shape, 30%
        assertTrue(RetroPixels.hasBody(pale))
        val hands = IntArray(n * n) { i -> if (i < 5) black else 0 }   // 5%
        assertEquals(false, RetroPixels.hasBody(hands))
    }
}
