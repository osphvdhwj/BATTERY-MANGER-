package com.crdroid.batterywellbeing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootAnchorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("BatteryWellbeingPrefs", Context.MODE_PRIVATE)

            // 1. Re-arm settings
            val settingsIntent = Intent("com.crdroid.batterywellbeing.UPDATE_SETTINGS").apply {
                putExtra("enableSmartCharge", prefs.getBoolean("enableSmartCharge", true))
                putExtra("smartChargeLimitPercent", prefs.getInt("smartChargeLimitPercent", 80))
                putExtra("enableThermalWarnings", prefs.getBoolean("enableThermalWarnings", true))
                putExtra("thermalWarningThresholdC", prefs.getInt("thermalWarningThresholdC", 42))

                putExtra("enableRogueApp", prefs.getBoolean("enableRogueApp", true))
                putExtra("enableStorageAbuse", prefs.getBoolean("enableStorageAbuse", true))
                putExtra("storageThresholdMB", prefs.getInt("storageThresholdMB", 500))

                putExtra("enableAppTimers", prefs.getBoolean("enableAppTimers", false))
                putExtra("enforceBedtimeMode", prefs.getBoolean("enforceBedtimeMode", false))

                putExtra("enableHotspotLimits", prefs.getBoolean("enableHotspotLimits", true))
                putExtra("hotspotDataLimitMB", prefs.getInt("hotspotDataLimitMB", 500))
                putExtra("dropAggressiveClients", prefs.getBoolean("dropAggressiveClients", true))
            }
            context.sendBroadcast(settingsIntent)

            // 2. Re-arm exemptions (Retrieve saved JSON list, default to empty)
            val exemptionsJson = prefs.getString("exempted_apps", "[]")
            val exemptionsIntent = Intent("com.crdroid.batterywellbeing.UPDATE_EXEMPTIONS").apply {
                putExtra("exemptions_json", exemptionsJson)
            }
            context.sendBroadcast(exemptionsIntent)

            // 3. Re-arm timers
            val timersJson = prefs.getString("app_timers_json", "{}")
            val timersIntent = Intent("com.crdroid.batterywellbeing.UPDATE_TIMERS").apply {
                putExtra("timers_payload", timersJson)
            }
            context.sendBroadcast(timersIntent)

        }
    }
}
