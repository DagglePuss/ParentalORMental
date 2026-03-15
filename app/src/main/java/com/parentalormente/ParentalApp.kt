package com.parentalormente

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.parentalormente.data.db.IncidentDatabase

class ParentalApp : Application() {

    lateinit var database: IncidentDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = IncidentDatabase.getInstance(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Alert channel — parent-facing, high priority
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Bullying Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when potential bullying is detected"
            }

            // Monitor channel — low priority, foreground service
            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR,
                "Monitoring Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows that protection is active"
            }

            manager.createNotificationChannel(alertChannel)
            manager.createNotificationChannel(monitorChannel)
        }
    }

    companion object {
        const val CHANNEL_ALERTS = "bullying_alerts"
        const val CHANNEL_MONITOR = "monitor_status"

        lateinit var instance: ParentalApp
            private set
    }
}
