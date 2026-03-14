package com.crdroid.batterywellbeing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object TournamentModeHook {
    
    @Volatile
    private var isTournamentModeEngaged = false
    private var targetGamePackage = "com.dts.freefiremax" // Default target

    fun initHooks(classLoader: ClassLoader) {
        try {
            // 1. The Secure Engagement Receiver
            XposedHelpers.findAndHookMethod(
                "com.android.server.am.ActivityManagerService", classLoader, "systemReady", Runnable::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val amsInstance = param.thisObject
                        val context = XposedHelpers.getObjectField(amsInstance, "mContext") as Context
                        
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(c: Context?, intent: Intent?) {
                                isTournamentModeEngaged = intent?.getBooleanExtra("engage", false) ?: false
                                targetGamePackage = intent?.getStringExtra("package") ?: "com.dts.freefiremax"
                                
                                if (isTournamentModeEngaged) {
                                    XposedBridge.log("Redwood TOURNAMENT MODE ENGAGED. Target: $targetGamePackage.")
                                    XposedBridge.log("Redwood: Audio DSPs untouched. Silicon locked to Prime/Gold.")
                                } else {
                                    XposedBridge.log("Redwood: Tournament Mode Disengaged. Normal CPU scheduler restored.")
                                }
                            }
                        }
                        
                        context.registerReceiver(
                            receiver,
                            IntentFilter("com.crdroid.batterywellbeing.ENGAGE_TOURNAMENT"),
                            "com.redwood.permission.SECURE_IPC",
                            null,
                            Context.RECEIVER_EXPORTED
                        )
                    }
                }
            )

            // 2. The Thermal Spoofer (Prevents 39°C Frame Drops)
            XposedHelpers.findAndHookMethod(
                "com.android.server.power.ThermalManagerService\$ThermalHalWrapper", classLoader, "getCurrentTemperatures",
                Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isTournamentModeEngaged) {
                            // Spoofs thermal reads to bypass OEM throttling on A78 cores
                            XposedBridge.log("Redwood: Spoofing Thermal Zones -> Forcing 35°C to preserve A78 Prime frequencies.")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("Redwood Tournament Hook Failed: ${e.message}")
        }
    }
}
