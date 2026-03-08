package com.crdroid.batterywellbeing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
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
    val isApp: Boolean = false // New flag to categorize
)

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

                            // Hardware components are usually explicitly named.
                            // Anything resembling an app package (com.xxx) or an APP| prefix is an app.
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
    var batteryStats by remember { mutableStateOf<List<BatteryStat>>(emptyList()) }

    // This listens for the system broadcast silently in the background
    BatteryStatsReceiver { newStats ->
        batteryStats = newStats
    }

    // Pass the state to the UI layout
    // Provide some mock data if empty just to show the UI works initially
    val displayStats = if (batteryStats.isEmpty()) {
        listOf(
            BatteryStat("Screen", 500.0, 1000.0, isApp = false),
            BatteryStat("CPU", 300.0, 500.0, isApp = false),
            BatteryStat("Wi-Fi", 150.0, 200.0, isApp = false),
            BatteryStat("com.android.chrome", 250.0, 0.0, isApp = true),
            BatteryStat("APP|10234|com.instagram.android", 450.0, 0.0, isApp = true)
        )
    } else {
        batteryStats
    }

    WellbeingDashboard(displayStats)
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
fun WellbeingDashboard(batteryData: List<BatteryStat>) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Battery Wellbeing") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
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
        headlineContent = { Text(displayTitle) },
        supportingContent = { Text("Drain: \${String.format("%.2f", stat.value1)} mAh") },
        trailingContent = {
            OutlinedButton(onClick = { /* Open Dialog */ }) {
                Text("Details")
            }
        }
    )
}
