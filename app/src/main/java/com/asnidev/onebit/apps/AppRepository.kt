package com.asnidev.onebit.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.asnidev.onebit.data.AppKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.Collator

data class AppEntry(val key: AppKey, val label: String, val isWork: Boolean)

/** Every launchable activity across the user's profiles, kept fresh via LauncherApps callbacks. */
class AppRepository(private val context: Context, scope: CoroutineScope) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    /** Bumped on every package change, so icons are redrawn (an update may bring a new one). */
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation

    @Volatile
    private var infos: Map<AppKey, LauncherActivityInfo> = emptyMap()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    /**
     * Refresh requests, conflated: one loader reads them in order, so a slow older
     * load can never overwrite a newer list, and a burst of package events costs
     * one or two loads, not one each.
     */
    private val refreshes = Channel<Unit>(Channel.CONFLATED)

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        scope.launch(Dispatchers.IO) {
            for (request in refreshes) {
                // A failed load keeps the last good list; the next package event or resume retries.
                runCatching {
                    val loaded = load()
                    infos = loaded.associateBy { it.first.key }.mapValues { it.value.second }
                    _apps.value = loaded.map { it.first }
                    _generation.value++
                }.onFailure { Log.w(TAG, "couldn't list apps", it) }
            }
        }
        refresh()
    }

    fun close() {
        refreshes.close()
        launcherApps.unregisterCallback(callback)
    }

    fun refresh() {
        refreshes.trySend(Unit)
    }

    private fun load(): List<Pair<AppEntry, LauncherActivityInfo>> {
        val me = Process.myUserHandle()
        val collator = Collator.getInstance()
        return userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            // A locked or removed profile throws; the other profiles still count.
            runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
                .filter { it.componentName.packageName != context.packageName }
                .map {
                    AppEntry(
                        key = AppKey(it.componentName.packageName, it.componentName.className, serial),
                        label = it.label.toString(),
                        isWork = user != me,
                    ) to it
                }
        }
            // Lists key their rows by app: a duplicate (seen on some phones) would crash them.
            .distinctBy { it.first.key }
            .sortedWith { a, b -> collator.compare(a.first.label, b.first.label) }
    }

    /** A fresh copy of the app's icon (safe to resize and draw on any thread), or null. */
    fun icon(key: AppKey): Drawable? = runCatching { infos[key]?.getIcon(0) }.getOrNull()

    /** Whether the app's icon has a one-colour layer (Android 13+ themed icons). */
    fun hasMonochrome(key: AppKey): Boolean =
        android.os.Build.VERSION.SDK_INT >= 33 && (icon(key) as? android.graphics.drawable.AdaptiveIconDrawable)?.monochrome != null

    private fun handle(key: AppKey): UserHandle? = runCatching { userManager.getUserForSerialNumber(key.user) }.getOrNull()

    fun launch(key: AppKey, bounds: Rect?): Boolean {
        val user = handle(key) ?: return false
        return try {
            launcherApps.startMainActivity(ComponentName(key.pkg, key.cls), user, bounds, null)
            true
        } catch (e: RuntimeException) { // SecurityException / ActivityNotFoundException
            Log.w(TAG, "couldn't launch $key", e)
            false
        }
    }

    fun openInfo(key: AppKey) {
        val user = handle(key) ?: return
        try {
            launcherApps.startAppDetailsActivity(ComponentName(key.pkg, key.cls), user, null, null)
        } catch (_: RuntimeException) { // SecurityException / ActivityNotFoundException (paused work profile)
        }
    }

    fun uninstall(key: AppKey) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${key.pkg}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Intent.EXTRA_USER, handle(key))
        try {
            context.startActivity(intent)
        } catch (_: RuntimeException) { // ActivityNotFoundException / SecurityException
        }
    }

    /**
     * The phone's default apps for a first start, in a sensible order: the
     * first three go on the taskbar, the rest into the home window.
     */
    fun defaults(): List<AppKey> {
        val list = apps.value.filter { !it.isWork }
        val pm = context.packageManager
        fun byIntent(intent: Intent): AppKey? {
            val pkg = runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
                .getOrNull() ?: return null
            // "android" is the chooser: no default set.
            return list.firstOrNull { it.key.pkg == pkg }?.key
        }
        fun category(c: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, c)
        val wanted = listOf(
            Intent(Intent.ACTION_DIAL),
            category(Intent.CATEGORY_APP_MESSAGING),
            category(Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            category(Intent.CATEGORY_APP_GALLERY),
            category(Intent.CATEGORY_APP_CONTACTS),
            category(Intent.CATEGORY_APP_CALENDAR),
            category(Intent.CATEGORY_APP_EMAIL),
            category(Intent.CATEGORY_APP_MAPS),
            category(Intent.CATEGORY_APP_MUSIC),
            category(Intent.CATEGORY_APP_CALCULATOR),
            Intent(Settings.ACTION_SETTINGS),
        )
        return wanted.mapNotNull(::byIntent).distinct()
    }

    private companion object {
        const val TAG = "AppRepository"
    }
}
