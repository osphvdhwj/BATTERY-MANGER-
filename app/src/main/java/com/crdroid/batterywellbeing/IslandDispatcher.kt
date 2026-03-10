package com.crdroid.batterywellbeing

import android.content.Context
import android.content.Intent
import org.json.JSONObject

object IslandDispatcher {

    private const val ISLAND_PACKAGE = "com.dynamicisland.systemui"
    private const val SECURE_PERMISSION = "com.redwood.permission.SECURE_IPC"

    fun dispatchSystemAlert(context: Context, alertType: String, title: String, message: String, colorHex: String) {
        val intent = Intent("com.crdroid.batterywellbeing.SYSTEM_ALERT").apply {
            setPackage(ISLAND_PACKAGE)
            putExtra("alertType", alertType)
            putExtra("title", title)
            putExtra("message", message)
            putExtra("colorHex", colorHex)
        }
        context.sendBroadcast(intent, SECURE_PERMISSION)
    }

    fun dispatchGracePeriodWarning(context: Context, targetPackage: String, appName: String) {
        val intent = Intent("com.crdroid.batterywellbeing.WARNING_1_MINUTE_REMAINING").apply {
            setPackage(ISLAND_PACKAGE)
            putExtra("package_name", targetPackage)
            putExtra("app_name", appName)
        }
        context.sendBroadcast(intent, SECURE_PERMISSION)
    }

    fun dispatchRealityTick(context: Context, appName: String, sessionMinutes: Int, colorHex: String) {
        val intent = Intent("com.crdroid.batterywellbeing.REALITY_PILL_TICK").apply {
            setPackage(ISLAND_PACKAGE)
            putExtra("app_name", appName)
            putExtra("session_minutes", sessionMinutes)
            putExtra("colorHex", colorHex)
        }
        context.sendBroadcast(intent, SECURE_PERMISSION)
    }

    fun dispatchConfigSync(context: Context, timers: Map<String, Long>, exemptedApps: Set<String>) {
        val jsonPayload = JSONObject()
        timers.forEach { (pkg, ms) -> jsonPayload.put(pkg, ms) }

        val intent = Intent("com.crdroid.batterywellbeing.SYNC_CONFIG").apply {
            setPackage(ISLAND_PACKAGE)
            putExtra("timers_json", jsonPayload.toString())
            putExtra("exemptions_csv", exemptedApps.joinToString(","))
        }
        context.sendBroadcast(intent, SECURE_PERMISSION)
    }
}
