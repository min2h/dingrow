import SwiftUI
import ComposeApp
import UserNotifications

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    open(url)
                }
        }
    }

    private func open(_ url: URL) {
        let host = url.host?.lowercased() ?? ""
        let path = url.path.lowercased()
        if url.scheme == "mongle", host == "schedule" || path.contains("schedule") {
            NavigationBus.shared.request(route: "add-schedule")
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    let router = NotificationRouter()

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        MongleKit.start()
        UNUserNotificationCenter.current().delegate = router
        router.registerCategories()
        return true
    }
}
