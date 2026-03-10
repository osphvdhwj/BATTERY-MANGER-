package com.crdroid.batterywellbeing

import android.content.ComponentName
import android.content.Intent
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object ActivityInterceptorHook {
    fun initHooks(classLoader: ClassLoader) {
        try {
            val atmsClass = "com.android.server.wm.ActivityTaskManagerService"

            XposedHelpers.findAndHookMethod(
                atmsClass,
                classLoader,
                "startActivityAsUser",
                // The exact parameters vary slightly by custom ROM, but we can hook the base method
                XposedHelpers.findClass("android.app.IApplicationThread", classLoader),
                String::class.java,
                Intent::class.java,
                String::class.java,
                android.os.IBinder::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                XposedHelpers.findClass("android.app.ProfilerInfo", classLoader),
                android.os.Bundle::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val intent = param.args[2] as? Intent ?: return
                        val targetPackage = intent.component?.packageName ?: return

                        // Check if the app is in our restricted list (e.g., social media or heavy time-sinks)
                        // Note: We leave essential utilities and heavy background tools like ArchiveAll alone
                        val restrictedApps = ModuleConfig.appTimeLimits.keys

                        if (restrictedApps.contains(targetPackage) && !intent.getBooleanExtra("friction_cleared", false)) {
                            XposedBridge.log("BatteryWellbeing: Intercepted launch for $targetPackage")

                            // Redirect to our Deep Breath Activity
                            val frictionIntent = Intent().apply {
                                component = ComponentName("com.crdroid.batterywellbeing", "com.crdroid.batterywellbeing.ui.DeepBreathActivity")
                                putExtra("target_intent", intent)
                                putExtra("target_package", targetPackage)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                            param.args[2] = frictionIntent
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("BatteryWellbeing Interceptor Error: ${e.message}")
        }
    }
}
