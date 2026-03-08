package com.crdroid.batterywellbeing

import android.content.Context
import android.content.Intent
import android.os.BatteryUsageStats
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.ArrayList

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

                        val obj = param.thisObject
                        val context = param.args[0] as Context

                        try {
                            // Use reflection to get the private mEntries list
                            val mEntriesField = XposedHelpers.findField(obj.javaClass, "mEntries")
                            mEntriesField.isAccessible = true
                            val entriesList = mEntriesField.get(obj) as? List<*>

                            if (entriesList != null) {
                                XposedBridge.log("BatteryWellbeing: Extracted \${entriesList.size} entries")
                                broadcastDataToFrontend(context, entriesList)
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

    private fun broadcastDataToFrontend(context: Context, entriesList: List<*>) {
        try {
            val intent = Intent("com.crdroid.batterywellbeing.UPDATE_DATA")

            // To pass the data via Intent, we need to extract and format the data
            // since the actual Entry objects are internal classes that we can't easily parcel.
            val formattedData = ArrayList<String>()

            for (entryObj in entriesList) {
                if (entryObj == null) continue

                // Use reflection to get fields from Entry class
                try {
                    val titleField = XposedHelpers.findField(entryObj.javaClass, "title")
                    titleField.isAccessible = true
                    val title = titleField.get(entryObj) as? String ?: "Unknown"

                    val value1Field = XposedHelpers.findField(entryObj.javaClass, "value1")
                    value1Field.isAccessible = true
                    val value1 = value1Field.getDouble(entryObj)

                    val value2Field = XposedHelpers.findField(entryObj.javaClass, "value2")
                    value2Field.isAccessible = true
                    val value2 = value2Field.getDouble(entryObj)

                    formattedData.add("\$title|\$value1|\$value2")
                } catch (e: Exception) {
                    XposedBridge.log("BatteryWellbeing: Error parsing entry: \${e.message}")
                }
            }

            intent.putStringArrayListExtra("battery_data", formattedData)
            intent.setPackage("com.crdroid.batterywellbeing")

            context.sendBroadcast(intent)
            XposedBridge.log("BatteryWellbeing: Broadcast sent with \${formattedData.size} items")
        } catch (e: Exception) {
            XposedBridge.log("BatteryWellbeing Broadcast Error: \${e.message}")
            e.printStackTrace()
        }
    }
}
