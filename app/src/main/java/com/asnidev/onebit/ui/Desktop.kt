package com.asnidev.onebit.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import com.asnidev.onebit.data.HPos
import com.asnidev.onebit.data.GridFit
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import com.asnidev.onebit.data.LabelMode
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.width
import com.asnidev.onebit.data.VPos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.asnidev.onebit.Dialog
import com.asnidev.onebit.LauncherViewModel
import com.asnidev.onebit.data.AppKey
import com.asnidev.onebit.data.IconStyle
import com.asnidev.onebit.data.InfoBar
import com.asnidev.onebit.data.Prefs
import com.asnidev.onebit.data.Presets
import com.asnidev.onebit.data.WindowStyle
import com.asnidev.onebit.system.SystemActions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** An icon being dragged across the home window. Positions in root pixels. */
private class HomeDrag(val key: AppKey, val from: Int, val start: Offset) {
    var delta by mutableStateOf(Offset.Zero)
    val at: Offset get() = start + delta
}

@Composable
fun Desktop(vm: LauncherViewModel, prefs: Prefs) {
    val r = LocalRetro.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var drag by remember { mutableStateOf<HomeDrag?>(null) }
    var areaHeight by remember { mutableStateOf(1f) }

    // Swipe up pulls the drawer; swipe down opens the notification shade.
    var settle by remember { mutableStateOf<Job?>(null) }
    var pulledDown by remember { mutableStateOf(0f) }
    val dragState = rememberDraggableState { dy ->
        if (vm.drawer == 0f && dy > 0f) pulledDown += dy
        else vm.drawer = (vm.drawer - dy / areaHeight).coerceIn(0f, 1f)
    }
    fun settleDrawer(velocity: Float, from: Float) {
        val v = velocity / areaHeight
        val target = when {
            v < -1.2f -> 1f
            v > 1.2f -> 0f
            from >= 1f -> if (vm.drawer < 0.8f) 0f else 1f
            else -> if (vm.drawer > 0.2f) 1f else 0f
        }
        settle?.cancel()
        settle = scope.launch { animate(vm.drawer, target, animationSpec = tween(180)) { x, _ -> vm.drawer = x } }
    }
    var dragFrom by remember { mutableStateOf(0f) }

    // The zoom rectangles of a launch, drawn over everything.
    val zoomT = remember { Animatable(1f) }
    LaunchedEffect(vm.zoom) {
        if (vm.zoom == null) return@LaunchedEffect
        zoomT.snapTo(0f)
        zoomT.animateTo(1f, tween(320, easing = LinearEasing))
        vm.zoom = null
    }

    Box(
        Modifier.fillMaxSize()
            .pattern(prefs.theme.pattern, r.deskInk, r.desk)
            .drawWithContent {
                drawContent()
                val z = vm.zoom ?: return@drawWithContent
                val screen = Rect(0f, 0f, size.width, size.height)
                val t = zoomT.value
                for (k in 0 until 8) {
                    val f = t * 1.4f - k * 0.05f
                    if (f <= 0f || f > 1f) continue
                    val rect = lerp(z.from, screen, f * f)
                    drawRect(Color.White, rect.topLeft, rect.size, style = Stroke(r.upx.toFloat()), blendMode = BlendMode.Difference)
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (!prefs.hideStatusBar) {
                Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(if (prefs.showMenuBar) r.paper else Color.Transparent))
            }
            if (prefs.showMenuBar) {
                MenuBar(menus(vm, prefs, context), right = if (prefs.menuClock) clockText() else null)
            }
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .onGloballyPositioned { areaHeight = it.size.height.toFloat().coerceAtLeast(1f) }
                    .draggable(
                        dragState,
                        Orientation.Vertical,
                        enabled = drag == null,
                        onDragStarted = { settle?.cancel(); dragFrom = vm.drawer; pulledDown = 0f },
                        onDragStopped = { velocity ->
                            val shade = dragFrom == 0f && vm.drawer == 0f && pulledDown > with(density) { 64.dp.toPx() }
                            if (shade && prefs.swipeDownNotifications) SystemActions.expandNotifications(context)
                            settleDrawer(velocity, dragFrom)
                        },
                    ),
            ) {
                Column(Modifier.fillMaxSize()) {
                    // The window takes the chosen share of the desk; the pattern shows around it.
                    Box(Modifier.weight(1f).fillMaxWidth().padding(r.u * 8)) {
                        HomeWindow(
                            vm, prefs, drag, { drag = it },
                            Modifier.fillMaxWidth(prefs.windowWidth / 100f)
                                .fillMaxHeight(prefs.windowHeight / 100f)
                                .align(windowAlignment(prefs)),
                        )
                    }
                    if (prefs.taskbarCount > 0) Taskbar(vm, prefs)
                    Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
                if (vm.drawer > 0f) {
                    Drawer(
                        vm, prefs,
                        Modifier.fillMaxSize()
                            .offset { IntOffset(0, ((1f - vm.drawer) * areaHeight).roundToInt()) }
                            .navigationBarsPadding(),
                    ) {
                        settleDrawer(it, 1f)
                    }
                }
                if (vm.settingsOpen) SettingsWindow(vm, prefs)
            }
        }

        // The dragged icon follows the finger above everything.
        drag?.let { d ->
            val side = with(density) { r.line.toPx() * 2.5f }
            Box(Modifier.offset { IntOffset((d.at.x - side / 2).roundToInt(), (d.at.y - side / 2).roundToInt()) }) {
                AppIcon(d.key, with(density) { side.toDp() }, selected = true)
            }
        }

        vm.dialog?.let { Dialogs(vm, prefs, it) }
    }
}

/** Icon size at 100%. */
private fun iconBase(r: Retro): Dp = r.line * 2.5f

/** The biggest icon a cell of [w]×[h] has room for, with its label. */
private fun iconRoom(w: Dp, h: Dp, labels: LabelMode, r: Retro): Dp = when (labels) {
    LabelMode.UNDER -> minOf(w * 0.9f, h - r.line - r.u * 8)
    LabelMode.BESIDE -> minOf(w * 0.45f, h - r.u * 4)
    LabelMode.HIDDEN -> minOf(w * 0.9f, h - r.u * 4)
}

private fun windowAlignment(prefs: Prefs): Alignment = BiasAlignment(
    horizontalBias = when (prefs.windowH) { HPos.LEFT -> -1f; HPos.CENTER -> 0f; HPos.RIGHT -> 1f },
    verticalBias = when (prefs.windowV) { VPos.TOP -> -1f; VPos.MIDDLE -> 0f; VPos.BOTTOM -> 1f },
)

private fun homeTitle(prefs: Prefs): String = prefs.homeTitle.ifBlank {
    if (prefs.theme.window == WindowStyle.BOXED) "A:\\HOME\\" else "Home"
}

@Composable
private fun HomeWindow(
    vm: LauncherViewModel,
    prefs: Prefs,
    drag: HomeDrag?,
    setDrag: (HomeDrag?) -> Unit,
    modifier: Modifier,
) {
    val r = LocalRetro.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Icon size wins: the grid shows as many columns and rows as fit it, the saved ones at most.
    var area by remember { mutableStateOf(Size.Zero) }
    val (cols, rows) = GridFit.fit(
        prefs.columns, prefs.rows,
        area.width - (r.u * 8).value, area.height - (r.u * 8).value,
        iconBase(r).value * prefs.iconScale / 100f, prefs.labels, r.line.value, r.u.value,
    )
    val grid = prefs.copy(columns = cols, rows = rows)
    SideEffect {
        vm.shownColumns = cols
        vm.shownRows = rows
    }

    // While dragging there is one extra, empty page to drop onto.
    val pageCount = grid.pages + if (drag != null) 1 else 0
    val pager = rememberPagerState { pageCount }
    val cells = remember { mutableStateMapOf<Int, Rect>() }
    // Slots move when the grid changes; old cell positions would be wrong drop targets.
    LaunchedEffect(cols, rows) { cells.clear() }
    var windowBounds by remember { mutableStateOf(Rect.Zero) }

    // Holding a dragged icon near the left or right edge turns the page.
    val edge by remember(drag) {
        derivedStateOf {
            val d = drag ?: return@derivedStateOf 0
            val margin = windowBounds.width * 0.08f
            when {
                d.at.x < windowBounds.left + margin -> -1
                d.at.x > windowBounds.right - margin -> 1
                else -> 0
            }
        }
    }
    LaunchedEffect(edge) {
        while (edge != 0) {
            delay(650)
            val to = (pager.currentPage + edge).coerceIn(0, pageCount - 1)
            if (to == pager.currentPage) break
            pager.animateScrollToPage(to)
        }
    }

    val hScroll = if (pageCount > 1) ScrollInfo(
        position = if (pageCount > 1) (pager.currentPage + pager.currentPageOffsetFraction) / (pageCount - 1) else 0f,
        visible = 1f / pageCount,
        step = { d -> scope.launch { pager.animateScrollToPage((pager.currentPage + d).coerceIn(0, pageCount - 1)) } },
        jump = { f -> scope.launch { pager.scrollToPage((f * (pageCount - 1)).roundToInt()) } },
    ) else null

    RetroWindow(
        title = homeTitle(prefs),
        modifier = modifier.onGloballyPositioned { windowBounds = it.boundsInRoot() },
        info = if (prefs.infoBar == InfoBar.NONE) null else ({ InfoLine(prefs, pager.currentPage, pageCount) }),
        onClose = { vm.drawer = 1f },
        onFull = { vm.settingsOpen = true },
        hScroll = hScroll,
    ) {
        HorizontalPager(
            pager,
            Modifier.fillMaxSize().onSizeChanged { area = with(density) { Size(it.width.toDp().value, it.height.toDp().value) } },
            userScrollEnabled = drag == null,
            // While dragging, every page stays composed: the gesture lives in its icon.
            beyondViewportPageCount = if (drag != null) pageCount else 0,
            key = { it },
        ) { page ->
            Column(Modifier.fillMaxSize().padding(r.u * 4)) {
                repeat(grid.rows) { row ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        repeat(grid.columns) { col ->
                            val slot = page * grid.pageSize + row * grid.columns + col
                            val key = prefs.home[slot]
                            Box(
                                Modifier.weight(1f).fillMaxHeight()
                                    .onGloballyPositioned { cells[slot] = it.boundsInRoot() },
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    key == null -> EmptyCell(vm, highlighted = drag != null && cells[slot]?.contains(drag.at) == true)
                                    // The dragged icon stays composed (its gesture lives there), drawn as a ghost.
                                    else -> HomeIcon(
                                        vm, prefs, key, ghost = drag?.from == slot,
                                        onLongPress = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            setDrag(HomeDrag(key, slot, cells[slot]?.center ?: Offset.Zero))
                                        },
                                        onDrag = { delta -> drag?.delta = delta },
                                        onDrop = { moved ->
                                            val d = drag
                                            setDrag(null)
                                            if (d == null) return@HomeIcon
                                            if (!moved) {
                                                vm.dialog = Dialog.AppMenu(key, slot = slot)
                                                return@HomeIcon
                                            }
                                            // Only cells of the page on screen count as targets.
                                            val first = pager.currentPage * grid.pageSize
                                            val target = cells.entries.firstOrNull { (s, b) ->
                                                s in first until first + grid.pageSize && b.contains(d.at)
                                            }?.key
                                            if (target != null) vm.moveSlot(d.from, target)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCell(vm: LauncherViewModel, highlighted: Boolean) {
    val r = LocalRetro.current
    Box(
        Modifier.fillMaxSize()
            .padding(r.u * 2)
            .then(if (highlighted) Modifier.pattern(com.asnidev.onebit.data.Pattern.GRAY25, r.ink, r.paper) else Modifier)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { vm.settingsOpen = true }) },
    )
}

/** Where a dragged icon came from: a dotted outline, as on the old desktops. */
@Composable
private fun GhostCell() {
    val r = LocalRetro.current
    Box(Modifier.fillMaxSize().padding(r.u * 6).pattern(com.asnidev.onebit.data.Pattern.GRAY12, r.ink, r.paper))
}

/**
 * An icon with its label under it. Tap opens; hold, then let go to get the
 * app's box, or hold and drag to move it ([onDrop] says whether it moved).
 */
@Composable
private fun HomeIcon(
    vm: LauncherViewModel,
    prefs: Prefs,
    key: AppKey,
    ghost: Boolean,
    onLongPress: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: (moved: Boolean) -> Unit,
) {
    val r = LocalRetro.current
    var pressed by remember { mutableStateOf(false) }
    // The gesture below outlives recompositions; it must call the newest callbacks.
    val longPress by rememberUpdatedState(onLongPress)
    val dragged by rememberUpdatedState(onDrag)
    val dropped by rememberUpdatedState(onDrop)
    if (ghost) {
        GhostCell()
    }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val long = awaitLongPressOrCancellation(down.id)
                    if (long == null) {
                        pressed = false
                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (up != null && up.changedToUp() && !up.isConsumed) vm.launch(key, bounds)
                        return@awaitEachGesture
                    }
                    longPress()
                    var total = Offset.Zero
                    var moved = false
                    drag(long.id) { c ->
                        total += c.positionChange()
                        c.consume()
                        if (total.getDistance() > viewConfiguration.touchSlop) moved = true
                        dragged(total)
                    }
                    pressed = false
                    dropped(moved)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val room = iconRoom(maxWidth, maxHeight, prefs.labels, r).coerceAtLeast(r.line * 0.8f)
        val iconSide = minOf(iconBase(r) * (prefs.iconScale / 100f), room)
        val labelText: @Composable (Modifier) -> Unit = { m ->
            RetroText(
                vm.label(key),
                m.background(if (pressed) r.ink else r.paper).padding(horizontal = r.u * 2),
                color = if (pressed) r.paper else r.ink,
                maxLines = if (prefs.labels == LabelMode.BESIDE) 2 else 1,
            )
        }
        val icon: @Composable () -> Unit = {
            AppIcon(
                key, iconSide, selected = pressed, limit = room,
                modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() },
            )
        }
        if (!ghost && prefs.labels == LabelMode.BESIDE) Row(
            Modifier.fillMaxWidth().padding(horizontal = r.u * 6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(r.u * 6))
            labelText(Modifier.weight(1f, fill = false))
        } else if (!ghost) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            icon()
            if (prefs.labels == LabelMode.UNDER) {
                RetroText(
                    vm.label(key),
                    Modifier.padding(top = r.u * 2)
                        .background(if (pressed) r.ink else r.paper)
                        .padding(horizontal = r.u * 2),
                    color = if (pressed) r.paper else r.ink,
                    align = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RowScope.InfoLine(prefs: Prefs, page: Int, pages: Int) {
    val context = LocalContext.current
    val items = prefs.home.size
    val itemText = if (items == 1) "1 item" else "$items items"
    val tick by produceState(0L) { while (true) { value = System.currentTimeMillis(); delay(20_000) } }
    val pageText = if (pages > 1) "  ${page + 1}/$pages" else ""
    val (left, right) = when (prefs.infoBar) {
        InfoBar.STORAGE -> {
            val (used, free) = remember(tick / 300_000) { storage() }
            if (prefs.theme.window == WindowStyle.BOXED) "${bytes(used)} used in $itemText" to "${bytes(free)} free"
            else itemText to "${bytes(used)} in disk  ${bytes(free)} available"
        }
        InfoBar.CLOCK -> itemText to remember(tick / 20_000) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date()) }
        InfoBar.BATTERY -> itemText to remember(tick / 20_000) { battery(context) }
        InfoBar.ITEMS, InfoBar.NONE -> itemText to ""
    }
    RetroText(left + pageText)
    Spacer(Modifier.weight(1f))
    RetroText(right)
}

private fun storage(): Pair<Long, Long> = runCatching {
    val s = StatFs(Environment.getDataDirectory().path)
    (s.totalBytes - s.availableBytes) to s.availableBytes
}.getOrDefault(0L to 0L)

private fun bytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1fG".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "${b shr 20}M"
    else -> "${b shr 10}K"
}

private fun battery(context: Context): String {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return ""
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    return "battery ${level * 100 / scale}%" + if (plugged) " charging" else ""
}

@Composable
private fun clockText(): String {
    val tick by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000)
        }
    }
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(tick))
}

@Composable
private fun Taskbar(vm: LauncherViewModel, prefs: Prefs) {
    val r = LocalRetro.current
    Row(
        Modifier.fillMaxWidth().padding(start = r.u * 8, end = r.u * 8, bottom = r.u * 8),
        horizontalArrangement = Arrangement.spacedBy(r.u * 6),
    ) {
        for (i in 0 until prefs.taskbarCount) {
            val key = prefs.taskbar.getOrNull(i)
            var bounds by remember { mutableStateOf(Rect.Zero) }
            RetroButton(
                text = when {
                    key == null -> "+"
                    prefs.taskbarLabels -> vm.label(key)
                    else -> ""
                },
                onClick = {
                    if (key != null) vm.launch(key, bounds)
                    else { vm.pickingFor = i; vm.drawer = 1f }
                },
                onLongClick = { vm.dialog = Dialog.TaskbarMenu(i) },
                modifier = Modifier.weight(1f).onGloballyPositioned { bounds = it.boundsInRoot() },
                default = false,
                leading = key?.let { k -> { fg -> AppIcon(k, r.line * 1.5f, selected = fg == r.paper, small = true) } },
            )
        }
    }
}

private fun menus(vm: LauncherViewModel, prefs: Prefs, context: Context): List<Menu> = listOf(
    Menu(
        "Desk", listOf(
            MenuItem("About OneBit…") { vm.dialog = Dialog.About },
            null,
            MenuItem("Options…") { vm.settingsOpen = true },
            MenuItem("Make default home…") { (context as? Activity)?.let(SystemActions::requestDefaultLauncher) },
            MenuItem("Phone settings…") { SystemActions.openSettings(context) },
        ),
    ),
    Menu(
        "File", listOf(
            MenuItem("Applications") { vm.drawer = 1f },
            MenuItem("Find…") { vm.focusSearch = true; vm.drawer = 1f },
            null,
            MenuItem("Clean up window", enabled = prefs.home.isNotEmpty()) { vm.cleanUp() },
        ),
    ),
    Menu(
        "View",
        IconStyle.entries.map { s ->
            MenuItem(s.label, checked = prefs.iconStyle == s) { vm.update { it.copy(iconStyle = s) } }
        } + null + listOf(
            null,
        ) + LabelMode.entries.map { m ->
            MenuItem(if (m == LabelMode.HIDDEN) "No labels" else "Labels ${m.label.lowercase()}", checked = prefs.labels == m) {
                vm.update { it.copy(labels = m) }
            }
        } + listOf(
            null,
            MenuItem("Outlines", checked = prefs.iconOutline) { vm.update { it.copy(iconOutline = !it.iconOutline) } },
            null,
            MenuItem("Layout…") { vm.dialog = Dialog.Layout },
        ),
    ),
    Menu(
        "Options",
        WindowStyle.entries.map { w ->
            MenuItem("${w.label} windows", checked = prefs.theme.window == w) {
                vm.update { it.copy(theme = it.theme.copy(window = w)) }
            }
        } + null + Presets.all.map { preset ->
            MenuItem(preset.name, checked = prefs.theme.palette == preset.theme.palette && prefs.theme.pattern == preset.theme.pattern) {
                vm.update { it.copy(theme = preset.theme) }
            }
        } + null + listOf(MenuItem("All options…") { vm.settingsOpen = true }),
    ),
)
