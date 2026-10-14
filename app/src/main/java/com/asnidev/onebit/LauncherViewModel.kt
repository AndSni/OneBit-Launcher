package com.asnidev.onebit

import android.app.Application
import android.graphics.Rect
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asnidev.onebit.apps.AppEntry
import com.asnidev.onebit.apps.AppRepository
import com.asnidev.onebit.data.AppKey
import com.asnidev.onebit.data.IconStyle
import com.asnidev.onebit.data.Prefs
import com.asnidev.onebit.data.SettingsStore
import com.asnidev.onebit.icons.GlyphLibrary
import com.asnidev.onebit.icons.IconPacks
import com.asnidev.onebit.icons.IconRef
import com.asnidev.onebit.icons.IconRenderer
import com.asnidev.onebit.icons.IconSource
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/** The boxes that can sit over the desktop; one at a time. */
sealed interface Dialog {
    /** An app's long-press box. [slot] is its home slot, [taskbar] its button, when opened from there. */
    data class AppMenu(val key: AppKey, val slot: Int? = null, val taskbar: Int? = null) : Dialog
    data class TaskbarMenu(val index: Int) : Dialog
    data class IconStyleFor(val key: AppKey) : Dialog
    data class Rename(val key: AppKey) : Dialog
    data class PickColor(val slot: ColorSlot) : Dialog
    data object About : Dialog
    data class IconPicker(val key: AppKey) : Dialog
    data object IconPackChooser : Dialog
    data object Layout : Dialog
}

/** The four theme colours, for the colour picker. */
enum class ColorSlot(val label: String) {
    INK("Ink"), PAPER("Paper"), DESK("Desktop"), DESK_INK("Desktop pattern");

    fun of(p: com.asnidev.onebit.data.Palette): Int = when (this) {
        INK -> p.ink; PAPER -> p.paper; DESK -> p.desk; DESK_INK -> p.deskInk
    }

    fun set(p: com.asnidev.onebit.data.Palette, c: Int) = when (this) {
        INK -> p.copy(ink = c); PAPER -> p.copy(paper = c); DESK -> p.copy(desk = c); DESK_INK -> p.copy(deskInk = c)
    }
}

/** Where a launch started, in window pixels, for the zoom rectangles. */
data class Zoom(val from: androidx.compose.ui.geometry.Rect, val id: Long = System.nanoTime())

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    // Background work that fails is logged, never allowed to crash the home screen.
    private val scope: CoroutineScope =
        viewModelScope + CoroutineExceptionHandler { _, e -> Log.w(TAG, "background task failed", e) }

    val repo = AppRepository(app, scope)
    private val store = SettingsStore(app)
    val icons = IconRenderer()
    val packs = IconPacks(app)

    /** Bundled 1-bit icon libraries, read from assets on first use. */
    private val glyphLibraries = java.util.concurrent.ConcurrentHashMap<String, GlyphLibrary>()

    fun glyphs(id: String): GlyphLibrary =
        glyphLibraries.getOrPut(id) { GlyphLibrary.load(getApplication(), id) }

    /**
     * Where [key]'s icon comes from, as a tag for caches: the user's pick, the
     * icon pack (when it has one for the app), or the app itself.
     */
    fun iconTag(key: AppKey, p: Prefs = prefs.value): String =
        p.customIcons[key] ?: if (p.iconPack.isNotEmpty()) "pack:${p.iconPack}" else "app"

    /** [key]'s icon picture, following [iconTag]'s order (call off the main thread). */
    fun iconSource(key: AppKey, p: Prefs = prefs.value): IconSource? {
        p.customIcons[key]?.let(IconRef::decode)?.let { ref -> refSource(ref)?.let { return it } }
        if (p.iconPack.isNotEmpty()) {
            packs.drawableFor(p.iconPack, key.pkg, key.cls)?.let { name ->
                packs.drawable(p.iconPack, name)?.let { return IconSource.Pack(it) }
            }
        }
        return repo.icon(key)?.let { IconSource.App(it) }
    }

    /** A chosen icon's picture (call off the main thread). */
    fun refSource(ref: IconRef): IconSource? = when (ref) {
        is IconRef.Glyph -> runCatching { glyphs(ref.library) }.getOrNull()?.let { lib ->
            lib.bits(ref.name)?.let { IconSource.Glyph(it, lib.side) }
        }
        is IconRef.Pack -> packs.drawable(ref.pkg, ref.drawable)?.let { IconSource.Pack(it) }
    }

    fun setCustomIcon(key: AppKey, ref: IconRef?) = update {
        it.copy(customIcons = if (ref == null) it.customIcons - key else it.customIcons + (key to ref.encode()))
    }

    fun setIconPack(pkg: String) {
        packs.forget(pkg)
        update { it.copy(iconPack = pkg) }
    }

    val apps: StateFlow<List<AppEntry>> = repo.apps
    val prefs: StateFlow<Prefs> = store.prefs.stateIn(scope, SharingStarted.Eagerly, Prefs())

    /** False until the saved settings are read, so the default look never flashes. */
    val loaded: StateFlow<Boolean> = store.prefs.map { true }.stateIn(scope, SharingStarted.Eagerly, false)

    /** 0 = closed, 1 = open; follows the finger while swiping. */
    var drawer by mutableFloatStateOf(0f)

    /** Set by "Find…": the drawer opens with the keyboard up. */
    var focusSearch by mutableStateOf(false)

    var settingsOpen by mutableStateOf(false)
    var dialog by mutableStateOf<Dialog?>(null)

    /** Set while the drawer is open to choose an app for this taskbar button. */
    var pickingFor by mutableStateOf<Int?>(null)

    var zoom by mutableStateOf<Zoom?>(null)

    /** Columns and rows the home window really shows (fewer than set when icons need the room); set by its layout. */
    var shownColumns by mutableIntStateOf(0)
    var shownRows by mutableIntStateOf(0)

    init {
        scope.launch {
            val saved = store.prefs.first()
            if (saved.seeded) return@launch
            // Wait for the first app list, then fill taskbar and home once.
            val list = repo.apps.first { it.isNotEmpty() }
            val defaults = repo.defaults().ifEmpty { list.take(8).map { it.key } }
            store.update { p ->
                if (p.seeded) p else p.copy(
                    taskbar = List(Prefs.MAX_TASKBAR) { defaults.getOrNull(it) },
                    home = defaults.drop(Prefs.MAX_TASKBAR).withIndex().associate { (i, k) -> i to k },
                    seeded = true,
                )
            }
        }
    }

    fun label(entry: AppEntry): String = prefs.value.renames[entry.key] ?: entry.label

    fun label(key: AppKey): String =
        prefs.value.renames[key] ?: apps.value.firstOrNull { it.key == key }?.label ?: key.pkg.substringAfterLast('.')

    fun launch(key: AppKey, from: androidx.compose.ui.geometry.Rect?) {
        if (from != null && prefs.value.zoomRects) zoom = Zoom(from)
        val bounds = from?.let { Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
        repo.launch(key, bounds)
    }

    /** Back to the bare desktop: Home pressed, or the launcher left. */
    fun closeAll() {
        dialog = null
        settingsOpen = false
        pickingFor = null
        focusSearch = false
        drawer = 0f
    }

    fun update(transform: (Prefs) -> Prefs) {
        scope.launch { store.update(transform) }
    }

    // --- home window ---

    fun addToHome(key: AppKey) = update { p ->
        if (key in p.home.values) p else p.copy(home = p.home + (p.firstFreeSlot() to key))
    }

    fun removeFromHome(key: AppKey) = update { p -> p.copy(home = p.home.filterValues { it != key }) }

    fun moveSlot(from: Int, to: Int) = update { it.swap(from, to) }

    fun cleanUp() = update { it.cleanUp() }

    // --- taskbar ---

    fun setTaskbar(index: Int, key: AppKey?) = update { p ->
        p.copy(taskbar = p.taskbar.mapIndexed { i, k -> if (i == index) key else k })
    }

    // --- per app ---

    fun rename(key: AppKey, label: String) = update {
        it.copy(renames = if (label.isBlank()) it.renames - key else it.renames + (key to label.trim()))
    }

    fun setIconStyle(key: AppKey, style: IconStyle?) = update {
        it.copy(iconOverrides = if (style == null) it.iconOverrides - key else it.iconOverrides + (key to style))
    }

    fun hide(key: AppKey) = update { it.withoutApp(key).copy(hidden = it.hidden + key) }
    fun unhide(key: AppKey) = update { it.copy(hidden = it.hidden - key) }

    /**
     * Debug builds: every app's icon through the 1-bit conversions side by side,
     * written to cache/iconsheet.png (`--ez iconsheet true`). Columns: original,
     * grain (Atkinson), pattern dither without edge lines, with them, line art,
     * the app's one-colour icon;
     * on a light theme, then the same on a dark one.
     */
    fun iconSheet(resOverride: Int? = null) {
        scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val list = apps.first { it.isNotEmpty() }.take(40)
            val res = resOverride ?: prefs.value.iconRes
            val scale = 3
            val cell = res * scale + 8
            val themes = listOf(0xFF000000.toInt() to 0xFFFFFFFF.toInt(), 0xFF33FF66.toInt() to 0xFF001A08.toInt())
            val cols = 6
            val sheet = android.graphics.Bitmap.createBitmap(cell * cols * themes.size + 16, cell * list.size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(sheet)
            canvas.drawColor(0xFF808080.toInt())
            val paint = android.graphics.Paint().apply { isFilterBitmap = false }
            for ((row, app) in list.withIndex()) {
                for ((ti, theme) in themes.withIndex()) {
                    val (ink, paper) = theme
                    val spec = com.asnidev.onebit.icons.IconSpec(
                        IconStyle.DITHER, res, ink, paper, prefs.value.iconOutline, prefs.value.iconStripBackground,
                    )
                    val d = repo.icon(app.key) ?: continue
                    val base = icons.prepare(d, spec)
                    val variants = listOf(
                        repo.icon(app.key)?.let { icons.prepare(it, spec.copy(stripBackground = false)) } ?: base,
                        com.asnidev.onebit.icons.RetroPixels.dither(base, res, ink, paper),
                        com.asnidev.onebit.icons.PatternDither.dither(base, res, ink, paper, prefs.value.ditherPatterns, edges = false),
                        com.asnidev.onebit.icons.PatternDither.dither(base, res, ink, paper, prefs.value.ditherPatterns),
                        com.asnidev.onebit.icons.RetroPixels.lineArt(base, res, ink, paper),
                        // What the 1-bit styles now draw for an app with a one-colour icon (blank if it has none).
                        if (repo.hasMonochrome(app.key)) icons.mono(d, spec) else IntArray(res * res),
                    ).mapIndexed { i, v -> if (i == 0 || i == 5 || !prefs.value.iconOutline) v else com.asnidev.onebit.icons.RetroPixels.outline(v, res, ink) }
                    for ((ci, v) in variants.withIndex()) {
                        val x = (ti * cols + ci) * cell + ti * 16
                        val y = row * cell
                        val bg = android.graphics.Paint().apply { color = paper }
                        canvas.drawRect(x.toFloat(), y.toFloat(), (x + cell).toFloat(), (y + cell).toFloat(), bg)
                        val bmp = android.graphics.Bitmap.createBitmap(v, res, res, android.graphics.Bitmap.Config.ARGB_8888)
                        canvas.drawBitmap(bmp, null, android.graphics.Rect(x + 4, y + 4, x + 4 + res * scale, y + 4 + res * scale), paint)
                    }
                }
            }
            java.io.File(getApplication<Application>().cacheDir, "iconsheet.png").outputStream().use {
                sheet.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            Log.i(TAG, "icon sheet written")
        }
    }

    /** Debug builds: which apps ship a one-colour (themed) icon, to logcat (`--ez monostats true`). */
    fun monoStats() {
        // One-colour icon layers only exist from Android 13.
        if (android.os.Build.VERSION.SDK_INT < 33) return
        scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val list = apps.first { it.isNotEmpty() }
            val with = list.filter { repo.hasMonochrome(it.key) }
            Log.i(TAG, "monochrome icons: ${with.size}/${list.size}")
            // How designers shade inside one colour: the alpha levels each layer uses.
            for (app in with) {
                val d = repo.icon(app.key) as? android.graphics.drawable.AdaptiveIconDrawable ?: continue
                val m = d.monochrome ?: continue
                val side = 96
                val bmp = android.graphics.Bitmap.createBitmap(side, side, android.graphics.Bitmap.Config.ARGB_8888)
                m.setBounds(-side / 4, -side / 4, side + side / 4, side + side / 4)
                m.draw(android.graphics.Canvas(bmp))
                val px = IntArray(side * side).also { bmp.getPixels(it, 0, side, 0, 0, side, side) }
                val hist = px.map { (it ushr 24) / 32 }.groupingBy { it }.eachCount().toSortedMap()
                val colours = px.filter { (it ushr 24) > 200 }.map { it and 0xFFFFFF }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
                Log.i(TAG, "alpha ${app.label}: ${hist.entries.joinToString { "${it.key * 32}:${it.value}" }} rgb ${colours.joinToString { "%06X:%d".format(it.key, it.value) }}")
            }
            list.forEach { Log.i(TAG, "mono ${if (it in with) "yes" else "no "} ${it.label}") }
        }
    }

    override fun onCleared() {
        repo.close()
    }

    private companion object {
        const val TAG = "OneBit"
    }
}
