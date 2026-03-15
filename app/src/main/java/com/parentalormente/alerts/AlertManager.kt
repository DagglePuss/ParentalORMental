package com.parentalormente.alerts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.parentalormente.ParentalApp
import com.parentalormente.R
import com.parentalormente.data.db.IncidentEntity
import com.parentalormente.data.prefs.AppPreferences
import com.parentalormente.detection.BullyingDetector
import kotlinx.coroutines.flow.first

/**
 * Handles on-device notifications when bullying is detected.
 * SMS relay to parent is now handled by RealTimeRelay.
 */
class AlertManager(private val context: Context) {

    companion object {
        private const val TAG = "AlertManager"
    }

    private val prefs = AppPreferences(context)

    suspend fun handleIncident(incident: IncidentEntity, severity: BullyingDetector.Severity) {
        val minSeverityStr = prefs.alertMinSeverity.first()
        val minSeverity = try {
            BullyingDetector.Severity.valueOf(minSeverityStr)
        } catch (_: Exception) {
            BullyingDetector.Severity.MEDIUM
        }

        if (severity.level < minSeverity.level) {
            Log.d(TAG, "Severity ${severity.name} below threshold ${minSeverity.name}, skipping alert")
            return
        }

        showNotification(incident, severity)
    }

    private fun showNotification(incident: IncidentEntity, severity: BullyingDetector.Severity) {
        val title = when (severity) {
            BullyingDetector.Severity.CRITICAL -> "URGENT: Threat Detected"
            BullyingDetector.Severity.HIGH -> "Alert: Harassment Detected"
            BullyingDetector.Severity.MEDIUM -> "Warning: Bullying Language"
            BullyingDetector.Severity.LOW -> "Notice: Possible Teasing"
            else -> return
        }

        val priority = when (severity) {
            BullyingDetector.Severity.CRITICAL -> NotificationCompat.PRIORITY_MAX
            BullyingDetector.Severity.HIGH -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(context, ParentalApp.CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText("From: ${incident.sender} — ${incident.summary}")
            .setSmallIcon(R.drawable.ic_warning)
            .setPriority(priority)
            .setAutoCancel(true)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("From: ${incident.sender}\n${incident.summary}\n\nOpen app to review.")
            )
            .build()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(
                incident.id.toInt(),
                notification
            )
        }
    }
}
