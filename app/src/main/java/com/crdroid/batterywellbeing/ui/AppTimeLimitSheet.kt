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
    existingTimers: Map<String, Long>,
    activePackagesToday: Set<String>
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
        }

        // Group by active vs inactive, then sort alphabetically
        val active = userApps.filter { activePackagesToday.contains(it.packageName) }.sortedBy { it.label }
        val inactive = userApps.filter { !activePackagesToday.contains(it.packageName) }.sortedBy { it.label }

        apps = active + inactive
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
                    var previousWasActive = true

                    items(apps) { app ->
                        val isActive = activePackagesToday.contains(app.packageName)
                        if (isActive && apps.indexOf(app) == 0) {
                            Text("Active Today", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp))
                        } else if (!isActive && previousWasActive) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("All Apps", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp))
                            previousWasActive = false
                        }

                        val limitMs = currentTimers[app.packageName] ?: 0L
                        val limitMinutes = limitMs / (60 * 1000)

                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                            }

                            // Zero-Friction Input Row (FilterChips)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = limitMinutes == 15L,
                                    onClick = { currentTimers[app.packageName] = 15 * 60 * 1000L },
                                    label = { Text("15 Min") }
                                )
                                FilterChip(
                                    selected = limitMinutes == 30L,
                                    onClick = { currentTimers[app.packageName] = 30 * 60 * 1000L },
                                    label = { Text("30 Min") }
                                )
                                FilterChip(
                                    selected = limitMinutes == 60L,
                                    onClick = { currentTimers[app.packageName] = 60 * 60 * 1000L },
                                    label = { Text("1 Hour") }
                                )
                                FilterChip(
                                    selected = limitMinutes == 0L,
                                    onClick = { currentTimers.remove(app.packageName) },
                                    label = { Text("None") }
                                )
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
        context.sendBroadcast(intent, "com.redwood.permission.SECURE_IPC")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
