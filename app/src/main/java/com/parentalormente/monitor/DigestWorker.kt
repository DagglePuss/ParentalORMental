package com.parentalormente.monitor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.parentalormente.ParentalApp
import com.parentalormente.alerts.RealTimeRelay
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that sends a daily incident digest to the parent.
 * Runs every 24 hours via WorkManager — survives app kills and reboots.
 */
class DigestWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = ParentalApp.instance.database.incidentDao()
        val relay = RealTimeRelay(applicationContext)
        relay.sendDailyDigest(dao)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily_digest"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DigestWorker>(
                24, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
