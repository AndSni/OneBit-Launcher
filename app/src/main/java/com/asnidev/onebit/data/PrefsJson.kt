package com.asnidev.onebit.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Prefs as JSON. Reading is forgiving: an unknown or missing field keeps its
 * default, so old files keep working as fields are added.
 */
object PrefsJson {

    fun write(p: Prefs): String = JSONObject().apply {
        put("home", JSONObject().apply { p.home.forEach { (i, k) -> put(i.toString(), k.encode()) } })
        put("taskbar", JSONArray().apply { p.taskbar.forEach { put(it?.encode() ?: "") } })
        put("taskbarCount", p.taskbarCount)
        put("taskbarLabels", p.taskbarLabels)
        put("columns", p.columns)
        put("rows", p.rows)
        put("labels", p.labels.name)
        put("windowWidth", p.windowWidth)
        put("windowHeight", p.windowHeight)
        put("windowH", p.windowH.name)
        put("windowV", p.windowV.name)
        put("iconStyle", p.iconStyle.name)
        put("iconRes", p.iconRes)
        put("iconScale", p.iconScale)
        put("iconOutline", p.iconOutline)
        put("iconStripBackground", p.iconStripBackground)
        put("preferMonochrome", p.preferMonochrome)
        put("iconPack", p.iconPack)
        put("customIcons", JSONObject().apply { p.customIcons.forEach { (k, v) -> put(k.encode(), v) } })
        put("iconOverrides", JSONObject().apply { p.iconOverrides.forEach { (k, s) -> put(k.encode(), s.name) } })
        put("ditherPatterns", JSONArray().apply { p.ditherPatterns.forEach { put(it) } })
        put("renames", JSONObject().apply { p.renames.forEach { (k, s) -> put(k.encode(), s) } })
        put("hidden", JSONArray().apply { p.hidden.forEach { put(it.encode()) } })
        put("theme", writeTheme(p.theme))
        put("homeTitle", p.homeTitle)
        put("infoBar", p.infoBar.name)
        put("showMenuBar", p.showMenuBar)
        put("menuClock", p.menuClock)
        put("hideStatusBar", p.hideStatusBar)
        put("zoomRects", p.zoomRects)
        put("swipeDownNotifications", p.swipeDownNotifications)
        put("seeded", p.seeded)
    }.toString()

    fun read(json: String?): Prefs {
        val d = Prefs()
        if (json.isNullOrBlank()) return d
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return d
        return Prefs(
            home = o.optJSONObject("home")?.let { h ->
                h.keys().asSequence().mapNotNull { i ->
                    val index = i.toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
                    AppKey.decode(h.optString(i))?.let { index to it }
                }.toMap()
            } ?: d.home,
            taskbar = o.optJSONArray("taskbar")?.let { a ->
                List(Prefs.MAX_TASKBAR) { i -> if (i < a.length()) AppKey.decode(a.optString(i)) else null }
            } ?: d.taskbar,
            taskbarCount = o.optInt("taskbarCount", d.taskbarCount).coerceIn(0, Prefs.MAX_TASKBAR),
            taskbarLabels = o.optBoolean("taskbarLabels", d.taskbarLabels),
            columns = o.optInt("columns", d.columns).coerceIn(Prefs.COLUMNS),
            rows = o.optInt("rows", d.rows).coerceIn(Prefs.ROWS),
            // Before 0.1.0's label modes there was only "showLabels".
            labels = if (o.has("labels")) enumOr(o.optString("labels"), d.labels)
            else if (!o.optBoolean("showLabels", true)) LabelMode.HIDDEN else d.labels,
            windowWidth = o.optInt("windowWidth", d.windowWidth).coerceIn(Prefs.WINDOW_WIDTH),
            windowHeight = o.optInt("windowHeight", d.windowHeight).coerceIn(Prefs.WINDOW_HEIGHT),
            windowH = enumOr(o.optString("windowH"), d.windowH),
            windowV = enumOr(o.optString("windowV"), d.windowV),
            iconStyle = enumOr(o.optString("iconStyle"), d.iconStyle),
            iconRes = o.optInt("iconRes", d.iconRes).takeIf { it in Prefs.ICON_RES } ?: d.iconRes,
            iconScale = o.optInt("iconScale", d.iconScale).coerceIn(Prefs.ICON_SCALE),
            iconOutline = o.optBoolean("iconOutline", d.iconOutline),
            iconStripBackground = o.optBoolean("iconStripBackground", d.iconStripBackground),
            preferMonochrome = o.optBoolean("preferMonochrome", d.preferMonochrome),
            iconPack = o.optString("iconPack", d.iconPack),
            customIcons = o.optJSONObject("customIcons")?.let { m ->
                m.keys().asSequence().mapNotNull { k -> AppKey.decode(k)?.let { it to m.optString(k) } }
                    .filter { it.second.isNotBlank() }.toMap()
            } ?: d.customIcons,
            iconOverrides = o.optJSONObject("iconOverrides")?.let { m ->
                m.keys().asSequence().mapNotNull { k ->
                    val key = AppKey.decode(k) ?: return@mapNotNull null
                    val style = runCatching { IconStyle.valueOf(m.optString(k)) }.getOrNull() ?: return@mapNotNull null
                    key to style
                }.toMap()
            } ?: d.iconOverrides,
            ditherPatterns = o.optJSONArray("ditherPatterns")?.let { a ->
                // One per palette colour; missing or unknown entries fall back to the default.
                d.ditherPatterns.mapIndexed { i, def ->
                    a.optInt(i, def).takeIf { it in com.asnidev.onebit.icons.PatternDither.PATTERNS.indices } ?: def
                }
            } ?: d.ditherPatterns,
            renames = o.optJSONObject("renames")?.let { m ->
                m.keys().asSequence().mapNotNull { k -> AppKey.decode(k)?.let { it to m.optString(k) } }
                    .filter { it.second.isNotBlank() }.toMap()
            } ?: d.renames,
            hidden = o.optJSONArray("hidden")?.let { a ->
                (0 until a.length()).mapNotNull { AppKey.decode(a.optString(it)) }.toSet()
            } ?: d.hidden,
            theme = o.optJSONObject("theme")?.let { readTheme(it, d.theme) } ?: d.theme,
            homeTitle = o.optString("homeTitle", d.homeTitle),
            infoBar = enumOr(o.optString("infoBar"), d.infoBar),
            showMenuBar = o.optBoolean("showMenuBar", d.showMenuBar),
            menuClock = o.optBoolean("menuClock", d.menuClock),
            hideStatusBar = o.optBoolean("hideStatusBar", d.hideStatusBar),
            zoomRects = o.optBoolean("zoomRects", d.zoomRects),
            swipeDownNotifications = o.optBoolean("swipeDownNotifications", d.swipeDownNotifications),
            seeded = o.optBoolean("seeded", d.seeded),
        )
    }

    private fun writeTheme(t: Theme) = JSONObject().apply {
        put("ink", t.palette.ink)
        put("paper", t.palette.paper)
        put("desk", t.palette.desk)
        put("deskInk", t.palette.deskInk)
        put("pattern", t.pattern.name)
        put("window", t.window.name)
        put("font", t.font.name)
        put("textSize", t.textSize)
    }

    private fun readTheme(o: JSONObject, d: Theme) = Theme(
        palette = Palette(
            ink = o.optInt("ink", d.palette.ink),
            paper = o.optInt("paper", d.palette.paper),
            desk = o.optInt("desk", d.palette.desk),
            deskInk = o.optInt("deskInk", d.palette.deskInk),
        ),
        pattern = enumOr(o.optString("pattern"), d.pattern),
        window = enumOr(o.optString("window"), d.window),
        font = enumOr(o.optString("font"), d.font),
        textSize = o.optInt("textSize", d.textSize).coerceIn(Prefs.TEXT_SIZES),
    )

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
}
