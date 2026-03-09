package com.crdroid.batterywellbeing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import java.util.concurrent.TimeUnit
import java.lang.reflect.Method
import org.json.JSONObject

class HeartbeatTrackerService : Service() {

    // Keep track of which apps have already been hit with the shield to avoid spamming
    private val blockedAppsCache = mutableSetOf<String>()

    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == "com.crdroid.batterywellbeing.TIMER_BREACHED") {
                val pkgName = intent.getStringExtra("package_name") ?: return

                if (!blockedAppsCache.contains(pkgName)) {
                    blockedAppsCache.add(pkgName)

                    // Time is up. Fire the Interstitial Shield
                    val shieldIntent = Intent(this@HeartbeatTrackerService, InterstitialShieldService::class.java).apply {
                        putExtra("package_name", pkgName)
                        putExtra("app_name", getAppName(this@HeartbeatTrackerService, pkgName))
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startService(shieldIntent)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        val channel = NotificationChannel("wellbeing_heartbeat", "Ecosystem Tracker", NotificationManager.IMPORTANCE_MIN)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, "wellbeing_heartbeat")
            .setContentTitle("Redwood Ecosystem Active")
            .setContentText("Event-driven telemetry engaged. Zero polling drain.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)

        // Register receiver for native OS usage callbacks
        val filter = IntentFilter("com.crdroid.batterywellbeing.TIMER_BREACHED")
        registerReceiver(timerReceiver, filter, Context.RECEIVER_EXPORTED)

        // Bootstrapping our event-driven observers
        registerAllAppObservers()
    }

    private fun registerAllAppObservers() {
        try {
            val prefs = getSharedPreferences("BatteryWellbeingPrefs", Context.MODE_PRIVATE)
            val enableAppTimers = prefs.getBoolean("enableAppTimers", false)

            if (enableAppTimers) {
                val savedJson = prefs.getString("app_timers_json", "{}") ?: "{}"
                val existingTimers = mutableMapOf<String, Long>()
                try {
                    val jsonObject = JSONObject(savedJson)
                    jsonObject.keys().forEach { key ->
                        existingTimers[key] = jsonObject.getLong(key)
                    }
                } catch (e: Exception) {}

                existingTimers.forEach { (packageName, timeLimitMs) ->
                    if (timeLimitMs > 0) {
                        registerHardwareObserver(packageName, timeLimitMs)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun registerHardwareObserver(targetPackage: String, timeLimitMs: Long) {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE)
            val observerId = targetPackage.hashCode()

            // This intent fires ONLY when the user hits the time limit
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                observerId,
                Intent("com.crdroid.batterywellbeing.TIMER_BREACHED").apply {
                    setPackage(packageName) // Send to our own receiver to trigger the Island warning
                    putExtra("package_name", targetPackage)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Safely invoke the hidden System API via reflection
            val method: Method = usm.javaClass.getMethod(
                "registerAppUsageObserver",
                Int::class.javaPrimitiveType,
                Array<String>::class.java,
                Long::class.javaPrimitiveType,
                TimeUnit::class.java,
                PendingIntent::class.java
            )

            method.invoke(usm, observerId, arrayOf(targetPackage), timeLimitMs, TimeUnit.MILLISECONDS, pendingIntent)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAppName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.split(".").last().replaceFirstChar { it.uppercase() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(timerReceiver)
    }
}
