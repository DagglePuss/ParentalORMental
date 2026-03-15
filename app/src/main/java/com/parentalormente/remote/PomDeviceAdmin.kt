package com.parentalormente.remote

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Device Admin receiver for ParentalORMental.
 *
 * Enables:
 * - Remote device lock (POM:LOCK)
 * - Lockdown mode (POM:LOCKDOWN)
 *
 * Must be activated by the parent during initial setup.
 * Android requires explicit user consent to grant Device Admin.
 */
class PomDeviceAdmin : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "PomDeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device Admin enabled — remote lock available")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w(TAG, "Device Admin disabled — remote lock unavailable")
    }
}
