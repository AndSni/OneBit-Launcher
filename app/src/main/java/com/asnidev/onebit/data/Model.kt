package com.asnidev.onebit.data

/**
 * One launchable activity in one user profile. The profile is stored as its
 * serial number because UserHandle itself isn't stable across reboots.
 */
data class AppKey(val pkg: String, val cls: String, val user: Long) {
    fun encode(): String = "$pkg/$cls#$user"

    companion object {
        fun decode(s: String): AppKey? {
            val slash = s.indexOf('/')
            val hash = s.lastIndexOf('#')
            if (slash <= 0 || hash <= slash) return null
            val user = s.substring(hash + 1).toLongOrNull() ?: return null
            return AppKey(s.substring(0, slash), s.substring(slash + 1, hash), user)
        }
    }
}

/** How an app icon is redrawn. Everything but ORIGINAL is drawn at [Prefs.iconRes] pixels. */
enum class IconStyle(val label: String) {
    ORIGINAL("Original"),
    PIXEL("Pixel"),
    COLORS16("16 colors"),
    DITHER("1-bit dither"),
    GRAIN("1-bit grain"),
    THRESHOLD("1-bit line art"),
    MONO("Silhouette"),
}

/** Window chrome. BOXED: patterned title, close and full boxes. STRIPED: pinstriped title bar. */
enum class WindowStyle(val label: String) { BOXED("Boxed"), STRIPED("Striped") }

/** What the line under a window's title shows. */
enum class InfoBar(val label: String) {
    STORAGE("Storage"),
    CLOCK("Date and time"),
    BATTERY("Battery"),
    ITEMS("Item count"),
    NONE("Hidden"),
}

/** 8×8 one-bit fill patterns, one byte per row, high bit on the left. */
enum class Pattern(val label: String, val rows: IntArray) {
    SOLID("Solid", intArrayOf(0, 0, 0, 0, 0, 0, 0, 0)),
    GRAY12("Light dots", intArrayOf(0x80, 0x00, 0x08, 0x00, 0x80, 0x00, 0x08, 0x00)),
    GRAY25("Dots", intArrayOf(0x88, 0x22, 0x88, 0x22, 0x88, 0x22, 0x88, 0x22)),
    GRAY50("Gray", intArrayOf(0xAA, 0x55, 0xAA, 0x55, 0xAA, 0x55, 0xAA, 0x55)),
    DIAGONAL("Diagonal", intArrayOf(0x80, 0x40, 0x20, 0x10, 0x08, 0x04, 0x02, 0x01)),
    WIDE_DIAGONAL("Stripes", intArrayOf(0xC1, 0x83, 0x07, 0x0E, 0x1C, 0x38, 0x70, 0xE0)),
    BRICK("Brick", intArrayOf(0xFF, 0x80, 0x80, 0x80, 0xFF, 0x08, 0x08, 0x08)),
    WEAVE("Weave", intArrayOf(0x88, 0x54, 0x22, 0x45, 0x88, 0x15, 0x22, 0x51)),
    GRID("Grid", intArrayOf(0xFF, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80)),
    CHECKER("Checker", intArrayOf(0xF0, 0xF0, 0xF0, 0xF0, 0x0F, 0x0F, 0x0F, 0x0F)),
    SCALES("Scales", intArrayOf(0x80, 0x41, 0x3E, 0x08, 0x08, 0x14, 0xE3, 0x80)),
    HLINES("Lines", intArrayOf(0xFF, 0x00, 0x00, 0x00, 0xFF, 0x00, 0x00, 0x00));

    fun bit(x: Int, y: Int): Boolean = (rows[y and 7] shr (7 - (x and 7))) and 1 == 1
}

/**
 * The four colours everything is drawn with, as 0xAARRGGBB. Windows are [paper]
 * with [ink] lines and text; the desktop is [desk] patterned with [deskInk].
 */
data class Palette(val ink: Int, val paper: Int, val desk: Int, val deskInk: Int)

/** Where app names go in the home window. BESIDE leaves icons the cell's full height. */
enum class LabelMode(val label: String) { UNDER("Under icons"), BESIDE("Beside icons"), HIDDEN("Hidden") }

/** Where the home window sits when it is smaller than the desktop. */
enum class HPos(val label: String) { LEFT("Left"), CENTER("Center"), RIGHT("Right") }
enum class VPos(val label: String) { TOP("Top"), MIDDLE("Middle"), BOTTOM("Bottom") }

/** Fonts bundled in res/font (plus the two system ones). */
enum class FontChoice(val label: String) {
    VGA("VGA 8×16"),
    PIXELIFY("Pixelify"),
    VT323("Terminal"),
    SILKSCREEN("Silkscreen"),
    PRESS_START("Arcade"),
    SYSTEM_MONO("System mono"),
    SYSTEM("System sans"),
}

data class Theme(
    val palette: Palette,
    val pattern: Pattern,
    val window: WindowStyle,
    val font: FontChoice = FontChoice.VGA,
    /** Text size in sp. */
    val textSize: Int = 16,
)

data class Preset(val name: String, val theme: Theme)

object Presets {
    val all = listOf(
        Preset("Green desk", Theme(Palette(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF00A800.toInt(), 0xFF000000.toInt()), Pattern.SOLID, WindowStyle.BOXED)),
        Preset("Gray desk", Theme(Palette(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt()), Pattern.GRAY50, WindowStyle.STRIPED, FontChoice.PIXELIFY, 17)),
        Preset("Office", Theme(Palette(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt()), Pattern.GRAY25, WindowStyle.BOXED, FontChoice.PIXELIFY, 17)),
        Preset("Blue screen", Theme(Palette(0xFFFFFFFF.toInt(), 0xFF0000AA.toInt(), 0xFF000055.toInt(), 0xFF5555FF.toInt()), Pattern.GRAY12, WindowStyle.BOXED)),
        Preset("Amber", Theme(Palette(0xFFFFB000.toInt(), 0xFF140C00.toInt(), 0xFF140C00.toInt(), 0xFF7A5200.toInt()), Pattern.GRAY25, WindowStyle.BOXED, FontChoice.VT323, 20)),
        Preset("Phosphor", Theme(Palette(0xFF33FF66.toInt(), 0xFF001A08.toInt(), 0xFF001A08.toInt(), 0xFF0E6B2A.toInt()), Pattern.DIAGONAL, WindowStyle.STRIPED, FontChoice.VT323, 20)),
        Preset("Cream", Theme(Palette(0xFF2B2B2B.toInt(), 0xFFF4EFE1.toInt(), 0xFFD8CFB8.toInt(), 0xFF8C826B.toInt()), Pattern.WEAVE, WindowStyle.STRIPED, FontChoice.PIXELIFY, 17)),
        Preset("Night", Theme(Palette(0xFFE0E0E0.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF555555.toInt()), Pattern.GRAY50, WindowStyle.BOXED)),
    )
    val default = all.first()
}

/** A slot on the home window: [index] counts across pages, page = index / (columns × rows). */
data class Prefs(
    val home: Map<Int, AppKey> = emptyMap(),
    /** Taskbar buttons; null is an empty button. Always [MAX_TASKBAR] long. */
    val taskbar: List<AppKey?> = List(MAX_TASKBAR) { null },
    val taskbarCount: Int = 3,
    val taskbarLabels: Boolean = true,
    val columns: Int = 2,
    val rows: Int = 8,
    val labels: LabelMode = LabelMode.UNDER,
    /** Home window size, percent of the desktop area. */
    val windowWidth: Int = 100,
    val windowHeight: Int = 100,
    val windowH: HPos = HPos.CENTER,
    val windowV: VPos = VPos.MIDDLE,
    val iconStyle: IconStyle = IconStyle.DITHER,
    /** Pixels per side for converted icons. */
    val iconRes: Int = 32,
    /**
     * Icon size, percent of 2.5 text lines. Icons never outgrow their grid
     * cell; past that they stay as big as fits.
     */
    val iconScale: Int = 100,
    val iconOutline: Boolean = true,
    /** Drop adaptive icons' background plate before converting, so only the glyph remains. */
    val iconStripBackground: Boolean = true,
    /**
     * The 1-bit styles use an app's own one-colour icon (Android 13 themed
     * icons) when it has one, and convert the colour icon only when it doesn't.
     */
    val preferMonochrome: Boolean = true,
    /** Package of the icon pack apps' icons come from first; blank for none. */
    val iconPack: String = "",
    /** Icons the user picked for single apps (IconRef text: a bundled glyph or a pack drawable). */
    val customIcons: Map<AppKey, String> = emptyMap(),
    val iconOverrides: Map<AppKey, IconStyle> = emptyMap(),
    /** "1-bit dither": the pattern (index into PatternDither.PATTERNS) of each palette colour, in palette order. */
    val ditherPatterns: List<Int> = com.asnidev.onebit.icons.PatternDither.DEFAULT_PATTERNS,
    val renames: Map<AppKey, String> = emptyMap(),
    val hidden: Set<AppKey> = emptySet(),
    val theme: Theme = Presets.default.theme,
    /** Title of the home window; blank uses the style's default. */
    val homeTitle: String = "",
    val infoBar: InfoBar = InfoBar.STORAGE,
    val showMenuBar: Boolean = true,
    val menuClock: Boolean = true,
    val hideStatusBar: Boolean = false,
    val zoomRects: Boolean = true,
    val swipeDownNotifications: Boolean = true,
    /** First start fills the home window and taskbar with the phone's default apps once. */
    val seeded: Boolean = false,
) {
    val pageSize: Int get() = columns * rows

    /** Pages in use, at least one. */
    val pages: Int get() = ((home.keys.maxOrNull() ?: 0) / pageSize) + 1

    fun styleFor(key: AppKey): IconStyle = iconOverrides[key] ?: iconStyle

    fun firstFreeSlot(): Int {
        var i = 0
        while (i in home) i++
        return i
    }

    /** Moves the app at [from] to [to]; whatever was at [to] goes to [from]. */
    fun swap(from: Int, to: Int): Prefs {
        if (from == to) return this
        val a = home[from] ?: return this
        val b = home[to]
        val m = home.toMutableMap()
        m[to] = a
        if (b != null) m[from] = b else m.remove(from)
        return copy(home = m)
    }

    /** Closes the gaps: apps keep their order, packed from the first slot. */
    fun cleanUp(): Prefs = copy(home = home.toSortedMap().values.withIndex().associate { (i, k) -> i to k })

    fun withoutApp(key: AppKey): Prefs = copy(
        home = home.filterValues { it != key },
        taskbar = taskbar.map { if (it == key) null else it },
    )

    companion object {
        const val MAX_TASKBAR = 3
        val COLUMNS = 1..6
        val ROWS = 2..10
        val ICON_RES = listOf(16, 24, 32, 48)
        val TEXT_SIZES = 12..24
        val ICON_SCALE = 50..200
        const val ICON_SCALE_STEP = 25
        val WINDOW_WIDTH = 40..100
        val WINDOW_HEIGHT = 30..100
        const val WINDOW_STEP = 5
    }
}
