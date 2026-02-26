package com.ht.batterytx

import android.content.Context
import android.os.Build

object Prefs {
    const val PREFS_NAME = "battery_ble_prefs"
    const val KEY_AUTO_START = "auto_start"
    const val KEY_TABLET_ID = "tablet_id"
    const val KEY_INTERVAL_SEC = "interval_sec"
    const val KEY_SERVICE_RUNNING = "service_running"
    const val KEY_BLE_STATUS = "ble_status"
    const val KEY_BLE_ERROR = "ble_error"
    const val KEY_BLE_TS = "ble_ts"

    private fun prefs(context: Context) =
        getDeviceProtectedContext(context)
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getDeviceProtectedContext(context: Context): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return context
        val deviceContext = context.createDeviceProtectedStorageContext()
        try {
            deviceContext.moveSharedPreferencesFrom(context, PREFS_NAME)
        } catch (_: Exception) {
            // Best effort migration; ignore failures to avoid crash at boot.
        }
        return deviceContext
    }

    fun setAutoStart(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_AUTO_START, enabled)
            .apply()
    }

    fun getAutoStart(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_START, false)
    }

    fun saveSettings(context: Context, tabletId: Int, intervalSec: Int) {
        prefs(context).edit()
            .putInt(KEY_TABLET_ID, tabletId)
            .putInt(KEY_INTERVAL_SEC, intervalSec)
            .apply()
    }

    fun getTabletId(context: Context): Int {
        return prefs(context).getInt(KEY_TABLET_ID, 1)
    }

    fun getIntervalSec(context: Context): Int {
        return prefs(context).getInt(KEY_INTERVAL_SEC, 5)
    }

    fun setServiceRunning(context: Context, running: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_SERVICE_RUNNING, running)
            .apply()
    }

    fun isServiceRunning(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SERVICE_RUNNING, false)
    }


    fun setBleStatus(context: Context, status: String, error: String? = null) {
        prefs(context).edit()
            .putString(KEY_BLE_STATUS, status)
            .putString(KEY_BLE_ERROR, error ?: "")
            .putLong(KEY_BLE_TS, System.currentTimeMillis())
            .apply()
    }

    fun getBleStatus(context: Context): Map<String, Any> {
        val sp = prefs(context)
        val status = sp.getString(KEY_BLE_STATUS, "unknown") ?: "unknown"
        val error = sp.getString(KEY_BLE_ERROR, "") ?: ""
        val ts = sp.getLong(KEY_BLE_TS, 0L)
        return mapOf(
            "status" to status,
            "error" to error,
            "timestamp_ms" to ts,
        )
    }
}
