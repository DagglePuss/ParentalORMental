package com.parentalormente.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Re-initializes monitoring after device reboot.
 * Ensures protection doesn't lapse just because the phone restarted.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Device rebooted — restarting monitor service")
            val serviceIntent = Intent(context, MonitorService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
