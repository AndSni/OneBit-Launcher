package com.asnidev.onebit.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asnidev.onebit.R
import com.asnidev.onebit.data.FontChoice
import com.asnidev.onebit.data.Pattern
import com.asnidev.onebit.data.Theme
import com.asnidev.onebit.data.WindowStyle
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The look everything is drawn with. [u] is one "screen pixel" of the old
 * machines: a whole number of real pixels, so lines and patterns stay crisp.
 */
@Immutable
class Retro(
    val theme: Theme,
    val ink: Color,
    val paper: Color,
    val desk: Color,
    val deskInk: Color,
    val u: Dp,
    /** [u] in real pixels. */
    val upx: Int,
    val text: TextStyle,
    /** Height of one line of text, the unit for bars, boxes and rows. */
    val line: Dp,
) {
    val window: WindowStyle get() = theme.window
    val striped: Boolean get() = theme.window == WindowStyle.STRIPED

    /** Bars (title, menu, scroll) are a text line plus a little air. */
    val bar: Dp get() = line + u * 4
}

val LocalRetro = staticCompositionLocalOf<Retro> { error("no Retro theme") }

fun FontChoice.family(): FontFamily = when (this) {
    FontChoice.VGA -> FontFamily(Font(R.font.px437_ibm_vga))
    FontChoice.PIXELIFY -> FontFamily(Font(R.font.pixelify_sans))
    FontChoice.VT323 -> FontFamily(Font(R.font.vt323_regular))
    FontChoice.SILKSCREEN -> FontFamily(Font(R.font.silkscreen_regular))
    FontChoice.PRESS_START -> FontFamily(Font(R.font.pressstart2p_regular))
    FontChoice.SYSTEM_MONO -> FontFamily.Monospace
    FontChoice.SYSTEM -> FontFamily.SansSerif
}

/** Some fonts are drawn much larger or smaller than others at the same size. */
private fun FontChoice.scale(): Float = when (this) {
    FontChoice.VT323 -> 1.15f
    FontChoice.PRESS_START -> 0.62f
    FontChoice.SILKSCREEN -> 0.85f
    else -> 1f
}

@Composable
fun rememberRetro(theme: Theme): Retro {
    val density = LocalDensity.current
    return remember(theme, density) {
        val upx = max(1, density.density.roundToInt())
        val u = (upx / density.density).dp
        val size = (theme.textSize * theme.font.scale()).sp
        val line = with(density) { (theme.textSize * 1.25f).sp.toDp() }
        val p = theme.palette
        Retro(
            theme = theme,
            ink = Color(p.ink),
            paper = Color(p.paper),
            desk = Color(p.desk),
            deskInk = Color(p.deskInk),
            u = u,
            upx = upx,
            text = TextStyle(
                fontFamily = theme.font.family(),
                fontSize = size,
                lineHeight = (theme.textSize * 1.25f).sp,
                color = Color(p.ink),
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            ),
            line = line,
        )
    }
}

/** An 8×8 pattern tile scaled up by [upx], repeating. Null for a solid fill. */
fun patternBrush(pattern: Pattern, fg: Color, bg: Color, upx: Int): ShaderBrush? {
    if (pattern == Pattern.SOLID) return null
    val side = 8 * upx
    val px = IntArray(side * side) { i ->
        val x = (i % side) / upx
        val y = (i / side) / upx
        if (pattern.bit(x, y)) fg.toArgb() else bg.toArgb()
    }
    val bitmap = Bitmap.createBitmap(px, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
    return ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}

/** Fills the background with [pattern] in [fg] on [bg]. */
@Composable
fun Modifier.pattern(pattern: Pattern, fg: Color, bg: Color): Modifier {
    val upx = LocalRetro.current.upx
    val brush = remember(pattern, fg, bg, upx) { patternBrush(pattern, fg, bg, upx) }
    return drawBehind {
        drawRect(bg)
        if (brush != null) drawRect(brush)
    }
}
