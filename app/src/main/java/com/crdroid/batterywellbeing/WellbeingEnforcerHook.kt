package com.crdroid.batterywellbeing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object WellbeingEnforcerHook {
    fun initHooks(classLoader: ClassLoader) {
        try {
            val amsClass = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", classLoader)

            XposedBridge.hookAllMethods(
                amsClass,
                "systemReady",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val amsInstance = param.thisObject
                            val context = XposedHelpers.getObjectField(amsInstance, "mContext") as Context

                            // 1. Register the Reverse-IPC Receiver ONCE
                            if (XposedHelpers.getAdditionalInstanceField(amsInstance, "enforcerRegistered") == null) {
                                val receiver = object : BroadcastReceiver() {
                                    override fun onReceive(ctx: Context, intent: Intent) {
                                        if (intent.action == "com.crdroid.batterywellbeing.EXECUTE_KILL") {
                                            val targetPackage = intent.getStringExtra("package_name") ?: return

                                            if (ModuleConfig.exemptedApps.contains(targetPackage)) {
                                                XposedBridge.log("WellbeingEnforcer: Skipped kill order for exempted app -> \$targetPackage")
                                                return
                                            }

                                            XposedBridge.log("WellbeingEnforcer: Timer expired. Executing force-stop on -> \$targetPackage")

                                            executeKill(amsInstance, targetPackage, context)
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
                                XposedHelpers.setAdditionalInstanceField(amsInstance, "enforcerRegistered", true)
                                XposedBridge.log("BatteryWellbeing: Wellbeing Executioner is armed and ready.")
                            }
                        } catch (e: Exception) {
                            XposedBridge.log("Redwood Enforcer Setup Error: \${e.message}")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("Redwood Critical Enforcer Hook Failure: \${e.message}")
        }
    }

    // Call this from your Executioner receiver when the grace period ends
    fun executeKill(amsInstance: Any, targetPackage: String, context: Context) {
        try {
            // Dynamically resolve the method by name and its foundational first parameter type (String).
            // This prevents crashes if Xiaomi/HyperOS added random boolean flags to the signature.
            val method = amsInstance.javaClass.methods.firstOrNull {
                it.name == "forceStopPackage" &&
                it.parameterTypes.isNotEmpty() &&
                it.parameterTypes[0] == String::class.java
            }

            if (method != null) {
                // Pad any extra OEM-injected arguments with safe defaults
                val args = Array(method.parameterTypes.size) { index ->
                    when (index) {
                        0 -> targetPackage // Target package name
                        1 -> -1 // UserHandle.USER_ALL (Standard 2nd param in AOSP)
                        else -> getDefaultValueForType(method.parameterTypes[index]) // Fills 0, false, or null
                    }
                }
                method.invoke(amsInstance, *args)
                XposedBridge.log("Redwood: Executioner dropped the hammer on \$targetPackage (OEM-Proofed)")

                val appName = getAppName(context, targetPackage)
                IslandDispatcher.dispatchSystemAlert(context, "EXECUTION_COMPLETE", "App Paused", "Time's up for \$appName.", "#2196F3")

            } else {
                XposedBridge.log("Redwood: CRITICAL - forceStopPackage method not found in AMS!")
            }
        } catch (e: Exception) {
            XposedBridge.log("Redwood Executioner Failed: \${e.message}")
        }
    }

    // Helper function for OEM padding
    private fun getDefaultValueForType(type: Class<*>): Any? {
        return when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            else -> null
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
