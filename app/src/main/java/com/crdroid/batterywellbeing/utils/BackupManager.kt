package com.crdroid.batterywellbeing.utils

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object BackupManager {

    private val backupDir = File(Environment.getExternalStorageDirectory(), "WellbeingBackup")

    fun exportBackup(context: Context): Boolean {
        try {
            if (!backupDir.exists()) backupDir.mkdirs()

            // 1. Backup the Room Database
            val dbFile = context.getDatabasePath("wellbeing_db")
            if (dbFile.exists()) {
                val destDb = File(backupDir, "wellbeing_db.sqlite")
                copyFile(dbFile, destDb)
            }

            // 2. Backup SharedPreferences (Timers, Exemptions)
            val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/BatteryWellbeingPrefs.xml")
            if (prefsFile.exists()) {
                val destPrefs = File(backupDir, "BatteryWellbeingPrefs.xml")
                copyFile(prefsFile, destPrefs)
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun restoreBackup(context: Context): Boolean {
        try {
            if (!backupDir.exists()) return false

            // Restore Database
            val backupDb = File(backupDir, "wellbeing_db.sqlite")
            if (backupDb.exists()) {
                val destDb = context.getDatabasePath("wellbeing_db")
                copyFile(backupDb, destDb)
            }

            // Restore Prefs
            val backupPrefs = File(backupDir, "BatteryWellbeingPrefs.xml")
            if (backupPrefs.exists()) {
                val destPrefs = File(context.applicationInfo.dataDir, "shared_prefs/BatteryWellbeingPrefs.xml")
                copyFile(backupPrefs, destPrefs)
            }

            // Note: App must be force-restarted after restore for prefs to load into memory
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun copyFile(source: File, dest: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                val buffer = ByteArray(1024)
                var length: Int
                while (input.read(buffer).also { length = it } > 0) {
                    output.write(buffer, 0, length)
                }
            }
        }
    }
}
