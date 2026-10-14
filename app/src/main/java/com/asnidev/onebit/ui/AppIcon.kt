package com.asnidev.onebit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.asnidev.onebit.LauncherViewModel
import com.asnidev.onebit.data.AppKey
import com.asnidev.onebit.data.IconStyle
import com.asnidev.onebit.icons.IconRef
import com.asnidev.onebit.icons.IconSource
import com.asnidev.onebit.icons.IconSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

val LocalVm = staticCompositionLocalOf<LauncherViewModel> { error("no view model") }

/** Converted icons this many pixels per side at [small] size: list rows and taskbar. */
fun smallRes(res: Int): Int = max(16, res / 2)

/**
 * The real-pixel side an icon is drawn at: the whole multiple of its own
 * pixels nearest [targetPx], but never over [limitPx].
 */
fun snappedSide(targetPx: Int, style: IconStyle, res: Int, limitPx: Int = targetPx): Int {
    if (style == IconStyle.ORIGINAL) return minOf(targetPx, limitPx)
    val k = ((targetPx + res / 2) / res).coerceAtMost(limitPx / res)
    return res * max(1, k)
}

/**
 * An app's icon in the chosen retro style, at most [size] square and snapped
 * so every icon pixel is a whole number of screen pixels. [selected] draws it
 * inverted, as while pressed. [style] overrides the saved style (previews).
 */
@Composable
fun AppIcon(
    key: AppKey,
    size: Dp,
    selected: Boolean = false,
    small: Boolean = false,
    style: IconStyle? = null,
    /** Room available; the icon may round up toward it, never past it. */
    limit: Dp = size,
    modifier: Modifier = Modifier,
) {
    val vm = LocalVm.current
    val prefs by vm.prefs.collectAsState()
    val generation by vm.repo.generation.collectAsState()
    val tag = vm.iconTag(key, prefs)
    RenderedIcon(
        id = "${key.encode()}@$generation|$tag",
        style = style ?: prefs.styleFor(key),
        glyph = tag.startsWith("glyph:"),
        size = size, selected = selected, small = small, limit = limit, modifier = modifier,
    ) { vm.iconSource(key, prefs) }
}

/** A choosable icon (bundled glyph or pack drawable) as it would look in the current style. */
@Composable
fun RefIcon(ref: IconRef, size: Dp, modifier: Modifier = Modifier) {
    val vm = LocalVm.current
    val prefs by vm.prefs.collectAsState()
    RenderedIcon(
        id = ref.encode(), style = prefs.iconStyle, glyph = ref is IconRef.Glyph,
        size = size, selected = false, small = false, limit = size, modifier = modifier, prewarm = false,
    ) { vm.refSource(ref) }
}

@Composable
private fun RenderedIcon(
    id: String,
    style: IconStyle,
    /** A bundled pixel icon: always drawn with crisp pixels, even in the Original style. */
    glyph: Boolean,
    size: Dp,
    selected: Boolean,
    small: Boolean,
    limit: Dp,
    modifier: Modifier,
    /** Also draw the pressed look in the background, so a touch shows it at once. */
    prewarm: Boolean = true,
    load: () -> IconSource?,
) {
    val vm = LocalVm.current
    val prefs by vm.prefs.collectAsState()
    val res = if (small) smallRes(prefs.iconRes) else prefs.iconRes
    val pal = prefs.theme.palette
    val spec = IconSpec(
        style, res, pal.ink, pal.paper, prefs.iconOutline, prefs.iconStripBackground, selected,
        // Only the dither looks at the patterns; leaving them out of other specs keeps their cache entries.
        if (style == IconStyle.DITHER) prefs.ditherPatterns else com.asnidev.onebit.icons.PatternDither.DEFAULT_PATTERNS,
        prefs.preferMonochrome,
    )
    val image: ImageBitmap? by produceState(
        // While a new look is drawn, show the other state of the same icon rather than nothing.
        initialValue = vm.icons.cached(id, spec) ?: vm.icons.cached(id, spec.copy(selected = !selected)),
        id, spec,
    ) {
        value = withContext(Dispatchers.Default) {
            val source = load() ?: return@withContext null
            val img = vm.icons.render(id, source, spec)
            if (prewarm) load()?.let { vm.icons.render(id, it, spec.copy(selected = !selected)) }
            img
        }
    }

    val density = LocalDensity.current
    val shown = if (glyph && style == IconStyle.ORIGINAL) IconStyle.MONO else style
    val side = snappedSide(with(density) { size.roundToPx() }, shown, res, with(density) { maxOf(size, limit).roundToPx() })
    Canvas(modifier.size(with(density) { side.toDp() })) {
        val img = image ?: return@Canvas
        drawImage(
            img,
            dstSize = IntSize(side, side),
            filterQuality = if (shown == IconStyle.ORIGINAL) FilterQuality.Low else FilterQuality.None,
        )
    }
}
