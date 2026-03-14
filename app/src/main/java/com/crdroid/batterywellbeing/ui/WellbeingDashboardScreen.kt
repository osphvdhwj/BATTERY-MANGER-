package com.crdroid.batterywellbeing.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.crdroid.batterywellbeing.*
import kotlinx.coroutines.delay

data class AppUsageItem(val packageName: String, val name: String, val iconUrl: Any, val screenTimeMs: Long, val batteryDrainMah: Int, val isThrottled: Boolean, val isExempted: Boolean)

@Composable
fun RealTimeDashboardScreen(navController: NavController) {
    val context = LocalContext.current
    var isPollingHardware by remember { mutableStateOf(true) }
    var appUsageList by remember { mutableStateOf<List<AppUsageItem>>(emptyList()) }
    var totalScreenTimeMs by remember { mutableStateOf(0L) }

    if (!hasUsageStatsPermission(context)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(
                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) { Text("Grant Usage Permission", color = Color.Black, fontWeight = FontWeight.Bold) }
        }
        return
    }

    LaunchedEffect(Unit) {
        delay(800) // Brief shimmer/loading effect for premium feel
        val realStats = getDailyScreenTime(context)
        if (realStats.isNotEmpty()) {
            val items = realStats.map { (pkg, time) ->
                AppUsageItem(pkg, getAppName(context, pkg), getAppIcon(context, pkg) ?: pkg, time, 0, false, false)
            }.sortedByDescending { it.screenTimeMs }.take(15) // Top 15 apps
            
            appUsageList = items
            totalScreenTimeMs = items.sumOf { it.screenTimeMs }
        }
        isPollingHardware = false
    }

    if (isPollingHardware) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 4.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("POLLING HARDWARE TELEMETRY...", color = Color(0xFF00E5FF), fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        WellbeingDashboardScreen(appUsageList, totalScreenTimeMs, 8L * 60 * 60 * 1000)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WellbeingDashboardScreen(appUsageList: List<AppUsageItem>, totalScreenTimeMs: Long, dailyGoalMs: Long) {
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("SYSTEM TELEMETRY", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { NeonHeroRing(totalScreenTimeMs, dailyGoalMs) }
            item {
                Text("ACTIVE PROCESSES", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
            }
            items(appUsageList) { app -> GlassAppRow(app) }
        }
    }
}

@Composable
fun NeonHeroRing(totalTimeMs: Long, goalTimeMs: Long) {
    val progress = remember(totalTimeMs, goalTimeMs) { (totalTimeMs.toFloat() / goalTimeMs.toFloat()).coerceIn(0f, 1f) }
    val minutes = (totalTimeMs / (1000 * 60)) % 60
    val hours = (totalTimeMs / (1000 * 60 * 60))
    val timeString = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    val gradientBrush = Brush.verticalGradient(colors = listOf(Color(0xFF00E5FF), Color(0xFF007BFF)))

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(260.dp)) {
            val strokeWidth = 28.dp.toPx()
            drawArc(color = Color(0xFF151515), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            drawArc(brush = gradientBrush, startAngle = -90f, sweepAngle = progress * 360f, useCenter = false, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(timeString, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text("SCREEN TIME", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

@Composable
fun GlassAppRow(app: AppUsageItem) {
    val minutes = (app.screenTimeMs / (1000 * 60)) % 60
    val hours = (app.screenTimeMs / (1000 * 60 * 60))
    val timeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = app.iconUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(CircleShape))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                val displayName = app.name.ifBlank { app.packageName }
                Text(displayName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("⏱ $timeStr", color = Color(0xFFA0A0A0), fontSize = 13.sp)
            }
        }
    }
}
