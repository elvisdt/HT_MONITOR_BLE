package com.example.app_bt_2

data class BatteryData(
    val percent: Int,
    val status: Int,
    val charging: Boolean,
    val full: Boolean,
    val plugged: Boolean,
    val temperatureC_x10: Int,
    val voltageMv: Int,
    val timestampMs: Long,
) {
    fun statusLabel(): String {
        return when (status) {
            android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            android.os.BatteryManager.BATTERY_STATUS_FULL -> "full"
            android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> "unknown"
        }
    }
}
