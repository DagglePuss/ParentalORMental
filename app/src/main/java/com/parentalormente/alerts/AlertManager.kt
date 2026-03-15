package com.parentalormente.alerts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
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
 * Handles alerting the parent when bullying is detected.
 *
 * Two alert channels:
 * 1. On-device notification (always)
 * 2. SMS to parent phone number (if configured)
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

        // Always show local notification
        showNotification(incident, severity)

        // SMS alert if enabled
        val smsEnabled = prefs.smsAlerts.first()
        val parentPhone = prefs.parentPhone.first()
        if (smsEnabled && parentPhone.isNotBlank()) {
            sendSmsAlert(parentPhone, incident, severity)
        }
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

    private fun sendSmsAlert(parentPhone: String, incident: IncidentEntity, severity: BullyingDetector.Severity) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "SEND_SMS permission not granted, skipping SMS alert")
            return
        }

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val message = "[ParentalORMental] ${severity.name} alert — " +
                "From: ${incident.sender} — ${incident.summary}"

            // SMS has 160 char limit, split if needed
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(parentPhone, null, parts, null, null)
            Log.i(TAG, "SMS alert sent to $parentPhone")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS alert", e)
        }
    }
}
