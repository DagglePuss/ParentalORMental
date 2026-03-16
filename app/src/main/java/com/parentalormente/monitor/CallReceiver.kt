package com.parentalormente.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import android.util.Log
import com.parentalormente.ParentalApp
import com.parentalormente.alerts.AlertManager
import com.parentalormente.alerts.RealTimeRelay
import com.parentalormente.data.db.IncidentEntity
import com.parentalormente.detection.BullyingDetector
import com.parentalormente.evidence.EvidenceCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Monitors incoming call events for harassment patterns.
 *
 * Detects:
 *   - Rapid repeated calls from the same number (flood calls)
 *   - Very short calls (< 8 seconds) repeated from same number (hang-up harassment)
 *
 * State (call start time, caller number) is persisted in SharedPreferences
 * because BroadcastReceivers are stateless across invocations.
 */
class CallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallReceiver"
        private const val PREFS_NAME = "call_tracker"
        private const val KEY_OFFHOOK_TIME = "offhook_time"
        private const val KEY_RINGING_NUMBER = "ringing_number"

        // A call shorter than this is treated as a potential hang-up harassment event
        private const val SHORT_CALL_THRESHOLD_MS = 8_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Save caller number so we have it when IDLE fires (number may not be in IDLE)
                prefs.edit().putString(KEY_RINGING_NUMBER, number).apply()
                Log.d(TAG, "Incoming call from $number")
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call was answered — record start time
                prefs.edit().putLong(KEY_OFFHOOK_TIME, System.currentTimeMillis()).apply()
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                val offhookTime = prefs.getLong(KEY_OFFHOOK_TIME, 0L)
                val caller = prefs.getString(KEY_RINGING_NUMBER, "") ?: ""

                prefs.edit()
                    .remove(KEY_OFFHOOK_TIME)
                    .remove(KEY_RINGING_NUMBER)
                    .apply()

                if (caller.isBlank()) return

                val callDurationMs = if (offhookTime > 0) System.currentTimeMillis() - offhookTime else 0L
                val wasAnswered = offhookTime > 0

                Log.d(TAG, "Call ended — caller=$caller answered=$wasAnswered duration=${callDurationMs}ms")

                // Analyse the call log to see if this number has a suspicious pattern
                CoroutineScope(Dispatchers.IO).launch {
                    analyzeAndFlag(context, caller, callDurationMs, wasAnswered, prefs)
                }
            }
        }
    }

    private suspend fun analyzeAndFlag(
        context: Context,
        caller: String,
        lastCallDurationMs: Long,
        wasAnswered: Boolean,
        prefs: SharedPreferences
    ) {
        val result = CallLogAnalyzer.analyze(context, caller, lastCallDurationMs, wasAnswered)
            ?: return  // No suspicious pattern

        Log.w(TAG, "Suspicious call pattern from $caller — ${result.severity}: ${result.summary}")

        val db = ParentalApp.instance.database.incidentDao()
        val alertManager = AlertManager(context)
        val relay = RealTimeRelay(context)
        val evidence = EvidenceCollector(context)

        val incident = IncidentEntity(
            sourceType = "CALL",
            sender = caller,
            content = result.detail,
            severity = result.severity.level,
            matchedPatterns = result.summary,
            summary = result.summary
        )

        val id = db.insert(incident)
        val saved = incident.copy(id = id)
        evidence.archiveIncident(saved)
        alertManager.handleIncident(saved, result.severity)
        relay.relayIncident(saved, result.severity)
    }
}
