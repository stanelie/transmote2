import SwiftUI
import UIKit
import UserNotifications
import BackgroundTasks
import Shared

/// Hand a magnet link or a .torrent file to the shared layer.
///
/// Called from SwiftUI's onOpenURL. The UIApplicationDelegate's
/// application(_:open:options:) is NOT reliably invoked under the SwiftUI
/// App lifecycle - SwiftUI intercepts URL opens and delivers them to the
/// scene instead - so routing through onOpenURL is what actually works.
func handleIncomingURL(_ url: URL) {
    guard url.isFileURL else {
        PendingTorrentLink.shared.offerMagnet(url: url.absoluteString)
        return
    }
    // A file opened from elsewhere arrives security-scoped, and iOS copies
    // it into Inbox/ which we should not leave litter in.
    let scoped = url.startAccessingSecurityScopedResource()
    defer { if scoped { url.stopAccessingSecurityScopedResource() } }
    do {
        let data = try Data(contentsOf: url)
        PendingTorrentLink.shared.offerMetainfo(base64: data.base64EncodedString())
    } catch {
        NSLog("could not read .torrent at \(url.lastPathComponent): \(error.localizedDescription)")
        return
    }
    if url.path.contains("/Inbox/") {
        try? FileManager.default.removeItem(at: url)
    }
}

let backgroundRefreshId = "ca.exmachina.transmote2.refresh"

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    /// iOS decides when this actually runs - the interval below is a hint,
    /// not a schedule. Reschedule on every fire, or it never runs again.
    func scheduleBackgroundRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: backgroundRefreshId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            PushRegistration.shared.onError(message: "Background refresh not scheduled: \(error.localizedDescription)")
        }
    }

    private func handleRefresh(task: BGAppRefreshTask) {
        scheduleBackgroundRefresh()          // always queue the next one first

        task.expirationHandler = {
            task.setTaskCompleted(success: false)
        }
        BackgroundRefreshKt.runBackgroundCheck { posted, error in
            // Reporting that work produced something makes iOS more willing
            // to grant future slots.
            task.setTaskCompleted(success: error == nil && (posted?.intValue ?? 0) >= 0)
        }
    }

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self

        // Must be registered before didFinishLaunching returns.
        BGTaskScheduler.shared.register(forTaskWithIdentifier: backgroundRefreshId, using: nil) { task in
            guard let refresh = task as? BGAppRefreshTask else { return }
            self.handleRefresh(task: refresh)
        }
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if let error = error {
                PushRegistration.shared.onError(message: "Authorization failed: \(error.localizedDescription)")
                return
            }
            guard granted else {
                PushRegistration.shared.onError(message: "Notifications not permitted")
                return
            }
            DispatchQueue.main.async {
                application.registerForRemoteNotifications()
            }
        }
        scheduleBackgroundRefresh()
        return true
    }

    /// Queue the next refresh whenever the app leaves the foreground.
    func applicationDidEnterBackground(_ application: UIApplication) {
        scheduleBackgroundRefresh()
    }

    /// Catch up on whatever happened while the app was away. iOS may never
    /// have granted a background slot, so without this the user could learn
    /// nothing until they scrolled the list themselves.
    func applicationDidBecomeActive(_ application: UIApplication) {
        BackgroundRefreshKt.runBackgroundCheck { _, error in
            if let error = error {
                NSLog("foreground catch-up failed: \(error.localizedDescription)")
            }
        }
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        PushRegistration.shared.onToken(token: token)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        PushRegistration.shared.onError(message: error.localizedDescription)
    }

    /// A silent push - {"aps":{"content-available":1}}, no alert - is the
    /// server saying "go and look", nothing more. Because it carries no
    /// content, the sender needs to know neither the RSS feed URL nor which
    /// Transmission host this device talks to: both stay in app settings,
    /// where they belong, and the check runs here against them.
    ///
    /// This supplements BGAppRefreshTask, it does not replace it. iOS does
    /// NOT deliver silent pushes to an app the user force-quit, holds them in
    /// Low Power Mode, and throttles them, so a wake can simply never arrive.
    /// We get roughly 30 seconds, and must call the handler within it.
    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        BackgroundRefreshKt.runBackgroundCheck { posted, error in
            if let error = error {
                NSLog("woken by push, but the check failed: \(error.localizedDescription)")
                completionHandler(.failed)
                return
            }
            // Claiming .newData only when something was actually posted is
            // what keeps iOS willing to grant these wake-ups.
            completionHandler((posted?.intValue ?? 0) > 0 ? .newData : .noData)
        }
    }

    /// Show notifications even while the app is in the foreground.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }

    /// Route a tapped notification to the screen its payload asks for.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let info = response.notification.request.content.userInfo
        let screen = info["screen"] as? String
        PushRouting.shared.onNotificationOpened(screen: screen)
        completionHandler()
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
                .onOpenURL { url in
                    NSLog("onOpenURL: \(url.scheme ?? "?")")
                    handleIncomingURL(url)
                }
        }
    }
}
