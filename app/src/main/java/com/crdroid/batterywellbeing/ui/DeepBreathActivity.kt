package com.crdroid.batterywellbeing.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class DeepBreathActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Suppress generic deprecation warning for Parcelable
        @Suppress("DEPRECATION")
        val targetIntent = intent.getParcelableExtra<Intent>("target_intent")

        setContent {
            val isDark = isSystemInDarkTheme()
            val bgColor = if (isDark) Color(0xEB000000) else Color(0xEBFFFFFF)
            val textColor = if (isDark) Color.White else Color.Black
            val circleColor = MaterialTheme.colorScheme.primary

            var countdown by remember { mutableIntStateOf(5) }
            val scale = remember { Animatable(0.5f) }

            LaunchedEffect(Unit) {
                scale.animateTo(
                    targetValue = 1.5f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500, easing = LinearOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }

            LaunchedEffect(countdown) {
                if (countdown > 0) {
                    delay(1000)
                    countdown--
                } else if (targetIntent != null) {
                    // Release the original intent with the clearance flag
                    targetIntent.putExtra("friction_cleared", true)
                    startActivity(targetIntent)
                    finish()
                } else {
                    finish()
                }
            }

            Box(
                modifier = Modifier.fillMaxSize().background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(150.dp).scale(scale.value).background(circleColor.copy(alpha = 0.2f), CircleShape)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Take a breath.", color = textColor, fontSize = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Light)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(countdown.toString(), color = circleColor, fontSize = 48.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
    }
}
