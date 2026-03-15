package com.parentalormente.monitor

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.parentalormente.ParentalApp
import com.parentalormente.R

/**
 * Foreground service that keeps monitoring alive.
 * Shows a persistent notification so the OS doesn't kill us.
 */
class MonitorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, ParentalApp.CHANNEL_MONITOR)
            .setContentTitle("ParentalORMental")
            .setContentText("Protection active")
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            context.stopService(intent)
        }
    }
}
