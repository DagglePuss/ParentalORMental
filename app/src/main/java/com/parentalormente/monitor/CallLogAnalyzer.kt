package com.parentalormente.monitor

import android.content.Context
import android.provider.CallLog
import android.util.Log
import com.parentalormente.detection.BullyingDetector

/**
 * Analyses the call log for a specific caller to detect harassment patterns.
 *
 * Patterns detected:
 *   - Flood calls: 5+ calls from the same number within 1 hour
 *   - Hang-up harassment: 3+ very short calls (< 8 s) from same number within 24 h
 *   - Combined: repeated calls where most were hung up quickly
 *
 * Requires READ_CALL_LOG permission (already requested in MainActivity).
 */
object CallLogAnalyzer {

    private const val TAG = "CallLogAnalyzer"

    // Windows for pattern analysis
    private const val FLOOD_WINDOW_MS = 60 * 60 * 1000L       // 1 hour
    private const val HANGUP_WINDOW_MS = 24 * 60 * 60 * 1000L // 24 hours
    private const val SHORT_CALL_MS = 8_000L                   // < 8 s = hang-up

    // Thresholds
    private const val FLOOD_CALL_CRITICAL = 8   // 8+ calls in 1 h → CRITICAL
    private const val FLOOD_CALL_HIGH = 5       // 5+ calls in 1 h → HIGH
    private const val HANGUP_HIGH = 5           // 5+ hang-ups in 24 h → HIGH
    private const val HANGUP_MEDIUM = 3         // 3+ hang-ups in 24 h → MEDIUM

    data class CallPatternResult(
        val severity: BullyingDetector.Severity,
        val summary: String,
        val detail: String
    )

    /**
     * Returns a [CallPatternResult] if a suspicious pattern is detected, or null if clean.
     */
    fun analyze(
        context: Context,
        callerNumber: String,
        lastCallDurationMs: Long,
        lastCallAnswered: Boolean
    ): CallPatternResult? {
        if (callerNumber.isBlank()) return null

        val now = System.currentTimeMillis()

        // --- Query call log ---
        val projection = arrayOf(
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )
        val selection = "${CallLog.Calls.NUMBER} = ? AND ${CallLog.Calls.DATE} > ?"
        val selectionArgs = arrayOf(callerNumber, (now - HANGUP_WINDOW_MS).toString())

        val calls = mutableListOf<Pair<Long, Long>>() // (timestamp, durationSeconds)
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                while (cursor.moveToNext()) {
                    val type = cursor.getInt(typeIdx)
                    // Only count incoming calls (and missed = 3)
                    if (type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.MISSED_TYPE) {
                        calls.add(
                            cursor.getLong(dateIdx) to cursor.getLong(durIdx)
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "READ_CALL_LOG permission not granted", e)
            return null
        }

        if (calls.isEmpty()) return null

        // Also account for the current call that may not be in the log yet
        if (lastCallAnswered) {
            calls.add(0, now to (lastCallDurationMs / 1000))
        } else {
            calls.add(0, now to 0L) // missed / unanswered
        }

        // --- Pattern: flood calls (many calls in 1 hour) ---
        val callsInHour = calls.count { (ts, _) -> now - ts <= FLOOD_WINDOW_MS }

        if (callsInHour >= FLOOD_CALL_CRITICAL) {
            return CallPatternResult(
                severity = BullyingDetector.Severity.CRITICAL,
                summary = "Extreme call flooding: $callsInHour calls from $callerNumber in the last hour",
                detail = buildDetail(callerNumber, calls, callsInHour, 0)
            )
        }
        if (callsInHour >= FLOOD_CALL_HIGH) {
            return CallPatternResult(
                severity = BullyingDetector.Severity.HIGH,
                summary = "$callsInHour calls from $callerNumber in the last hour — possible harassment",
                detail = buildDetail(callerNumber, calls, callsInHour, 0)
            )
        }

        // --- Pattern: hang-up harassment (repeated short calls in 24 h) ---
        val shortCalls = calls.count { (_, dur) -> dur * 1000 < SHORT_CALL_MS }

        if (shortCalls >= HANGUP_HIGH) {
            return CallPatternResult(
                severity = BullyingDetector.Severity.HIGH,
                summary = "$shortCalls hang-up calls from $callerNumber in 24 hours",
                detail = buildDetail(callerNumber, calls, callsInHour, shortCalls)
            )
        }
        if (shortCalls >= HANGUP_MEDIUM) {
            return CallPatternResult(
                severity = BullyingDetector.Severity.MEDIUM,
                summary = "$shortCalls short/hang-up calls from $callerNumber in 24 hours",
                detail = buildDetail(callerNumber, calls, callsInHour, shortCalls)
            )
        }

        return null
    }

    private fun buildDetail(
        number: String,
        calls: List<Pair<Long, Long>>,
        callsInHour: Int,
        shortCalls: Int
    ): String = buildString {
        appendLine("Call harassment pattern detected from $number")
        appendLine("Total calls in 24h: ${calls.size}")
        appendLine("Calls in last hour: $callsInHour")
        if (shortCalls > 0) appendLine("Hang-up calls (< 8s): $shortCalls")
        appendLine("Durations (s): " + calls.take(10).joinToString { it.second.toString() })
    }
}
