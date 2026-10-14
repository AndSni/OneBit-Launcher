package com.asnidev.onebit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asnidev.onebit.BuildConfig
import com.asnidev.onebit.ColorSlot
import com.asnidev.onebit.Dialog
import com.asnidev.onebit.LauncherViewModel
import com.asnidev.onebit.data.AppKey
import com.asnidev.onebit.data.HPos
import com.asnidev.onebit.data.LabelMode
import com.asnidev.onebit.data.VPos
import com.asnidev.onebit.data.IconStyle
import com.asnidev.onebit.data.Prefs
import com.asnidev.onebit.icons.GlyphLibrary
import com.asnidev.onebit.icons.IconPacks
import com.asnidev.onebit.icons.IconRef
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun Dialogs(vm: LauncherViewModel, prefs: Prefs, dialog: Dialog) {
    val dismiss = { vm.dialog = null }
    when (dialog) {
        is Dialog.AppMenu -> AppMenu(vm, prefs, dialog, dismiss)
        is Dialog.TaskbarMenu -> TaskbarMenu(vm, prefs, dialog.index, dismiss)
        is Dialog.IconStyleFor -> IconStyleBox(vm, prefs, dialog.key, dismiss)
        is Dialog.Rename -> RenameBox(vm, dialog.key, dismiss)
        is Dialog.PickColor -> ColorBox(vm, prefs, dialog.slot, dismiss)
        Dialog.About -> AboutBox(dismiss)
        is Dialog.IconPicker -> IconPicker(vm, prefs, dialog.key, dismiss)
        Dialog.IconPackChooser -> IconPackChooser(vm, prefs, dismiss)
        Dialog.Layout -> LayoutBox(vm, prefs, dismiss)
    }
}

/** Full-width choice in a box. */
@Composable
private fun Choice(text: String, onClick: () -> Unit, default: Boolean = false) {
    val r = LocalRetro.current
    RetroButton(text, onClick, Modifier.fillMaxWidth().padding(vertical = r.u * 2), default = default)
}

@Composable
private fun BoxHeader(key: AppKey, vm: LauncherViewModel) {
    val r = LocalRetro.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = r.u * 6)) {
        AppIcon(key, r.line * 2.5f)
        Spacer(Modifier.width(r.u * 8))
        RetroText(vm.label(key), Modifier.weight(1f), maxLines = 2)
    }
}

@Composable
private fun AppMenu(vm: LauncherViewModel, prefs: Prefs, d: Dialog.AppMenu, dismiss: () -> Unit) {
    val r = LocalRetro.current
    val key = d.key
    RetroDialog(dismiss) {
        Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
            BoxHeader(key, vm)
            Choice("Open", { dismiss(); vm.launch(key, null) }, default = true)
            if (key in prefs.home.values) Choice("Remove from home", { dismiss(); vm.removeFromHome(key) })
            else Choice("Add to home", { dismiss(); vm.addToHome(key) })
            Row(
                Modifier.fillMaxWidth().padding(vertical = r.u * 2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(r.u * 4),
            ) {
                RetroText("Taskbar", Modifier.weight(1f))
                for (i in 0 until Prefs.MAX_TASKBAR) {
                    val here = prefs.taskbar.getOrNull(i) == key
                    RetroButton("${i + 1}", {
                        vm.setTaskbar(i, if (here) null else key)
                        if (!here && prefs.taskbarCount <= i) vm.update { it.copy(taskbarCount = i + 1) }
                    }, default = here)
                }
            }
            Choice("Change icon…", { vm.dialog = Dialog.IconPicker(key) })
            if (key in prefs.customIcons) Choice("Automatic icon", { dismiss(); vm.setCustomIcon(key, null) })
            Choice("Icon style…", { vm.dialog = Dialog.IconStyleFor(key) })
            Choice("Rename…", { vm.dialog = Dialog.Rename(key) })
            Choice("Hide from apps", { dismiss(); vm.hide(key) })
            Choice("App info", { dismiss(); vm.repo.openInfo(key) })
            Choice("Uninstall", { dismiss(); vm.repo.uninstall(key) })
        }
    }
}

@Composable
private fun TaskbarMenu(vm: LauncherViewModel, prefs: Prefs, index: Int, dismiss: () -> Unit) {
    val key = prefs.taskbar.getOrNull(index)
    RetroDialog(dismiss) {
        if (key != null) BoxHeader(key, vm) else RetroText("Button ${index + 1} is empty.", Modifier.padding(bottom = LocalRetro.current.u * 6))
        Choice("Choose app…", { dismiss(); vm.pickingFor = index; vm.drawer = 1f }, default = true)
        if (key != null) {
            Choice("Clear button", { dismiss(); vm.setTaskbar(index, null) })
            Choice("App options…", { vm.dialog = Dialog.AppMenu(key, taskbar = index) })
        }
        Choice("Cancel", dismiss)
    }
}

@Composable
private fun IconStyleBox(vm: LauncherViewModel, prefs: Prefs, key: AppKey, dismiss: () -> Unit) {
    val r = LocalRetro.current
    val override = prefs.iconOverrides[key]
    RetroDialog(dismiss) {
        Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
            BoxHeader(key, vm)
            StyleRow(key, "Same as all (${prefs.iconStyle.label})", prefs.iconStyle, override == null) { vm.setIconStyle(key, null) }
            IconStyle.entries.forEach { s ->
                StyleRow(key, s.label, s, override == s) { vm.setIconStyle(key, s) }
            }
            Spacer(Modifier.height(r.u * 6))
            Choice("Done", dismiss, default = true)
        }
    }
}

/** A choice with the icon drawn in that style beside it. */
@Composable
private fun StyleRow(key: AppKey, label: String, style: IconStyle, selected: Boolean, onSelect: () -> Unit) {
    val r = LocalRetro.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { RetroRadio(label, selected, onSelect) }
        AppIcon(key, r.line * 2f, style = style)
    }
}

@Composable
private fun RenameBox(vm: LauncherViewModel, key: AppKey, dismiss: () -> Unit) {
    val r = LocalRetro.current
    var text by remember { mutableStateOf(vm.label(key)) }
    val save = { vm.rename(key, text); dismiss() }
    RetroDialog(dismiss) {
        BoxHeader(key, vm)
        RetroField(text, { text = it.take(40) }, Modifier.fillMaxWidth(), onDone = save)
        RetroText("Empty brings back the app's own name.", Modifier.padding(vertical = r.u * 4), color = r.ink.copy(alpha = 0.6f), maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(r.u * 6)) {
            RetroButton("Cancel", dismiss, Modifier.weight(1f))
            RetroButton("OK", save, Modifier.weight(1f), default = true)
        }
    }
}

/** Extra colours beyond the 16: phosphors, paper and desk tones of the presets. */
private val EXTRA_COLORS = intArrayOf(
    0xFFB000, 0x140C00, 0x7A5200, 0x33FF66, 0x001A08, 0x0E6B2A, 0x00A800, 0x000055,
    0xF4EFE1, 0xD8CFB8, 0x8C826B, 0x2B2B2B, 0xE0E0E0, 0xC0C0C0, 0x808080, 0x404040,
).map { it or 0xFF000000.toInt() }.toIntArray()

@Composable
private fun ColorBox(vm: LauncherViewModel, prefs: Prefs, slot: ColorSlot, dismiss: () -> Unit) {
    val r = LocalRetro.current
    val current = slot.of(prefs.theme.palette)
    var hex by remember(current) { mutableStateOf("%06X".format(current and 0xFFFFFF)) }
    fun set(c: Int) = vm.update { it.copy(theme = it.theme.copy(palette = slot.set(it.theme.palette, c))) }
    RetroDialog(dismiss) {
        RetroText(slot.label, Modifier.padding(bottom = r.u * 6))
        (com.asnidev.onebit.icons.RetroPixels.COLORS16 + EXTRA_COLORS).toList().chunked(8).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(r.u * 3)) {
                row.forEach { c ->
                    val chosen = c == current
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).padding(vertical = r.u * 1.5f)
                            .border(if (chosen) r.u * 3 else r.u, r.ink)
                            .clickable { set(c) }
                            .padding(if (chosen) r.u * 3 else r.u)
                            .background(Color(c)),
                    )
                }
            }
        }
        Row(Modifier.padding(top = r.u * 6), verticalAlignment = Alignment.CenterVertically) {
            RetroText("Hex #")
            RetroField(hex, { t ->
                hex = t.filter { it.isLetterOrDigit() }.take(6).uppercase()
                hex.takeIf { it.length == 6 }?.toIntOrNull(16)?.let { set(it or 0xFF000000.toInt()) }
            }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(r.u * 6))
        Choice("Done", dismiss, default = true)
    }
}

/**
 * Size and place of the home window, its grid and its icons. Docked at the
 * bottom so the window stays in sight while it changes.
 */
@Composable
private fun LayoutBox(vm: LauncherViewModel, prefs: Prefs, dismiss: () -> Unit) {
    val r = LocalRetro.current
    val step = Prefs.WINDOW_STEP
    RetroDialog(dismiss, alignment = Alignment.BottomCenter) {
        RetroText("Layout", Modifier.padding(bottom = r.u * 4))
        Stepper("Width", "${prefs.windowWidth}%", { d ->
            vm.update { it.copy(windowWidth = (it.windowWidth + d * step).coerceIn(Prefs.WINDOW_WIDTH)) }
        })
        Stepper("Height", "${prefs.windowHeight}%", { d ->
            vm.update { it.copy(windowHeight = (it.windowHeight + d * step).coerceIn(Prefs.WINDOW_HEIGHT)) }
        })
        Stepper("Across", prefs.windowH.label, { d ->
            vm.update { it.copy(windowH = HPos.entries[(it.windowH.ordinal + d).coerceIn(0, HPos.entries.lastIndex)]) }
        })
        Stepper("Up/down", prefs.windowV.label, { d ->
            vm.update { it.copy(windowV = VPos.entries[(it.windowV.ordinal + d).coerceIn(0, VPos.entries.lastIndex)]) }
        })
        HLine(Modifier.padding(vertical = r.u * 3))
        Stepper("Columns", "${prefs.columns}", { d -> vm.update { it.copy(columns = (it.columns + d).coerceIn(Prefs.COLUMNS)) } })
        Stepper("Rows", "${prefs.rows}", { d -> vm.update { it.copy(rows = (it.rows + d).coerceIn(Prefs.ROWS)) } })
        Stepper("Icon size", "${prefs.iconScale}%", { d ->
            vm.update { it.copy(iconScale = (it.iconScale + d * Prefs.ICON_SCALE_STEP).coerceIn(Prefs.ICON_SCALE)) }
        })
        Stepper("Names", prefs.labels.label.substringBefore(' '), { d ->
            vm.update { it.copy(labels = LabelMode.entries[(it.labels.ordinal + d).coerceIn(0, LabelMode.entries.lastIndex)]) }
        })
        if (vm.shownRows in 1 until prefs.rows || vm.shownColumns in 1 until prefs.columns) {
            RetroText(
                "Showing ${vm.shownColumns}×${vm.shownRows}: icons this size need the room. Other apps move to the next page.",
                Modifier.padding(top = r.u * 2), maxLines = 4,
                style = r.text.copy(fontSize = r.text.fontSize * 0.85f),
            )
        }
        Spacer(Modifier.height(r.u * 6))
        Row(horizontalArrangement = Arrangement.spacedBy(r.u * 6)) {
            RetroButton("Defaults", {
                val d = Prefs()
                vm.update {
                    it.copy(
                        windowWidth = d.windowWidth, windowHeight = d.windowHeight, windowH = d.windowH, windowV = d.windowV,
                        columns = d.columns, rows = d.rows, iconScale = d.iconScale, labels = d.labels,
                    )
                }
            }, Modifier.weight(1f))
            RetroButton("Done", dismiss, Modifier.weight(1f), default = true)
        }
    }
}

@Composable
private fun AboutBox(dismiss: () -> Unit) {
    val r = LocalRetro.current
    RetroDialog(dismiss) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RetroText("OneBit", align = TextAlign.Center)
            RetroText("-".repeat(15), align = TextAlign.Center)
            Spacer(Modifier.height(r.u * 4))
            RetroText(
                "A home screen in the style of the first graphical desktops.\n\nVersion ${BuildConfig.VERSION_NAME}",
                Modifier.fillMaxWidth(), align = TextAlign.Center, maxLines = 6,
            )
            Spacer(Modifier.height(r.u * 6))
            RetroText(
                "Fonts: VGA 8×16 (int10h.org, CC BY-SA 4.0), Pixelify Sans, VT323, Silkscreen, Press Start 2P (SIL OFL). " +
                    "Icons: pixelarticons by Gerrit Halfmann (MIT), 1-Bit Pack by Kenney (CC0).",
                Modifier.fillMaxWidth(), align = TextAlign.Center, maxLines = 9,
                style = r.text.copy(fontSize = r.text.fontSize * 0.8f),
            )
            Spacer(Modifier.height(r.u * 8))
            RetroButton("OK", dismiss, Modifier.width(r.line * 5), default = true)
        }
    }
}

private enum class PickerTab(val label: String) { PIXEL("Pixel icons"), TILES("1-bit tiles"), PACK("Icon pack") }

/**
 * A whole window of icons to pick from for one app: the bundled pixel icons
 * (searchable by name), the 1-bit tiles, and every icon of the chosen pack.
 */
@Composable
private fun IconPicker(vm: LauncherViewModel, prefs: Prefs, key: AppKey, dismiss: () -> Unit) {
    val r = LocalRetro.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(if (prefs.iconPack.isNotEmpty()) PickerTab.PACK else PickerTab.PIXEL) }
    var query by remember { mutableStateOf("") }
    val grid = rememberLazyGridState()

    // Pack icons: the one the pack maps to this app first, then the rest by name.
    val refs by produceState<List<IconRef>?>(null, tab, query, prefs.iconPack) {
        value = null
        value = withContext(Dispatchers.Default) {
            val q = query.trim().lowercase()
            when (tab) {
                PickerTab.PIXEL -> vm.glyphs(GlyphLibrary.PIXELART).names
                    .filter { q.isEmpty() || it.contains(q) }
                    .map { IconRef.Glyph(GlyphLibrary.PIXELART, it) }
                PickerTab.TILES -> vm.glyphs(GlyphLibrary.KENNEY).names.map { IconRef.Glyph(GlyphLibrary.KENNEY, it) }
                PickerTab.PACK -> {
                    val pack = prefs.iconPack
                    val filter = if (pack.isEmpty()) null else vm.packs.filter(pack)
                    if (filter == null) emptyList() else {
                        val mapped = vm.packs.drawableFor(pack, key.pkg, key.cls)
                        val names = filter.drawables.filter { q.isEmpty() || it.lowercase().contains(q) }
                        (listOfNotNull(mapped?.takeIf { q.isEmpty() || it.contains(q) }) + names).distinct()
                            .map { IconRef.Pack(pack, it) }
                    }
                }
            }
        }
    }

    val total = refs?.size ?: 0
    val visible = grid.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val scrollable = (total - visible).coerceAtLeast(0)
    val vScroll = ScrollInfo(
        position = if (scrollable == 0) 0f else grid.firstVisibleItemIndex.toFloat() / scrollable,
        visible = if (total == 0) 1f else visible.toFloat() / total,
        step = { d -> scope.launch { grid.animateScrollToItem((grid.firstVisibleItemIndex + d * 4).coerceIn(0, (total - 1).coerceAtLeast(0))) } },
        jump = { f -> scope.launch { grid.scrollToItem((f * scrollable).roundToInt()) } },
    )

    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } } // nothing behind takes touches
            .statusBarsPadding().navigationBarsPadding()
            .padding(r.u * 8),
    ) {
        RetroWindow(
            title = "Icon: ${vm.label(key)}",
            modifier = Modifier.fillMaxSize(),
            info = {
                RetroText(
                    when {
                        refs == null -> "Reading…"
                        total == 1 -> "1 icon"
                        else -> "$total icons"
                    },
                )
            },
            onClose = dismiss,
            vScroll = vScroll,
        ) {
            Row(Modifier.fillMaxWidth().padding(r.u * 4), horizontalArrangement = Arrangement.spacedBy(r.u * 4)) {
                PickerTab.entries.forEach { t ->
                    RetroButton(t.label, { tab = t; query = "" }, Modifier.weight(1f), default = tab == t)
                }
            }
            if (tab != PickerTab.TILES) {
                Row(Modifier.fillMaxWidth().padding(horizontal = r.u * 6), verticalAlignment = Alignment.CenterVertically) {
                    RetroText("Find: ")
                    RetroField(query, { query = it }, Modifier.weight(1f))
                }
            }
            HLine(Modifier.padding(top = r.u * 4))
            if (tab == PickerTab.PACK && prefs.iconPack.isEmpty()) {
                Column(Modifier.padding(r.u * 8)) {
                    RetroText("No icon pack chosen.", maxLines = 2)
                    Spacer(Modifier.height(r.u * 6))
                    RetroButton("Choose an icon pack…", { vm.dialog = Dialog.IconPackChooser }, Modifier.fillMaxWidth())
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(r.line * 3),
                    state = grid,
                    modifier = Modifier.fillMaxSize().padding(r.u * 4),
                ) {
                    items(refs ?: emptyList(), key = { it.encode() }) { ref ->
                        Box(
                            Modifier.aspectRatio(1f)
                                .clickable { vm.setCustomIcon(key, ref); dismiss() }
                                .padding(r.u * 3),
                            contentAlignment = Alignment.Center,
                        ) { RefIcon(ref, r.line * 2.4f) }
                    }
                }
            }
        }
    }
}

/** Which installed icon pack apps' icons come from. */
@Composable
private fun IconPackChooser(vm: LauncherViewModel, prefs: Prefs, dismiss: () -> Unit) {
    val r = LocalRetro.current
    val packs by produceState<List<IconPacks.Pack>?>(null) {
        value = withContext(Dispatchers.Default) { vm.packs.installed() }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    RetroDialog(dismiss) {
        RetroText("Icon pack", Modifier.padding(bottom = r.u * 6))
        val list = packs
        when {
            list == null -> RetroText("Looking…")
            list.isEmpty() -> {
                RetroText(
                    "No icon packs installed. Line-icon packs such as Arcticons or Lawnicons (both free, on F-Droid) " +
                        "suit OneBit: their one-colour icons come out clean in every 1-bit style.",
                    maxLines = 8,
                )
                Spacer(Modifier.height(r.u * 6))
                Choice("Find icon packs on F-Droid", {
                    com.asnidev.onebit.system.SystemActions.open(
                        context, android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://search.f-droid.org/?q=icon+pack")),
                    )
                })
            }
            else -> Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                RetroRadio("None (apps' own icons)", prefs.iconPack.isEmpty(), { vm.setIconPack(""); dismiss() })
                list.forEach { p ->
                    RetroRadio(p.label, prefs.iconPack == p.pkg, { vm.setIconPack(p.pkg); dismiss() })
                }
            }
        }
        Spacer(Modifier.height(r.u * 6))
        Choice("Close", dismiss, default = true)
    }
}
