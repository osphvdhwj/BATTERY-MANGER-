package com.crdroid.batterywellbeing.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ShieldSettingsScreen(prefs: SharedPreferences, context: Context) {
    var smartCharge by remember { mutableStateOf(prefs.getBoolean("enableSmartCharge", true)) }
    var thermalThreshold by remember { mutableStateOf(prefs.getInt("thermalWarningThresholdC", 42).toFloat()) }
    var rogueApp by remember { mutableStateOf(prefs.getBoolean("enableRogueApp", true)) }
    var storageAbuse by remember { mutableStateOf(prefs.getBoolean("enableStorageAbuse", true)) }

    fun saveAndBroadcast() {
        prefs.edit().apply {
            putBoolean("enableSmartCharge", smartCharge)
            putInt("thermalWarningThresholdC", thermalThreshold.toInt())
            putBoolean("enableRogueApp", rogueApp)
            putBoolean("enableStorageAbuse", storageAbuse)
        }.apply()
        
        val intent = Intent("com.crdroid.batterywellbeing.UPDATE_SETTINGS").apply {
            putExtra("enableSmartCharge", smartCharge)
            putExtra("thermalWarningThresholdC", thermalThreshold.toInt())
            putExtra("enableRogueApp", rogueApp)
            putExtra("enableStorageAbuse", storageAbuse)
        }
        context.sendBroadcast(intent, "com.redwood.permission.SECURE_IPC")
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        item {
            Text("ECOSYSTEM SHIELD", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 16.dp))
        }

        item { SectionHeader("Hardware Engine") }
        item { 
            GlassSettingToggle("Smart Charge Limit", "Halt charging at 80% to protect battery lifecycle", smartCharge) { 
                smartCharge = it; saveAndBroadcast() 
            } 
        }
        item { 
            GlassSettingSlider("Thermal Warning: ${thermalThreshold.toInt()}°C", thermalThreshold, 35f..50f) { 
                thermalThreshold = it; saveAndBroadcast() 
            } 
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        item { SectionHeader("System Security") }
        item { 
            GlassSettingToggle("Rogue App Defense", "Actively kill massive background CPU drainers", rogueApp) { 
                rogueApp = it; saveAndBroadcast() 
            } 
        }
        item { 
            GlassSettingToggle("Storage I/O Shield", "Alert on unauthorized background disk writes", storageAbuse) { 
                storageAbuse = it; saveAndBroadcast() 
            } 
        }
    }
}

@Composable
fun SectionHeader(title: String) { 
    Text(title, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) 
}

@Composable
fun GlassSettingToggle(title: String, subtitle: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)), 
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = Color(0xFFA0A0A0), fontSize = 13.sp)
            }
            Switch(
                checked = isChecked, 
                onCheckedChange = onCheckedChange, 
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF00E5FF), uncheckedTrackColor = Color(0xFF222222), uncheckedThumbColor = Color.Gray)
            )
        }
    }
}

@Composable
fun GlassSettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)), 
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Slider(
                value = value, 
                onValueChange = onValueChange, 
                valueRange = range, 
                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF), inactiveTrackColor = Color(0xFF222222))
            )
        }
    }
}
