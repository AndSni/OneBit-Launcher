package com.asnidev.onebit.icons

/**
 * One-bit icons by pattern palette, the way charts and paint programs showed
 * colour on one-bit screens and printers: every colour of a fixed palette has
 * its own fill pattern. Grays are dot screens of their density; hues are line
 * patterns whose direction tells them apart (red and green stay different even
 * when equally bright), with ink coverage close to the colour's darkness so
 * shading still reads.
 *
 * Each pixel snaps to the nearest palette colour, then takes that colour's
 * pattern bit. Patterns are 4×4 and laid on one grid across the whole icon, so
 * neighbouring areas of the same colour join up seamlessly.
 */
object PatternDither {

    /** A 4×4 pattern, one 4-bit row each (high bit on the left). */
    class Pattern(val name: String, vararg val rows: Int) {
        fun bit(x: Int, y: Int): Boolean = (rows[y and 3] shr (3 - (x and 3))) and 1 == 1
        val coverage: Float get() = rows.sumOf { Integer.bitCount(it) } / 16f
    }

    /** The pattern library; palette entries point into it, and the user may re-point them. */
    val PATTERNS = listOf(
        Pattern("paper", 0b0000, 0b0000, 0b0000, 0b0000),
        Pattern("light dots", 0b1000, 0b0000, 0b0010, 0b0000),
        Pattern("half", 0b1010, 0b0101, 0b1010, 0b0101),
        Pattern("dark dots", 0b0111, 0b1101, 0b0111, 0b1101),
        Pattern("ink", 0b1111, 0b1111, 0b1111, 0b1111),
        Pattern("thick \\", 0b1100, 0b0110, 0b0011, 0b1001),
        Pattern("thin \\", 0b1000, 0b0100, 0b0010, 0b0001),
        Pattern("dashes", 0b1100, 0b0000, 0b0011, 0b0000),
        Pattern("rows", 0b1111, 0b0000, 0b1111, 0b0000),
        Pattern("thin columns", 0b1000, 0b1000, 0b1000, 0b1000),
        Pattern("columns", 0b1010, 0b1010, 0b1010, 0b1010),
        Pattern("grid", 0b1111, 0b1010, 0b1111, 0b1010),
        Pattern("thick /", 0b0011, 0b0110, 0b1100, 0b1001),
        Pattern("thin /", 0b0001, 0b0010, 0b0100, 0b1000),
        Pattern("crosshatch", 0b1001, 0b0110, 0b0110, 0b1001),
        Pattern("thin rows", 0b1111, 0b0000, 0b0000, 0b0000),
        Pattern("bricks", 0b1111, 0b1000, 0b1111, 0b0010),
        Pattern("plus", 0b0100, 0b1110, 0b0100, 0b0000),
    )

    /** A palette colour and the pattern it gets unless the user changed it. */
    class Swatch(val name: String, val color: Int, val pattern: Int)

    val PALETTE = listOf(
        Swatch("White", 0xFFFFFF, 0),
        Swatch("Silver", 0xC8C8C8, 1),
        Swatch("Gray", 0x888888, 2),
        Swatch("Charcoal", 0x444444, 3),
        Swatch("Black", 0x000000, 4),
        Swatch("Red", 0xE53935, 5),
        Swatch("Orange", 0xFB8C00, 6),
        Swatch("Yellow", 0xFDD835, 7),
        Swatch("Green", 0x43A047, 8),
        Swatch("Lime", 0x9CCC65, 15),
        Swatch("Teal", 0x00ACC1, 9),
        Swatch("Blue", 0x1E88E5, 10),
        Swatch("Navy", 0x1A237E, 11),
        Swatch("Purple", 0x8E24AA, 12),
        Swatch("Pink", 0xF06292, 13),
        Swatch("Brown", 0x795548, 14),
    ).map { Swatch(it.name, it.color or OPAQUE, it.pattern) }

    val DEFAULT_PATTERNS: List<Int> = PALETTE.map { it.pattern }

    private const val OPAQUE = 0xFF000000.toInt()

    /**
     * [px] in ink and paper. [patterns] picks each palette colour's pattern (an
     * index into [PATTERNS]); [edges] draws an ink line where two patterned
     * areas meet, so their shapes don't dissolve into each other.
     */
    fun dither(px: IntArray, n: Int, ink: Int, paper: Int, patterns: List<Int> = DEFAULT_PATTERNS, edges: Boolean = true): IntArray {
        val label = IntArray(px.size) { if (RetroPixels.isOpaque(px[it])) nearest(px[it]) else -1 }
        despeckle(label, n)
        // On a dark theme ink is the bright colour: the patterns turn inside out,
        // so light colours get the dense fills and the picture doesn't come out negative.
        val inkDark = RetroPixels.luminance(ink) <= RetroPixels.luminance(paper)
        fun patternOf(l: Int) = PATTERNS[patterns.getOrElse(l) { PALETTE[l].pattern }.coerceIn(0, PATTERNS.lastIndex)]

        val out = IntArray(px.size)
        for (i in px.indices) {
            val l = label[i]
            if (l < 0) continue
            val on = patternOf(l).bit(i % n, i / n) == inkDark
            out[i] = if (on) ink else paper
        }
        if (edges) {
            val edge = BooleanArray(px.size)
            for (i in px.indices) {
                val a = label[i]
                if (a < 0) continue
                val x = i % n
                val y = i / n
                for (j in intArrayOf(if (x + 1 < n) i + 1 else -1, if (y + 1 < n) i + n else -1)) {
                    if (j < 0) continue
                    val b = label[j]
                    if (b < 0 || a == b) continue
                    val pa = patternOf(a)
                    val pb = patternOf(b)
                    // Solid fills already show their edge; where a texture is involved, draw it.
                    if (pa.coverage in 0.01f..0.99f || pb.coverage in 0.01f..0.99f) {
                        // On the inkier side: a small light shape (a white play button on red)
                        // keeps all of its pixels and gets an outline around it, not inside it.
                        val inkA = if (inkDark) pa.coverage else 1f - pa.coverage
                        val inkB = if (inkDark) pb.coverage else 1f - pb.coverage
                        val at = if (inkA >= inkB) i else j
                        out[at] = ink
                        edge[at] = true
                    }
                }
            }
            // A ring of clean paper inside a texture, along its outline: without it the
            // stripes run straight into the line and swallow small shapes.
            val clear = if (inkDark) paper else ink
            for (i in px.indices) {
                val l = label[i]
                if (l < 0 || edge[i]) continue
                val c = patternOf(l).coverage
                if (c < 0.01f || c > 0.99f) continue
                val x = i % n
                val y = i / n
                val touches = (x > 0 && edge[i - 1] && label[i - 1] == l) || (x + 1 < n && edge[i + 1] && label[i + 1] == l) ||
                    (y > 0 && edge[i - n] && label[i - n] == l) || (y + 1 < n && edge[i + n] && label[i + n] == l)
                if (touches) out[i] = clear
            }
        }
        return out
    }

    /** The palette colour closest to [c] ("redmean" distance: cheap and close to how the eye sees it). */
    fun nearest(c: Int): Int {
        var best = 0
        var bestD = Long.MAX_VALUE
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        for ((k, s) in PALETTE.withIndex()) {
            val pr = (s.color shr 16) and 0xFF; val pg = (s.color shr 8) and 0xFF; val pb = s.color and 0xFF
            val rm = (r + pr) / 2
            val dr = (r - pr).toLong(); val dg = (g - pg).toLong(); val db = (b - pb).toLong()
            val d = ((512 + rm) * dr * dr shr 8) + 4 * dg * dg + ((767 - rm) * db * db shr 8)
            if (d < bestD) { bestD = d; best = k }
        }
        return best
    }

    /** One pass: a pixel whose four neighbours mostly agree on another colour joins them (stray blend pixels). */
    private fun despeckle(label: IntArray, n: Int) {
        val copy = label.copyOf()
        for (i in label.indices) {
            val own = copy[i]
            if (own < 0) continue
            val x = i % n
            val y = i / n
            val around = listOfNotNull(
                if (x > 0) copy[i - 1] else null, if (x + 1 < n) copy[i + 1] else null,
                if (y > 0) copy[i - n] else null, if (y + 1 < n) copy[i + n] else null,
            ).filter { it >= 0 }
            if (own in around) continue
            val major = around.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: continue
            if (major.value >= 3) label[i] = major.key
        }
    }
}
