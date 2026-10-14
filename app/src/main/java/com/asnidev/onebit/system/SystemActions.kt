package com.asnidev.onebit.system

import android.annotation.SuppressLint
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

object SystemActions {

    /** No public API for this; every launcher uses the same StatusBarManager reflection. */
    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context) {
        runCatching {
            val sbm = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
        }
    }

    fun openSettings(context: Context) = open(context, Intent(Settings.ACTION_SETTINGS))

    fun isDefaultLauncher(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == context.packageName
    }

    /** Asks to become the home app: the system's role dialog where there is one, else the settings page. */
    fun requestDefaultLauncher(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = activity.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                runCatching { activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 1) }
                    .onSuccess { return }
            }
        }
        open(activity, Intent(Settings.ACTION_HOME_SETTINGS))
    }

    /**
     * Starts [intent]; false when nothing handles it. Settings screens are
     * missing or locked down on some phones, which must not crash the launcher.
     */
    fun open(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) { // ActivityNotFoundException, SecurityException
        Log.w("SystemActions", "couldn't open $intent: $e")
        false
    }
}
