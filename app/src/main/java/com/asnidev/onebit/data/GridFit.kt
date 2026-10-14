package com.asnidev.onebit.data

import kotlin.math.floor

/**
 * How many columns and rows the home window shows. The icon size wins: when
 * cells would be too small for it, fewer columns or rows are shown (the saved
 * values are the most there can be), and the apps that no longer fit move on
 * to the next page. All sizes in the same unit (dp).
 */
object GridFit {

    /** Cell room an icon of side [icon] needs, with its name, as width to height. */
    fun cellNeed(icon: Float, labels: LabelMode, line: Float, u: Float): Pair<Float, Float> = when (labels) {
        LabelMode.UNDER -> icon / 0.9f to icon + line + u * 8
        LabelMode.BESIDE -> icon / 0.45f to icon + u * 4
        LabelMode.HIDDEN -> icon / 0.9f to icon + u * 4
    }

    fun fit(columns: Int, rows: Int, width: Float, height: Float, icon: Float, labels: LabelMode, line: Float, u: Float): Pair<Int, Int> {
        // Not measured yet: show what was asked for.
        if (width <= 0f || height <= 0f) return columns to rows
        val (w, h) = cellNeed(icon, labels, line, u)
        val c = floor(width / w).toInt().coerceIn(1, columns)
        val r = floor(height / h).toInt().coerceIn(1, rows)
        return c to r
    }
}
