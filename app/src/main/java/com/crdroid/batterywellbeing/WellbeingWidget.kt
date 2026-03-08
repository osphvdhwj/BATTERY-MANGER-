package com.crdroid.batterywellbeing

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class WellbeingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WellbeingWidget()
}

class WellbeingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Fetch top app by screen time natively so the widget doesn't rely on the app being open
        val screenTimeMap = getDailyScreenTime(context)
        val topApp = screenTimeMap.maxByOrNull { it.value }

        val appName = if (topApp != null) {
            getAppNameFromPackage(context, topApp.key)
        } else {
            "No Data"
        }

        val timeString = if (topApp != null) formatScreenTime(topApp.value) else "0m"

        provideContent {
            // Glassmorphism-style container using Material 3 Surface colors
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(androidx.glance.color.ColorProvider(day = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor("#E3E3E3")), night = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor("#1E1E1E"))))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Top Usage Today",
                    style = TextStyle(
                        color = androidx.glance.color.ColorProvider(day = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor("#5A5A5A")), night = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor("#A0A0A0"))),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = GlanceModifier.height(8.dp))

                Text(
                    text = appName,
                    style = TextStyle(
                        color = androidx.glance.color.ColorProvider(day = androidx.compose.ui.graphics.Color(android.graphics.Color.BLACK), night = androidx.compose.ui.graphics.Color(android.graphics.Color.WHITE)),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                Text(
                    text = timeString,
                    style = TextStyle(
                        color = androidx.glance.color.ColorProvider(day = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor("#0066FF")), night = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor("#66B2FF"))),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    private fun getAppNameFromPackage(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.split(".").last().replaceFirstChar { it.uppercase() }
        }
    }
}
