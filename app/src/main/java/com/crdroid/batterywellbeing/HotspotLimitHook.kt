package com.crdroid.batterywellbeing

import android.content.Context
import android.net.MacAddress
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread

object HotspotLimitHook {

    // Default limit: 500 MB per connected device
    private var DATA_LIMIT_BYTES = 500 * 1024 * 1024L
    private val activeClients = mutableMapOf<String, Long>() // MAC -> Bytes Used

    fun initHooks(classLoader: ClassLoader) {
        try {
            // Disable eBPF Tethering Offload to force software iptables counting
            executeShellCommand("settings put global tether_offload_disabled 1")

            val callbackClass = "android.net.TetheringManager\$TetheringEventCallback"

            XposedHelpers.findAndHookMethod(
                callbackClass,
                classLoader,
                "onClientsChanged",
                Collection::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val clients = param.args[0] as Collection<*>
                            val currentMacs = mutableSetOf<String>()

                            for (client in clients) {
                                if (client == null) continue

                                // Extract the TetheredClient's MAC Address
                                val macAddressObj = XposedHelpers.callMethod(client, "getMacAddress")
                                val macString = macAddressObj.toString()
                                currentMacs.add(macString)

                                // If it's a newly discovered MAC, inject the iptables accounting rule
                                if (!activeClients.containsKey(macString)) {
                                    activeClients[macString] = 0L
                                    // injectIptablesRule(macString) // Deprecated: Replaced by future eBPF
                                    XposedBridge.log("BatteryWellbeing: New Hotspot Client -> $macString. Tracking started.")
                                }
                            }

                            // Clean up disconnected clients
                            activeClients.keys.retainAll(currentMacs)

                        } catch (e: Exception) {
                            XposedBridge.log("BatteryWellbeing Hotspot Hook Error: ${e.message}")
                        }
                    }
                }
            )

            // Start the background polling thread for iptables
            // startDataPollingThread() // Deprecated: High battery drain

            XposedBridge.log("BatteryWellbeing: Hotspot Per-Connection Limits Engaged.")

        } catch (e: Exception) {
            XposedBridge.log("BatteryWellbeing Critical Hotspot Hook Failure: ${e.message}")
        }
    }

    private fun injectIptablesRule(macAddress: String) {
        // Inject an accounting rule that simply returns, incrementing the byte counter
        executeShellCommand("iptables -I tetherctrl_FORWARD -m mac --mac-source $macAddress -j RETURN")
    }

    private fun startDataPollingThread() {
        thread(start = true) {
            while (true) {
                Thread.sleep(5000) // Poll every 5 seconds
                if (activeClients.isEmpty()) continue

                // Read the unformatted byte counters
                val output = executeShellCommand("iptables -L tetherctrl_FORWARD -v -n -x")

                for (mac in activeClients.keys.toList()) {
                    val bytesUsed = parseIptablesOutputForMac(output, mac)
                    if (bytesUsed > 0) {
                        activeClients[mac] = bytesUsed

                        // Check against quota
                        val dynamicLimit = ModuleConfig.hotspotDataLimitMB * 1024 * 1024L
                        if (ModuleConfig.enableHotspotLimits && bytesUsed > dynamicLimit) {
                            XposedBridge.log("BatteryWellbeing: Client $mac exceeded limit. Terminating connection.")
                            kickClient(mac)
                        }
                    }
                }
            }
        }
    }

    private fun parseIptablesOutputForMac(output: String, mac: String): Long {
        // Logic to extract rxBytes/txBytes from iptables output string for the specific MAC
        val lines = output.split("\n")
        for (line in lines) {
            if (line.contains(mac, ignoreCase = true)) {
                val columns = line.trim().split("\\s+".toRegex())
                if (columns.size > 1) {
                    return columns[1].toLongOrNull() ?: 0L // Column 1 usually holds bytes in -v -x mode
                }
            }
        }
        return 0L
    }

    private fun kickClient(macAddress: String) {
        try {
            val activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", null)
            val currentActivityThread = XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread")
            val context = XposedHelpers.callMethod(currentActivityThread, "getSystemContext") as Context

            val wifiManager = context.getSystemService(Context.WIFI_SERVICE)

            // Use reflection to bypass hidden API restrictions
            val currentConfig = XposedHelpers.callMethod(wifiManager, "getSoftApConfiguration")
            val blockedListObj = XposedHelpers.callMethod(currentConfig, "getBlockedClientList") as List<*>
            val blockedList = blockedListObj.toMutableList()

            val macObj = android.net.MacAddress.fromString(macAddress)
            if (!blockedList.contains(macObj)) {
                blockedList.add(macObj)

                // Reflective Builder
                val builderClass = XposedHelpers.findClass("android.net.wifi.SoftApConfiguration\$Builder", null)
                val builder = XposedHelpers.newInstance(builderClass, currentConfig)
                XposedHelpers.callMethod(builder, "setBlockedClientList", blockedList)
                val newConfig = XposedHelpers.callMethod(builder, "build")

                XposedHelpers.callMethod(wifiManager, "setSoftApConfiguration", newConfig)
                activeClients.remove(macAddress)
            }
        } catch (e: Exception) {
            XposedBridge.log("BatteryWellbeing Kick Error: ${e.message}")
        }
    }

    private fun executeShellCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            ""
        }
    }
}
