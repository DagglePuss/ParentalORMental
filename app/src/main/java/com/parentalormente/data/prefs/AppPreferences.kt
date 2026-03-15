package com.parentalormente.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppPreferences(private val context: Context) {

    companion object {
        val PARENT_PHONE = stringPreferencesKey("parent_phone")
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val STEALTH_MODE = booleanPreferencesKey("stealth_mode")
        val SMS_ALERTS = booleanPreferencesKey("sms_alerts")
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val ALERT_MIN_SEVERITY = stringPreferencesKey("alert_min_severity") // "LOW","MEDIUM","HIGH","CRITICAL"
        val LOCKDOWN_ACTIVE = booleanPreferencesKey("lockdown_active")
        val EVIDENCE_AUTO_CAPTURE = booleanPreferencesKey("evidence_auto_capture")
    }

    val parentPhone: Flow<String> = context.dataStore.data.map { it[PARENT_PHONE] ?: "" }
    val monitoringEnabled: Flow<Boolean> = context.dataStore.data.map { it[MONITORING_ENABLED] ?: true }
    val stealthMode: Flow<Boolean> = context.dataStore.data.map { it[STEALTH_MODE] ?: false }
    val smsAlerts: Flow<Boolean> = context.dataStore.data.map { it[SMS_ALERTS] ?: true }
    val setupComplete: Flow<Boolean?> = context.dataStore.data.map { it[SETUP_COMPLETE] }
    val alertMinSeverity: Flow<String> = context.dataStore.data.map { it[ALERT_MIN_SEVERITY] ?: "MEDIUM" }
    val lockdownActive: Flow<Boolean> = context.dataStore.data.map { it[LOCKDOWN_ACTIVE] ?: false }
    val evidenceAutoCapture: Flow<Boolean> = context.dataStore.data.map { it[EVIDENCE_AUTO_CAPTURE] ?: true }

    suspend fun setParentPhone(phone: String) {
        context.dataStore.edit { it[PARENT_PHONE] = phone }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { it[MONITORING_ENABLED] = enabled }
    }

    suspend fun setStealthMode(enabled: Boolean) {
        context.dataStore.edit { it[STEALTH_MODE] = enabled }
    }

    suspend fun setSmsAlerts(enabled: Boolean) {
        context.dataStore.edit { it[SMS_ALERTS] = enabled }
    }

    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { it[SETUP_COMPLETE] = complete }
    }

    suspend fun setAlertMinSeverity(severity: String) {
        context.dataStore.edit { it[ALERT_MIN_SEVERITY] = severity }
    }

    suspend fun setLockdownActive(active: Boolean) {
        context.dataStore.edit { it[LOCKDOWN_ACTIVE] = active }
    }

    suspend fun setEvidenceAutoCapture(enabled: Boolean) {
        context.dataStore.edit { it[EVIDENCE_AUTO_CAPTURE] = enabled }
    }
}
