package com.crdroid.batterywellbeing

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.json.JSONArray

data class AppItem(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExemptionsDialog(
    prefs: SharedPreferences,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load current exemptions
    val initialExemptionsJson = prefs.getString("exempted_apps", "[]") ?: "[]"
    val initialExemptions = remember {
        val list = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(initialExemptionsJson)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (e: Exception) { }
        list
    }

    val selectedApps = remember { mutableStateListOf<String>().apply { addAll(initialExemptions) } }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val userApps = packages.filter {
            // Optional: filter out system apps if desired, but we want all apps just in case
            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }.map {
            AppItem(
                packageName = it.packageName,
                label = pm.getApplicationLabel(it).toString(),
                icon = pm.getApplicationIcon(it)
            )
        }.sortedBy { it.label }

        apps = userApps
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxHeight(0.8f).fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Manage Exemptions", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Select apps allowed to bypass battery & storage restrictions.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(apps) { app ->
                            val isChecked = selectedApps.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = rememberDrawablePainter(app.icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedApps.add(app.packageName)
                                        else selectedApps.remove(app.packageName)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val jsonArray = JSONArray()
                        selectedApps.forEach { jsonArray.put(it) }

                        val jsonStr = jsonArray.toString()
                        prefs.edit().putString("exempted_apps", jsonStr).apply()

                        val intent = Intent("com.crdroid.batterywellbeing.UPDATE_EXEMPTIONS").apply {
                            putExtra("exemptions_json", jsonStr)
                        }
                        context.sendBroadcast(intent)

                        onDismiss()
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
