package com.parentalormente.remote

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.parentalormente.data.prefs.AppPreferences
import com.parentalormente.evidence.EvidenceCollector
import com.parentalormente.evidence.ScreenCaptureService
import com.parentalormente.monitor.CrisisOverlayService
import com.parentalormente.ParentalApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Intercepts SMS commands from the parent's phone number.
 * Allows the parent to remotely intervene on the child's device
 * during critical situations.
 *
 * Commands (sent as SMS from parent's registered phone):
 *
 *   POM:LOCK          — Immediately lock the device screen
 *   POM:LOCATE        — Reply with current GPS coordinates
 *   POM:SCREENSHOT    — Capture and archive a screenshot
 *   POM:LOCKDOWN      — Full lockdown: lock device + disable notifications
 *   POM:STATUS        — Reply with current monitoring status
 *   POM:EXPORT        — Generate and archive a full evidence report
 *   POM:UNLOCK        — Release lockdown mode
 *   POM:CRISIS        — Show the crisis overlay screen on child's device
 *   POM:CRISIS:OFF    — Dismiss the crisis overlay remotely
 *
 * Security: Only responds to SMS from the registered parent phone number.
 * All commands are logged.
 */
class ParentCommandReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ParentCommand"
        private const val CMD_PREFIX = "POM:"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val prefs = AppPreferences(context)

        for (sms in messages) {
            val sender = sms.originatingAddress ?: continue
            val body = sms.messageBody?.trim()?.uppercase() ?: continue

            if (!body.startsWith(CMD_PREFIX)) continue

            // Verify this is from the registered parent
            CoroutineScope(Dispatchers.IO).launch {
                val parentPhone = prefs.parentPhone.first()
                if (parentPhone.isBlank()) {
                    Log.w(TAG, "No parent phone configured, ignoring command")
                    return@launch
                }

                // Normalize phone numbers for comparison (strip non-digits)
                val normalizedSender = sender.replace(Regex("[^0-9+]"), "")
                val normalizedParent = parentPhone.replace(Regex("[^0-9+]"), "")

                if (!normalizedSender.endsWith(normalizedParent.takeLast(10)) &&
                    !normalizedParent.endsWith(normalizedSender.takeLast(10))
                ) {
                    Log.w(TAG, "Command from unauthorized number: $sender")
                    return@launch
                }

                val command = body.removePrefix(CMD_PREFIX).trim()
                Log.i(TAG, "Parent command received: $command")

                executeCommand(context, command, parentPhone)
            }
        }
    }

    private suspend fun executeCommand(context: Context, command: String, parentPhone: String) {
        when {
            command == "LOCK" -> cmdLock(context, parentPhone)
            command == "LOCKDOWN" -> cmdLockdown(context, parentPhone)
            command == "UNLOCK" -> cmdUnlock(context, parentPhone)
            command == "LOCATE" -> cmdLocate(context, parentPhone)
            command == "SCREENSHOT" -> cmdScreenshot(context, parentPhone)
            command == "STATUS" -> cmdStatus(context, parentPhone)
            command == "EXPORT" -> cmdExport(context, parentPhone)
            command == "CRISIS" -> cmdCrisis(context, parentPhone)
            command == "CRISIS:OFF" -> cmdCrisisOff(context, parentPhone)
            else -> reply(context, parentPhone, "Unknown command: $command\n\nAvailable: LOCK, LOCKDOWN, UNLOCK, LOCATE, SCREENSHOT, STATUS, EXPORT, CRISIS, CRISIS:OFF")
        }
    }

    /**
     * LOCK — Immediately lock the device screen.
     */
    private fun cmdLock(context: Context, parentPhone: String) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, PomDeviceAdmin::class.java)

        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
            reply(context, parentPhone, "Device locked successfully.")
            Log.i(TAG, "Device locked by parent command")
        } else {
            reply(context, parentPhone, "Device Admin not active. Open the app on the child's device and enable Device Admin in Settings.")
            Log.w(TAG, "Cannot lock — Device Admin not active")
        }
    }

    /**
     * LOCKDOWN — Full lockdown mode.
     * Locks device and sets a flag so the app shows a lockdown screen on unlock.
     */
    private suspend fun cmdLockdown(context: Context, parentPhone: String) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, PomDeviceAdmin::class.java)
        val prefs = AppPreferences(context)

        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
            prefs.setLockdownActive(true)
            reply(context, parentPhone, "LOCKDOWN ACTIVE. Device locked. Send POM:UNLOCK to release.")
            Log.i(TAG, "LOCKDOWN activated by parent")
        } else {
            reply(context, parentPhone, "Device Admin not active. Cannot lockdown.")
        }
    }

    /**
     * UNLOCK — Release lockdown mode.
     */
    private suspend fun cmdUnlock(context: Context, parentPhone: String) {
        val prefs = AppPreferences(context)
        prefs.setLockdownActive(false)
        reply(context, parentPhone, "Lockdown released. Device is accessible.")
        Log.i(TAG, "Lockdown released by parent")
    }

    /**
     * LOCATE — Reply with GPS coordinates.
     */
    private fun cmdLocate(context: Context, parentPhone: String) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        try {
            val location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                val mapsUrl = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                reply(context, parentPhone, "Device location:\n${location.latitude}, ${location.longitude}\n\n$mapsUrl\n\nAccuracy: ${location.accuracy}m")
            } else {
                reply(context, parentPhone, "Location unavailable. GPS may be disabled on the device.")
            }
        } catch (e: SecurityException) {
            reply(context, parentPhone, "Location permission not granted on child's device.")
        }
    }

    /**
     * SCREENSHOT — Capture current screen.
     */
    private fun cmdScreenshot(context: Context, parentPhone: String) {
        if (ScreenCaptureService.hasProjection()) {
            ScreenCaptureService.capture(context, -1)
            reply(context, parentPhone, "Screenshot captured and archived in evidence folder.")
        } else {
            reply(context, parentPhone, "Screen capture not initialized. The child needs to approve screen capture permission once in the app.")
        }
    }

    /**
     * STATUS — Report current monitoring state.
     */
    private suspend fun cmdStatus(context: Context, parentPhone: String) {
        val prefs = AppPreferences(context)
        val monitoring = prefs.monitoringEnabled.first()
        val lockdown = prefs.lockdownActive.first()
        val dao = ParentalApp.instance.database.incidentDao()
        val unreviewedCount = dao.getUnreviewedCount().first()
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val last24h = dao.getCountSince(oneDayAgo).first()

        reply(context, parentPhone, buildString {
            append("ParentalORMental Status:\n\n")
            append("Monitoring: ${if (monitoring) "ACTIVE" else "DISABLED"}\n")
            append("Lockdown: ${if (lockdown) "ACTIVE" else "Off"}\n")
            append("Unreviewed: $unreviewedCount incident(s)\n")
            append("Last 24h: $last24h incident(s)\n")
        })
    }

    /**
     * EXPORT — Generate full evidence report.
     */
    private suspend fun cmdExport(context: Context, parentPhone: String) {
        val collector = EvidenceCollector(context)
        val dao = ParentalApp.instance.database.incidentDao()
        val report = collector.exportFullReport(dao)
        reply(context, parentPhone, "Evidence report generated: ${report.name}\n\nConnect device to computer to retrieve from app storage, or open the app to share.")
    }

    /**
     * CRISIS — Show the crisis overlay on the child's device.
     */
    private fun cmdCrisis(context: Context, parentPhone: String) {
        CrisisOverlayService.show(context)
        reply(context, parentPhone, "Crisis support screen activated on child's device.")
        Log.i(TAG, "Crisis overlay shown by parent command")
    }

    /**
     * CRISIS:OFF — Dismiss the crisis overlay remotely.
     */
    private fun cmdCrisisOff(context: Context, parentPhone: String) {
        CrisisOverlayService.dismiss(context)
        reply(context, parentPhone, "Crisis support screen dismissed.")
        Log.i(TAG, "Crisis overlay dismissed by parent command")
    }

    private fun reply(context: Context, phone: String, message: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage("[POM] $message")
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reply to parent", e)
        }
    }
}
