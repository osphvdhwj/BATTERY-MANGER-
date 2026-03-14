package com.crdroid.batterywellbeing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object CpusetManagerHook {
    private const val THREAD_GROUP_BACKGROUND = 0
    private const val THREAD_GROUP_TOP_APP = 5
    
    // Thread-safe dynamic list of apps locked to efficiency cores
    private val throttledApps = ConcurrentHashMap.newKeySet<String>()

    fun initHooks(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.os.Process", classLoader, "setProcessGroup",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pid = param.args[0] as Int
                        val group = param.args[1] as Int

                        if (group == THREAD_GROUP_TOP_APP) {
                            val packageName = getPackageNameFromPid(pid)
                            if (throttledApps.contains(packageName)) {
                                param.args[1] = THREAD_GROUP_BACKGROUND // Force to LITTLE cores
                            }
                        }
                    }
                }
            )

            // Register the secure IPC receiver inside system_server
            XposedHelpers.findAndHookMethod(
                "com.android.server.am.ActivityManagerService", classLoader, "systemReady", Runnable::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val amsInstance = param.thisObject
                        val context = XposedHelpers.getObjectField(amsInstance, "mContext") as Context
                        
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(c: Context?, intent: Intent?) {
                                val csv = intent?.getStringExtra("throttled_apps_csv") ?: ""
                                throttledApps.clear()
                                if (csv.isNotEmpty()) throttledApps.addAll(csv.split(","))
                                XposedBridge.log("Redwood CpusetManager: Target list updated -> $throttledApps")
                            }
                        }
                        
                        // Using our Signature-level permission to prevent spoofing
                        context.registerReceiver(
                            receiver,
                            IntentFilter("com.crdroid.batterywellbeing.SYNC_CPU_PINNING"),
                            "com.redwood.permission.SECURE_IPC",
                            null,
                            Context.RECEIVER_EXPORTED
                        )
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("Redwood CpusetManager Hook Failed: ${e.message}")
        }
    }

    private fun getPackageNameFromPid(pid: Int): String {
        return try { File("/proc/$pid/cmdline").readText().trim().replace("\u0000", "") } catch (e: Exception) { "unknown" }
    }
}
