package com.ht.batterytx

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val BOOT_DELAY_MS = 10_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_USER_UNLOCKED
        ) {
            return
        }
        if (!Prefs.getAutoStart(context)) {
            return
        }

        val tabletId = Prefs.getTabletId(context)
        val intervalSec = Prefs.getIntervalSec(context)

        // Start immediately (best effort) and also schedule a delayed start as fallback.
        val serviceIntent = Intent(context, BatteryBleService::class.java).apply {
            putExtra(BatteryBleService.EXTRA_TABLET_ID, tabletId)
            putExtra(BatteryBleService.EXTRA_INTERVAL_SEC, intervalSec)
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (_: Exception) {
            // Ignore; fallback alarm below.
        }

        val alarmIntent = Intent(context, BootDelayReceiver::class.java).apply {
            putExtra(BatteryBleService.EXTRA_TABLET_ID, tabletId)
            putExtra(BatteryBleService.EXTRA_INTERVAL_SEC, intervalSec)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + BOOT_DELAY_MS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pending,
                )
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
            }
        } catch (_: SecurityException) {
            val serviceIntent = Intent(context, BatteryBleService::class.java).apply {
                putExtra(BatteryBleService.EXTRA_TABLET_ID, tabletId)
                putExtra(BatteryBleService.EXTRA_INTERVAL_SEC, intervalSec)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
