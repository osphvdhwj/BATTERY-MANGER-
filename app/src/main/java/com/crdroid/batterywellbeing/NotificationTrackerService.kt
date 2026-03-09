package com.crdroid.batterywellbeing

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationTrackerService : NotificationListenerService() {

    // In-memory counter: PackageName -> Count
    companion object {
        val dailyNotificationCounts = mutableMapOf<String, Int>()
        var totalNotificationsToday = 0
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        dailyNotificationCounts[pkg] = (dailyNotificationCounts[pkg] ?: 0) + 1
        totalNotificationsToday++
    }
}
