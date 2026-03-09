package com.crdroid.batterywellbeing

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay

class InterstitialShieldService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val targetPackage = intent?.getStringExtra("package_name") ?: return START_NOT_STICKY
        val appName = intent.getStringExtra("app_name") ?: "This App"

        showOverlay(targetPackage, appName)
        return START_NOT_STICKY
    }

    private fun showOverlay(targetPackage: String, appName: String) {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Setup WindowManager LayoutParams for a full-screen overlay
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@InterstitialShieldService)
            setViewTreeSavedStateRegistryOwner(this@InterstitialShieldService)

            setContent {
                MaterialTheme {
                    InterstitialScreen(appName, targetPackage)
                }
            }
        }

        windowManager.addView(composeView, params)
    }

    @Composable
    fun InterstitialScreen(appName: String, targetPackage: String) {
        var countdown by remember { mutableIntStateOf(5) }

        LaunchedEffect(Unit) {
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
            // Time is up. Drop the hammer via system_server.
            val killIntent = Intent("com.crdroid.batterywellbeing.EXECUTE_KILL").apply {
                putExtra("package_name", targetPackage)
            }
            sendBroadcast(killIntent)

            // Clean up the overlay
            stopSelf()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE6000000)) // 90% opaque black for dramatic effect
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Time's Up", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("You have reached your daily limit for \$appName.", color = Color.LightGray, fontSize = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(modifier = Modifier.height(48.dp))
                Text(countdown.toString(), color = Color(0xFF0A84FF), fontSize = 72.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(24.dp))
                Text("Taking a breath...", color = Color.Gray, fontSize = 14.sp)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (::composeView.isInitialized) {
            windowManager.removeView(composeView)
        }
    }
}
