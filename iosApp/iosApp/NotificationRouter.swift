import Foundation
import UserNotifications
import WidgetKit
import ComposeApp

final class NotificationRouter: NSObject, UNUserNotificationCenterDelegate {
    func registerCategories() {
        let lang = MongleKit.snapshot().language
        let ack = UNNotificationAction(identifier: "ACK", title: DingrowStrings.text("action_ack", language: lang), options: [])
        let snooze = UNNotificationAction(identifier: "SNOOZE", title: DingrowStrings.text("action_snooze", language: lang), options: [])
        let complete = UNNotificationAction(identifier: "COMPLETE", title: DingrowStrings.text("action_done", language: lang), options: [])
        let category = UNNotificationCategory(
            identifier: "DINGROW_SCHEDULE",
            actions: [ack, snooze, complete],
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
            case "ACK":
                MongleKit.acknowledge(id: scheduleId)
            case "SNOOZE":
                MongleKit.snooze(id: scheduleId)
            case "COMPLETE":
                MongleKit.completeSchedule(id: scheduleId)
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
