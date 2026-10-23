package app.mongle

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import app.mongle.platform.MongleBridge
import app.mongle.platform.MongleSession
import app.mongle.ui.MongleAppScreen
import app.mongle.ui.MongleTheme
import app.mongle.ui.PermissionCenter
import app.mongle.ui.PermissionSnapshot
import platform.UIKit.UIViewController
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

fun MainViewController(): UIViewController {
    MongleBridge.start()
    app.mongle.cloud.IosCloudBridge.install() // no-op unless FirebaseCloud.swift installed its hooks
    PermissionCenter.snapshot.value = PermissionSnapshot(
        notificationsOn = false,
        exactAlarmsOn = true,
        supportsExactAlarm = false,
        supportsWallpaper = false,
    )
    // Keyboard: the root Box pads by WindowInsets.safeDrawing (includes the IME inset), so Compose's default
    // FocusableAboveKeyboard view shift doubled it and left the form lifted/clipped after the keyboard closed.
    // Insets alone now move the bottom Save button above the keyboard and back down when it hides.
    return ComposeUIViewController(configure = { onFocusBehavior = androidx.compose.ui.uikit.OnFocusBehavior.DoNothing }) {
        val permissions by PermissionCenter.snapshot.collectAsState()
        MongleTheme {
          app.mongle.ui.AuthGate(IosAuth.service) {
            MongleAppScreen(
                service = MongleSession.service,
                permissions = permissions,
                onRequestNotifications = { requestIosNotifications() },
                onOpenExactAlarm = {},
                onOpenWallpaper = {},
            )
          }
        }
    }
}

private fun requestIosNotifications() {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
    center.requestAuthorizationWithOptions(options) { granted, _ ->
        PermissionCenter.snapshot.value = PermissionCenter.snapshot.value.copy(notificationsOn = granted)
    }
}
