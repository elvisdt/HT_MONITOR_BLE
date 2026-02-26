package com.ht.batterytx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootDelayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.getAutoStart(context)) {
            return
        }

        val tabletId = intent.getIntExtra(BatteryBleService.EXTRA_TABLET_ID, Prefs.getTabletId(context))
        val intervalSec = intent.getIntExtra(BatteryBleService.EXTRA_INTERVAL_SEC, Prefs.getIntervalSec(context))

        val serviceIntent = Intent(context, BatteryBleService::class.java).apply {
            putExtra(BatteryBleService.EXTRA_TABLET_ID, tabletId)
            putExtra(BatteryBleService.EXTRA_INTERVAL_SEC, intervalSec)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
