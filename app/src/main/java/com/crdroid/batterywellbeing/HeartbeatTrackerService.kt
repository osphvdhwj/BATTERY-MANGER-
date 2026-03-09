package com.crdroid.batterywellbeing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import kotlinx.coroutines.*
import org.json.JSONObject

class HeartbeatTrackerService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

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

        // Register receiver for native OS usage callbacks
        val filter = IntentFilter("com.crdroid.batterywellbeing.TIMER_BREACHED")
        registerReceiver(timerReceiver, filter, Context.RECEIVER_EXPORTED)

        // 2. Setup the event-driven hardware tracking
        setupHardwareObservers()
    }

    private fun setupHardwareObservers() {
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

                for ((pkgName, limitMs) in existingTimers) {
                    if (limitMs > 0) {
                        registerHardwareObserver(pkgName, limitMs)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun registerHardwareObserver(targetPackage: String, timeLimitMs: Long) {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val observerId = targetPackage.hashCode()

        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            observerId,
            Intent("com.crdroid.batterywellbeing.TIMER_BREACHED").apply {
                putExtra("package_name", targetPackage)
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // The OS tracks this natively with zero extra battery drain
        try {
            val method = usm.javaClass.getMethod("registerAppUsageObserver", Int::class.javaPrimitiveType, Array<String>::class.java, Long::class.javaPrimitiveType, java.util.concurrent.TimeUnit::class.java, android.app.PendingIntent::class.java)
            method.invoke(usm, observerId, arrayOf(targetPackage), timeLimitMs, java.util.concurrent.TimeUnit.MILLISECONDS, pendingIntent)
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
        job.cancel()
    }
}
