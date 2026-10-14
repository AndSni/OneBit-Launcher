package com.asnidev.onebit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/** One menu entry; [checked] non-null draws a check mark column. Null in a list draws a divider. */
class MenuItem(
    val label: String,
    val checked: Boolean? = null,
    val enabled: Boolean = true,
    val action: () -> Unit,
)

class Menu(val title: String, val items: List<MenuItem?>)

/** The bar across the top: menu titles left, an optional [right] text (the clock). */
@Composable
fun MenuBar(menus: List<Menu>, right: String?, modifier: Modifier = Modifier) {
    val r = LocalRetro.current
    var open by remember { mutableIntStateOf(-1) }
    val xs = remember { IntArray(menus.size) }
    Column(modifier.fillMaxWidth().background(r.paper)) {
        Row(Modifier.fillMaxWidth().height(r.bar).padding(horizontal = r.u * 4), verticalAlignment = Alignment.CenterVertically) {
            menus.forEachIndexed { i, menu ->
                val active = open == i
                Box(
                    Modifier.fillMaxHeight()
                        .onGloballyPositioned { xs[i] = it.positionInParent().x.toInt() }
                        .clickable(remember { MutableInteractionSource() }, null) { open = if (active) -1 else i }
                        .background(if (active) r.ink else r.paper)
                        .padding(horizontal = r.u * 6),
                    contentAlignment = Alignment.Center,
                ) { RetroText(menu.title, color = if (active) r.paper else r.ink) }
            }
            Spacer(Modifier.weight(1f))
            if (right != null) RetroText(right, Modifier.padding(horizontal = r.u * 4))
        }
        HLine()
    }
    if (open in menus.indices) {
        val density = LocalDensity.current
        val y = with(density) { r.bar.roundToPx() }
        Popup(
            offset = IntOffset(xs[open], y),
            onDismissRequest = { open = -1 },
            properties = PopupProperties(focusable = true),
        ) {
            Column(Modifier.width(IntrinsicSize.Max).widthIn(min = r.line * 8).frame(shadow = true)) {
                for (item in menus[open].items) {
                    if (item == null) {
                        HLine(Modifier.padding(vertical = r.u * 2).background(r.paper).let { it })
                        continue
                    }
                    InvertingRow(
                        onClick = { open = -1; item.action() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = r.u),
                        enabled = item.enabled,
                    ) { fg ->
                        val c = if (item.enabled) fg else fg.copy(alpha = 0.4f)
                        RetroText(if (item.checked == true) "✓" else " ", Modifier.width(r.line).padding(start = r.u * 2), color = c)
                        RetroText(item.label, Modifier.padding(end = r.u * 8), color = c)
                    }
                }
            }
        }
    }
}
