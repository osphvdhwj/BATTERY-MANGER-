package com.crdroid.batterywellbeing

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.app.usage.NetworkStatsManager
import android.app.usage.NetworkStats
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.google.accompanist.drawablepainter.rememberDrawablePainter

import androidx.compose.ui.unit.dp
import org.json.JSONArray
import com.crdroid.batterywellbeing.ui.theme.BatteryWellbeingTheme
import com.crdroid.batterywellbeing.ui.WellbeingDashboardScreen
import com.crdroid.batterywellbeing.ui.AppUsageItem
import java.util.Calendar

// Vico Imports
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry

data class BatteryStat(
    val title: String,
    val value1: Double,
    val value2: Double,
    val isApp: Boolean = false,
    var screenTimeMs: Long = 0L,
    var wifiBytes: Long = 0L,
    var mobileBytes: Long = 0L
)

// Helpers
fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun getDailyUnlockCount(context: Context): Int {
    if (!hasUsageStatsPermission(context)) return 0

    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    val startTime = calendar.timeInMillis
    val endTime = System.currentTimeMillis()

    val events = usm.queryEvents(startTime, endTime)
    val event = UsageEvents.Event()
    var unlockCount = 0

    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) {
            unlockCount++
        }
    }
    return unlockCount
}

fun getDailyScreenTime(context: Context): Map<String, Long> {
    if (!hasUsageStatsPermission(context)) return emptyMap()

    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    val startTime = calendar.timeInMillis
    val endTime = System.currentTimeMillis()

    val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)

    val screenTimeMap = mutableMapOf<String, Long>()
    for ((packageName, usageStat) in stats) {
        if (usageStat.totalTimeInForeground > 0) {
            screenTimeMap[packageName] = usageStat.totalTimeInForeground
        }
    }
    return screenTimeMap
}

fun getDailyNetworkUsage(context: Context): Map<Int, Pair<Long, Long>> {
    if (!hasUsageStatsPermission(context)) return emptyMap()

    val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    val calendar = java.util.Calendar.getInstance()
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    val startTime = calendar.timeInMillis
    val endTime = System.currentTimeMillis()

    val usageMap = mutableMapOf<Int, Pair<Long, Long>>() // UID -> Pair(WiFi, Mobile)

    try {
        // Query Wi-Fi
        val wifiStats = networkStatsManager.querySummary(ConnectivityManager.TYPE_WIFI, null, startTime, endTime)
        val bucket = NetworkStats.Bucket()
        while (wifiStats.hasNextBucket()) {
            wifiStats.getNextBucket(bucket)
            val current = usageMap[bucket.uid] ?: Pair(0L, 0L)
            usageMap[bucket.uid] = current.copy(first = current.first + bucket.rxBytes + bucket.txBytes)
        }
        wifiStats.close()

        // Query Cellular
        val mobileStats = networkStatsManager.querySummary(ConnectivityManager.TYPE_MOBILE, null, startTime, endTime)
        while (mobileStats.hasNextBucket()) {
            mobileStats.getNextBucket(bucket)
            val current = usageMap[bucket.uid] ?: Pair(0L, 0L)
            usageMap[bucket.uid] = current.copy(second = current.second + bucket.rxBytes + bucket.txBytes)
        }
        mobileStats.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return usageMap
}

fun formatScreenTime(timeMs: Long): String {
    if (timeMs == 0L) return "0m"
    val minutes = (timeMs / (1000 * 60)) % 60
    val hours = (timeMs / (1000 * 60 * 60))
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

fun formatBytes(bytes: Long): String {
    if (bytes == 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.2f KB", kb)
        else -> "$bytes B"
    }
}


class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        // Bootstrap the Heartbeat Service
        val serviceIntent = Intent(this, HeartbeatTrackerService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)

        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("BatteryWellbeingPrefs", Context.MODE_PRIVATE)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showSettings by remember { mutableStateOf(false) }
                    var showExemptions by remember { mutableStateOf(false) }
                    var showTimeLimitSheet by remember { mutableStateOf(false) }

                    if (showSettings) {
                        SettingsScreen(
                            prefs = prefs,
                            context = this@MainActivity,
                            onNavigateBack = { showSettings = false },
                            onShowExemptions = { showExemptions = true },
                            onShowTimeLimits = { showTimeLimitSheet = true }
                        )
                    } else {
                        WellbeingDashboardHost(prefs) { action ->
                            when (action) {
                                "Configure App Timers" -> showTimeLimitSheet = true
                                "Exemptions" -> showExemptions = true
                                "Settings" -> showSettings = true
                                "Thermal Settings" -> showSettings = true
                                "Manage Hotspot Limits" -> showSettings = true
                            }
                        }
                    }

                    if (showExemptions) {
                        ExemptionsDialog(prefs) { showExemptions = false }
                    }

                    if (showTimeLimitSheet) {
                        val savedJson = prefs.getString("app_timers_json", "{}") ?: "{}"
                        val existingTimers = mutableMapOf<String, Long>()
                        try {
                            val jsonObject = org.json.JSONObject(savedJson)
                            jsonObject.keys().forEach { key ->
                                existingTimers[key] = jsonObject.getLong(key)
                            }
                        } catch (e: Exception) {}

                        com.crdroid.batterywellbeing.ui.AppTimeLimitSheet(
                            onDismiss = { showTimeLimitSheet = false },
                            existingTimers = existingTimers
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryStatsReceiver(onStatsUpdated: (List<BatteryStat>) -> Unit) {
    val context = LocalContext.current

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.crdroid.batterywellbeing.UPDATE_STATS") {
                    val jsonString = intent.getStringExtra("battery_data_json") ?: return

                    try {
                        val jsonArray = JSONArray(jsonString)
                        val statsList = mutableListOf<BatteryStat>()

                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val title = obj.getString("title")

                            val isAppDrain = title.contains(".") || title.startsWith("APP|")

                            statsList.add(
                                BatteryStat(
                                    title = title,
                                    value1 = obj.getDouble("value1"),
                                    value2 = obj.getDouble("value2"),
                                    isApp = isAppDrain
                                )
                            )
                        }
                        onStatsUpdated(statsList)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        val filter = IntentFilter("com.crdroid.batterywellbeing.UPDATE_STATS")
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}

@Composable
fun WellbeingDashboardHost(prefs: SharedPreferences, onActionClick: (String) -> Unit) {
    val context = LocalContext.current
    var batteryStats by remember { mutableStateOf<List<BatteryStat>>(emptyList()) }
    var screenTimeMap by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var networkUsageMap by remember { mutableStateOf<Map<Int, Pair<Long, Long>>>(emptyMap()) }
    var unlockCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        screenTimeMap = getDailyScreenTime(context)
        networkUsageMap = getDailyNetworkUsage(context)
        unlockCount = getDailyUnlockCount(context)
    }

    BatteryStatsReceiver { newStats ->

        val mergedStats = newStats.map { stat ->
            var pkgName = stat.title
            var uid = -1
            if (pkgName.startsWith("APP|")) {
                val parts = pkgName.split("|")
                if (parts.size >= 3) {
                    try {
                        uid = parts[1].toInt()
                    } catch (e: Exception) {
                        // ignore
                    }
                    pkgName = parts[2]
                }
            }

            val time = screenTimeMap[pkgName] ?: 0L
            val network = if (uid != -1) networkUsageMap[uid] else Pair(0L, 0L)

            stat.copy(
                screenTimeMs = time,
                wifiBytes = network?.first ?: 0L,
                mobileBytes = network?.second ?: 0L
            )
        }
        batteryStats = mergedStats
    }

    val displayStats = if (batteryStats.isEmpty()) {
        listOf(
            BatteryStat("com.android.chrome", 250.0, 0.0, isApp = true, screenTimeMs = 3600000L, wifiBytes = 500000000L, mobileBytes = 150000000L),
            BatteryStat("APP|10234|com.instagram.android", 450.0, 0.0, isApp = true, screenTimeMs = 5400000L, wifiBytes = 1200000000L, mobileBytes = 0L)
        )
    } else {
        batteryStats
    }

    val appUsageItems = displayStats.filter { it.isApp }.map { stat ->
        var pkgName = stat.title
        if (pkgName.startsWith("APP|")) {
            val parts = pkgName.split("|")
            if (parts.size >= 3) {
                pkgName = parts[2]
            }
        }

        AppUsageItem(
            packageName = pkgName,
            name = pkgName, // In reality, resolve with getAppName
            iconUrl = pkgName,
            screenTimeMs = stat.screenTimeMs,
            batteryDrainMah = stat.value1.toInt(),
            is60HzCapped = setOf("com.instagram.android", "com.zhiliaoapp.musically", "com.twitter.android").contains(pkgName),
            isStorageMonitored = !ModuleConfig.exemptedApps.contains(pkgName)
        )
    }.sortedByDescending { it.screenTimeMs }

    WellbeingDashboardScreen(
        appUsageList = appUsageItems,
        totalScreenTimeMs = appUsageItems.sumOf { it.screenTimeMs },
        dailyGoalMs = 8L * 60 * 60 * 1000, // 8 Hours Goal
        onActionClick = onActionClick
    )
}

@Composable
fun PermissionBanner(context: Context) {
    var hasPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    if (!hasPermission) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Usage Access Required",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "To view Screen Time, please grant Usage Data Access in Settings.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onErrorContainer)
                ) {
                    Text("Grant Permission", color = MaterialTheme.colorScheme.errorContainer)
                }
            }
        }
    }
}

@Composable
fun BatteryBarChart(batteryData: List<BatteryStat>) {
    val topDrainers = remember(batteryData) {
        batteryData
            .filter { it.value1 > 0 }
            .sortedByDescending { it.value1 }
            .take(5)
    }

    val chartEntryModelProducer = remember { ChartEntryModelProducer() }

    LaunchedEffect(topDrainers) {
        val entries = topDrainers.mapIndexed { index, stat ->
            FloatEntry(x = index.toFloat(), y = stat.value1.toFloat())
        }
        chartEntryModelProducer.setEntries(entries)
    }

    val bottomAxisFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
        val index = value.toInt()
        if (index >= 0 && index < topDrainers.size) {
            var titleStr = topDrainers[index].title
            if (titleStr.startsWith("APP|")) {
                val parts = titleStr.split("|")
                if (parts.size >= 3) {
                    titleStr = parts[2]
                }
            }
            titleStr.take(10)
        } else {
            ""
        }
    }

    if (topDrainers.isNotEmpty()) {
        Chart(
            chart = columnChart(),
            chartModelProducer = chartEntryModelProducer,
            startAxis = rememberStartAxis(
                titleComponent = null,
                title = "Drain (mAh)"
            ),
            bottomAxis = rememberBottomAxis(
                valueFormatter = bottomAxisFormatter
            ),
            modifier = Modifier.fillMaxSize().padding(16.dp)
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Waiting for battery data...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WellbeingDashboard(batteryData: List<BatteryStat>, context: Context, unlockCount: Int, onSettingsClick: () -> Unit) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Digital Wellbeing") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Text("⚙️") // Simple settings icon
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            PermissionBanner(context)

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Daily Unlocks: $unlockCount", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                BatteryBarChart(batteryData = batteryData)
            }

            Spacer(modifier = Modifier.height(24.dp))

            val appStats = batteryData.filter { it.isApp }.sortedByDescending { it.value1 }
            val hardwareStats = batteryData.filter { !it.isApp }.sortedByDescending { it.value1 }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (appStats.isNotEmpty()) {
                    item {
                        Text(
                            text = "App Usage",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(appStats) { stat ->
                        AppUsageLimitItem(stat)
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                if (hardwareStats.isNotEmpty()) {
                    item {
                        Text(
                            text = "Hardware & System",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(hardwareStats) { stat ->
                        AppUsageLimitItem(stat)
                    }
                }
            }
        }
    }
}

@Composable
fun AppUsageLimitItem(stat: BatteryStat) {
    var displayTitle = stat.title
    if (displayTitle.startsWith("APP|")) {
        val parts = displayTitle.split("|")
        if (parts.size >= 3) {
            displayTitle = parts[2]
        }
    }

    val context = LocalContext.current
    val iconDrawable = remember(displayTitle) {
        try {
            context.packageManager.getApplicationIcon(displayTitle)
        } catch (e: Exception) {
            null
        }
    }

    val is60HzCapped = setOf("com.instagram.android", "com.zhiliaoapp.musically", "com.twitter.android").contains(displayTitle)
    val isStorageMonitored = !ModuleConfig.exemptedApps.contains(displayTitle)

    val titlePrefix = buildString {
        if (is60HzCapped) append("[60Hz] ")
        if (!isStorageMonitored) append("[Exempt] ")
    }

    ListItem(
        leadingContent = {
            if (iconDrawable != null) {
                Image(
                    painter = rememberDrawablePainter(iconDrawable),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                // Fallback placeholder
                Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer))
            }
        },
        headlineContent = { Text(titlePrefix + displayTitle, maxLines = 1) },
        supportingContent = {
            Column {
                Text("Drain: ${String.format("%.2f", stat.value1)} mAh")
                if (stat.screenTimeMs > 0) Text("Time: ${formatScreenTime(stat.screenTimeMs)}")
                if (stat.wifiBytes > 0 || stat.mobileBytes > 0) Text("Data: ${formatBytes(stat.wifiBytes + stat.mobileBytes)}")
            }
        },
        trailingContent = {
            OutlinedButton(onClick = { /* Open Dialog */ }) {
                Text("Details")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: SharedPreferences, context: Context, onNavigateBack: () -> Unit, onShowExemptions: () -> Unit, onShowTimeLimits: () -> Unit) {
    // 🔋 Battery States
    var smartCharge by remember { mutableStateOf(prefs.getBoolean("enableSmartCharge", true)) }
    var smartChargePercent by remember { mutableStateOf(prefs.getInt("smartChargeLimitPercent", 80).toFloat()) }
    var thermalThreshold by remember { mutableStateOf(prefs.getInt("thermalWarningThresholdC", 42).toFloat()) }

    // 🛡️ Storage & Background
    var rogueApp by remember { mutableStateOf(prefs.getBoolean("enableRogueApp", true)) }
    var storageAbuse by remember { mutableStateOf(prefs.getBoolean("enableStorageAbuse", true)) }
    var storageAbuseLimitMB by remember { mutableStateOf(prefs.getInt("storageThresholdMB", 500).toFloat()) }

    // ⏱️ Wellbeing States
    var enableAppTimers by remember { mutableStateOf(prefs.getBoolean("enableAppTimers", false)) }
    var bedtimeMode by remember { mutableStateOf(prefs.getBoolean("enforceBedtimeMode", false)) }

    // 📡 Hotspot States
    var hotspotLimit by remember { mutableStateOf(prefs.getBoolean("enableHotspotLimits", true)) }
    var hotspotDataMB by remember { mutableStateOf(prefs.getInt("hotspotDataLimitMB", 500).toFloat()) }
    var dropAggressiveClients by remember { mutableStateOf(prefs.getBoolean("dropAggressiveClients", true)) }

    // Broadcast helper to send everything to system_server
    fun saveAndBroadcast() {
        prefs.edit().apply {
            putBoolean("enableSmartCharge", smartCharge)
            putInt("smartChargeLimitPercent", smartChargePercent.toInt())
            putBoolean("enableThermalWarnings", thermalThreshold > 0)
            putInt("thermalWarningThresholdC", thermalThreshold.toInt())

            putBoolean("enableRogueApp", rogueApp)
            putBoolean("enableStorageAbuse", storageAbuse)
            putInt("storageThresholdMB", storageAbuseLimitMB.toInt())

            putBoolean("enableAppTimers", enableAppTimers)
            putBoolean("enforceBedtimeMode", bedtimeMode)

            putBoolean("enableHotspotLimits", hotspotLimit)
            putInt("hotspotDataLimitMB", hotspotDataMB.toInt())
            putBoolean("dropAggressiveClients", dropAggressiveClients)
        }.apply()

        val intent = Intent("com.crdroid.batterywellbeing.UPDATE_SETTINGS").apply {
            putExtra("enableSmartCharge", smartCharge)
            putExtra("smartChargeLimitPercent", smartChargePercent.toInt())
            putExtra("enableThermalWarnings", thermalThreshold > 0)
            putExtra("thermalWarningThresholdC", thermalThreshold.toInt())

            putExtra("enableRogueApp", rogueApp)
            putExtra("enableStorageAbuse", storageAbuse)
            putExtra("storageThresholdMB", storageAbuseLimitMB.toInt())

            putExtra("enableAppTimers", enableAppTimers)
            putExtra("enforceBedtimeMode", bedtimeMode)

            putExtra("enableHotspotLimits", hotspotLimit)
            putExtra("hotspotDataLimitMB", hotspotDataMB.toInt())
            putExtra("dropAggressiveClients", dropAggressiveClients)
        }
        context.sendBroadcast(intent)
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Ecosystem Control") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Text("←", style = MaterialTheme.typography.titleLarge) }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {

            // --- SECTION: HARDWARE & BATTERY ---
            item { SectionHeader("Hardware & Battery") }
            item { SettingToggle("Smart Charge Limit", "Stop charging to protect battery health", smartCharge) { smartCharge = it; saveAndBroadcast() } }
            if (smartCharge) {
                item {
                    SettingSlider("Charge Limit: ${smartChargePercent.toInt()}%", smartChargePercent, 50f..95f) {
                        smartChargePercent = it; saveAndBroadcast()
                    }
                }
            }
            item {
                SettingSlider("Thermal Warning Alert: ${thermalThreshold.toInt()}°C", thermalThreshold, 35f..50f) {
                    thermalThreshold = it; saveAndBroadcast()
                }
            }

            // --- SECTION: DIGITAL WELLBEING ---
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { SectionHeader("Digital Wellbeing") }
            item { SettingToggle("Strict App Timers", "Force close apps when daily limit is reached", enableAppTimers) { enableAppTimers = it; saveAndBroadcast() } }
            item { SettingToggle("Bedtime Mode Enforcer", "Aggressively kill media/game processes at night", bedtimeMode) { bedtimeMode = it; saveAndBroadcast() } }
            item {
                OutlinedButton(onClick = onShowTimeLimits, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("Configure Per-App Time Limits")
                }
            }

            // --- SECTION: BACKGROUND & STORAGE ---
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { SectionHeader("Background & Storage") }
            item { SettingToggle("Rogue App Drain Detection", "Alert if apps abuse wakelocks", rogueApp) { rogueApp = it; saveAndBroadcast() } }
            item { SettingToggle("Background Storage Abuse", "Alert if background apps thrash disk I/O", storageAbuse) { storageAbuse = it; saveAndBroadcast() } }
            if (storageAbuse) {
                item {
                    SettingSlider("Write Limit: ${storageAbuseLimitMB.toInt()} MB", storageAbuseLimitMB, 100f..2000f) {
                        storageAbuseLimitMB = it; saveAndBroadcast()
                    }
                }
            }
            item {
                OutlinedButton(onClick = onShowExemptions, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("Manage Heavy Workload Exemptions")
                }
            }

            // --- SECTION: NETWORK & HOTSPOT ---
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { SectionHeader("Network & Tethering") }
            item { SettingToggle("Per-Connection Hotspot Limit", "Drop clients exceeding data quotas", hotspotLimit) { hotspotLimit = it; saveAndBroadcast() } }
            if (hotspotLimit) {
                item {
                    SettingSlider("Client Data Quota: ${hotspotDataMB.toInt()} MB", hotspotDataMB, 100f..2000f) {
                        hotspotDataMB = it; saveAndBroadcast()
                    }
                }
            }
        }
    }
}

// Reusable UI Components
@Composable
fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun SettingToggle(title: String, subtitle: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}
