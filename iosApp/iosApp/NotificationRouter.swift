import Foundation
import UserNotifications
import WidgetKit
import ComposeApp

final class NotificationRouter: NSObject, UNUserNotificationCenterDelegate {
    func registerCategories() {
        let complete = UNNotificationAction(
            identifier: "COMPLETE",
            title: "완료",
            options: [.foreground]
        )
        let snooze = UNNotificationAction(
            identifier: "SNOOZE",
            title: "10분 뒤",
            options: []
        )
        let category = UNNotificationCategory(
            identifier: "DINGROW_SCHEDULE",
            actions: [complete, snooze],
            intentIdentifiers: [],
            options: []
        )
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let info = response.notification.request.content.userInfo
        let scheduleId = (info["scheduleId"] as? String) ?? ""
        if !scheduleId.isEmpty {
            switch response.actionIdentifier {
            case "COMPLETE":
                MongleKit.completeSchedule(id: scheduleId)
            case "SNOOZE":
                MongleKit.snooze(id: scheduleId)
            default:
                break
            }
            WidgetCenter.shared.reloadAllTimelines()
        }
        completionHandler()
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .list])
    }
}
