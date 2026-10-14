package com.asnidev.onebit.icons

import android.content.Context

/**
 * A bundled library of 1-bit pixel icons (assets/glyphs, made by
 * tools/make_glyphs.py): [side]×[side] each, one bit per pixel, rows of
 * [side]/8 bytes, high bit on the left.
 */
class GlyphLibrary(val id: String, val title: String, val side: Int, val names: List<String>, private val data: ByteArray) {

    private val bytesPerGlyph = side * side / 8
    private val index = names.withIndex().associate { it.value to it.index }

    val size: Int get() = names.size

    fun has(name: String): Boolean = name in index

    /** The glyph called [name] as row-major bits, or null if there is none. */
    fun bits(name: String): BooleanArray? {
        val i = index[name] ?: return null
        val off = i * bytesPerGlyph
        val rowBytes = side / 8
        return BooleanArray(side * side) { p ->
            val x = p % side
            val y = p / side
            val b = data[off + y * rowBytes + x / 8].toInt()
            (b shr (7 - x % 8)) and 1 == 1
        }
    }

    companion object {
        /** pixelarticons (MIT): interface icons drawn on a 24×24 grid. */
        const val PIXELART = "px"

        /** Kenney's 1-Bit Pack (CC0): 16×16 game tiles; named by their place in the sheet. */
        const val KENNEY = "kn"

        fun load(context: Context, id: String): GlyphLibrary {
            val (file, title, side) = when (id) {
                PIXELART -> Triple("pixelarticons", "Pixel icons", 24)
                else -> Triple("kenney", "1-bit tiles", 16)
            }
            val names = context.assets.open("glyphs/$file.txt").bufferedReader().readLines().filter { it.isNotBlank() }
            val data = context.assets.open("glyphs/$file.bin").readBytes()
            return GlyphLibrary(id, title, side, names, data)
        }
    }
}

/**
 * A chosen icon, saved as text: "glyph:<library>:<name>" for a bundled glyph,
 * "pack:<package>:<drawable>" for a drawable from an installed icon pack.
 */
sealed interface IconRef {
    fun encode(): String

    data class Glyph(val library: String, val name: String) : IconRef {
        override fun encode() = "glyph:$library:$name"
    }

    data class Pack(val pkg: String, val drawable: String) : IconRef {
        override fun encode() = "pack:$pkg:$drawable"
    }

    companion object {
        fun decode(s: String): IconRef? {
            val parts = s.split(':', limit = 3)
            if (parts.size != 3 || parts[1].isEmpty() || parts[2].isEmpty()) return null
            return when (parts[0]) {
                "glyph" -> Glyph(parts[1], parts[2])
                "pack" -> Pack(parts[1], parts[2])
                else -> null
            }
        }
    }
}
