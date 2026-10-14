package com.asnidev.onebit.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.asnidev.onebit.BuildConfig
import com.asnidev.onebit.ColorSlot
import com.asnidev.onebit.Dialog
import com.asnidev.onebit.LauncherViewModel
import com.asnidev.onebit.data.FontChoice
import com.asnidev.onebit.data.IconStyle
import com.asnidev.onebit.data.InfoBar
import com.asnidev.onebit.data.LabelMode
import com.asnidev.onebit.data.Pattern
import com.asnidev.onebit.data.Prefs
import com.asnidev.onebit.data.Presets
import com.asnidev.onebit.data.Theme
import com.asnidev.onebit.data.WindowStyle
import com.asnidev.onebit.system.SystemActions
import com.asnidev.onebit.icons.PatternDither
import kotlinx.coroutines.launch

/** Everything adjustable, in one scrolling window over the desktop. */
@Composable
fun SettingsWindow(vm: LauncherViewModel, prefs: Prefs) {
    val r = LocalRetro.current
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val apps by vm.apps.collectAsState()
    fun theme(t: (Theme) -> Theme) = vm.update { it.copy(theme = t(it.theme)) }

    val vScroll = ScrollInfo(
        position = if (scroll.maxValue == 0) 0f else scroll.value.toFloat() / scroll.maxValue,
        visible = if (scroll.maxValue == 0) 1f else scroll.viewportSize.toFloat() / (scroll.maxValue + scroll.viewportSize),
        step = { d -> scope.launch { scroll.animateScrollBy(d * scroll.viewportSize * 0.25f) } },
        jump = { f -> scope.launch { scroll.scrollTo((f * scroll.maxValue).toInt()) } },
    )

    RetroWindow(
        title = if (prefs.theme.window == WindowStyle.BOXED) "OPTIONS" else "Options",
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(r.u * 8),
        onClose = { vm.settingsOpen = false },
        vScroll = vScroll,
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = r.u * 8, vertical = r.u * 4)) {

            Heading("Presets")
            Presets.all.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(vertical = r.u * 2), horizontalArrangement = Arrangement.spacedBy(r.u * 6)) {
                    pair.forEach { p ->
                        RetroButton(
                            p.name, { vm.update { it.copy(theme = p.theme) } }, Modifier.weight(1f),
                            default = prefs.theme == p.theme,
                            leading = { _ -> Swatch(Color(p.theme.palette.desk), Color(p.theme.palette.ink), Color(p.theme.palette.paper)) },
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Heading("Colors")
            ColorSlot.entries.forEach { slot ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.dialog = Dialog.PickColor(slot) }.padding(vertical = r.u * 3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RetroText(slot.label, Modifier.weight(1f))
                    RetroText("#%06X".format(slot.of(prefs.theme.palette) and 0xFFFFFF), Modifier.padding(end = r.u * 6))
                    Box(Modifier.size(r.line * 1.6f, r.line).border(r.u, r.ink).padding(r.u * 2).background(Color(slot.of(prefs.theme.palette))))
                }
            }

            Heading("Desktop pattern")
            Pattern.entries.chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = r.u * 2), horizontalArrangement = Arrangement.spacedBy(r.u * 4)) {
                    row.forEach { p ->
                        val chosen = prefs.theme.pattern == p
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .border(if (chosen) r.u * 3 else r.u, r.ink)
                                .clickable { theme { it.copy(pattern = p) } }
                                .padding(if (chosen) r.u * 3 else r.u)
                                .pattern(p, r.deskInk, r.desk),
                        )
                    }
                    repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Heading("Windows")
            WindowStyle.entries.forEach { w ->
                RetroRadio("${w.label} title bars", prefs.theme.window == w, { theme { it.copy(window = w) } })
            }
            Spacer(Modifier.height(r.u * 4))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RetroText("Home title ")
                RetroField(prefs.homeTitle, { t -> vm.update { it.copy(homeTitle = t.take(40)) } }, Modifier.weight(1f))
            }
            RetroText("Empty uses the default.", Modifier.padding(top = r.u * 2), color = r.ink.copy(alpha = 0.6f))

            Heading("Font")
            FontChoice.entries.forEach { f ->
                RetroRadio(f.label, prefs.theme.font == f, { theme { it.copy(font = f) } })
            }
            Stepper("Text size", "${prefs.theme.textSize}", { d ->
                theme { it.copy(textSize = (it.textSize + d).coerceIn(Prefs.TEXT_SIZES)) }
            })

            Heading("Home window")
            RetroText(
                "${prefs.windowWidth}% wide, ${prefs.windowHeight}% high, ${prefs.windowV.label.lowercase()} ${prefs.windowH.label.lowercase()}",
                Modifier.padding(vertical = r.u * 2),
            )
            RetroButton(
                "Layout…",
                { vm.settingsOpen = false; vm.dialog = Dialog.Layout },
                Modifier.fillMaxWidth().padding(bottom = r.u * 4),
            )
            Stepper("Columns", "${prefs.columns}", { d -> vm.update { it.copy(columns = (it.columns + d).coerceIn(Prefs.COLUMNS)) } })
            Stepper("Rows", "${prefs.rows}", { d -> vm.update { it.copy(rows = (it.rows + d).coerceIn(Prefs.ROWS)) } })
            RetroText("App names", Modifier.padding(top = r.u * 4))
            LabelMode.entries.forEach { m ->
                RetroRadio(m.label, prefs.labels == m, { vm.update { it.copy(labels = m) } })
            }
            RetroText("Info line", Modifier.padding(top = r.u * 4))
            InfoBar.entries.forEach { b ->
                RetroRadio(b.label, prefs.infoBar == b, { vm.update { it.copy(infoBar = b) } })
            }

            Heading("Icons")
            IconStyle.entries.forEach { s ->
                RetroRadio(s.label, prefs.iconStyle == s, { vm.update { it.copy(iconStyle = s) } })
            }
            Stepper("Size", "${prefs.iconScale}%", { d ->
                vm.update { it.copy(iconScale = (it.iconScale + d * Prefs.ICON_SCALE_STEP).coerceIn(Prefs.ICON_SCALE)) }
            })
            Stepper("Pixels", "${prefs.iconRes}×${prefs.iconRes}", { d ->
                vm.update {
                    val i = (Prefs.ICON_RES.indexOf(it.iconRes) + d).coerceIn(0, Prefs.ICON_RES.lastIndex)
                    it.copy(iconRes = Prefs.ICON_RES[i])
                }
            })
            RetroCheckbox("Outline around icons", prefs.iconOutline, { v -> vm.update { it.copy(iconOutline = v) } })
            DitherPalette(vm, prefs)
            Row(
                Modifier.fillMaxWidth().clickable { vm.dialog = Dialog.IconPackChooser }.padding(vertical = r.u * 3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RetroText("Icon pack", Modifier.weight(1f))
                RetroText(if (prefs.iconPack.isEmpty()) "none" else vm.packs.label(prefs.iconPack))
            }
            if (prefs.customIcons.isNotEmpty()) {
                RetroButton(
                    "Reset ${prefs.customIcons.size} chosen icons",
                    { vm.update { it.copy(customIcons = emptyMap()) } },
                    Modifier.padding(vertical = r.u * 2),
                )
            }
            RetroCheckbox("Drop icon backgrounds", prefs.iconStripBackground, { v -> vm.update { it.copy(iconStripBackground = v) } })
            RetroCheckbox(
                "1-bit styles: use the app's own one-colour icon when it has one",
                prefs.preferMonochrome, { v -> vm.update { it.copy(preferMonochrome = v) } },
            )
            val monoCount by produceState<Pair<Int, Int>?>(null, apps) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    apps.count { vm.repo.hasMonochrome(it.key) } to apps.size
                }
            }
            monoCount?.let { (with, all) ->
                RetroText(
                    if (android.os.Build.VERSION.SDK_INT < 33) "One-colour icons need Android 13 or newer."
                    else "$with of $all apps have one; the rest are converted.",
                    Modifier.padding(start = r.u * 4, bottom = r.u * 2), color = r.ink.copy(alpha = 0.6f), maxLines = 2,
                )
            }
            if (prefs.iconOverrides.isNotEmpty()) {
                RetroButton(
                    "Reset ${prefs.iconOverrides.size} app styles",
                    { vm.update { it.copy(iconOverrides = emptyMap()) } },
                    Modifier.padding(top = r.u * 4),
                )
            }

            Heading("Taskbar")
            Stepper("Buttons", if (prefs.taskbarCount == 0) "off" else "${prefs.taskbarCount}", { d ->
                vm.update { it.copy(taskbarCount = (it.taskbarCount + d).coerceIn(0, Prefs.MAX_TASKBAR)) }
            })
            RetroCheckbox("App names on buttons", prefs.taskbarLabels, { v -> vm.update { it.copy(taskbarLabels = v) } })
            RetroText("Hold a button to change its app.", Modifier.padding(top = r.u * 2), color = r.ink.copy(alpha = 0.6f))

            Heading("Desktop")
            RetroCheckbox("Menu bar", prefs.showMenuBar, { v -> vm.update { it.copy(showMenuBar = v) } })
            RetroCheckbox("Clock in the menu bar", prefs.menuClock, { v -> vm.update { it.copy(menuClock = v) } })
            RetroCheckbox("Hide the status bar", prefs.hideStatusBar, { v -> vm.update { it.copy(hideStatusBar = v) } })
            RetroCheckbox("Swipe down for notifications", prefs.swipeDownNotifications, { v -> vm.update { it.copy(swipeDownNotifications = v) } })
            RetroCheckbox("Zoom rectangles when opening apps", prefs.zoomRects, { v -> vm.update { it.copy(zoomRects = v) } })
            if (!prefs.showMenuBar) {
                RetroText("Without the menu bar, hold an empty spot in the home window for options.", Modifier.padding(top = r.u * 2), color = r.ink.copy(alpha = 0.6f), maxLines = 3)
            }

            if (prefs.hidden.isNotEmpty()) {
                Heading("Hidden apps")
                prefs.hidden.forEach { key ->
                    Row(Modifier.fillMaxWidth().padding(vertical = r.u * 2), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(key, r.line * 1.5f, small = true)
                        Spacer(Modifier.width(r.u * 6))
                        RetroText(apps.firstOrNull { it.key == key }?.label ?: key.pkg, Modifier.weight(1f))
                        RetroButton("Show", { vm.unhide(key) })
                    }
                }
            }

            Heading("System")
            if (!SystemActions.isDefaultLauncher(context)) {
                RetroButton("Make OneBit the home app", { (context as? Activity)?.let(SystemActions::requestDefaultLauncher) }, Modifier.fillMaxWidth(), default = true)
            } else {
                RetroText("OneBit is the home app.")
            }
            Spacer(Modifier.height(r.u * 4))
            RetroButton("About OneBit", { vm.dialog = Dialog.About }, Modifier.fillMaxWidth())
            RetroText("Version ${BuildConfig.VERSION_NAME}", Modifier.padding(vertical = r.u * 6), color = r.ink.copy(alpha = 0.6f))
        }
    }
}

/** Three colour chips: a theme at a glance. */
@Composable
private fun Swatch(desk: Color, ink: Color, paper: Color) {
    val r = LocalRetro.current
    Row(Modifier.border(r.u, r.ink)) {
        Box(Modifier.size(r.line * 0.6f, r.line).background(desk))
        Box(Modifier.size(r.line * 0.6f, r.line).background(paper))
        Box(Modifier.size(r.line * 0.6f, r.line).background(ink))
    }
}

/**
 * The colour-to-pattern table "1-bit dither" uses: each palette colour with its
 * pattern beside it. Tapping a pattern moves that colour on to the next one.
 */
@Composable
private fun DitherPalette(vm: LauncherViewModel, prefs: Prefs) {
    val r = LocalRetro.current
    RetroText("Dither palette (tap a pattern to change it)", Modifier.padding(top = r.u * 6, bottom = r.u * 2), maxLines = 2)
    PatternDither.PALETTE.withIndex().chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(r.u * 8)) {
            pair.forEach { (i, swatch) ->
                val pattern = prefs.ditherPatterns.getOrElse(i) { swatch.pattern }
                Row(
                    Modifier.weight(1f).clickable {
                        vm.update { p ->
                            val list = p.ditherPatterns.toMutableList()
                            list[i] = (list[i] + 1) % PatternDither.PATTERNS.size
                            p.copy(ditherPatterns = list)
                        }
                    }.padding(vertical = r.u * 2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(r.line).border(r.u, r.ink).padding(r.u).background(Color(swatch.color)))
                    Spacer(Modifier.width(r.u * 3))
                    PatternSwatch(pattern, Modifier.size(r.line * 1.6f, r.line))
                    Spacer(Modifier.width(r.u * 4))
                    RetroText(swatch.name, Modifier.weight(1f), style = r.text.copy(fontSize = r.text.fontSize * 0.85f))
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    if (prefs.ditherPatterns != PatternDither.DEFAULT_PATTERNS) {
        RetroButton("Default patterns", { vm.update { it.copy(ditherPatterns = PatternDither.DEFAULT_PATTERNS) } }, Modifier.padding(top = r.u * 4))
    }
}

/** A dither pattern tiled at two screen pixels per pattern pixel, in ink on paper. */
@Composable
private fun PatternSwatch(pattern: Int, modifier: Modifier) {
    val r = LocalRetro.current
    val p = PatternDither.PATTERNS[pattern.coerceIn(0, PatternDither.PATTERNS.lastIndex)]
    androidx.compose.foundation.Canvas(modifier.border(r.u, r.ink)) {
        val cell = r.upx * 2f
        val w = (size.width / cell).toInt()
        val h = (size.height / cell).toInt()
        drawRect(r.paper)
        for (y in 0 until h) for (x in 0 until w) {
            if (p.bit(x, y)) drawRect(r.ink, androidx.compose.ui.geometry.Offset(x * cell, y * cell), androidx.compose.ui.geometry.Size(cell, cell))
        }
    }
}
