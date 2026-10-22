import SwiftUI
import ComposeApp
import UserNotifications
import WidgetKit
import CoreLocation
import StoreKit
import AuthenticationServices
import Security
import CryptoKit
#if canImport(FirebaseCore) && canImport(FirebaseAuth) && canImport(FirebaseFirestore) && canImport(FirebaseFunctions)
import FirebaseCore
import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions
#endif

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
        FirebaseCloud.shared.install()   // before MongleKit/MainViewController so IosCloudBridge sees the hooks
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
    private var nonce = ""

    static func randomNonce() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return bytes.map { String(format: "%02x", $0) }.joined()
    }

    func install() {
        IosAuth.shared.secureGet = { key in Keychain.get(key) }
        IosAuth.shared.secureSet = { key, value in Keychain.set(key, value) }
        IosAuth.shared.appleSignIn = { complete in
            self.completion = { id, name, email in _ = complete(id, name, email) }
            let request = ASAuthorizationAppleIDProvider().createRequest()
            request.requestedScopes = [.fullName, .email]
            self.nonce = AppleSignIn.randomNonce()
            request.nonce = SHA256.hash(data: Data(self.nonce.utf8)).map { String(format: "%02x", $0) }.joined()
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let cred = authorization.credential as? ASAuthorizationAppleIDCredential else { completion?("", "", ""); return }
        let name = [cred.fullName?.familyName, cred.fullName?.givenName].compactMap { $0 }.joined()
        // Firebase account linking (IosAuth -> CloudHooks.link("apple", idToken, rawNonce)).
        IosAuth.shared.appleIdToken = cred.identityToken.flatMap { String(data: $0, encoding: .utf8) } ?? ""
        IosAuth.shared.appleNonce = nonce
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
        // Server verification (Cloud Functions -> App Store Server API). Rejected = no grant, no finish;
        // unavailable (no Firebase / offline) = local grant; the server credit later dedups on the same token.
        if await FirebaseCloud.shared.verify(productId: tx.productID, transactionId: String(tx.id)) == "rejected" {
            return MongleKit.text("msg_purchase_unverified")
        }
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


// MARK: - Firebase server save (Auth anonymous/Apple, Firestore users/{uid}, callable functions).
// Compiles to a no-op until the Firebase SPM packages and GoogleService-Info.plist are added (see FIREBASE_SETUP.md).

final class FirebaseCloud {
    static let shared = FirebaseCloud()

    func install() {
        #if canImport(FirebaseCore) && canImport(FirebaseAuth) && canImport(FirebaseFirestore) && canImport(FirebaseFunctions)
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else { return }
        FirebaseApp.configure()
        let bridge = IosCloudBridge.shared
        let db = Firestore.firestore()
        let fns = Functions.functions(region: "asia-northeast3")
        bridge.ensureUser = { done in
            if let u = Auth.auth().currentUser { _ = done(u.uid); return }
            Auth.auth().signInAnonymously { r, _ in _ = done(r?.user.uid) }
        }
        bridge.fetch = { uid, done in
            db.collection("users").document(uid).getDocument { snap, err in
                guard err == nil, let d = snap?.data(), let state = d["state"] as? String else {
                    _ = done(nil, err == nil ? 0 : -1); return
                }
                let at = (d["updatedAt"] as? Timestamp)?.dateValue().timeIntervalSince1970 ?? 0
                _ = done(state, Int64(at * 1000))
            }
        }
        bridge.credits = { uid, done in
            db.collection("users").document(uid).collection("credits").getDocuments { snap, _ in
                let rows: [[String: Any]] = (snap?.documents ?? []).map { doc in
                    let d = doc.data()
                    return ["token": doc.documentID, "productId": d["productId"] as? String ?? "",
                            "coins": d["coins"] as? Int ?? 0, "premium": d["premium"] as? Bool ?? false,
                            "legendary": d["legendary"] as? Bool ?? false,
                            "createdAt": Int64(((d["createdAt"] as? Timestamp)?.dateValue().timeIntervalSince1970 ?? 0) * 1000)]
                }
                let json = (try? JSONSerialization.data(withJSONObject: rows)).flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
                _ = done(json)
            }
        }
        bridge.push = { uid, state, summary, done in
            let sum = (try? JSONSerialization.jsonObject(with: Data(summary.utf8))) as? [String: Any] ?? [:]
            let ref = db.collection("users").document(uid)
            ref.setData(["state": state, "schema": 1, "summary": sum, "updatedAt": FieldValue.serverTimestamp()], merge: true) { err in
                if err != nil { _ = done(-1); return }
                ref.getDocument(source: .server) { snap, _ in
                    let at = (snap?.data()?["updatedAt"] as? Timestamp)?.dateValue().timeIntervalSince1970 ?? Date().timeIntervalSince1970
                    _ = done(Int64(at * 1000))
                }
            }
        }
        bridge.link = { provider, idToken, nonce, done in
            guard provider == "apple" else { _ = done(nil); return }
            let cred = OAuthProvider.appleCredential(withIDToken: idToken, rawNonce: nonce, fullName: nil)
            let finish: (AuthDataResult?, Error?) -> Void = { r, err in
                if let r = r { _ = done(r.user.uid); return }
                // Already linked to another Firebase user (reinstall / second device): sign into that account to restore.
                if let e = err as NSError?, e.code == AuthErrorCode.credentialAlreadyInUse.rawValue {
                    let again = (e.userInfo[AuthErrorUserInfoUpdatedCredentialKey] as? AuthCredential) ?? cred
                    Auth.auth().signIn(with: again) { r2, _ in _ = done(r2?.user.uid) }
                } else { _ = done(nil) }
            }
            if let u = Auth.auth().currentUser { u.link(with: cred, completion: finish) }
            else { Auth.auth().signIn(with: cred, completion: finish) }
        }
        bridge.verify = { platform, productId, token, done in
            fns.httpsCallable("verifyPurchase").call(["platform": platform, "productId": productId, "token": token]) { r, err in
                if let e = err as NSError?, e.domain == FunctionsErrorDomain {
                    let code = FunctionsErrorCode(rawValue: e.code)
                    let rejected = code == .invalidArgument || code == .permissionDenied || code == .failedPrecondition
                    _ = done(rejected ? "rejected" : "unavailable", 0); return
                }
                if err != nil { _ = done("unavailable", 0); return }
                let coins = (r?.data as? [String: Any])?["coins"] as? Int ?? 0
                _ = done("ok", Int32(coins))
            }
        }
        bridge.deleteAccount = { done in
            fns.httpsCallable("deleteAccount").call { _, err in
                if err == nil { try? Auth.auth().signOut(); _ = done(true); return }
                guard let u = Auth.auth().currentUser else { _ = done(false); return }
                db.collection("users").document(u.uid).delete { e in
                    if e != nil { _ = done(false); return }
                    u.delete { e2 in _ = done(e2 == nil) }
                }
            }
        }
        bridge.signOut = { try? Auth.auth().signOut() }
        #endif
    }

    /** "ok" | "rejected" | "unavailable" for a StoreKit 2 transaction. */
    func verify(productId: String, transactionId: String) async -> String {
        guard let hook = IosCloudBridge.shared.verify, let ensure = IosCloudBridge.shared.ensureUser else { return "unavailable" }
        return await withCheckedContinuation { cont in
            ensure { uid in
                guard uid != nil else { cont.resume(returning: "unavailable"); return }
                hook("ios", productId, transactionId) { status, _ in cont.resume(returning: status) }
            }
        }
    }
}
