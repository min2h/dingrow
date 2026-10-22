package app.mongle

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.app.WallpaperManager
import androidx.activity.ComponentActivity
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

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { updatePermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.ensure(this)
        updatePermissions()
        handleIntent(intent)
        setContent {
            val permissions by PermissionCenter.snapshot.collectAsState()
            MongleTheme {
                MongleAppScreen(
                    service = AppGraph.service,
                    permissions = permissions,
                    onRequestNotifications = { requestNotifications() },
                    onOpenExactAlarm = { openExactAlarmSettings() },
                    onOpenWallpaper = { openWallpaper() },
                )
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
    }

    private fun handleIntent(intent: Intent?) {
        val route = intent?.getStringExtra("route")
        val data = intent?.data
        val scheme = data?.scheme
        if (route == "add-schedule" || ((scheme == "dingrow" || scheme == "mongle") && data?.host == "schedule")) {
            NavigationBus.request("add-schedule")
        }
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
        PermissionCenter.snapshot.value = PermissionSnapshot(
            notificationsOn = notificationsOn,
            exactAlarmsOn = exact,
            supportsExactAlarm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            supportsWallpaper = true,
        )
    }
}
