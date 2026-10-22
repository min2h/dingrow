import SwiftUI
import ComposeApp
import UserNotifications
import WidgetKit
import CoreLocation
import StoreKit
import AuthenticationServices
import Security

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
        guard url.scheme == "dingrow" || url.scheme == "mongle", host == "schedule" || path.contains("schedule") else { return }
        // dingrow://schedule/<id> (widget row / notification) focuses that schedule; /add opens the composer.
        let id = url.pathComponents.filter { $0 != "/" }.first ?? ""
        if !id.isEmpty && id != "add" {
            MongleBridge.shared.openSchedule(id: id)
        } else {
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
        IosHooks.shared.reloadWidgets = { WidgetCenter.shared.reloadAllTimelines() }
        UNUserNotificationCenter.current().delegate = router
        router.registerCategories()
        WeatherLocation.shared.attach()
        LocationBridge.shared.request = { WeatherLocation.shared.requestPermission() }
        AppleSignIn.shared.install()
        CoinStore.shared.start()
        return true
    }
}


// MARK: - Weather (CoreLocation). Weather stays OFF unless the user grants location; no city fallback.

final class WeatherLocation: NSObject, CLLocationManagerDelegate {
    static let shared = WeatherLocation()
    private let manager = CLLocationManager()

    func attach() {
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
        refreshIfAllowed()
    }

    func requestPermission() { manager.requestWhenInUseAuthorization() }

    func refreshIfAllowed() {
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways: manager.requestLocation()
        case .denied, .restricted: MongleKit.clearWeather()
        default: break
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) { refreshIfAllowed() }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        // Coarse: 2 decimals (~1 km), same as Android.
        let lat = (loc.coordinate.latitude * 100).rounded() / 100
        let lon = (loc.coordinate.longitude * 100).rounded() / 100
        let url = URL(string: "https://api.open-meteo.com/v1/forecast?latitude=\(lat)&longitude=\(lon)&current=temperature_2m,weather_code,wind_speed_10m,precipitation&hourly=temperature_2m,weather_code&forecast_hours=24&timezone=auto")!
        URLSession.shared.dataTask(with: url) { data, _, _ in
            guard let data, let json = String(data: data, encoding: .utf8) else { return }
            DispatchQueue.main.async {
                MongleKit.applyWeather(json: json, lat: lat, lon: lon)
                WidgetCenter.shared.reloadAllTimelines()
            }
        }.resume()
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {}
}

// MARK: - Sign in with Apple (stub wired into the shared login screen).

final class AppleSignIn: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    static let shared = AppleSignIn()
    private var completion: ((String, String, String) -> Void)?

    func install() {
        IosAuth.shared.secureGet = { key in Keychain.get(key) }
        IosAuth.shared.secureSet = { key, value in Keychain.set(key, value) }
        IosAuth.shared.appleSignIn = { complete in
            self.completion = { id, name, email in _ = complete(id, name, email) }
            let request = ASAuthorizationAppleIDProvider().createRequest()
            request.requestedScopes = [.fullName, .email]
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let cred = authorization.credential as? ASAuthorizationAppleIDCredential else { completion?("", "", ""); return }
        let name = [cred.fullName?.familyName, cred.fullName?.givenName].compactMap { $0 }.joined()
        completion?(cred.user, name, cred.email ?? "")
        completion = nil
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        completion?("", "", "")
        completion = nil
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first ?? ASPresentationAnchor()
    }
}

// MARK: - StoreKit 2 coin packs. Product ids come from the shared StoreProducts config.

@MainActor
final class CoinStore {
    static let shared = CoinStore()
    private(set) var products: [Product] = []
    private var updates: Task<Void, Never>?

    func start() {
        updates = Task { for await result in Transaction.updates { await self.handle(result) } }
        // Consumables not yet finished (app killed mid-purchase, Ask to Buy approved later): grant (idempotent by transaction id) + finish.
        Task { for await result in Transaction.unfinished { await self.handle(result) } }
        Task {
            products = (try? await Product.products(for: MongleKit.productIds())) ?? []
            MongleKit.updatePrices(Dictionary(uniqueKeysWithValues: products.map { ($0.id, $0.displayPrice) }))
        }
    }

    /// Localized price string from the App Store (e.g. "₩2,500" / "$1.99").
    func price(for id: String) -> String? { products.first { $0.id == id }?.displayPrice }

    func buy(_ id: String) async -> String {
        guard let product = products.first(where: { $0.id == id }) else { return "" }
        do {
            switch try await product.purchase() {
            case .success(let result): return await handle(result)
            case .pending: return MongleKit.text("msg_billing_pending")   // Ask to Buy / SCA: granted later via Transaction.updates
            default: return ""
            }
        } catch { return error.localizedDescription }
    }

    @discardableResult
    private func handle(_ result: VerificationResult<Transaction>) async -> String {
        guard case .verified(let tx) = result else { return "" }
        let msg = MongleKit.grantPurchase(productId: tx.productID, transactionId: String(tx.id))
        await tx.finish()
        WidgetCenter.shared.reloadAllTimelines()
        return msg
    }
}


/// Minimal generic-password Keychain wrapper for the sign-in session.
enum Keychain {
    private static let service = "app.dingrow.auth"
    private static func query(_ key: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: key]
    }
    static func get(_ key: String) -> String? {
        var q = query(key)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
    static func set(_ key: String, _ value: String?) {
        SecItemDelete(query(key) as CFDictionary)
        guard let value = value, let data = value.data(using: .utf8) else { return }
        var q = query(key)
        q[kSecValueData as String] = data
        q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(q as CFDictionary, nil)
    }
}
