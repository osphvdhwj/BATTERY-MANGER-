package com.crdroid.batterywellbeing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

data class AppUsageItem(
    val packageName: String,
    val name: String,
    val iconUrl: Any,
    val screenTimeMs: Long,
    val batteryDrainMah: Int,
    val is60HzCapped: Boolean,
    val isStorageMonitored: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WellbeingDashboardScreen(
    appUsageList: List<AppUsageItem>,
    totalScreenTimeMs: Long,
    dailyGoalMs: Long,
    onActionClick: (String) -> Unit
) {
    Scaffold(
        containerColor = Color.Black, // 🚀 True AMOLED Black
        topBar = {
            TopAppBar(
                title = {
                    Text("SYSTEM TELEMETRY", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {

            // --- 1. The Glowing Neon Hero Ring ---
            item {
                NeonHeroRing(totalScreenTimeMs, dailyGoalMs)
            }

            item {
                Text(
                    text = "ACTIVE PROCESSES",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }

            // --- 2. Glassmorphism App Rows ---
            items(appUsageList) { app ->
                GlassAppRow(app)
            }

            item {
                Text(
                    text = "ECOSYSTEM OVERRIDES",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 16.dp)
                )
            }

            // --- 3. Glass Action Grid ---
            item {
                GlassActionGrid(onActionClick)
            }
        }
    }
}

@Composable
fun NeonHeroRing(totalTimeMs: Long, goalTimeMs: Long) {
    val progress = remember(totalTimeMs, goalTimeMs) {
        (totalTimeMs.toFloat() / goalTimeMs.toFloat()).coerceIn(0f, 1f)
    }

    val minutes = (totalTimeMs / (1000 * 60)) % 60
    val hours = (totalTimeMs / (1000 * 60 * 60))

    // 🚀 FIX: Ensure there are NO backslashes before the dollar signs here!
    val timeString = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF00E5FF), Color(0xFF007BFF))
    )

    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(260.dp)) {
            val strokeWidth = 28.dp.toPx()

            drawArc(
                color = Color(0xFF151515),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                brush = gradientBrush,
                startAngle = -90f,
                sweepAngle = progress * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The timeString variable is passed directly to the Text component
            Text(text = timeString, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "SCREEN TIME", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

@Composable
fun GlassAppRow(app: AppUsageItem) {
    val minutes = (app.screenTimeMs / (1000 * 60)) % 60
    val hours = (app.screenTimeMs / (1000 * 60 * 60))

    // 🚀 FIX: Standard string interpolation
    val timeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                // 🚀 FIX: Fallback to package name ONLY if app.name is blank
                val displayName = app.name.ifBlank { app.packageName }
                Text(text = displayName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 🚀 FIX: Standard string interpolation for the stats
                    Text(text = "⏱ $timeStr", color = Color(0xFFA0A0A0), fontSize = 13.sp)
                    Text(text = "🔋 ${app.batteryDrainMah} mAh", color = Color(0xFFA0A0A0), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun GlassActionGrid(onActionClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassActionCard("App Timers", "⏳", Modifier.weight(1f)) { onActionClick("Configure App Timers") }
            GlassActionCard("Network Quotas", "📡", Modifier.weight(1f)) { onActionClick("Manage Hotspot Limits") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassActionCard("Thermal Engine", "🔥", Modifier.weight(1f)) { onActionClick("Thermal Settings") }
            GlassActionCard("Exemptions", "🛡️", Modifier.weight(1f)) { onActionClick("Exemptions") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassActionCard("Module Settings", "⚙️", Modifier.fillMaxWidth()) { onActionClick("Settings") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassActionCard(title: String, icon: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(icon, fontSize = 24.sp)
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
