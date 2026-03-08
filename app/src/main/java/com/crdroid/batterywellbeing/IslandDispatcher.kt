package com.crdroid.batterywellbeing

import android.content.Context
import android.content.Intent
import de.robv.android.xposed.XposedBridge

object IslandDispatcher {
    private const val ACTION_SYSTEM_OVERRIDE = "com.crdroid.batterywellbeing.SYSTEM_OVERRIDE"
    private const val TARGET_PACKAGE = "com.android.systemui"

    fun dispatchEvent(
        context: Context,
        actionType: String,
        batteryLevel: Int,
        extraData: String? = null
    ) {
        try {
            val intent = Intent(ACTION_SYSTEM_OVERRIDE).apply {
                setPackage(TARGET_PACKAGE)
                putExtra("action", actionType)
                putExtra("level", batteryLevel)
                extraData?.let { putExtra("extra_info", it) }
                // FLAG_RECEIVER_REGISTERED_ONLY to ensure only active, registered receivers catch it
                addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
            }
            context.sendBroadcast(intent)
            XposedBridge.log("IslandDispatcher: Fired \$actionType to Dynamic Island at \$batteryLevel%")
        } catch (e: Exception) {
            XposedBridge.log("IslandDispatcher Error: \${e.message}")
        }
    }
}
