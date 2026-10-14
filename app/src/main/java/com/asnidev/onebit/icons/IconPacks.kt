package com.asnidev.onebit.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Icon packs installed on the phone, in the format most launchers read
 * (ADW, extended by Nova and others): an activity answering
 * `org.adw.ActivityStarter.THEMES`, and an appfilter.xml mapping each app's
 * component to a drawable in the pack.
 */
class IconPacks(private val context: Context) {

    data class Pack(val pkg: String, val label: String)

    /** A pack's map of "package/class" to drawable name, and every drawable it names. */
    class Filter(val byComponent: Map<String, String>, val byPackage: Map<String, String>, val drawables: List<String>)

    private val filters = ConcurrentHashMap<String, Filter>()

    fun installed(): List<Pack> {
        val pm = context.packageManager
        return ACTIONS.flatMap { action ->
            runCatching { pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
        }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .map { Pack(it.first, it.second) }
            .sortedBy { it.label.lowercase() }
    }

    fun label(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    }.getOrDefault(pkg)

    /** The pack's appfilter, read once (call off the main thread); null if the pack is gone or has none. */
    fun filter(pkg: String): Filter? {
        filters[pkg]?.let { return it }
        val parsed = runCatching { parse(pkg) }.onFailure { Log.w(TAG, "couldn't read $pkg's appfilter", it) }.getOrNull() ?: return null
        filters[pkg] = parsed
        return parsed
    }

    /** The drawable [pkg] maps to the app [appPkg]/[cls], if it has one. */
    fun drawableFor(pkg: String, appPkg: String, cls: String): String? {
        val f = filter(pkg) ?: return null
        return f.byComponent["$appPkg/$cls"] ?: f.byPackage[appPkg]
    }

    /** A drawable from the pack at high density (call off the main thread). */
    fun drawable(pkg: String, name: String): Drawable? = runCatching {
        val res = resources(pkg) ?: return null
        val id = res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 } ?: return null
        res.getDrawableForDensity(id, DisplayMetrics.DENSITY_XXHIGH, null)
    }.getOrNull()

    fun forget(pkg: String) {
        filters.remove(pkg)
    }

    private fun resources(pkg: String): Resources? = runCatching { context.packageManager.getResourcesForApplication(pkg) }.getOrNull()

    private fun parse(pkg: String): Filter? {
        val res = resources(pkg) ?: return null
        val xmlId = res.getIdentifier("appfilter", "xml", pkg)
        val parser: XmlPullParser = if (xmlId != 0) {
            res.getXml(xmlId)
        } else {
            val stream = runCatching { res.assets.open("appfilter.xml") }.getOrNull() ?: return null
            XmlPullParserFactory.newInstance().newPullParser().apply { setInput(stream, "utf-8") }
        }
        val byComponent = HashMap<String, String>()
        val byPackage = HashMap<String, String>()
        val drawables = LinkedHashSet<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
            val component = parser.getAttributeValue(null, "component") ?: continue
            val drawable = parser.getAttributeValue(null, "drawable")?.takeIf { it.isNotBlank() } ?: continue
            drawables += drawable
            // ComponentInfo{com.app/com.app.Main}; a class may be written relative (".Main").
            val inner = component.substringAfter('{', "").substringBefore('}', "")
            val appPkg = inner.substringBefore('/', "")
            var cls = inner.substringAfter('/', "")
            if (appPkg.isEmpty() || cls.isEmpty()) continue
            if (cls.startsWith(".")) cls = appPkg + cls
            byComponent["$appPkg/$cls"] = drawable
            byPackage.putIfAbsent(appPkg, drawable)
        }
        return Filter(byComponent, byPackage, drawables.sorted())
    }

    companion object {
        private const val TAG = "IconPacks"

        /** The intents icon packs answer; the first is the ADW standard every pack has. */
        val ACTIONS = listOf("org.adw.ActivityStarter.THEMES", "com.novalauncher.THEME")
    }
}
