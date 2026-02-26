package com.ht.batterytx

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
            android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "cargando"
            android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "descargando"
            android.os.BatteryManager.BATTERY_STATUS_FULL -> "completo"
            android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "no cargando"
            else -> "desconocido"
        }
    }
}
