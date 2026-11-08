package com.stanelie.transmote2

/**
 * Posts a notification from the device itself - no server involved.
 *
 * [screen] matches the push payload's "screen" value so a tap lands in the
 * same place whether the notification came from here or from APNs.
 */
expect fun showLocalNotification(id: String, title: String, body: String, screen: String?)
