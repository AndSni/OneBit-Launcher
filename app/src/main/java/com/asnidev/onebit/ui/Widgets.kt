package com.asnidev.onebit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.asnidev.onebit.data.Pattern
import kotlin.math.roundToInt

// ---------------------------------------------------------------- text

@Composable
fun RetroText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalRetro.current.ink,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    style: TextStyle = LocalRetro.current.text,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        softWrap = maxLines > 1,
    )
}

/** Text inverted on press, the way menus and lists answer a touch. */
@Composable
fun InvertingRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.(fg: Color) -> Unit,
) {
    val r = LocalRetro.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier
            .clickable(source, indication = null, enabled = enabled, onClick = onClick)
            .background(if (pressed) r.ink else r.paper),
        verticalAlignment = Alignment.CenterVertically,
    ) { content(if (pressed) r.paper else r.ink) }
}

// ---------------------------------------------------------------- frames

/** One-pixel ink border; with [shadow], a hard drop shadow down and right. */
@Composable
fun Modifier.frame(shadow: Boolean = false, thick: Boolean = false): Modifier {
    val r = LocalRetro.current
    val shadowed = if (shadow) drawBehind {
        val s = r.upx * 2f
        drawRect(r.ink, topLeft = Offset(s, s), size = size)
    } else this
    return shadowed.border(if (thick) r.u * 2 else r.u, r.ink).background(r.paper).padding(if (thick) r.u * 2 else r.u)
}

/** A window: title bar, an optional info line, content and optional scroll bars. */
@Composable
fun RetroWindow(
    title: String,
    modifier: Modifier = Modifier,
    info: (@Composable RowScope.() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onFull: (() -> Unit)? = null,
    vScroll: ScrollInfo? = null,
    hScroll: ScrollInfo? = null,
    titleModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val r = LocalRetro.current
    Column(modifier.frame(shadow = r.striped)) {
        TitleBar(title, onClose, onFull, titleModifier)
        if (info != null) {
            Row(
                Modifier.fillMaxWidth().height(r.line + r.u * 2).padding(horizontal = r.u * 4),
                verticalAlignment = Alignment.CenterVertically,
                content = info,
            )
            if (r.striped) {
                HLine()
                Spacer(Modifier.height(r.u))
            }
            HLine()
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.weight(1f).fillMaxHeight(), content = content)
            if (vScroll != null) {
                VLine()
                Scrollbar(vScroll, vertical = true, Modifier.width(r.bar).fillMaxHeight())
            }
        }
        if (hScroll != null) {
            HLine()
            Row(Modifier.fillMaxWidth().height(r.bar)) {
                Scrollbar(hScroll, vertical = false, Modifier.weight(1f).fillMaxHeight())
                if (vScroll != null) {
                    VLine()
                    Box(Modifier.width(r.bar).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
fun HLine(modifier: Modifier = Modifier) {
    val r = LocalRetro.current
    Box(modifier.fillMaxWidth().height(r.u).background(r.ink))
}

@Composable
fun VLine(modifier: Modifier = Modifier) {
    val r = LocalRetro.current
    Box(modifier.width(r.u).fillMaxHeight().background(r.ink))
}

@Composable
private fun TitleBar(title: String, onClose: (() -> Unit)?, onFull: (() -> Unit)?, modifier: Modifier) {
    val r = LocalRetro.current
    Column(modifier.fillMaxWidth()) {
        if (r.striped) StripedTitle(title, onClose) else BoxedTitle(title, onClose, onFull)
        HLine()
    }
}

/** Patterned bar between a close box and a full box, the title on a paper label. */
@Composable
private fun BoxedTitle(title: String, onClose: (() -> Unit)?, onFull: (() -> Unit)?) {
    val r = LocalRetro.current
    Row(Modifier.fillMaxWidth().height(r.bar)) {
        GlyphBox(onClose) { closeGlyph(it) }
        VLine()
        Box(
            Modifier.weight(1f).fillMaxHeight().pattern(Pattern.GRAY50, r.ink, r.paper),
            contentAlignment = Alignment.Center,
        ) {
            RetroText(title, Modifier.background(r.paper).padding(horizontal = r.u * 3))
        }
        VLine()
        GlyphBox(onFull) { fullGlyph(it) }
    }
}

/** Pinstripes across the bar, broken by the close box and the title. */
@Composable
private fun StripedTitle(title: String, onClose: (() -> Unit)?) {
    val r = LocalRetro.current
    Box(
        Modifier.fillMaxWidth().height(r.bar).drawBehind {
            val u = r.upx.toFloat()
            var y = u * 3
            while (y + u <= size.height - u * 2) {
                drawRect(r.ink, Offset(u * 2, y), Size(size.width - u * 4, u))
                y += u * 2
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.align(Alignment.CenterStart).padding(start = r.u * 6)
                .background(r.paper).padding(horizontal = r.u * 2)
                .size(r.bar - r.u * 4)
                .then(if (onClose != null) Modifier.clickable(onClick = onClose) else Modifier)
                .border(r.u, r.ink),
        )
        RetroText(title, Modifier.background(r.paper).padding(horizontal = r.u * 6))
    }
}

@Composable
private fun GlyphBox(onClick: (() -> Unit)?, glyph: DrawScope.(Color) -> Unit) {
    val r = LocalRetro.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Canvas(
        Modifier.size(r.bar)
            .then(if (onClick != null) Modifier.clickable(source, null, onClick = onClick) else Modifier)
            .background(if (pressed) r.ink else r.paper),
    ) { glyph(if (pressed) r.paper else r.ink) }
}

private fun DrawScope.closeGlyph(c: Color) {
    val s = size.minDimension
    val u = (s / 16f).coerceAtLeast(1f)
    // A small box with a corner tick, like the close box of the boxed-window desktops.
    drawRect(c, Offset(s * 0.25f, s * 0.25f), Size(s * 0.5f, s * 0.5f), style = Stroke(u))
    drawRect(c, Offset(s * 0.25f, s * 0.25f), Size(s * 0.2f, s * 0.2f))
}

private fun DrawScope.fullGlyph(c: Color) {
    val s = size.minDimension
    val u = (s / 16f).coerceAtLeast(1f)
    val p = Path().apply {
        moveTo(s / 2, s * 0.2f); lineTo(s * 0.8f, s / 2); lineTo(s / 2, s * 0.8f); lineTo(s * 0.2f, s / 2); close()
    }
    drawPath(p, c, style = Stroke(u))
}

// ---------------------------------------------------------------- scroll bars

/**
 * [position] 0..1 of the scrollable range, [visible] the share on screen (thumb
 * size). [step] moves one notch (-1/+1); [jump] scrolls to a 0..1 position.
 */
class ScrollInfo(
    val position: Float,
    val visible: Float,
    val step: (Int) -> Unit,
    val jump: (Float) -> Unit,
)

@Composable
fun Scrollbar(info: ScrollInfo, vertical: Boolean, modifier: Modifier) {
    val r = LocalRetro.current
    val track = if (r.striped) Pattern.GRAY50 else Pattern.GRAY25
    // The track takes the room between the arrows; built with weights below.
    if (vertical) {
        Column(modifier) {
            GlyphBox({ info.step(-1) }) { arrowGlyph(it, 0) }
            HLine()
            Track(info, vertical = true, Modifier.weight(1f).fillMaxWidth(), track)
            HLine()
            GlyphBox({ info.step(1) }) { arrowGlyph(it, 2) }
        }
    } else {
        Row(modifier) {
            GlyphBox({ info.step(-1) }) { arrowGlyph(it, 3) }
            VLine()
            Track(info, vertical = false, Modifier.weight(1f).fillMaxHeight(), track)
            VLine()
            GlyphBox({ info.step(1) }) { arrowGlyph(it, 1) }
        }
    }
}

@Composable
private fun Track(info: ScrollInfo, vertical: Boolean, modifier: Modifier, track: Pattern) {
    val r = LocalRetro.current
    var length by remember { mutableFloatStateOf(0f) }
    val thumbMin = with(LocalDensity.current) { r.bar.toPx() }
    val thumb = (length * info.visible.coerceIn(0f, 1f)).coerceAtLeast(thumbMin).coerceAtMost(length)
    val room = (length - thumb).coerceAtLeast(0f)
    val at = room * info.position.coerceIn(0f, 1f)
    // Read through a lambda so a drag that started stays on the latest position.
    val latest by rememberUpdatedState(info)
    Box(
        modifier
            .onSizeChanged { length = if (vertical) it.height.toFloat() else it.width.toFloat() }
            .pattern(track, r.ink, r.paper)
            .pointerInput(vertical) {
                detectTapGestures { o ->
                    val p = if (vertical) o.y else o.x
                    val l = if (vertical) size.height else size.width
                    // Page toward the tap, like clicking the gray area.
                    val cur = latest.position
                    val dir = if (p / l > cur) 1 else -1
                    latest.jump((cur + dir * latest.visible).coerceIn(0f, 1f))
                }
            },
    ) {
        if (length > 0f) {
            var dragAt by remember { mutableFloatStateOf(0f) }
            Box(
                Modifier
                    .offset { if (vertical) IntOffset(0, at.roundToInt()) else IntOffset(at.roundToInt(), 0) }
                    .then(
                        if (vertical) Modifier.fillMaxWidth().height(with(LocalDensity.current) { thumb.toDp() })
                        else Modifier.fillMaxHeight().width(with(LocalDensity.current) { thumb.toDp() }),
                    )
                    .border(r.u, r.ink)
                    .background(r.paper)
                    .pointerInput(vertical) {
                        detectDragGestures(onDragStart = { dragAt = latest.position * room }) { change, d ->
                            change.consume()
                            dragAt = (dragAt + if (vertical) d.y else d.x).coerceIn(0f, room)
                            if (room > 0f) latest.jump(dragAt / room)
                        }
                    },
            )
        }
    }
}

/** A triangle pointing up (0), right (1), down (2) or left (3). */
private fun DrawScope.arrowGlyph(c: Color, dir: Int) {
    val s = size.minDimension
    val u = (s / 16f).coerceAtLeast(1f)
    val p = Path().apply {
        moveTo(s / 2, s * 0.25f); lineTo(s * 0.78f, s * 0.62f); lineTo(s * 0.62f, s * 0.62f)
        lineTo(s * 0.62f, s * 0.78f); lineTo(s * 0.38f, s * 0.78f); lineTo(s * 0.38f, s * 0.62f)
        lineTo(s * 0.22f, s * 0.62f); close()
    }
    val cx = size.width / 2
    val cy = size.height / 2
    drawContext.transform.rotate(dir * 90f, Offset(cx, cy))
    drawContext.transform.translate(cx - s / 2, cy - s / 2)
    drawPath(p, c, style = Stroke(u))
    drawContext.transform.translate(-(cx - s / 2), -(cy - s / 2))
    drawContext.transform.rotate(-dir * 90f, Offset(cx, cy))
}

// ---------------------------------------------------------------- controls

/** A push button; inverted while pressed. [default] draws the thick border of the default choice. */
@Composable
fun RetroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    default: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable (fg: Color) -> Unit)? = null,
) {
    val r = LocalRetro.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = if (r.striped) RoundedCornerShape(r.u * 5) else RoundedCornerShape(0.dp)
    val fg = if (pressed) r.paper else r.ink
    Row(
        modifier
            .clip(shape)
            .border(if (default) r.u * 3 else r.u, r.ink, shape)
            .background(if (pressed) r.ink else r.paper, shape)
            .combinedClick(source, enabled, onClick, onLongClick)
            .padding(horizontal = r.u * 6, vertical = r.u * 3),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading(fg)
            if (text.isNotEmpty()) Spacer(Modifier.width(r.u * 4))
        }
        if (text.isNotEmpty()) RetroText(text, color = if (enabled) fg else fg.copy(alpha = 0.4f), align = TextAlign.Center)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClick(
    source: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): Modifier = combinedClickable(
    interactionSource = source,
    indication = null,
    enabled = enabled,
    onLongClick = onLongClick,
    onClick = onClick,
)

/** A square box crossed through when on. */
@Composable
fun RetroCheckbox(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val r = LocalRetro.current
    InvertingRow({ onChange(!checked) }, modifier.fillMaxWidth().padding(vertical = r.u)) { fg ->
        val bg = if (fg == r.ink) r.paper else r.ink
        Canvas(Modifier.padding(r.u * 2).size(r.line - r.u * 2)) {
            val u = r.upx.toFloat()
            drawRect(bg)
            drawRect(fg, style = Stroke(u * 2), topLeft = Offset(u, u), size = Size(size.width - u * 2, size.height - u * 2))
            if (checked) {
                drawLine(fg, Offset(u * 2, u * 2), Offset(size.width - u * 2, size.height - u * 2), u * 2)
                drawLine(fg, Offset(size.width - u * 2, u * 2), Offset(u * 2, size.height - u * 2), u * 2)
            }
        }
        Spacer(Modifier.width(r.u * 4))
        RetroText(label, Modifier.weight(1f), color = fg, maxLines = 2)
    }
}

/** A round button with a dot when chosen. */
@Composable
fun RetroRadio(label: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val r = LocalRetro.current
    InvertingRow(onSelect, modifier.fillMaxWidth().padding(vertical = r.u)) { fg ->
        Canvas(Modifier.padding(r.u * 2).size(r.line - r.u * 2)) {
            val u = r.upx.toFloat()
            drawCircle(fg, radius = size.minDimension / 2 - u, style = Stroke(u * 2))
            if (selected) drawCircle(fg, radius = size.minDimension / 4)
        }
        Spacer(Modifier.width(r.u * 4))
        RetroText(label, Modifier.weight(1f), color = fg, maxLines = 2)
    }
}

/** "label   [◀] value [▶]" */
@Composable
fun Stepper(label: String, value: String, onStep: (Int) -> Unit, modifier: Modifier = Modifier) {
    val r = LocalRetro.current
    Row(modifier.fillMaxWidth().padding(vertical = r.u * 2), verticalAlignment = Alignment.CenterVertically) {
        RetroText(label, Modifier.weight(1f))
        Box(Modifier.border(r.u, r.ink)) { GlyphBox({ onStep(-1) }) { arrowGlyph(it, 3) } }
        RetroText(value, Modifier.widthIn(min = r.line * 2.5f).padding(horizontal = r.u * 4), align = TextAlign.Center)
        Box(Modifier.border(r.u, r.ink)) { GlyphBox({ onStep(1) }) { arrowGlyph(it, 1) } }
    }
}

/** One-line text field on an underline. */
@Composable
fun RetroField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focus: FocusRequester? = null,
    onDone: (() -> Unit)? = null,
) {
    val r = LocalRetro.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .drawBehind { drawRect(r.ink, Offset(0f, size.height - r.upx), Size(size.width, r.upx.toFloat())) }
            .padding(vertical = r.u * 2),
        textStyle = r.text,
        singleLine = true,
        cursorBrush = SolidColor(r.ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
    )
}

/** A section heading in settings: text on a rule. */
@Composable
fun Heading(text: String) {
    val r = LocalRetro.current
    Column(Modifier.fillMaxWidth().padding(top = r.u * 8, bottom = r.u * 2)) {
        RetroText(text.uppercase(), Modifier.background(r.ink).padding(horizontal = r.u * 3), color = r.paper)
        HLine()
    }
}

/**
 * A modal box in the middle of the screen, double-bordered like an alert.
 * Tapping outside it calls [onDismiss].
 */
@Composable
fun RetroDialog(
    onDismiss: () -> Unit,
    maxWidth: Dp = 360.dp,
    alignment: Alignment = Alignment.Center,
    content: @Composable ColumnScope.() -> Unit,
) {
    val r = LocalRetro.current
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .padding(r.u * 12),
        contentAlignment = alignment,
    ) {
        Column(
            Modifier.widthIn(max = maxWidth).fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { } } // taps inside don't dismiss
                .frame(shadow = true)
                .padding(r.u * 2)
                .border(r.u * 2, r.ink)
                .padding(r.u * 8),
            content = content,
        )
    }
}
