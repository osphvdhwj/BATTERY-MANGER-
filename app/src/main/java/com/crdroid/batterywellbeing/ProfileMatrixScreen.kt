package com.crdroid.batterywellbeing.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileMatrixScreen() {
    val context = LocalContext.current
    var isTournamentEngaged by remember { mutableStateOf(false) }

    fun dispatchTournamentState(engage: Boolean) {
        val intent = Intent("com.crdroid.batterywellbeing.ENGAGE_TOURNAMENT").apply {
            putExtra("engage", engage)
            putExtra("package", "com.dts.freefiremax")
        }
        context.sendBroadcast(intent, "com.redwood.permission.SECURE_IPC")
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(20.dp)
    ) {
        Text("EXECUTION PROFILES", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 2.sp)
        Text("Select hardware silicon states.", color = Color(0xFFA0A0A0), fontSize = 14.sp)
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = if (isTournamentEngaged) Color(0xFF1A0000) else Color(0xFF151515)),
            border = BorderStroke(1.dp, if (isTournamentEngaged) Color(0xFFFF004D) else Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("TOURNAMENT MODE", color = if (isTournamentEngaged) Color(0xFFFF004D) else Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Switch(
                        checked = isTournamentEngaged,
                        onCheckedChange = { 
                            isTournamentEngaged = it
                            dispatchTournamentState(it) 
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFFF004D), uncheckedTrackColor = Color(0xFF222222))
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("> A78 Prime Core Isolation", color = Color(0xFFA0A0A0), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text("> IThermalService Spoofing", color = Color(0xFFA0A0A0), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text("> Audio DSPs: UNTOUCHED", color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
        }
    }
}
