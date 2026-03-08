package com.crdroid.batterywellbeing.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.json.JSONObject

data class AppTimerItem(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimeLimitSheet(
    onDismiss: () -> Unit,
    existingTimers: Map<String, Long>
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppTimerItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Mutable state for the currently active timers being configured in the sheet
    val currentTimers = remember { mutableStateMapOf<String, Long>().apply { putAll(existingTimers) } }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val userApps = packages.filter {
            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }.map {
            AppTimerItem(
                packageName = it.packageName,
                label = pm.getApplicationLabel(it).toString(),
                icon = pm.getApplicationIcon(it)
            )
        }.sortedBy { it.label }

        apps = userApps
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.9f)) {
            Text("App Time Limits", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Set daily screen time quotas. Apps exceeding this will be forcefully stopped if Strict App Timers are enabled.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(apps) { app ->
                        val limitMs = currentTimers[app.packageName] ?: 0L
                        val limitMinutes = limitMs / (60 * 1000)

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = rememberDrawablePainter(app.icon),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                Text(if (limitMinutes > 0) "\$limitMinutes minutes" else "No Limit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }

                            // Simple quick-add minutes button for demo/functional purposes
                            Row {
                                IconButton(onClick = {
                                    if (limitMinutes >= 15) currentTimers[app.packageName] = (limitMinutes - 15) * 60 * 1000
                                    else currentTimers.remove(app.packageName)
                                }) {
                                    Text("-", style = MaterialTheme.typography.titleLarge)
                                }
                                IconButton(onClick = {
                                    currentTimers[app.packageName] = (limitMinutes + 15) * 60 * 1000
                                }) {
                                    Text("+", style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    dispatchTimersToBackend(context, currentTimers.toMap())
                    onDismiss()
                }) {
                    Text("Save & Apply")
                }
            }
        }
    }
}

private fun dispatchTimersToBackend(context: Context, timers: Map<String, Long>) {
    try {
        val jsonPayload = JSONObject()
        timers.forEach { (pkg, ms) ->
            if (ms > 0) jsonPayload.put(pkg, ms)
        }
        val jsonString = jsonPayload.toString()

        // Save to SharedPreferences for BootAnchorReceiver using correct prefs name
        val prefs = context.getSharedPreferences("BatteryWellbeingPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_timers_json", jsonString).apply()

        // Broadcast to system_server
        val intent = Intent("com.crdroid.batterywellbeing.UPDATE_TIMERS").apply {
            putExtra("timers_payload", jsonString)
        }
        context.sendBroadcast(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
