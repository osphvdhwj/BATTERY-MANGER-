package com.crdroid.batterywellbeing

import android.content.Context
import android.content.pm.PackageManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object RogueAppHook {

    fun initHooks(classLoader: ClassLoader) {
        try {
            // Hook 1: The User Warning Intercept
            // This catches the exact moment Android tries to warn the user about a rogue app.
            val notificationHelperClass = "com.android.server.am.AppRestrictionController\$NotificationHelper"

            XposedHelpers.findAndHookMethod(
                notificationHelperClass,
                classLoader,
                "postRequestBgRestrictedIfNecessary",
                String::class.java, // packageName
                Int::class.javaPrimitiveType, // uid
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val packageName = param.args[0] as String
                            XposedBridge.log("BatteryWellbeing: Rogue app notification intercepted for -> \$packageName")

                            // Retrieve the system_server Context to fire our Island broadcast
                            // NotificationHelper is usually an inner class, so we grab the outer AppRestrictionController's context
                            val appRestrictionController = XposedHelpers.getObjectField(param.thisObject, "this$0")
                            val context = XposedHelpers.getObjectField(appRestrictionController, "mContext") as Context

                            // Get the human-readable app name from the package manager
                            val appName = getAppName(context, packageName)

                            // Fire the Dynamic Island trigger!
                            if (ModuleConfig.enableRogueApp) {
                                IslandDispatcher.dispatchSystemAlert(context, "ROGUE_APP_DETECTED", "High Background Drain", "$appName is abusing system resources.", "#FF9800")
                            }

                            // 🚨 ELITE MOVE: Block the ugly default Android system notification
                            // By setting the result to null, we skip the original method execution.
                            // The Dynamic Island is now the exclusive handler for this warning.
                            param.result = null

                        } catch (e: Exception) {
                            XposedBridge.log("BatteryWellbeing RogueApp Hook Error: \${e.message}")
                        }
                    }
                }
            )

            // Hook 2: The Executioner Intercept (Phantom Process CPU Abusers)
            val phantomProcessListClass = "com.android.server.am.PhantomProcessList"

            // Note: PhantomProcessRecord is usually a package-private class in com.android.server.am,
            // so using its string name is the correct approach here.
            XposedHelpers.findAndHookMethod(
                phantomProcessListClass,
                classLoader,
                "killPhantomProcessGroupLocked",
                XposedHelpers.findClass("com.android.server.am.ProcessRecord", classLoader), // app
                XposedHelpers.findClass("com.android.server.am.PhantomProcessRecord", classLoader), // cpr
                Int::class.javaPrimitiveType, // reason
                Int::class.javaPrimitiveType, // subReason
                String::class.java, // msg
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val processRecord = param.args[0]
                            val processInfo = XposedHelpers.getObjectField(processRecord, "info")
                            val packageName = XposedHelpers.getObjectField(processInfo, "packageName") as String
                            val killReasonMsg = param.args[4] as String

                            XposedBridge.log("BatteryWellbeing: Killed phantom process -> \$packageName (\$killReasonMsg)")

                            // Grab context via Android's internal ActivityThread for system_server
                            val activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", classLoader)
                            val currentActivityThread = XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread")
                            val context = XposedHelpers.callMethod(currentActivityThread, "getSystemContext") as Context

                            val appName = getAppName(context, packageName)

                            // Fire to the Island with the reason
                            if (ModuleConfig.enableRogueApp) {
                                IslandDispatcher.dispatchSystemAlert(context, "ROGUE_APP_DETECTED", "Phantom Processes Killed", "\$appName spawned abusive phantom threads.", "#FF9800")
                            }

                        } catch (e: Exception) {
                            XposedBridge.log("BatteryWellbeing PhantomProcess Hook Error: \${e.message}")
                        }
                    }
                }
            )

            XposedBridge.log("BatteryWellbeing: Rogue App Detection hooks applied successfully.")

        } catch (e: Exception) {
            XposedBridge.log("BatteryWellbeing Critical Rogue Hook Failure: \${e.message}")
        }
    }

    // Helper function to convert "com.whatsapp" into "WhatsApp"
    private fun getAppName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName // Fallback to package name if not found
        }
    }
}
