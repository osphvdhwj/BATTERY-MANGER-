package com.crdroid.batterywellbeing

import android.content.Context
import android.content.Intent
import android.os.BatteryUsageStats
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.json.JSONArray
import org.json.JSONObject

class XposedInit : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: LoadPackageParam) {

        // 1. Hook the System Server for Persistent Background Island Triggers
        if (lpparam.packageName == "android") {
            hookBatteryServiceForIsland(lpparam)
            return
        }

        // 2. Hook the UI App for the Frontend Dashboard
        if (lpparam.packageName == "com.android.frameworks.core.batterystatsviewer") {
            hookBatteryDashboardUI(lpparam)
            return
        }

        // 3. 🚀 NEW: Hook the Tethering APEX for Hotspot Limits
        if (lpparam.packageName == "com.google.android.tethering") {
            HotspotLimitHook.initHooks(lpparam.classLoader)
            return
        }
    }

    private fun hookBatteryServiceForIsland(lpparam: LoadPackageParam) {
        // Initialize Rogue App Detectors
        RogueAppHook.initHooks(lpparam.classLoader)
        StorageAbuseHook.initHooks(lpparam.classLoader)

        try {
            val batteryServiceClass = "com.android.server.BatteryService"

            XposedHelpers.findAndHookMethod(
                batteryServiceClass,
                lpparam.classLoader,
                "sendIntentLocked",
                object : XC_MethodHook() {
                    var lastNotifiedTemp = 0
                    var lastNotifiedLevel = 0

                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val serviceInstance = param.thisObject
                            val context = XposedHelpers.getObjectField(serviceInstance, "mContext") as Context

                            // 1. Register the Reverse-IPC Receiver ONCE
                            if (XposedHelpers.getAdditionalInstanceField(serviceInstance, "receiverRegistered") == null) {
                                val receiver = object : android.content.BroadcastReceiver() {
                                    override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
                                        if (intent.action == "com.crdroid.batterywellbeing.UPDATE_SETTINGS") {
                                            ModuleConfig.enableSmartCharge = intent.getBooleanExtra("enableSmartCharge", true)
                                            ModuleConfig.enableThermalWarnings = intent.getBooleanExtra("enableThermalWarnings", true)
                                            ModuleConfig.enableRogueApp = intent.getBooleanExtra("enableRogueApp", true)
                                            ModuleConfig.enableStorageAbuse = intent.getBooleanExtra("enableStorageAbuse", true)
                                            ModuleConfig.enableHotspotLimits = intent.getBooleanExtra("enableHotspotLimits", true)
                                            de.robv.android.xposed.XposedBridge.log("BatteryWellbeing: Module settings updated from App UI.")
                                        }
                                    }
                                }
                                val filter = android.content.IntentFilter("com.crdroid.batterywellbeing.UPDATE_SETTINGS")
                                context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
                                XposedHelpers.setAdditionalInstanceField(serviceInstance, "receiverRegistered", true)
                            }

                            val healthInfo = XposedHelpers.getObjectField(serviceInstance, "mHealthInfo")
                            val currentLevel = XposedHelpers.getIntField(healthInfo, "batteryLevel")
                            // AOSP stores temp in tenths of a degree Celsius
                            val batteryTemp = XposedHelpers.getIntField(healthInfo, "batteryTemperature") / 10
                            val plugType = XposedHelpers.getIntField(serviceInstance, "mPlugType")

                            // --- DYNAMIC ISLAND TRIGGERS ---

                            // Smart Charge Limit (e.g., holding at 80% while plugged in)
                            if (ModuleConfig.enableSmartCharge && currentLevel == 80 && plugType != 0 && lastNotifiedLevel != 80) {
                                IslandDispatcher.dispatchEvent(context, "SMART_CHARGE_LIMIT", currentLevel)
                                lastNotifiedLevel = currentLevel
                            } else if (currentLevel != 80) {
                                lastNotifiedLevel = currentLevel // Reset
                            }

                            // Thermal Throttling Warning (Over 42°C)
                            if (ModuleConfig.enableThermalWarnings && batteryTemp >= 42 && batteryTemp != lastNotifiedTemp) {
                                IslandDispatcher.dispatchEvent(context, "THERMAL_WARNING", currentLevel, "\$batteryTemp°C")
                                lastNotifiedTemp = batteryTemp
                            } else if (batteryTemp < 40) {
                                lastNotifiedTemp = 0 // Reset when cooled down
                            }

                        } catch (e: Exception) {
                            XposedBridge.log("BatteryWellbeing Island Hook Error: \${e.message}")
                        }
                    }
                }
            )
            XposedBridge.log("BatteryWellbeing: Successfully hooked system_server BatteryService")
        } catch (e: Exception) {
            XposedBridge.log("BatteryWellbeing system_server hook failed: \${e.message}")
        }
    }

    private fun hookBatteryDashboardUI(lpparam: LoadPackageParam) {
        try {
            val targetClass = "com.android.frameworks.core.batterystatsviewer.BatteryConsumerData"

            XposedHelpers.findAndHookConstructor(
                targetClass,
                lpparam.classLoader,
                Context::class.java,
                BatteryUsageStats::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val mEntriesList = XposedHelpers.getObjectField(param.thisObject, "mEntries") as? List<*>
                            if (mEntriesList != null) {
                                val jsonArray = JSONArray()
                                for (entry in mEntriesList) {
                                    if (entry == null) continue
                                    val title = XposedHelpers.getObjectField(entry, "title") as? String ?: "Unknown"
                                    val value1 = XposedHelpers.getDoubleField(entry, "value1")
                                    val value2 = XposedHelpers.getDoubleField(entry, "value2")

                                    val jsonObject = JSONObject().apply {
                                        put("title", title)
                                        put("value1", value1)
                                        put("value2", value2)
                                    }
                                    jsonArray.put(jsonObject)
                                }
                                val context = param.args[0] as Context
                                val intent = Intent("com.crdroid.batterywellbeing.UPDATE_STATS").apply {
                                    putExtra("battery_data_json", jsonArray.toString())
                                    setPackage("com.crdroid.batterywellbeing")
                                }
                                context.sendBroadcast(intent)
                            }
                        } catch (e: Exception) {
                            XposedBridge.log("BatteryWellbeing Dashboard Hook Error: \${e.message}")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("BatteryWellbeing UI Hook Error: \${e.message}")
        }
    }
}
