package com.crdroid.batterywellbeing

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.crdroid.batterywellbeing.ui.*
import com.crdroid.batterywellbeing.ui.theme.BatteryWellbeingTheme
import java.util.Calendar

// --- Global Utilities ---
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
        // Start Heartbeat Tracker natively
        val serviceIntent = Intent(this, HeartbeatTrackerService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)

        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("BatteryWellbeingPrefs", Context.MODE_PRIVATE)

        setContent {
            BatteryWellbeingTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    val navController = rememberNavController()
                    
                    Scaffold(
                        containerColor = Color.Black,
                        bottomBar = { WellbeingBottomBar(navController) }
                    ) { innerPadding ->
                        WellbeingNavGraph(
                            navController = navController,
                            prefs = prefs,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WellbeingNavGraph(navController: NavHostController, prefs: SharedPreferences, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = "dashboard", modifier = modifier) {
        composable("dashboard") { RealTimeDashboardScreen(navController) }
        composable("telemetry") { CpuMonitorScreen(onNavigateBack = { navController.navigateUp() }) }
        composable("executioner") { ExecutionerScreen(prefs) }
        composable("shield") { ShieldSettingsScreen(prefs, LocalContext.current) }
    }
}

@Composable
fun WellbeingBottomBar(navController: NavHostController) {
    val items = listOf(
        Triple("dashboard", "Dashboard", "📊"),
        Triple("telemetry", "Hardware", "⚙️"),
        Triple("executioner", "Executioner", "⏳"),
        Triple("shield", "Shield", "🛡️")
    )
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = Color(0xFF151515), // Glassmorphism surface
        contentColor = Color.White,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        items.forEach { (route, label, icon) ->
            val selected = currentRoute == route
            NavigationBarItem(
                icon = { Text(icon) },
                label = { Text(label, color = if (selected) Color(0xFF00E5FF) else Color(0xFFA0A0A0)) },
                selected = selected,
                onClick = {
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.White.copy(alpha = 0.05f),
                    selectedIconColor = Color(0xFF00E5FF),
                    unselectedIconColor = Color(0xFFA0A0A0)
                )
            )
        }
    }
}
