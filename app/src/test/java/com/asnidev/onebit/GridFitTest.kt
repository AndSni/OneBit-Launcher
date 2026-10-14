package com.asnidev.onebit

import com.asnidev.onebit.data.GridFit
import com.asnidev.onebit.data.LabelMode
import org.junit.Assert.assertEquals
import org.junit.Test

class GridFitTest {
    private val line = 20f
    private val u = 1f

    @Test
    fun roomyWindowKeepsTheSetGrid() {
        assertEquals(2 to 8, GridFit.fit(2, 8, 400f, 800f, 50f, LabelMode.UNDER, line, u))
    }

    @Test
    fun biggerIconsShowFewerRows() {
        // Each row needs 75 + 20 + 8 = 103: 600 / 103 = 5 rows.
        assertEquals(2 to 5, GridFit.fit(2, 8, 400f, 600f, 75f, LabelMode.UNDER, line, u))
        // Without names a row needs 79: 7 rows.
        assertEquals(2 to 7, GridFit.fit(2, 8, 400f, 600f, 75f, LabelMode.HIDDEN, line, u))
    }

    @Test
    fun widthCanLimitColumnsToo() {
        // Names beside: a cell needs 100 / 0.45 = 222 wide; 400 holds one.
        assertEquals(1 to 6, GridFit.fit(2, 8, 400f, 625f, 100f, LabelMode.BESIDE, line, u))
    }

    @Test
    fun alwaysAtLeastOneCellAndUnmeasuredShowsTheSetting() {
        assertEquals(1 to 1, GridFit.fit(4, 6, 10f, 10f, 200f, LabelMode.UNDER, line, u))
        assertEquals(4 to 6, GridFit.fit(4, 6, 0f, 0f, 50f, LabelMode.UNDER, line, u))
    }
}
