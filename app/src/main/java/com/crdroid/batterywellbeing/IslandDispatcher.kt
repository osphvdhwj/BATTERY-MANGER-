package com.crdroid.batterywellbeing

import android.content.Context
import android.content.Intent
import org.json.JSONObject

object IslandDispatcher {

    // The exact package name of the Dynamic Island module (assuming standard SystemUI overlay)
    // Using an explicit package name bypasses Android 15 implicit broadcast restrictions.
    private const val ISLAND_PACKAGE = "com.dynamicisland.systemui"

    // 1. Hardware System Alerts (Thermals, Storage Abuse, Rogue Apps)
    fun dispatchSystemAlert(context: Context, alertType: String, title: String, message: String, colorHex: String) {
        val intent = Intent("com.crdroid.batterywellbeing.SYSTEM_ALERT").apply {
            setPackage(ISLAND_PACKAGE)
            putExtra("alertType", alertType) // e.g., "THERMAL", "ROGUE", "STORAGE"
            putExtra("title", title)
            putExtra("message", message)
            putExtra("colorHex", colorHex)
        }
        context.sendBroadcast(intent)
    }

    // 2. The 60-Second Execution Warning
    fun dispatchGracePeriodWarning(context: Context, targetPackage: String, appName: String) {
        val intent = Intent("com.crdroid.batterywellbeing.WARNING_1_MINUTE_REMAINING").apply {
            setPackage(ISLAND_PACKAGE)
            putExtra("package_name", targetPackage)
            putExtra("app_name", appName)
        }
        context.sendBroadcast(intent)
    }

    // 3. Reality Pill Live Data Sync (Tick every minute during heavy sessions)
    fun dispatchRealityTick(context: Context, appName: String, sessionMinutes: Int) {
        val intent = Intent("com.crdroid.batterywellbeing.REALITY_PILL_TICK").apply {
            setPackage(ISLAND_PACKAGE)
            putExtra("app_name", appName)
            putExtra("session_minutes", sessionMinutes)
        }
        context.sendBroadcast(intent)
    }

    // 4. Config Sync (App Timers & Exemptions)
    fun dispatchConfigSync(context: Context, timers: Map<String, Long>, exemptedApps: Set<String>) {
        val jsonPayload = JSONObject()
        timers.forEach { (pkg, ms) -> jsonPayload.put(pkg, ms) }

        val intent = Intent("com.crdroid.batterywellbeing.SYNC_CONFIG").apply {
            setPackage(ISLAND_PACKAGE)
            putExtra("timers_json", jsonPayload.toString())
            putExtra("exemptions_csv", exemptedApps.joinToString(","))
        }
        context.sendBroadcast(intent)
    }
}
