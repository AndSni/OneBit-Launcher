package com.asnidev.onebit.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Velocity
import com.asnidev.onebit.Dialog
import com.asnidev.onebit.LauncherViewModel
import com.asnidev.onebit.apps.AppEntry
import com.asnidev.onebit.data.Prefs
import com.asnidev.onebit.data.WindowStyle
import kotlinx.coroutines.launch
import java.text.Normalizer
import kotlin.math.roundToInt

/** Lower case without accents, so "e" finds "é". */
private fun fold(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/**
 * All apps as a list window, pulled up over the desktop. Swiping down at the
 * top of the list pushes it back down; [onFling] settles it open or shut.
 */
@Composable
fun Drawer(vm: LauncherViewModel, prefs: Prefs, modifier: Modifier, onFling: (Float) -> Unit) {
    val r = LocalRetro.current
    val apps by vm.apps.collectAsState()
    var query by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var height by remember { mutableStateOf(1f) }
    val picking = vm.pickingFor

    val shown = remember(apps, prefs.hidden, prefs.renames, query) {
        val q = fold(query.trim())
        apps.filter { it.key !in prefs.hidden }
            .map { it to (prefs.renames[it.key] ?: it.label) }
            .filter { q.isEmpty() || fold(it.second).contains(q) }
            .sortedBy { fold(it.second) }
    }

    LaunchedEffect(vm.focusSearch, vm.drawer >= 1f) {
        if (vm.focusSearch && vm.drawer >= 1f) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
            vm.focusSearch = false
        }
    }
    // Closing (even part way) drops the keyboard; fully closed resets the window.
    LaunchedEffect(vm.drawer < 1f) {
        if (vm.drawer < 1f) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    val fling by rememberUpdatedState(onFling)
    val pull = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Pushed part way down: the finger moves the window before the list.
                if (source == NestedScrollSource.UserInput && vm.drawer < 1f) {
                    vm.drawer = (vm.drawer - available.y / height).coerceIn(0f, 1f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Pulling down past the top of the list moves the window down.
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    vm.drawer = (vm.drawer - available.y / height).coerceIn(0f, 1f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (vm.drawer < 1f) {
                    fling(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val total = shown.size
    val info = list.layoutInfo
    val visibleCount = info.visibleItemsInfo.size.coerceAtLeast(1)
    val scrollable = (total - visibleCount).coerceAtLeast(0)
    val vScroll = ScrollInfo(
        position = if (scrollable == 0) 0f else list.firstVisibleItemIndex.toFloat() / scrollable,
        visible = if (total == 0) 1f else visibleCount.toFloat() / total,
        step = { d -> scope.launch { list.animateScrollToItem((list.firstVisibleItemIndex + d).coerceIn(0, total.coerceAtLeast(1) - 1)) } },
        jump = { f -> scope.launch { list.scrollToItem((f * scrollable).roundToInt()) } },
    )

    val title = when {
        picking != null -> "Choose button ${picking + 1}"
        prefs.theme.window == WindowStyle.BOXED -> "A:\\APPS\\*.*"
        else -> "Applications"
    }

    RetroWindow(
        title = title,
        modifier = modifier.onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }.imePadding(),
        info = {
            RetroText(if (total == 1) "1 item" else "$total items")
            Spacer(Modifier.weight(1f))
            if (prefs.hidden.isNotEmpty() && query.isEmpty()) RetroText("${prefs.hidden.size} hidden")
        },
        onClose = { vm.pickingFor = null; onFling(Float.MAX_VALUE) },
        onFull = { vm.settingsOpen = true },
        vScroll = vScroll,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = r.u * 4, vertical = r.u * 2), verticalAlignment = Alignment.CenterVertically) {
            RetroText("Find: ")
            RetroField(query, { query = it }, Modifier.weight(1f), focus = focus, onDone = {
                shown.firstOrNull()?.let { (app, _) -> open(vm, app, null) }
            })
        }
        HLine()
        LazyColumn(Modifier.fillMaxSize().nestedScroll(pull), state = list) {
            items(shown, key = { it.first.key.encode() }) { (app, label) ->
                DrawerRow(vm, app, label, prefs.iconScale)
            }
        }
    }
}

private fun open(vm: LauncherViewModel, app: AppEntry, bounds: Rect?) {
    val slot = vm.pickingFor
    if (slot != null) {
        vm.setTaskbar(slot, app.key)
        vm.pickingFor = null
        vm.drawer = 0f
    } else {
        vm.launch(app.key, bounds)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerRow(vm: LauncherViewModel, app: AppEntry, label: String, iconScale: Int) {
    val r = LocalRetro.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onLongClick = { vm.dialog = Dialog.AppMenu(app.key) },
                onClick = { open(vm, app, bounds) },
            )
            .background(if (pressed) r.ink else r.paper)
            .padding(horizontal = r.u * 6, vertical = r.u * 3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            app.key, r.line * 1.6f * iconScale.coerceAtMost(150) / 100f, selected = pressed, small = true,
            modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() },
        )
        Spacer(Modifier.width(r.u * 8))
        Column(Modifier.weight(1f)) {
            RetroText(label, color = if (pressed) r.paper else r.ink)
        }
        if (app.isWork) RetroText("work", color = if (pressed) r.paper else r.ink)
    }
}
