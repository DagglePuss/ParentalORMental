package com.parentalormente.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.parentalormente.ParentalApp
import com.parentalormente.data.db.IncidentEntity
import com.parentalormente.detection.BullyingDetector
import com.parentalormente.alerts.AlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Intercepts incoming SMS messages and runs them through
 * the BullyingDetector. If flagged, logs the incident
 * and alerts the parent.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group message parts by sender
        val grouped = mutableMapOf<String, StringBuilder>()
        for (sms in messages) {
            val sender = sms.originatingAddress ?: "unknown"
            grouped.getOrPut(sender) { StringBuilder() }.append(sms.messageBody)
        }

        val db = ParentalApp.instance.database.incidentDao()
        val alertManager = AlertManager(context)

        for ((sender, body) in grouped) {
            val messageText = body.toString()
            val result = BullyingDetector.analyze(messageText)

            if (result.severity == BullyingDetector.Severity.NONE) continue

            Log.w(TAG, "Flagged SMS from $sender — severity: ${result.severity}")

            val incident = IncidentEntity(
                sourceType = "SMS",
                sender = sender,
                content = messageText,
                severity = result.severity.level,
                matchedPatterns = result.matchedPatterns.joinToString("|"),
                summary = result.summary
            )

            CoroutineScope(Dispatchers.IO).launch {
                val id = db.insert(incident)
                Log.d(TAG, "Incident logged with id=$id")

                // Alert parent based on severity threshold
                alertManager.handleIncident(
                    incident.copy(id = id),
                    result.severity
                )
            }
        }
    }
}
