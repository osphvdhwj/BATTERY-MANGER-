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

        // 2. Hook the UI App for the Frontend Dashboard (Your existing code)
        if (lpparam.packageName == "com.android.frameworks.core.batterystatsviewer") {
            hookBatteryStatsViewer(lpparam)
            return
        }
    }

    private fun hookBatteryServiceForIsland(lpparam: LoadPackageParam) {
        try {
            val batteryServiceClass = "com.android.server.BatteryService"

            // In AOSP, BatteryService updates state via a method that processes HealthInfo.
            // We hook the broadcast sender method to catch the finalized battery properties.
            XposedHelpers.findAndHookMethod(
                batteryServiceClass,
                lpparam.classLoader,
                "sendIntentLocked", // This method fires whenever real battery state changes
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val serviceInstance = param.thisObject
                            val context = XposedHelpers.getObjectField(serviceInstance, "mContext") as Context

                            // Extract live hardware values directly from the service fields
                            val healthInfo = XposedHelpers.getObjectField(serviceInstance, "mHealthInfo")
                            val currentLevel = XposedHelpers.getIntField(healthInfo, "batteryLevel")
                            val batteryTemp = XposedHelpers.getIntField(healthInfo, "batteryTemperature") / 10 // Usually in tenths of a degree C
                            val plugType = XposedHelpers.getIntField(serviceInstance, "mPlugType")

                            // --- DYNAMIC ISLAND LOGIC TRIGGERS ---

                            // Trigger 1: Smart Charge Limit Reached (e.g., holding at 80%)
                            if (currentLevel == 80 && plugType != 0) {
                                IslandDispatcher.dispatchEvent(context, "SMART_CHARGE_LIMIT", currentLevel)
                            }

                            // Trigger 2: Thermal Throttling Warning (e.g., over 42°C)
                            if (batteryTemp >= 42) {
                                IslandDispatcher.dispatchEvent(context, "THERMAL_WARNING", currentLevel, "\$batteryTemp°C")
                            }
                        } catch (e: NoSuchMethodError) {
                            XposedBridge.log("BatteryWellbeing system_server field extraction failed: NoSuchMethodError. \${e.message}")
                        } catch (e: Exception) {
                            XposedBridge.log("BatteryWellbeing system_server field extraction failed: \${e.message}")
                        }
                    }
                }
            )
            XposedBridge.log("BatteryWellbeing: Successfully hooked system_server BatteryService")
        } catch (e: NoSuchMethodError) {
            XposedBridge.log("BatteryWellbeing system_server hook failed: Method sendIntentLocked not found in BatteryService. \${e.message}")
        } catch (e: Exception) {
            XposedBridge.log("BatteryWellbeing system_server hook failed: \${e.message}")
        }
    }

    private fun hookBatteryStatsViewer(lpparam: LoadPackageParam) {
        XposedBridge.log("BatteryWellbeing: Hooked into BatteryStatsViewer on CrDroid 15")

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
                        XposedBridge.log("BatteryWellbeing: Constructor hooked successfully!")

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

                                // Grab the context from the constructor arguments to send the broadcast
                                val context = param.args[0] as Context

                                val intent = Intent("com.crdroid.batterywellbeing.UPDATE_STATS").apply {
                                    putExtra("battery_data_json", jsonArray.toString())
                                    setPackage("com.crdroid.batterywellbeing") // Explicitly target our frontend app
                                }

                                context.sendBroadcast(intent)
                                XposedBridge.log("BatteryWellbeing: Broadcast sent to frontend with \${jsonArray.length()} items!")
                            } else {
                                XposedBridge.log("BatteryWellbeing: mEntries is null")
                            }
                        } catch (e: Exception) {
                            XposedBridge.log("BatteryWellbeing Field Extraction Error: \${e.message}")
                            e.printStackTrace()
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("BatteryWellbeing Error: \${e.message}")
            e.printStackTrace()
        }
    }
}
