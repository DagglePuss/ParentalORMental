package com.parentalormente.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.parentalormente.data.prefs.AppPreferences
import com.parentalormente.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Listens for the secret dialer code *#*#7676#*#* and relaunches the app
 * when stealth mode is active (i.e. the launcher icon is hidden).
 *
 * 7676 = P-O-M-S on a phone keypad — easy for a parent to remember.
 */
class StealthDialerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val stealthActive = runBlocking { AppPreferences(context).stealthMode.first() }
        if (!stealthActive) return

        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }
}
