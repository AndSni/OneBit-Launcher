package com.asnidev.onebit

import com.asnidev.onebit.icons.PatternDither
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternDitherTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private fun index(name: String) = PatternDither.PALETTE.indexOfFirst { it.name == name }

    @Test
    fun brandColoursFindTheirSwatch() {
        assertEquals(index("Red"), PatternDither.nearest(0xFFEA4335.toInt()))
        assertEquals(index("Blue"), PatternDither.nearest(0xFF4285F4.toInt()))
        assertEquals(index("Green"), PatternDither.nearest(0xFF34A853.toInt()))
        assertEquals(index("Yellow"), PatternDither.nearest(0xFFFBBC04.toInt()))
        assertEquals(index("White"), PatternDither.nearest(white))
        assertEquals(index("Black"), PatternDither.nearest(black))
    }

    @Test
    fun everyColourHasItsOwnPatternByDefault() {
        val patterns = PatternDither.DEFAULT_PATTERNS
        assertEquals(patterns.size, patterns.toSet().size)
        assertEquals(PatternDither.PALETTE.size, patterns.size)
    }

    @Test
    fun aColourAreaShowsItsPattern() {
        // An 8x8 green square (no edges): ink exactly where the green pattern has bits.
        val n = 8
        val green = 0xFF43A047.toInt()
        val out = PatternDither.dither(IntArray(n * n) { green }, n, black, white, edges = false)
        val p = PatternDither.PATTERNS[PatternDither.DEFAULT_PATTERNS[index("Green")]]
        for (i in out.indices) assertEquals(if (p.bit(i % n, i / n)) black else white, out[i])
    }

    @Test
    fun redAndGreenOfEqualBrightnessStayDifferent() {
        val n = 8
        val red = PatternDither.dither(IntArray(n * n) { 0xFFE53935.toInt() }, n, black, white, edges = false)
        val green = PatternDither.dither(IntArray(n * n) { 0xFF43A047.toInt() }, n, black, white, edges = false)
        assertNotEquals(red.toList(), green.toList())
    }

    @Test
    fun aSmallWhiteShapeOnAPatternKeepsItsPixels() {
        // A 2x2 white block in a 12x12 red field: outlined from outside, still paper inside.
        val n = 12
        val px = IntArray(n * n) { 0xFFE53935.toInt() }
        for (p in listOf(5 * n + 5, 5 * n + 6, 6 * n + 5, 6 * n + 6)) px[p] = white
        val out = PatternDither.dither(px, n, black, white)
        for (p in listOf(5 * n + 5, 5 * n + 6, 6 * n + 5, 6 * n + 6)) assertEquals(white, out[p])
        assertEquals(black, out[5 * n + 4]) // the outline, on the red side
    }

    @Test
    fun darkThemeTurnsPatternsInsideOut() {
        val n = 4
        val ink = 0xFF33FF66.toInt()
        val paper = 0xFF001A08.toInt()
        val out = PatternDither.dither(IntArray(n * n) { white }, n, ink, paper, edges = false)
        // White is the empty pattern; on a dark theme it lights up fully.
        assertTrue(out.all { it == ink })
    }

    @Test
    fun theUsersPatternChoiceIsUsed() {
        val n = 4
        val patterns = PatternDither.DEFAULT_PATTERNS.toMutableList().also { it[index("White")] = 4 } // ink
        val out = PatternDither.dither(IntArray(n * n) { white }, n, black, white, patterns, edges = false)
        assertTrue(out.all { it == black })
    }
}
