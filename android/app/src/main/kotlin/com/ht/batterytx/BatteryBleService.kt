package com.ht.batterytx

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class BatteryBleService : Service() {
    companion object {
        const val EXTRA_TABLET_ID = "tabletId"
        const val EXTRA_INTERVAL_SEC = "intervalSec"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "battery_ble_channel"
        const val BLE_NAME = "HT-MT"
    }

    private var tabletId: Int = 1
    private var intervalMs: Long = 2000
    private var seq: Int = 0
    private var running = false
    private var lastPayload: ByteArray? = null
    private var bleStatus: String = "unknown"
    private var lastData: BatteryData? = null

    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private var advertiser: BluetoothLeAdvertiser? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            setBleStatus("advertising", "")
            lastData?.let { updateNotification(it) }
        }

        override fun onStartFailure(errorCode: Int) {
            val message = "Advertise error $errorCode"
            setBleStatus("error", message)
            updateNotificationError(message)
        }
    }

    private val advertiseRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            advertiseOnce()
            handler.postDelayed(this, intervalMs)
        }
    }

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (!running) return
            when (state) {
                BluetoothAdapter.STATE_ON -> handler.post {
                    applyBluetoothName(BLE_NAME)
                    advertiseOnce()
                }
                BluetoothAdapter.STATE_OFF -> {
                    stopAdvertising()
                    val message = "Bluetooth desactivado"
                    setBleStatus("bt_off", message)
                    updateNotificationError(message)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        handlerThread = HandlerThread("BatteryBleService")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        advertiser = BluetoothAdapter.getDefaultAdapter()?.bluetoothLeAdvertiser
        createNotificationChannel()
        registerReceiver(btStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val providedTabletId = intent?.getIntExtra(EXTRA_TABLET_ID, -1) ?: -1
        val providedIntervalSec = intent?.getIntExtra(EXTRA_INTERVAL_SEC, -1) ?: -1

        tabletId = if (providedTabletId >= 0) providedTabletId else Prefs.getTabletId(this)
        val intervalSec = if (providedIntervalSec > 0) providedIntervalSec else Prefs.getIntervalSec(this)
        intervalMs = (intervalSec.coerceIn(1, 60) * 1000).toLong()

        Prefs.saveSettings(this, tabletId, (intervalMs / 1000).toInt())
        applyBluetoothName(BLE_NAME)

        startForeground(NOTIFICATION_ID, buildNotification("Monitoreo de bateria activo"))

        running = true
        Prefs.setServiceRunning(this, true)
        setBleStatus("starting", "")
        acquireWakeLock()
        handler.removeCallbacksAndMessages(null)
        handler.post(advertiseRunnable)

        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        Prefs.setServiceRunning(this, false)
        setBleStatus("stopped", "")
        handler.removeCallbacksAndMessages(null)
        stopAdvertising()
        releaseWakeLock()
        handlerThread.quitSafely()
        unregisterReceiver(btStateReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAdvertising(payload: ByteArray) {
        if (!hasBlePermissions()) {
            val message = "Permiso BLE pendiente"
            setBleStatus("no_permission", message)
            updateNotificationError(message)
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            val message = "Bluetooth desactivado"
            setBleStatus("bt_off", message)
            updateNotificationError(message)
            return
        }
        advertiser = adapter.bluetoothLeAdvertiser
        val adv = advertiser
        if (adv == null) {
            val message = "BLE advertising no soportado"
            setBleStatus("no_advertiser", message)
            updateNotificationError(message)
            return
        }

        setBleStatus("starting", "")
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(PayloadBuilder.MANUFACTURER_ID, payload)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        adv.stopAdvertising(advertiseCallback)
        adv.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
    }

    private fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
    }

    private fun advertiseOnce() {
        val data = BatteryReader.read(this)
        lastData = data
        val payload = PayloadBuilder.build(tabletId, data, seq)
        lastPayload = payload
        seq = (seq + 1) and 0xFF
        startAdvertising(payload)
        updateNotification(data)
    }

    private fun applyBluetoothName(name: String) {
        val safeName = name.trim()
        if (safeName.isEmpty()) return
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        try {
            if (adapter.name != safeName) {
                adapter.name = safeName
            }
        } catch (_: SecurityException) {
            // Ignore if missing BLUETOOTH_CONNECT.
        } catch (_: Exception) {
            // Ignore failures from OEM stacks.
        }
    }

    private fun buildNotification(content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPending = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val bluetoothIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        val bluetoothPending = PendingIntent.getActivity(
            this,
            1,
            bluetoothIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Monitoreo de bateria activo")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPending)
            .addAction(0, "Bluetooth", bluetoothPending)
            .build()
    }

    private fun updateNotification(data: BatteryData) {
        val charge = when {
            data.full -> "Cargador: FULL"
            data.charging || data.plugged -> "Cargador: ON"
            else -> "Cargador: OFF"
        }
        val tx = "TX: ${if (bleStatus == "advertising") "ON" else "OFF"}"
        val content = "${data.percent}% | ${data.temperatureC_x10 / 10.0} C | ${data.voltageMv} mV | $charge | $tx"
        val notification = buildNotification(content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationError(message: String) {
        val notification = buildNotification("TX: OFF | $message")
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun setBleStatus(status: String, error: String? = null) {
        bleStatus = status
        Prefs.setBleStatus(this, status, error)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HTBatteryTX:BleWakeLock",
        ).apply {
            setReferenceCounted(false)
            try {
                acquire()
            } catch (_: Exception) {
                // Ignore if system blocks wakelock.
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
            // Ignore.
        } finally {
            wakeLock = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Battery BLE",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun hasBlePermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val advertiseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        ) == PackageManager.PERMISSION_GRANTED
        val connectGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
        return advertiseGranted && connectGranted
    }
}
