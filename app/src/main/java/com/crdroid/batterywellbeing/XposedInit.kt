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
        if (lpparam.packageName != "com.android.frameworks.core.batterystatsviewer") return

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
