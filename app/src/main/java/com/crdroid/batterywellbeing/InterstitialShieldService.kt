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
import androidx.compose.ui.draw.alpha
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
        val alpha = remember { androidx.compose.animation.core.Animatable(0f) }

        LaunchedEffect(Unit) {
            // 1.5 second fade in to thick glass
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
            )

            // Time is up. Drop the hammer via system_server securely.
            val killIntent = Intent("com.crdroid.batterywellbeing.EXECUTE_KILL").apply {
                putExtra("package_name", targetPackage)
            }
            sendBroadcast(killIntent, "com.redwood.permission.SECURE_IPC")

            // Wait 1 second after the kill, then fade out
            delay(1000)

            alpha.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.tween(500)
            )

            // Clean up the overlay
            stopSelf()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000).copy(alpha = alpha.value * 0.95f)) // Deep black fade
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(alpha.value)) {
                Text("🔒", fontSize = 80.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Text("Session Complete", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Your time limit for $appName has been reached.", color = Color.LightGray, fontSize = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
