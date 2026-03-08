package com.crdroid.batterywellbeing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.ArrayList

data class BatteryEntry(
    val title: String,
    val value1: Double,
    val value2: Double
)

class MainActivity : ComponentActivity() {

    private val batteryDataState = mutableStateListOf<BatteryEntry>()

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.crdroid.batterywellbeing.UPDATE_DATA") {
                val dataList = intent.getStringArrayListExtra("battery_data")
                if (dataList != null) {
                    updateData(dataList)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register receiver for broadcasts from Xposed hook
        val filter = IntentFilter("com.crdroid.batterywellbeing.UPDATE_DATA")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dataReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(dataReceiver, filter)
        }

        // Add some mock data for initial UI testing
        if (batteryDataState.isEmpty()) {
            batteryDataState.addAll(
                listOf(
                    BatteryEntry("Screen", 500.0, 1000.0),
                    BatteryEntry("CPU", 300.0, 500.0),
                    BatteryEntry("Wi-Fi", 150.0, 200.0)
                )
            )
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WellbeingDashboard(batteryDataState)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(dataReceiver)
    }

    private fun updateData(rawData: ArrayList<String>) {
        val newEntries = rawData.mapNotNull { item ->
            val parts = item.split("|")
            if (parts.size >= 3) {
                try {
                    BatteryEntry(
                        title = parts[0],
                        value1 = parts[1].toDouble(),
                        value2 = parts[2].toDouble()
                    )
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }

        batteryDataState.clear()
        batteryDataState.addAll(newEntries)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WellbeingDashboard(batteryData: List<BatteryEntry>) {
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
                    .height(200.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    // Vico chart placeholder for now
                    Text("Battery Drain Overview", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Battery Consumers List
            Text(
                text = "Battery Consumers",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                items(batteryData) { entry ->
                    AppUsageLimitItem(entry)
                }
            }
        }
    }
}

@Composable
fun AppUsageLimitItem(entry: BatteryEntry) {
    ListItem(
        headlineContent = { Text(entry.title) },
        supportingContent = { Text("Drain: \${String.format("%.2f", entry.value1)} mAh") },
        trailingContent = {
            OutlinedButton(onClick = { /* Open Dialog */ }) {
                Text("Details")
            }
        }
    )
}
