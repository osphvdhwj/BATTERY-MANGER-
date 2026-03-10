package com.crdroid.batterywellbeing

import java.io.DataOutputStream

object FocusManager {

    fun setFocusMode(packagesToBlock: List<String>, enable: Boolean) {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)

                val state = if (enable) "true" else "false"

                for (pkg in packagesToBlock) {
                    // This native command suspends the app, graying out its icon
                    os.writeBytes("pm suspend --user 0 $pkg $state\n")
                }

                os.writeBytes("exit\n")
                os.flush()
                process.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
