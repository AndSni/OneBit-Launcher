package com.asnidev.onebit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.asnidev.onebit.data.IconStyle
import com.asnidev.onebit.data.Presets
import com.asnidev.onebit.ui.Desktop
import com.asnidev.onebit.ui.LocalRetro
import com.asnidev.onebit.ui.LocalVm
import com.asnidev.onebit.ui.rememberRetro

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        debugExtras(intent)
        setContent { Root(vm, this) }
    }

    /** Pressing Home while already home closes the drawer, options and boxes. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.closeAll()
        debugExtras(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.repo.refresh()
    }

    /** Coming back from an app finds the bare desktop. */
    override fun onStop() {
        super.onStop()
        vm.closeAll()
    }

    /**
     * Debug builds only, for looking at things over adb without saving them:
     * `--es preset "Amber"`, `--es icons dither|pixel|…`, `--ez drawer true`,
     * `--ez options true`, `--es iconpack <package|none>`, `--es picker <app label>`
     * (opens that app's icon picker). These are saved like a user change would be.
     */
    private fun debugExtras(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        intent.getStringExtra("preset")?.let { name ->
            Presets.all.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { p -> vm.update { it.copy(theme = p.theme) } }
        }
        intent.getStringExtra("icons")?.let { name ->
            IconStyle.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { s -> vm.update { it.copy(iconStyle = s) } }
        }
        if (intent.getBooleanExtra("drawer", false)) vm.drawer = 1f
        if (intent.getBooleanExtra("options", false)) vm.settingsOpen = true
        if (intent.getBooleanExtra("iconsheet", false)) vm.iconSheet(intent.getIntExtra("sheetres", 0).takeIf { it > 0 })
        if (intent.getBooleanExtra("monostats", false)) vm.monoStats()
        intent.getStringExtra("iconpack")?.let { vm.setIconPack(if (it == "none") "" else it) }
        intent.getStringExtra("picker")?.let { label ->
            vm.apps.value.firstOrNull { it.label.equals(label, ignoreCase = true) }?.let { vm.dialog = Dialog.IconPicker(it.key) }
        }
    }
}

@Composable
private fun Root(vm: LauncherViewModel, activity: ComponentActivity) {
    val prefs by vm.prefs.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val retro = rememberRetro(prefs.theme)

    // Bar icons dark on light colours, light on dark: top over the menu bar (paper), bottom over the desk.
    val top = if (prefs.showMenuBar) retro.paper else retro.desk
    LaunchedEffect(top, retro.desk) {
        fun style(c: Color) = if (c.luminance() > 0.5f) {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(style(top), style(retro.desk))
    }
    LaunchedEffect(prefs.hideStatusBar) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (prefs.hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars())
        else controller.show(WindowInsetsCompat.Type.statusBars())
    }

    // A launcher never backs out of itself; Back closes the topmost thing.
    BackHandler {
        when {
            vm.dialog != null -> vm.dialog = null
            vm.settingsOpen -> vm.settingsOpen = false
            vm.drawer > 0f -> { vm.drawer = 0f; vm.pickingFor = null }
        }
    }

    CompositionLocalProvider(LocalRetro provides retro, LocalVm provides vm) {
        if (loaded) Desktop(vm, prefs)
        else Box(Modifier.fillMaxSize().background(Color(Presets.default.theme.palette.desk)))
    }
}
