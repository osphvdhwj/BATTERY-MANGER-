package com.crdroid.batterywellbeing

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.app.usage.NetworkStatsManager
import android.app.usage.NetworkStats
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
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

// 1. Create a data model for the parsed JSON
data class BatteryStat(
    val title: String,
    val value1: Double,
    val value2: Double,
    val isApp: Boolean = false,
    var screenTimeMs: Long = 0L, // NEW: Holds daily screen time
    var wifiBytes: Long = 0L,    // NEW
    var mobileBytes: Long = 0L   // NEW
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
    return if (hours > 0) "\${hours}h \${minutes}m" else "\${minutes}m"
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
        else -> "\$bytes B"
    }
}


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WellbeingDashboardScreen()
                }
            }
        }
    }
}

// 2. Set up the Broadcast Receiver
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

        // Register the receiver
        val filter = IntentFilter("com.crdroid.batterywellbeing.UPDATE_STATS")
        // Use RECEIVER_EXPORTED for Android 14+ (API 34+) compatibility
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}

// 3. Connecting it to the UI
@Composable
fun WellbeingDashboardScreen() {
    val context = LocalContext.current
    var batteryStats by remember { mutableStateOf<List<BatteryStat>>(emptyList()) }
    var screenTimeMap by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var networkUsageMap by remember { mutableStateOf<Map<Int, Pair<Long, Long>>>(emptyMap()) }

    // Fetch Screen Time and Network whenever the UI recomposes
    LaunchedEffect(Unit) {
        screenTimeMap = getDailyScreenTime(context)
        networkUsageMap = getDailyNetworkUsage(context)
    }

    // Merge battery data with screen time data and network usage data
    BatteryStatsReceiver { newStats ->
        val mergedStats = newStats.map { stat ->
            // Try to extract pure package name for mapping
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

            // If the title is a package name, try to fetch its screen time
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

    // Pass the state to the UI layout
    // Provide some mock data if empty just to show the UI works initially
    val displayStats = if (batteryStats.isEmpty()) {
        listOf(
            BatteryStat("Screen", 500.0, 1000.0, isApp = false),
            BatteryStat("CPU", 300.0, 500.0, isApp = false),
            BatteryStat("Wi-Fi", 150.0, 200.0, isApp = false),
            BatteryStat("com.android.chrome", 250.0, 0.0, isApp = true, screenTimeMs = 3600000L, wifiBytes = 500000000L, mobileBytes = 150000000L),
            BatteryStat("APP|10234|com.instagram.android", 450.0, 0.0, isApp = true, screenTimeMs = 5400000L, wifiBytes = 1200000000L, mobileBytes = 0L)
        )
    } else {
        batteryStats
    }

    WellbeingDashboard(displayStats, context)
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
    // 1. Sort and filter to get the top 5 highest drainers to keep the chart clean
    val topDrainers = remember(batteryData) {
        batteryData
            .filter { it.value1 > 0 } // Only show items that actually drained power
            .sortedByDescending { it.value1 }
            .take(5)
    }

    // 2. Map the data into Vico's FloatEntry format
    val chartEntryModelProducer = remember { ChartEntryModelProducer() }

    LaunchedEffect(topDrainers) {
        val entries = topDrainers.mapIndexed { index, stat ->
            FloatEntry(x = index.toFloat(), y = stat.value1.toFloat())
        }
        chartEntryModelProducer.setEntries(entries)
    }

    // 3. Create a custom formatter to show the App/Component name on the X-axis
    val bottomAxisFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
        val index = value.toInt()
        if (index >= 0 && index < topDrainers.size) {
            // Truncate long names so they fit on the screen
            var titleStr = topDrainers[index].title
            // Quick cleanup for "APP|..." prefix for the chart
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

    // 4. Render the Chart
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Waiting for battery data...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WellbeingDashboard(batteryData: List<BatteryStat>, context: Context) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Digital Wellbeing") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Show the permission request if needed
            PermissionBanner(context)

            // 1. The Main Chart
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp), // Increased height slightly for better chart visibility
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                BatteryBarChart(batteryData = batteryData)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Categorized Lists ---

            val appStats = batteryData.filter { it.isApp }.sortedByDescending { it.value1 }
            val hardwareStats = batteryData.filter { !it.isApp }.sortedByDescending { it.value1 }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Section: App Drain
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
                        AppUsageLimitItem(stat) // Reusing your existing item UI
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                // Section: Hardware Drain
                if (hardwareStats.isNotEmpty()) {
                    item {
                        Text(
                            text = "Hardware & System",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary, // Different color for distinction
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
    // Quick cleanup for "APP|uid|package" prefix for display
    var displayTitle = stat.title
    if (displayTitle.startsWith("APP|")) {
        val parts = displayTitle.split("|")
        if (parts.size >= 3) {
            displayTitle = parts[2]
        }
    }

    ListItem(
        headlineContent = { Text(displayTitle, maxLines = 1) },
        supportingContent = {
            val drainText = "Drain: \${String.format("%.2f", stat.value1)} mAh"
            val timeText = if (stat.screenTimeMs > 0) " • Time: \${formatScreenTime(stat.screenTimeMs)}" else ""
            val networkText = if (stat.wifiBytes > 0 || stat.mobileBytes > 0) " • Data: \${formatBytes(stat.wifiBytes + stat.mobileBytes)}" else ""
            Text(drainText + timeText + networkText)
        },
        trailingContent = {
            OutlinedButton(onClick = { /* Open Dialog */ }) {
                Text("Details")
            }
        }
    )
}
