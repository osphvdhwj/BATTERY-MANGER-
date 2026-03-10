package com.crdroid.batterywellbeing

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import org.json.JSONArray
import com.crdroid.batterywellbeing.ui.theme.BatteryWellbeingTheme
import com.crdroid.batterywellbeing.ui.WellbeingDashboardScreen
import com.crdroid.batterywellbeing.ui.AppUsageItem
import java.util.Calendar

data class BatteryStat(
    val title: String, val value1: Double, val value2: Double, val isApp: Boolean = false,
    var screenTimeMs: Long = 0L, var wifiBytes: Long = 0L, var mobileBytes: Long = 0L
)

fun getAppIcon(context: Context, packageName: String): Drawable? = try { context.packageManager.getApplicationIcon(packageName) } catch (e: Exception) { null }
fun getAppName(context: Context, packageName: String): String = try { val appInfo = context.packageManager.getApplicationInfo(packageName, 0); context.packageManager.getApplicationLabel(appInfo).toString() } catch (e: Exception) { packageName }
fun hasUsageStatsPermission(context: Context): Boolean { val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager; return appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED }

fun getDailyScreenTime(context: Context): Map<String, Long> {
    if (!hasUsageStatsPermission(context)) return emptyMap()
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
    val stats = usm.queryAndAggregateUsageStats(cal.timeInMillis, System.currentTimeMillis())
    return stats.filterValues { it.totalTimeInForeground > 0 }.mapValues { it.value.totalTimeInForeground }
}

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        val serviceIntent = Intent(this, HeartbeatTrackerService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)

        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("BatteryWellbeingPrefs", Context.MODE_PRIVATE)

        setContent {
            // 🚀 CRITICAL FIX: We are now enforcing the Custom AMOLED Theme globally
            BatteryWellbeingTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    var showSettings by remember { mutableStateOf(false) }
                    var showExemptions by remember { mutableStateOf(false) }
                    var showTimeLimitSheet by remember { mutableStateOf(false) }

                    if (showSettings) {
                        SettingsScreen(prefs, this@MainActivity, { showSettings = false }, { showExemptions = true }, { showTimeLimitSheet = true })
                    } else {
                        WellbeingDashboardHost(prefs) { action ->
                            when (action) {
                                "Configure App Timers", "Manage Hotspot Limits", "Thermal Settings", "Settings" -> showSettings = true
                                "Exemptions" -> showExemptions = true
                            }
                        }
                    }

                    if (showExemptions) ExemptionsDialog(prefs) { showExemptions = false }

                    if (showTimeLimitSheet) {
                        val savedJson = prefs.getString("app_timers_json", "{}") ?: "{}"
                        val existingTimers = mutableMapOf<String, Long>()
                        try {
                            val jsonObject = org.json.JSONObject(savedJson)
                            jsonObject.keys().forEach { existingTimers[it] = jsonObject.getLong(it) }
                        } catch (e: Exception) {}

                        com.crdroid.batterywellbeing.ui.AppTimeLimitSheet(
                            onDismiss = { showTimeLimitSheet = false },
                            existingTimers = existingTimers,
                            activePackagesToday = getDailyScreenTime(this@MainActivity).keys
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WellbeingDashboardHost(prefs: SharedPreferences, onActionClick: (String) -> Unit) {
    val context = LocalContext.current
    var batteryStats by remember { mutableStateOf<List<BatteryStat>>(emptyList()) }
    var screenTimeMap by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    LaunchedEffect(Unit) { screenTimeMap = getDailyScreenTime(context) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == "com.crdroid.batterywellbeing.UPDATE_STATS") {
                    try {
                        val jsonArray = JSONArray(intent.getStringExtra("battery_data_json") ?: return)
                        val statsList = mutableListOf<BatteryStat>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val title = obj.getString("title")
                            statsList.add(BatteryStat(title, obj.getDouble("value1"), obj.getDouble("value2"), title.contains(".") || title.startsWith("APP|")))
                        }
                        batteryStats = statsList
                    } catch (e: Exception) {}
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter("com.crdroid.batterywellbeing.UPDATE_STATS"), Context.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val displayStats = if (batteryStats.isEmpty()) {
        listOf(BatteryStat("com.android.chrome", 250.0, 0.0, true, 3600000L), BatteryStat("APP|10234|com.instagram.android", 450.0, 0.0, true, 5400000L))
    } else batteryStats.map { stat ->
        var pkg = stat.title
        if (pkg.startsWith("APP|")) pkg = pkg.split("|").getOrNull(2) ?: pkg
        stat.copy(screenTimeMs = screenTimeMap[pkg] ?: 0L)
    }

    val appUsageItems = displayStats.filter { it.isApp }.map { stat ->
        var pkg = stat.title
        if (pkg.startsWith("APP|")) pkg = pkg.split("|").getOrNull(2) ?: pkg
        AppUsageItem(pkg, getAppName(context, pkg), getAppIcon(context, pkg) ?: pkg, stat.screenTimeMs, stat.value1.toInt(), setOf("com.instagram.android", "com.zhiliaoapp.musically").contains(pkg), !ModuleConfig.exemptedApps.contains(pkg))
    }.sortedByDescending { it.screenTimeMs }

    if (!hasUsageStatsPermission(context)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Grant Usage Permission to Start") }
        }
    } else {
        WellbeingDashboardScreen(appUsageItems, appUsageItems.sumOf { it.screenTimeMs }, 8L * 60 * 60 * 1000, onActionClick)
    }
}

// 🚀 REWRITTEN: Premium Glassmorphism Settings UI
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: SharedPreferences, context: Context, onNavigateBack: () -> Unit, onShowExemptions: () -> Unit, onShowTimeLimits: () -> Unit) {
    var smartCharge by remember { mutableStateOf(prefs.getBoolean("enableSmartCharge", true)) }
    var thermalThreshold by remember { mutableStateOf(prefs.getInt("thermalWarningThresholdC", 42).toFloat()) }
    var rogueApp by remember { mutableStateOf(prefs.getBoolean("enableRogueApp", true)) }
    var storageAbuse by remember { mutableStateOf(prefs.getBoolean("enableStorageAbuse", true)) }
    var enableAppTimers by remember { mutableStateOf(prefs.getBoolean("enableAppTimers", false)) }
    var hotspotLimit by remember { mutableStateOf(prefs.getBoolean("enableHotspotLimits", true)) }

    fun saveAndBroadcast() {
        prefs.edit().apply {
            putBoolean("enableSmartCharge", smartCharge); putInt("thermalWarningThresholdC", thermalThreshold.toInt())
            putBoolean("enableRogueApp", rogueApp); putBoolean("enableStorageAbuse", storageAbuse)
            putBoolean("enableAppTimers", enableAppTimers); putBoolean("enableHotspotLimits", hotspotLimit)
        }.apply()
        val intent = Intent("com.crdroid.batterywellbeing.UPDATE_SETTINGS").apply {
            putExtra("enableSmartCharge", smartCharge); putExtra("thermalWarningThresholdC", thermalThreshold.toInt())
            putExtra("enableRogueApp", rogueApp); putExtra("enableStorageAbuse", storageAbuse)
            putExtra("enableAppTimers", enableAppTimers); putExtra("enableHotspotLimits", hotspotLimit)
        }
        context.sendBroadcast(intent, "com.redwood.permission.SECURE_IPC")
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("ECOSYSTEM CONTROL", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Text("←", color = Color.White, fontSize = 24.sp) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionHeader("Hardware Engine") }
            item { GlassSettingToggle("Smart Charge Limit", "Stop charging at 80% to protect battery health", smartCharge) { smartCharge = it; saveAndBroadcast() } }
            item { GlassSettingSlider("Thermal Warning: ${thermalThreshold.toInt()}°C", thermalThreshold, 35f..50f) { thermalThreshold = it; saveAndBroadcast() } }

            item { Spacer(modifier = Modifier.height(24.dp)) }
            item { SectionHeader("Digital Wellbeing") }
            item { GlassSettingToggle("Strict App Timers", "Violently terminate apps when time runs out", enableAppTimers) { enableAppTimers = it; saveAndBroadcast() } }
            item { GlassButton("Configure Daily Limits", "⏳", onShowTimeLimits) }

            item { Spacer(modifier = Modifier.height(24.dp)) }
            item { SectionHeader("System Security") }
            item { GlassSettingToggle("Rogue App Defense", "Detect & kill hidden phantom processes", rogueApp) { rogueApp = it; saveAndBroadcast() } }
            item { GlassSettingToggle("Storage I/O Shield", "Alert on massive background disk writes", storageAbuse) { storageAbuse = it; saveAndBroadcast() } }
            item { GlassButton("Manage Heavy Workloads", "🛡️", onShowExemptions) }
        }
    }
}

@Composable
fun SectionHeader(title: String) { Text(title, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) }

@Composable
fun GlassSettingToggle(title: String, subtitle: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = Color(0xFFA0A0A0), fontSize = 13.sp)
            }
            Switch(checked = isChecked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF00E5FF), uncheckedTrackColor = Color(0xFF222222)))
        }
    }
}

@Composable
fun GlassSettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Slider(value = value, onValueChange = onValueChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF), inactiveTrackColor = Color(0xFF222222)))
        }
    }
}

@Composable
fun GlassButton(label: String, icon: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E))) {
        Text("$icon   $label", color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
    }
}
