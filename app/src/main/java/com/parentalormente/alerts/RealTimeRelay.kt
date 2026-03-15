package com.parentalormente.alerts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.parentalormente.data.db.IncidentDao
import com.parentalormente.data.db.IncidentEntity
import com.parentalormente.data.prefs.AppPreferences
import com.parentalormente.detection.BullyingDetector
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real-time incident relay to parent device.
 *
 * Streams full incident data to the parent's phone via structured SMS
 * immediately upon detection. No delay. No cloud. No internet required.
 *
 * Protocol: POM|version|type|fields...
 * - Parseable by a future companion parent app
 * - Human-readable as plain SMS for parents without the app
 *
 * Escalation tiers:
 * - CRITICAL: Immediate multi-SMS with full message content, patterns, and action guidance
 * - HIGH: Full detail SMS with content and matched patterns
 * - MEDIUM: Summary SMS with severity and sender
 * - LOW: Batched into periodic digests (configurable)
 */
class RealTimeRelay(private val context: Context) {

    companion object {
        private const val TAG = "RealTimeRelay"
        private const val PROTOCOL_VERSION = "1"
        private const val HEADER = "POM" // ParentalORMental protocol prefix
    }

    private val prefs = AppPreferences(context)

    /**
     * Immediately relay a flagged incident to the parent.
     * Called the instant BullyingDetector flags something.
     */
    suspend fun relayIncident(incident: IncidentEntity, severity: BullyingDetector.Severity) {
        val parentPhone = prefs.parentPhone.first()
        if (parentPhone.isBlank()) {
            Log.w(TAG, "No parent phone configured, cannot relay")
            return
        }

        if (!hasSmsPermission()) {
            Log.w(TAG, "SEND_SMS permission not granted")
            return
        }

        when (severity) {
            BullyingDetector.Severity.CRITICAL -> relayCritical(parentPhone, incident)
            BullyingDetector.Severity.HIGH -> relayHigh(parentPhone, incident)
            BullyingDetector.Severity.MEDIUM -> relayMedium(parentPhone, incident)
            BullyingDetector.Severity.LOW -> relayLow(parentPhone, incident)
            else -> {}
        }
    }

    /**
     * CRITICAL: Threat of violence, self-harm language.
     * Send EVERYTHING immediately. No filtering. No delay.
     * Multiple SMS if needed — parent needs full context NOW.
     */
    private fun relayCritical(phone: String, incident: IncidentEntity) {
        val timestamp = formatTime(incident.timestamp)

        // Message 1: Urgent header with action guidance
        sendSms(phone, buildString {
            append("!! URGENT THREAT DETECTED !!\n")
            append("Time: $timestamp\n")
            append("From: ${incident.sender}\n\n")
            append("ACTION NEEDED: Check on your child immediately. ")
            append("If there is an imminent threat, contact law enforcement.")
        })

        // Message 2: Full message content — parent needs to see exactly what was said
        sendSms(phone, buildString {
            append("FULL MESSAGE FROM ${incident.sender}:\n\n")
            append("\"${incident.content}\"\n\n")
            append("Severity: CRITICAL\n")
            append("Detection: ${incident.summary}")
        })

        // Message 3: Matched patterns — evidence for documentation
        sendSms(phone, buildString {
            append("MATCHED THREAT PATTERNS:\n")
            append(incident.matchedPatterns.replace("|", "\n"))
            append("\n\n[POM|$PROTOCOL_VERSION|CRITICAL|${incident.id}|${incident.sender}|${incident.timestamp}]")
        })

        Log.i(TAG, "CRITICAL relay sent: 3 messages to $phone")
    }

    /**
     * HIGH: Direct harassment, targeted abuse.
     * Full detail immediately — content + patterns.
     */
    private fun relayHigh(phone: String, incident: IncidentEntity) {
        val timestamp = formatTime(incident.timestamp)

        sendSms(phone, buildString {
            append("HARASSMENT ALERT\n")
            append("Time: $timestamp\n")
            append("From: ${incident.sender}\n\n")
            append("Message: \"${incident.content}\"\n\n")
            append("${incident.summary}\n")
            append("Patterns: ${incident.matchedPatterns.replace("|", ", ")}\n\n")
            append("[POM|$PROTOCOL_VERSION|HIGH|${incident.id}|${incident.sender}|${incident.timestamp}]")
        })

        Log.i(TAG, "HIGH relay sent to $phone")
    }

    /**
     * MEDIUM: Insults, exclusion language.
     * Summary with sender and detection info.
     */
    private fun relayMedium(phone: String, incident: IncidentEntity) {
        val timestamp = formatTime(incident.timestamp)

        sendSms(phone, buildString {
            append("Bullying Warning\n")
            append("Time: $timestamp\n")
            append("From: ${incident.sender}\n\n")
            append("\"${truncate(incident.content, 100)}\"\n\n")
            append("${incident.summary}\n\n")
            append("[POM|$PROTOCOL_VERSION|MEDIUM|${incident.id}|${incident.sender}|${incident.timestamp}]")
        })

        Log.i(TAG, "MEDIUM relay sent to $phone")
    }

    /**
     * LOW: Possible teasing. Still relay but less urgently.
     */
    private fun relayLow(phone: String, incident: IncidentEntity) {
        val timestamp = formatTime(incident.timestamp)

        sendSms(phone, buildString {
            append("Notice: Possible teasing detected\n")
            append("Time: $timestamp | From: ${incident.sender}\n")
            append("\"${truncate(incident.content, 80)}\"\n\n")
            append("[POM|$PROTOCOL_VERSION|LOW|${incident.id}|${incident.sender}|${incident.timestamp}]")
        })

        Log.i(TAG, "LOW relay sent to $phone")
    }

    /**
     * Send a daily digest summary of all incidents in the last 24h.
     * Called by WorkManager periodic task.
     */
    suspend fun sendDailyDigest(dao: IncidentDao) {
        val parentPhone = prefs.parentPhone.first()
        if (parentPhone.isBlank() || !hasSmsPermission()) return

        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val count = dao.getCountSince(oneDayAgo).first()

        if (count == 0) return

        sendSms(parentPhone, buildString {
            append("ParentalORMental Daily Summary\n\n")
            append("$count incident${if (count != 1) "s" else ""} flagged in the last 24 hours.\n")
            append("Open the app on your child's device to review details.\n\n")
            append("[POM|$PROTOCOL_VERSION|DIGEST|$count|${System.currentTimeMillis()}]")
        })

        Log.i(TAG, "Daily digest sent: $count incidents")
    }

    /**
     * Emergency broadcast — triggered manually by the child via panic button.
     */
    suspend fun sendPanicAlert(locationInfo: String? = null) {
        val parentPhone = prefs.parentPhone.first()
        if (parentPhone.isBlank() || !hasSmsPermission()) return

        val timestamp = formatTime(System.currentTimeMillis())

        sendSms(parentPhone, buildString {
            append("!! SOS — YOUR CHILD NEEDS HELP !!\n\n")
            append("Your child pressed the emergency button at $timestamp.\n")
            if (!locationInfo.isNullOrBlank()) {
                append("Location: $locationInfo\n")
            }
            append("\nCall them or check on them immediately.\n\n")
            append("[POM|$PROTOCOL_VERSION|PANIC|${System.currentTimeMillis()}]")
        })

        Log.i(TAG, "PANIC alert sent to $parentPhone")
    }

    private fun sendSms(phone: String, message: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
        }
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
    }

    private fun truncate(text: String, maxLen: Int): String {
        return if (text.length <= maxLen) text else text.take(maxLen) + "..."
    }
}
