import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging
import Shared

final class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        PushBridge.shared.start()
        // Set before returning so a cold-start tap reaches didReceive.
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self
        application.registerForRemoteNotifications()
        return true
    }

    // FirebaseAppDelegateProxyEnabled = NO, so the APNs token is mapped by hand.
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // FID registration still proceeds; APNs delivery starts once a token is mapped.
        NSLog("APNs registration failed: %@", error.localizedDescription)
    }
}

extension AppDelegate: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistration installationId: String?) {
        guard let installationId, !installationId.isEmpty else { return }
        PushBridge.shared.onRegistered(installationId: installationId)
    }
}

extension AppDelegate: UNUserNotificationCenterDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        Messaging.messaging().appDidReceiveMessage(notification.request.content.userInfo)
        PushBridge.shared.shouldPresentInForeground { show in
            completionHandler(show.boolValue ? [.banner, .list, .sound] : [])
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        Messaging.messaging().appDidReceiveMessage(userInfo)
        if response.actionIdentifier == UNNotificationDefaultActionIdentifier {
            PushBridge.shared.onNotificationTapped(userInfo: userInfo)
        }
        completionHandler()
    }
}