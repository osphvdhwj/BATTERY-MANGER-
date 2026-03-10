package com.crdroid.batterywellbeing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

// --- Data Model ---
data class CpuCoreStat(
    val coreId: Int,
    val frequencyKHz: Long
)

// --- Main Composable ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuMonitorScreen(onNavigateBack: () -> Unit) {
    var cpuStats by remember { mutableStateOf<List<CpuCoreStat>>(emptyList()) }

    // Hardware Polling Loop
    LaunchedEffect(Unit) {
        while (true) {
            cpuStats = getLiveCpuFrequencies()
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Processor Telemetry",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", color = Color.White, fontSize = 24.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black // Deep AMOLED Black
                )
            )
        },
        containerColor = Color.Black // Deep AMOLED Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header / System Summary
            Text(
                text = "REAL-TIME CORE FREQUENCIES",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                letterSpacing = 1.5.sp
            )

            // The Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(cpuStats, key = { it.coreId }) { stat ->
                    CpuCoreCard(stat = stat)
                }
            }
        }
    }
}

// --- Component Composables ---
@Composable
fun CpuCoreCard(stat: CpuCoreStat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp), // Premium rounded glassmorphism
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF151515) // Strict ecosystem spec
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)) // Subtle glass stroke
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Core ${stat.coreId}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            val displayFreq = if (stat.frequencyKHz > 0L) {
                "${stat.frequencyKHz / 1000} MHz"
            } else {
                "Offline"
            }

            Text(
                text = displayFreq,
                fontFamily = FontFamily.Monospace, // Prevents UI jitter
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = if (stat.frequencyKHz > 0L) Color(0xFF00E5FF) else Color.DarkGray // Neon Cyan Spec
            )
        }
    }
}

// --- Hardware Polling Helper ---
private suspend fun getLiveCpuFrequencies(): List<CpuCoreStat> {
    return withContext(Dispatchers.IO) { // Ensure disk reads don't block the UI thread
        val stats = mutableListOf<CpuCoreStat>()
        // Poll cores 0 through 7 (standard for modern octacore SOCs)
        for (i in 0 until 8) {
            val freqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            if (freqFile.exists()) {
                val freqKHz = try {
                    freqFile.readText().trim().toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    0L // Core is likely offline/sleeping (common on LITTLE cores)
                }
                stats.add(CpuCoreStat(coreId = i, frequencyKHz = freqKHz))
            } else {
                // Core does not exist or node is inaccessible
                stats.add(CpuCoreStat(coreId = i, frequencyKHz = 0L))
            }
        }
        stats
    }
}
