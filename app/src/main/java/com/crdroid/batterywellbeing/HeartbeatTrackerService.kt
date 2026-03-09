package com.crdroid.batterywellbeing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*
import org.json.JSONObject

class HeartbeatTrackerService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    // Keep track of which apps have already been hit with the shield to avoid spamming
    private val blockedAppsCache = mutableSetOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // 1. Create the persistent notification to stay alive
        val channel = NotificationChannel("wellbeing_heartbeat", "Ecosystem Tracker", NotificationManager.IMPORTANCE_MIN)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, "wellbeing_heartbeat")
            .setContentTitle("Digital Wellbeing Active")
            .setContentText("Monitoring ecosystem health...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)

        // 2. Start the tracking loop
        startUsageTrackingLoop()
    }

    private fun startUsageTrackingLoop() {
        scope.launch {
            while (isActive) {
                try {
                    val prefs = getSharedPreferences("BatteryWellbeingPrefs", Context.MODE_PRIVATE)
                    val enableAppTimers = prefs.getBoolean("enableAppTimers", false)

                    if (enableAppTimers) {
                        // Retrieve the stored app timers JSON
                        val savedJson = prefs.getString("app_timers_json", "{}") ?: "{}"
                        val existingTimers = mutableMapOf<String, Long>()
                        try {
                            val jsonObject = JSONObject(savedJson)
                            jsonObject.keys().forEach { key ->
                                existingTimers[key] = jsonObject.getLong(key)
                            }
                        } catch (e: Exception) {}

                        if (existingTimers.isNotEmpty()) {
                            // Get live screen time
                            val screenTimeMap = getDailyScreenTime(this@HeartbeatTrackerService)

                            for ((pkgName, limitMs) in existingTimers) {
                                val timeUsed = screenTimeMap[pkgName] ?: 0L

                                if (limitMs > 0 && timeUsed >= limitMs) {
                                    if (!blockedAppsCache.contains(pkgName)) {
                                        blockedAppsCache.add(pkgName)

                                        // Time is up. Fire the Interstitial Shield
                                        val intent = Intent(this@HeartbeatTrackerService, InterstitialShieldService::class.java).apply {
                                            putExtra("package_name", pkgName)
                                            putExtra("app_name", getAppName(this@HeartbeatTrackerService, pkgName))
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        startService(intent)
                                    }
                                } else if (timeUsed < limitMs && blockedAppsCache.contains(pkgName)) {
                                    // If timer was extended, unblock
                                    blockedAppsCache.remove(pkgName)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(5000) // Check every 5 seconds
            }
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
        job.cancel()
    }
}
