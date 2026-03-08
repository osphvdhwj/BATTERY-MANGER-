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
        WellbeingEnforcerHook.initHooks(lpparam.classLoader)

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
                                            ModuleConfig.smartChargeLimitPercent = intent.getIntExtra("smartChargeLimitPercent", 80)

                                            ModuleConfig.enableThermalWarnings = intent.getBooleanExtra("enableThermalWarnings", true)
                                            ModuleConfig.thermalWarningThresholdC = intent.getIntExtra("thermalWarningThresholdC", 42)

                                            ModuleConfig.enableRogueApp = intent.getBooleanExtra("enableRogueApp", true)
                                            ModuleConfig.enableStorageAbuse = intent.getBooleanExtra("enableStorageAbuse", true)
                                            ModuleConfig.storageThresholdMB = intent.getIntExtra("storageThresholdMB", 500)

                                            ModuleConfig.enableAppTimers = intent.getBooleanExtra("enableAppTimers", false)
                                            ModuleConfig.enforceBedtimeMode = intent.getBooleanExtra("enforceBedtimeMode", false)

                                            ModuleConfig.enableHotspotLimits = intent.getBooleanExtra("enableHotspotLimits", true)
                                            ModuleConfig.hotspotDataLimitMB = intent.getIntExtra("hotspotDataLimitMB", 500)
                                            ModuleConfig.dropAggressiveClients = intent.getBooleanExtra("dropAggressiveClients", true)
                                        } else if (intent.action == "com.crdroid.batterywellbeing.UPDATE_EXEMPTIONS") {
                                            val jsonStr = intent.getStringExtra("exemptions_json") ?: "[]"
                                            val set = mutableSetOf<String>()
                                            try {
                                                val array = JSONArray(jsonStr)
                                                for (i in 0 until array.length()) {
                                                    set.add(array.getString(i))
                                                }
                                            } catch (e: Exception) { }
                                            ModuleConfig.exemptedApps = set
                                            de.robv.android.xposed.XposedBridge.log("BatteryWellbeing: Exemptions updated. Loaded ${set.size} apps.")

                                            de.robv.android.xposed.XposedBridge.log("BatteryWellbeing: Module settings updated from App UI.")
                                        }
                                    }
                                }
                                val filter = android.content.IntentFilter()
                                filter.addAction("com.crdroid.batterywellbeing.UPDATE_SETTINGS")
                                filter.addAction("com.crdroid.batterywellbeing.UPDATE_EXEMPTIONS")
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
                            val chargeLimit = ModuleConfig.smartChargeLimitPercent
                            if (ModuleConfig.enableSmartCharge && currentLevel == chargeLimit && plugType != 0 && lastNotifiedLevel != chargeLimit) {
                                IslandDispatcher.dispatchEvent(context, "SMART_CHARGE_LIMIT", currentLevel)
                                lastNotifiedLevel = currentLevel
                            } else if (currentLevel != chargeLimit) {
                                lastNotifiedLevel = currentLevel // Reset
                            }

                            // Thermal Throttling Warning (Over 42°C)
                            val thermalLimit = ModuleConfig.thermalWarningThresholdC
                            if (ModuleConfig.enableThermalWarnings && batteryTemp >= thermalLimit && batteryTemp != lastNotifiedTemp) {
                                IslandDispatcher.dispatchEvent(context, "THERMAL_WARNING", currentLevel, "\$batteryTemp°C")
                                lastNotifiedTemp = batteryTemp
                            } else if (batteryTemp < thermalLimit - 2) {
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
