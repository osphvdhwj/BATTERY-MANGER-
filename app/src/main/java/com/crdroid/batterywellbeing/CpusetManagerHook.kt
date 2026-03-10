package com.crdroid.batterywellbeing

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File

object CpusetManagerHook {

    // Standard Android OS Process Groups
    private const val THREAD_GROUP_BACKGROUND = 0
    private const val THREAD_GROUP_TOP_APP = 5

    // In v2.1, we will pull this list dynamically from your SharedPreferences/UI
    private val throttledApps = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.facebook.katana"
    )

    fun initHooks(classLoader: ClassLoader) {
        try {
            // We hook the foundational Android OS Process class
            XposedHelpers.findAndHookMethod(
                "android.os.Process",
                classLoader,
                "setProcessGroup",
                Int::class.javaPrimitiveType, // pid
                Int::class.javaPrimitiveType, // group
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pid = param.args[0] as Int
                        val group = param.args[1] as Int

                        // If Android is trying to give this app the Prime Cores (TOP_APP)
                        if (group == THREAD_GROUP_TOP_APP) {
                            val packageName = getPackageNameFromPid(pid)

                            if (throttledApps.contains(packageName)) {
                                // 🚀 THE OVERRIDE: Force it to the efficiency cores
                                param.args[1] = THREAD_GROUP_BACKGROUND
                                XposedBridge.log("Redwood CpusetManager: Intercepted $packageName (PID: $pid). Locked to Efficiency Cores.")
                            }
                        }
                    }
                }
            )
            XposedBridge.log("Redwood: CpusetManager (Core Pinning) Engine Armed.")
        } catch (e: Exception) {
            XposedBridge.log("Redwood CpusetManager Hook Failed: ${e.message}")
        }
    }

    /**
     * Reads the Linux /proc filesystem to instantly find the package name for a given PID.
     * This is lightning fast and avoids locking up the system_server.
     */
    private fun getPackageNameFromPid(pid: Int): String {
        return try {
            val cmdline = File("/proc/$pid/cmdline").readText()
            // Linux cmdline strings are null-terminated, so we trim the invisible characters
            cmdline.trim().replace("\u0000", "")
        } catch (e: Exception) {
            "unknown"
        }
    }
}
