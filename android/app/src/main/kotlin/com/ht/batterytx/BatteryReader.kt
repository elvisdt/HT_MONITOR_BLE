package com.ht.batterytx

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object BatteryReader {
    fun read(context: Context): BatteryData {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) {
            return BatteryData(
                percent = 0,
                status = BatteryManager.BATTERY_STATUS_UNKNOWN,
                charging = false,
                full = false,
                plugged = false,
                temperatureC_x10 = 0,
                voltageMv = 0,
                timestampMs = System.currentTimeMillis(),
            )
        }

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val full = status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val temperatureC_x10 = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

        return BatteryData(
            percent = percent,
            status = status,
            charging = charging,
            full = full,
            plugged = plugged,
            temperatureC_x10 = temperatureC_x10,
            voltageMv = voltageMv,
            timestampMs = System.currentTimeMillis(),
        )
    }
}
