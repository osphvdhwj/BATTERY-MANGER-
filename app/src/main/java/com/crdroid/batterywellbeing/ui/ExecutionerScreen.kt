package com.crdroid.batterywellbeing.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

data class AppTimerItem(val packageName: String, val label: String, val icon: android.graphics.drawable.Drawable)

@Composable
fun ExecutionerScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppTimerItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load active timers from prefs
    val currentTimers = remember { mutableStateMapOf<String, Long>() }
    
    LaunchedEffect(Unit) {
        val savedJson = prefs.getString("app_timers_json", "{}") ?: "{}"
        try {
            val jsonObject = JSONObject(savedJson)
            jsonObject.keys().forEach { currentTimers[it] = jsonObject.getLong(it) }
        } catch (e: Exception) {}

        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        apps = packages.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { AppTimerItem(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it)) }
            .sortedBy { it.label }
        isLoading = false
    }

    fun saveTimers() {
        val jsonPayload = JSONObject().apply { currentTimers.filterValues { it > 0 }.forEach { (k, v) -> put(k, v) } }.toString()
        prefs.edit().putString("app_timers_json", jsonPayload).apply()
        context.sendBroadcast(Intent("com.crdroid.batterywellbeing.UPDATE_TIMERS").apply { putExtra("timers_payload", jsonPayload) }, "com.redwood.permission.SECURE_IPC")
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("THE EXECUTIONER", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 2.sp)
        Text("Terminate background drainers securely.", color = Color(0xFFA0A0A0), fontSize = 14.sp)
        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00E5FF))
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(apps) { app ->
                    val limitMs = currentTimers[app.packageName] ?: 0L
                    val limitMinutes = limitMs / (60 * 1000)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(painter = rememberDrawablePainter(app.icon), contentDescription = null, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(if (limitMinutes > 0) "$limitMinutes min limit" else "Unrestricted", color = if (limitMinutes > 0) Color(0xFF00E5FF) else Color(0xFF666666), fontSize = 13.sp)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TimeLimitChip("15m", limitMinutes == 15L) { currentTimers[app.packageName] = 15 * 60 * 1000L; saveTimers() }
                                TimeLimitChip("30m", limitMinutes == 30L) { currentTimers[app.packageName] = 30 * 60 * 1000L; saveTimers() }
                                TimeLimitChip("1h", limitMinutes == 60L) { currentTimers[app.packageName] = 60 * 60 * 1000L; saveTimers() }
                                TimeLimitChip("None", limitMinutes == 0L) { currentTimers.remove(app.packageName); saveTimers() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimeLimitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.15f),
            selectedLabelColor = Color(0xFF00E5FF),
            labelColor = Color.Gray
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            borderColor = Color(0xFF222222), selectedBorderColor = Color(0xFF00E5FF)
        )
    )
}
