package com.crdroid.batterywellbeing.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

        val active = userApps.filter { activePackagesToday.contains(it.packageName) }.sortedBy { it.label }
        val inactive = userApps.filter { !activePackagesToday.contains(it.packageName) }.sortedBy { it.label }

        apps = active + inactive
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0F0F0F), // True Black AMOLED Base
        scrimColor = Color.Black.copy(alpha = 0.8f)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.9f)) {
            Text("RESTRICT APP TIME", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Terminate heavy drainers automatically.", color = Color(0xFFA0A0A0), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF))
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    var previousWasActive = true

                    items(apps) { app ->
                        val isActive = activePackagesToday.contains(app.packageName)
                        if (isActive && apps.indexOf(app) == 0) {
                            Text("ACTIVE TODAY", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.padding(vertical = 12.dp))
                        } else if (!isActive && previousWasActive) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("ALL APPS", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.padding(vertical = 12.dp))
                            previousWasActive = false
                        }

                        val limitMs = currentTimers[app.packageName] ?: 0L
                        val limitMinutes = limitMs / (60 * 1000)

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(painter = rememberDrawablePainter(app.icon), contentDescription = null, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(if (limitMinutes > 0) "$limitMinutes min limit" else "No Limit", color = if (limitMinutes > 0) Color(0xFF00E5FF) else Color(0xFF666666), fontSize = 13.sp)
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TimeLimitChip("15m", limitMinutes == 15L) { currentTimers[app.packageName] = 15 * 60 * 1000L }
                                    TimeLimitChip("30m", limitMinutes == 30L) { currentTimers[app.packageName] = 30 * 60 * 1000L }
                                    TimeLimitChip("1h", limitMinutes == 60L) { currentTimers[app.packageName] = 60 * 60 * 1000L }
                                    TimeLimitChip("None", limitMinutes == 0L) { currentTimers.remove(app.packageName) }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("CANCEL", color = Color(0xFFA0A0A0)) }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = { dispatchTimersToBackend(context, currentTimers.toMap()); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))) {
                    Text("APPLY", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RowScope.TimeLimitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .background(
                color = if (selected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF222222),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color(0xFF00E5FF) else Color.White, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun dispatchTimersToBackend(context: Context, timers: Map<String, Long>) {
    try {
        val jsonPayload = JSONObject()
        timers.forEach { (pkg, ms) -> if (ms > 0) jsonPayload.put(pkg, ms) }
        val jsonString = jsonPayload.toString()

        val prefs = context.getSharedPreferences("BatteryWellbeingPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_timers_json", jsonString).apply()

        val intent = Intent("com.crdroid.batterywellbeing.UPDATE_TIMERS").apply { putExtra("timers_payload", jsonString) }
        context.sendBroadcast(intent, "com.redwood.permission.SECURE_IPC")
    } catch (e: Exception) { e.printStackTrace() }
}
