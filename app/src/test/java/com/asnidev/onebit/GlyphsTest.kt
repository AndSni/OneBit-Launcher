package com.asnidev.onebit

import com.asnidev.onebit.icons.GlyphLibrary
import com.asnidev.onebit.icons.IconRef
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlyphsTest {
    @Test
    fun bitsDecodeRowMajorHighBitLeft() {
        // One 8x8 glyph: a diagonal from top-left.
        val data = ByteArray(8) { (0x80 ushr it).toByte() }
        val lib = GlyphLibrary("t", "Test", 8, listOf("diag"), data)
        val bits = lib.bits("diag")!!
        for (y in 0 until 8) for (x in 0 until 8) assertEquals(x == y, bits[y * 8 + x])
        assertNull(lib.bits("missing"))
    }

    @Test
    fun secondGlyphStartsAfterTheFirst() {
        val data = ByteArray(16).also { it[8] = 0xFF.toByte() }
        val lib = GlyphLibrary("t", "Test", 8, listOf("a", "b"), data)
        assertArrayEquals(BooleanArray(8) { true }, lib.bits("b")!!.copyOfRange(0, 8))
        assertEquals(0, lib.bits("a")!!.count { it })
    }

    @Test
    fun iconRefsRoundTrip() {
        val refs = listOf(IconRef.Glyph("px", "home"), IconRef.Pack("com.donnnno.arcticons", "chrome"))
        for (r in refs) assertEquals(r, IconRef.decode(r.encode()))
        assertNull(IconRef.decode("nonsense"))
        assertNull(IconRef.decode("glyph::x"))
        // Drawable names never hold a colon, but the part after the second one is kept whole anyway.
        assertEquals(IconRef.Pack("p", "a:b"), IconRef.decode("pack:p:a:b"))
    }
}
