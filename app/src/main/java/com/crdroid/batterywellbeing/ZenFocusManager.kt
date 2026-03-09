package com.crdroid.batterywellbeing

import android.app.NotificationManager
import android.content.Context
import android.service.notification.ZenDeviceEffects
import android.service.notification.ZenPolicy

class ZenFocusManager(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun activateBedtimeMode() {
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            // Need to prompt user for DND access in settings
            return
        }

        // The Elite Android 15 API for hardware-level focus effects
        val zenEffects = ZenDeviceEffects.Builder()
            .setShouldDisplayGrayscale(true)
            .setShouldDimWallpaper(true)
            .setShouldSuppressAmbientDisplay(true) // Turns off AOD
            .setShouldUseNightMode(true) // Forces Dark Mode
            .build()

        val zenPolicy = ZenPolicy.Builder()
            .allowAlarms(true)
            .allowMedia(false)
            .allowSystem(false)
            .build()

        // Apply these effects to a custom AutomaticZenRule
        val rule = android.app.AutomaticZenRule.Builder("Wellbeing Bedtime", android.net.Uri.parse("wellbeing://bedtime"))
            .setDeviceEffects(zenEffects)
            .setZenPolicy(zenPolicy)
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            .build()

        val ruleId = notificationManager.addAutomaticZenRule(rule)
        notificationManager.setAutomaticZenRuleState(ruleId, android.service.notification.Condition(android.net.Uri.EMPTY, "", android.service.notification.Condition.STATE_TRUE))
    }
}
