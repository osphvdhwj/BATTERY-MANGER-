package com.crdroid.batterywellbeing

import android.view.WindowManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object DisplayEngineHook {

    // Apps that should be hard-capped to 60Hz to save battery
    private val refreshRateRestrictedApps = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.twitter.android"
    )

    fun initHooks(classLoader: ClassLoader) {
        try {
            val phoneWindowClass = "com.android.internal.policy.PhoneWindow"

            // We hook generateLayout, which is called when an app's window is first being constructed
            XposedHelpers.findAndHookMethod(
                phoneWindowClass,
                classLoader,
                "generateLayout",
                "com.android.internal.policy.DecorView",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val context = XposedHelpers.callMethod(param.thisObject, "getContext") as android.content.Context
                            val packageName = context.packageName

                            // If it's a restricted app, intercept its WindowManager LayoutParams
                            if (refreshRateRestrictedApps.contains(packageName)) {
                                val attributes = XposedHelpers.callMethod(param.thisObject, "getAttributes") as WindowManager.LayoutParams

                                // Force preferred refresh rate to 60Hz (60.0f)
                                attributes.preferredRefreshRate = 60.0f

                                XposedHelpers.callMethod(param.thisObject, "setAttributes", attributes)
                                XposedBridge.log("BatteryWellbeing: DisplayEngine capped \$packageName to 60Hz.")
                            }
                        } catch (e: Exception) {
                            // Silently fail to avoid UI rendering crashes
                        }
                    }
                }
            )
            XposedBridge.log("BatteryWellbeing: Display Override Engine armed.")
        } catch (e: Exception) {
            XposedBridge.log("BatteryWellbeing DisplayEngine Hook Failure: \${e.message}")
        }
    }
}
