package app.mongle

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.app.WallpaperManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import app.mongle.platform.NavigationBus
import app.mongle.ui.MongleAppScreen
import app.mongle.ui.MongleTheme
import app.mongle.ui.PermissionCenter
import app.mongle.ui.PermissionSnapshot
import app.mongle.wallpaper.PetWallpaperService
import app.mongle.widget.LargeWidgetReceiver
import app.mongle.widget.SmallWidgetReceiver

class MainActivity : ComponentActivity() {
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        AppGraph.launch {
            val st = AppGraph.service.state.value.settings
            AppGraph.service.updateSettings(st.copy(useLocation = granted))
            app.mongle.weather.WeatherFetcher.refresh(applicationContext, AppGraph.service, force = true)
        }
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { updatePermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppGraph.ensure(this)
        app.mongle.billing.BillingHost.activity = this
        updatePermissions()
        handleIntent(intent)
        app.mongle.ui.LocationBridge.request = { locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) }
        app.mongle.auth.AndroidAuth.activity = java.lang.ref.WeakReference(this)
        app.mongle.ui.ThemeState.mode = AppGraph.service.state.value.settings.theme
        setContent {
            val permissions by PermissionCenter.snapshot.collectAsState()
            val dark = app.mongle.ui.ThemeState.dark
            androidx.compose.runtime.LaunchedEffect(dark) {
                val style = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            MongleTheme {
                app.mongle.ui.AuthGate(app.mongle.auth.AndroidAuth.get(this)) {
                MongleAppScreen(
                    service = AppGraph.service,
                    permissions = permissions,
                    onRequestNotifications = { requestNotifications() },
                    onOpenExactAlarm = { openExactAlarmSettings() },
                    onOpenWallpaper = { openWallpaper() },
                    onPinWidget = { large -> pinWidget(large) },
                )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updatePermissions()
        AppGraph.launch { runCatching { app.mongle.weather.WeatherFetcher.refresh(applicationContext, AppGraph.service) } }
    }

    private fun handleIntent(intent: Intent?) {
        val route = intent?.getStringExtra("route")
        val data = intent?.data
        val scheme = data?.scheme
        val alarmId = intent?.getStringExtra("alarmScheduleId")
        if (!alarmId.isNullOrBlank()) NavigationBus.request("alarm:$alarmId")
        if (route == "gift") { NavigationBus.request("gift"); return }
        val focusId = scheduleIdFromUri(data)
        if (focusId != null) { NavigationBus.request("schedule/$focusId"); return }
        if (route == "add-schedule" || ((scheme == "dingrow" || scheme == "mongle") && data?.host == "schedule")) {
            NavigationBus.request("add-schedule")
        }
    }

    private fun pinWidget(large: Boolean) {
        val manager = AppWidgetManager.getInstance(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !manager.isRequestPinAppWidgetSupported) return
        val type = if (large) LargeWidgetReceiver::class.java else SmallWidgetReceiver::class.java
        val callback = PendingIntent.getActivity(
            this,
            71,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.requestPinAppWidget(ComponentName(this, type), null, callback)
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            updatePermissions()
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                },
            )
        }
    }

    private fun openWallpaper() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this@MainActivity, PetWallpaperService::class.java),
            )
        }
        runCatching { startActivity(intent) }
    }

    private fun updatePermissions() {
        val notificationsOn = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            true
        }
        val canPin = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            AppWidgetManager.getInstance(this).isRequestPinAppWidgetSupported
        PermissionCenter.snapshot.value = PermissionSnapshot(
            notificationsOn = notificationsOn,
            exactAlarmsOn = exact,
            supportsExactAlarm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            supportsWallpaper = true,
            canPinWidget = canPin,
        )
    }
}

/** `dingrow://schedule/<id>` → id; `/add` and other shapes → null. */
internal fun scheduleIdFromUri(data: android.net.Uri?): String? {
    if (data == null || data.scheme != "dingrow" || data.host != "schedule") return null
    val seg = data.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() && it != "add" } ?: return null
    return seg
}
