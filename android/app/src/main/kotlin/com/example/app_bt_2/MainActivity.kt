package com.example.app_bt_2

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "battery_ble"
    private val requestCodePermissions = 7001
    private val requestCodeEnableBluetooth = 7002
    private var pendingPermissionResult: MethodChannel.Result? = null
    private var pendingBluetoothResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startService" -> {
                        val args = call.arguments as? Map<*, *> ?: emptyMap<Any, Any>()
                        val tabletId = (args["tabletId"] as? Number)?.toInt() ?: 1
                        val intervalSec = (args["intervalSec"] as? Number)?.toInt() ?: 2

                        val intent = Intent(this, BatteryBleService::class.java).apply {
                            putExtra(BatteryBleService.EXTRA_TABLET_ID, tabletId)
                            putExtra(BatteryBleService.EXTRA_INTERVAL_SEC, intervalSec)
                        }
                        ContextCompat.startForegroundService(this, intent)
                        Prefs.saveSettings(this, tabletId, intervalSec)
                        result.success(true)
                    }
                    "stopService" -> {
                        stopService(Intent(this, BatteryBleService::class.java))
                        result.success(true)
                    }
                    "getBatterySnapshot" -> {
                        val data = BatteryReader.read(this)
                        val map = mapOf(
                            "percent" to data.percent,
                            "status" to data.statusLabel(),
                            "charging" to data.charging,
                            "full" to data.full,
                            "plugged" to data.plugged,
                            "temperatureC_x10" to data.temperatureC_x10,
                            "voltage_mv" to data.voltageMv,
                            "timestamp_ms" to data.timestampMs,
                        )
                        result.success(map)
                    }
                    "setAutoStart" -> {
                        val args = call.arguments as? Map<*, *> ?: emptyMap<Any, Any>()
                        val enabled = (args["enabled"] as? Boolean) ?: false
                        Prefs.setAutoStart(this, enabled)
                        result.success(true)
                    }
                    "getAutoStart" -> {
                        result.success(Prefs.getAutoStart(this))
                    }
                    "getSettings" -> {
                        val map = mapOf(
                            "tabletId" to Prefs.getTabletId(this),
                            "intervalSec" to Prefs.getIntervalSec(this),
                        )
                        result.success(map)
                    }
                    "saveSettings" -> {
                        val args = call.arguments as? Map<*, *> ?: emptyMap<Any, Any>()
                        val tabletId = (args["tabletId"] as? Number)?.toInt() ?: Prefs.getTabletId(this)
                        val intervalSec = (args["intervalSec"] as? Number)?.toInt() ?: Prefs.getIntervalSec(this)
                        Prefs.saveSettings(this, tabletId, intervalSec)
                        result.success(true)
                    }
                    "getServiceStatus" -> {
                        result.success(Prefs.isServiceRunning(this))
                    }
                    "getBleStatus" -> {
                        val adapter = BluetoothAdapter.getDefaultAdapter()
                        val btEnabled = try {
                            adapter?.isEnabled == true
                        } catch (_: SecurityException) {
                            false
                        }
                        val status = Prefs.getBleStatus(this).toMutableMap()
                        status["btEnabled"] = btEnabled
                        status["serviceRunning"] = Prefs.isServiceRunning(this)
                        result.success(status)
                    }
                    "getAppVersion" -> {
                        result.success(getAppVersionLabel())
                    }
                    "requestPermissions" -> {
                        requestRuntimePermissions(result)
                    }
                    "ensureBluetoothEnabled" -> {
                        ensureBluetoothEnabled(result)
                    }
                    "openBatteryOptimizationSettings" -> {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(intent)
                        result.success(true)
                    }
                    "isBatteryOptimizationIgnored" -> {
                        val pm = getSystemService(PowerManager::class.java)
                        val ignored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            pm.isIgnoringBatteryOptimizations(packageName)
                        } else {
                            true
                        }
                        result.success(ignored)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun requestRuntimePermissions(result: MethodChannel.Result) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            result.success(true)
            return
        }

        if (pendingPermissionResult != null) {
            result.error("PERMISSION_PENDING", "Permission request already pending", null)
            return
        }

        pendingPermissionResult = result
        ActivityCompat.requestPermissions(this, missing.toTypedArray(), requestCodePermissions)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != requestCodePermissions) return

        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        pendingPermissionResult?.success(granted)
        pendingPermissionResult = null
    }

    @Deprecated("Deprecated in Android API. Required for enable Bluetooth result.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != requestCodeEnableBluetooth) return
        val enabled = resultCode == Activity.RESULT_OK
        pendingBluetoothResult?.success(enabled)
        pendingBluetoothResult = null
    }

    private fun ensureBluetoothEnabled(result: MethodChannel.Result) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            result.success(false)
            return
        }
        val isEnabled = try {
            adapter.isEnabled
        } catch (_: SecurityException) {
            false
        }
        if (isEnabled) {
            result.success(true)
            return
        }
        if (pendingBluetoothResult != null) {
            result.error("BT_REQUEST_PENDING", "Bluetooth enable request already pending", null)
            return
        }
        try {
            pendingBluetoothResult = result
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, requestCodeEnableBluetooth)
        } catch (e: Exception) {
            pendingBluetoothResult = null
            result.error("BT_REQUEST_FAILED", e.message, null)
        }
    }

    private fun getAppVersionLabel(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            val versionName = packageInfo.versionName ?: "0.0.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            "$versionName ($versionCode)"
        } catch (_: Exception) {
            "0.0.0"
        }
    }
}
