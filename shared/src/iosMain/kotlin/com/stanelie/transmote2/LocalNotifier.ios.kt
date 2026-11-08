package com.stanelie.transmote2

import platform.Foundation.NSUUID
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

actual fun showLocalNotification(id: String, title: String, body: String, screen: String?) {
    val content = UNMutableNotificationContent().apply {
        setTitle(title)
        setBody(body)
        setSound(UNNotificationSound.defaultSound)
        // Same key the APNs payload uses, so taps route identically.
        setUserInfo(mapOf<Any?, Any?>("screen" to (screen ?: "main")))
        setThreadIdentifier("transmote")
    }
    // nil trigger = deliver immediately.
    val request = UNNotificationRequest.requestWithIdentifier(
        identifier = id.ifBlank { NSUUID().UUIDString },
        content = content,
        trigger = null
    )
    UNUserNotificationCenter.currentNotificationCenter()
        .addNotificationRequest(request) { error ->
            if (error != null) Logger.e("LocalNotifier", "could not post: $error")
        }
}
