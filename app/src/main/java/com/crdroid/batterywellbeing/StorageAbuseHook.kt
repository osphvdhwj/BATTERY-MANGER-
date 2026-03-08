package com.crdroid.batterywellbeing

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Proxy

object StorageAbuseHook {

    // Threshold: 500 MB written in the background triggers a warning
    private const val BACKGROUND_WRITE_THRESHOLD_BYTES = 500 * 1024 * 1024L

    fun initHooks(classLoader: ClassLoader) {
        try {
            val batteryServiceClass = "com.android.server.BatteryService"

            // We piggyback on BatteryService's frequent state updates to poll the disk I/O
            XposedHelpers.findAndHookMethod(
                batteryServiceClass,
                classLoader,
                "processValuesLocked",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    var lastCheckTime = 0L

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val currentTime = System.currentTimeMillis()
                        // Only poll disk I/O every 5 minutes to avoid overhead
                        if (currentTime - lastCheckTime < 5 * 60 * 1000) return
                        lastCheckTime = currentTime

                        try {
                            val context = XposedHelpers.getObjectField(param.thisObject, "mContext") as Context

                            // Instantiate the hidden AOSP I/O Reader
                            val readerClass = XposedHelpers.findClass("com.android.internal.os.StoragedUidIoStatsReader", classLoader)
                            val readerInstance = XposedHelpers.newInstance(readerClass)
                            val callbackInterface = XposedHelpers.findClass("com.android.internal.os.StoragedUidIoStatsReader\$Callback", classLoader)

                            // Create a dynamic proxy to handle the AOSP callback
                            val callbackProxy = Proxy.newProxyInstance(
                                classLoader,
                                arrayOf(callbackInterface)
                            ) { _, method, args ->
                                if (method.name == "onUidStorageStats") {
                                    val uid = args[0] as Int
                                    // args map: [uid, fgCharsRead, fgCharsWrite, fgBytesRead, fgBytesWrite, bgCharsRead, bgCharsWrite, bgBytesRead, bgBytesWrite, fgFsync, bgFsync]
                                    val bgBytesWrite = args[8] as Long

                                    if (bgBytesWrite > BACKGROUND_WRITE_THRESHOLD_BYTES) {
                                        val pm = context.packageManager
                                        val packages = pm.getPackagesForUid(uid)
                                        if (!packages.isNullOrEmpty()) {
                                            val appName = getAppName(context, packages[0])
                                            val formattedWrite = formatBytes(bgBytesWrite)

                                            XposedBridge.log("BatteryWellbeing: STORAGE ABUSE -> \$appName wrote \$formattedWrite in background!")

                                            // Fire to Dynamic Island
                                            if (ModuleConfig.enableStorageAbuse) {
                                                IslandDispatcher.dispatchEvent(
                                                context,
                                                "ROGUE_APP_DETECTED", // Reusing the rogue app UI template
                                                0,
                                                "\$appName (Storage Abuse: \$formattedWrite)"
                                            )
                                        }
                                    }
                                }
                                null
                            }

                            // Execute the read
                            XposedHelpers.callMethod(readerInstance, "readAbsolute", callbackProxy)

                        } catch (e: Exception) {
                            XposedBridge.log("BatteryWellbeing StorageAbuse Read Error: \${e.message}")
                        }
                    }
                }
            )
            XposedBridge.log("BatteryWellbeing: Storage Abuse tracking engaged.")
        } catch (e: Exception) {
            XposedBridge.log("BatteryWellbeing Critical Storage Hook Failure: \${e.message}")
        }
    }

    private fun getAppName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }
}
