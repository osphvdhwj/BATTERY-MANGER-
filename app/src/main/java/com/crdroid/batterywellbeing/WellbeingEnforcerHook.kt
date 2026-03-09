package com.crdroid.batterywellbeing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserHandle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object WellbeingEnforcerHook {

    fun initHooks(classLoader: ClassLoader) {
        try {
            // We piggyback on ActivityManagerService's systemReady to register our Executioner receiver
            val amsClass = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", classLoader)

            XposedBridge.hookAllMethods(
                amsClass,
                "systemReady",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val amsInstance = param.thisObject
                            val context = XposedHelpers.getObjectField(amsInstance, "mContext") as Context

                            val receiver = object : BroadcastReceiver() {
                                override fun onReceive(ctx: Context, intent: Intent) {
                                    if (intent.action == "com.crdroid.batterywellbeing.EXECUTE_KILL") {
                                        val targetPackage = intent.getStringExtra("package_name") ?: return

                                        if (ModuleConfig.exemptedApps.contains(targetPackage)) {
                                            XposedBridge.log("WellbeingEnforcer: Skipped kill order for exempted app -> \$targetPackage")
                                            return
                                        }

                                        XposedBridge.log("WellbeingEnforcer: Timer expired. Executing force-stop on -> \$targetPackage")

                                        // 🚨 The Elite Kill Command: forceStopPackage
                                        // This completely halts the app, its background services, and phantom processes
                                        try {
                                            XposedHelpers.callMethod(
                                                amsInstance,
                                                "forceStopPackage",
                                                targetPackage,
                                                -1
                                            )

                                            // Notify the Dynamic Island that justice was served
                                            val appName = getAppName(context, targetPackage)
                                            IslandDispatcher.dispatchSystemAlert(context, "EXECUTION_COMPLETE", "App Paused", "Time's up for $appName.", "#2196F3")
                                        } catch (e: Exception) {
                                            XposedBridge.log("WellbeingEnforcer Kill Failed: \${e.message}")
                                        }
                                    }
                                }
                            }

                            val filter = IntentFilter("com.crdroid.batterywellbeing.EXECUTE_KILL")
                            context.registerReceiver(
                                receiver,
                                filter,
                                "android.permission.PACKAGE_USAGE_STATS",
                                null,
                                Context.RECEIVER_EXPORTED
                            )

                            XposedBridge.log("BatteryWellbeing: Wellbeing Executioner is armed and ready.")

                        } catch (e: Exception) {
                            XposedBridge.log("BatteryWellbeing Enforcer Setup Error: \${e.message}")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("BatteryWellbeing Critical Enforcer Hook Failure: \${e.message}")
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
}
