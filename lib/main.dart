import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const _channel = MethodChannel('battery_ble');

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const BatteryBleApp());
}

class BatteryBleApp extends StatelessWidget {
  const BatteryBleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'HT Monitor',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blueGrey),
        useMaterial3: true,
      ),
      home: const BatteryBlePage(),
    );
  }
}

class BatterySnapshot {
  final int percent;
  final String status;
  final bool charging;
  final bool full;
  final bool plugged;
  final int temperatureC_x10;
  final int voltageMv;
  final int timestampMs;

  BatterySnapshot({
    required this.percent,
    required this.status,
    required this.charging,
    required this.full,
    required this.plugged,
    required this.temperatureC_x10,
    required this.voltageMv,
    required this.timestampMs,
  });

  double get temperatureC => temperatureC_x10 / 10.0;

  DateTime get timestamp => DateTime.fromMillisecondsSinceEpoch(timestampMs);

  static BatterySnapshot fromMap(Map<dynamic, dynamic> map) {
    return BatterySnapshot(
      percent: map['percent'] ?? 0,
      status: map['status'] ?? 'unknown',
      charging: map['charging'] ?? false,
      full: map['full'] ?? false,
      plugged: map['plugged'] ?? false,
      temperatureC_x10: map['temperatureC_x10'] ?? 0,
      voltageMv: map['voltage_mv'] ?? 0,
      timestampMs: map['timestamp_ms'] ?? DateTime.now().millisecondsSinceEpoch,
    );
  }
}

class BatteryBlePage extends StatefulWidget {
  const BatteryBlePage({super.key});

  @override
  State<BatteryBlePage> createState() => _BatteryBlePageState();
}

class _BatteryBlePageState extends State<BatteryBlePage> {
  final TextEditingController _tabletIdController =
      TextEditingController(text: '1');
  final TextEditingController _intervalController =
      TextEditingController(text: '5');

  BatterySnapshot? _snapshot;
  Timer? _pollTimer;
  bool _isTransmitting = false;
  bool _autoStart = false;
  bool _batteryOptimizationIgnored = false;
  bool _autoStartAttempted = false;
  String _appVersion = '-';
  String _bleStatus = 'unknown';
  String _bleError = '';
  bool _btEnabled = false;

  @override
  void initState() {
    super.initState();
    _loadInitialState();
    _pollTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      _refreshBatterySnapshot();
      _refreshBleStatus();
    });
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    _tabletIdController.dispose();
    _intervalController.dispose();
    super.dispose();
  }

  Future<void> _loadInitialState() async {
    await _refreshBatterySnapshot();
    await _loadSettings();
    await _loadAutoStart();
    await _checkBatteryOptimization();
    await _loadAppVersion();
    await _refreshBleStatus();
    await _autoStartIfNeeded();
  }

  Future<void> _loadSettings() async {
    try {
      final Map<dynamic, dynamic>? settings =
          await _channel.invokeMethod('getSettings');
      if (settings != null) {
        final int? tabletId = settings['tabletId'] as int?;
        final int? intervalSec = settings['intervalSec'] as int?;
        if (tabletId != null) {
          _tabletIdController.text = tabletId.toString();
        }
        if (intervalSec != null) {
          _intervalController.text = intervalSec.toString();
        }
      }
    } catch (_) {}
  }

  Future<void> _loadAutoStart() async {
    try {
      final bool? enabled =
          await _channel.invokeMethod<bool>('getAutoStart');
      if (enabled != null) {
        setState(() {
          _autoStart = enabled;
        });
      }
    } catch (_) {}
  }

  Future<void> _checkBatteryOptimization() async {
    try {
      final bool? ignored = await _channel
          .invokeMethod<bool>('isBatteryOptimizationIgnored');
      if (ignored != null) {
        setState(() {
          _batteryOptimizationIgnored = ignored;
        });
      }
    } catch (_) {}
  }

  Future<void> _loadAppVersion() async {
    try {
      final String? version =
          await _channel.invokeMethod<String>('getAppVersion');
      if (version != null && version.isNotEmpty) {
        setState(() {
          _appVersion = version;
        });
      }
    } catch (_) {}
  }

  Future<void> _refreshBatterySnapshot() async {
    try {
      final Map<dynamic, dynamic>? data =
          await _channel.invokeMethod('getBatterySnapshot');
      if (data != null) {
        setState(() {
          _snapshot = BatterySnapshot.fromMap(data);
        });
      }
    } catch (_) {}
  }

  Future<void> _refreshBleStatus() async {
    try {
      final Map<dynamic, dynamic>? data =
          await _channel.invokeMethod('getBleStatus');
      if (data != null && mounted) {
        setState(() {
          _isTransmitting = data['serviceRunning'] == true;
          _btEnabled = data['btEnabled'] == true;
          _bleStatus = (data['status'] ?? 'unknown').toString();
          _bleError = (data['error'] ?? '').toString();
        });
      }
    } catch (_) {}
  }

  Future<void> _autoStartIfNeeded() async {
    if (_autoStartAttempted) return;
    _autoStartAttempted = true;
    if (_isTransmitting) return;

    final bool permissionsOk = await _requestPermissions();
    if (!permissionsOk) {
      _showMessage('Permisos BLE o notificaciones no otorgados.');
      return;
    }
    final bool btOk = await _ensureBluetoothEnabled();
    if (!btOk) {
      _showMessage('Bluetooth desactivado. Activalo para transmitir.');
      return;
    }

    await _startTransmissionInternal(showErrors: false);
  }

  Map<String, dynamic>? _readSettings({bool showErrors = true}) {
    final int? tabletId = int.tryParse(_tabletIdController.text.trim());
    final int? intervalSec = int.tryParse(_intervalController.text.trim());
    if (tabletId == null || tabletId < 0 || tabletId > 65535) {
      if (showErrors) {
        _showMessage('ID de Tablet invalido (0-65535).');
      }
      return null;
    }
    if (intervalSec == null || intervalSec < 1 || intervalSec > 60) {
      if (showErrors) {
        _showMessage('Intervalo invalido (1-60 segundos).');
      }
      return null;
    }
    return {
      'tabletId': tabletId,
      'intervalSec': intervalSec,
    };
  }

  Future<void> _startTransmissionInternal({bool showErrors = true}) async {
    final Map<String, dynamic>? settings = _readSettings(showErrors: showErrors);
    if (settings == null) return;

    try {
      await _channel.invokeMethod('startService', settings);
      if (mounted) {
        setState(() {
          _isTransmitting = true;
        });
      }
    } catch (e) {
      if (showErrors) {
        _showMessage('Error iniciando servicio: $e');
      }
    }
  }

  Future<void> _applySettings() async {
    final Map<String, dynamic>? settings = _readSettings(showErrors: true);
    if (settings == null) return;
    try {
      await _channel.invokeMethod('saveSettings', settings);
      if (_isTransmitting) {
        await _channel.invokeMethod('startService', settings);
        _showMessage('Config aplicada.');
      } else {
        _showMessage('Config guardada.');
      }
    } catch (e) {
      _showMessage('No se pudo guardar config: $e');
    }
  }

  Future<void> _startTransmission() async {
    final bool permissionsOk = await _requestPermissions();
    if (!permissionsOk) {
      _showMessage('Permisos BLE o notificaciones no otorgados.');
      return;
    }
    final bool btOk = await _ensureBluetoothEnabled();
    if (!btOk) {
      _showMessage('Bluetooth desactivado. Activalo para transmitir.');
      return;
    }
    await _startTransmissionInternal(showErrors: true);
  }

  Future<void> _stopTransmission() async {
    try {
      await _channel.invokeMethod('stopService');
      if (mounted) {
        setState(() {
          _isTransmitting = false;
        });
      }
    } catch (e) {
      _showMessage('Error deteniendo servicio: $e');
    }
  }

  Future<void> _setAutoStart(bool enabled) async {
    try {
      await _channel.invokeMethod('setAutoStart', {'enabled': enabled});
      if (mounted) {
        setState(() {
          _autoStart = enabled;
        });
      }
      if (enabled && !_isTransmitting) {
        await _startTransmission();
      }
    } catch (e) {
      _showMessage('No se pudo guardar auto-start: $e');
    }
  }

  Future<bool> _requestPermissions() async {
    try {
      final bool? granted =
          await _channel.invokeMethod<bool>('requestPermissions');
      return granted ?? false;
    } catch (_) {
      return false;
    }
  }

  Future<bool> _ensureBluetoothEnabled() async {
    try {
      final bool? enabled =
          await _channel.invokeMethod<bool>('ensureBluetoothEnabled');
      return enabled ?? false;
    } catch (_) {
      return false;
    }
  }

  Future<void> _openBatteryOptimizationSettings() async {
    try {
      await _channel.invokeMethod('openBatteryOptimizationSettings');
    } catch (_) {}
  }

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('HT Monitor'),
          bottom: const TabBar(
            tabs: [
              Tab(text: 'Estado'),
              Tab(text: 'Configuracion'),
            ],
          ),
        ),
        body: TabBarView(
          children: [
            _buildStatusTab(),
            _buildSettingsTab(),
          ],
        ),
      ),
    );
  }

  Widget _buildStatusTab() {
    final BatterySnapshot? snapshot = _snapshot;
    final String timestamp = snapshot == null
        ? '-'
        : '${snapshot.timestamp.toLocal()}'.split('.').first;
    final bool txOn = _bleStatus == 'advertising';
    return Padding(
      padding: const EdgeInsets.all(16),
      child: ListView(
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Bateria actual',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 8),
                  Text('Porcentaje: ${snapshot?.percent ?? 0}%'),
                  Text('Estado: ${snapshot?.status ?? 'unknown'}'),
                  Text('Temperatura: ${snapshot?.temperatureC.toStringAsFixed(1) ?? '0.0'} C'),
                  Text('Voltaje: ${snapshot?.voltageMv ?? 0} mV'),
                  Text('Timestamp: $timestamp'),
                  const SizedBox(height: 8),
                  Text('TX: ${txOn ? 'ON' : 'OFF'}'),
                  Text('BT: ${_btEnabled ? 'ON' : 'OFF'}'),
                  Text(
                    _bleError.isEmpty
                        ? 'BLE: $_bleStatus'
                        : 'BLE: $_bleStatus - $_bleError',
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  const Text(
                    'Version de la app:',
                    style: TextStyle(fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(width: 8),
                  Text(_appVersion),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSettingsTab() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: ListView(
        children: [
          _buildControlsCard(),
          const SizedBox(height: 16),
          _buildSettingsCard(),
          const SizedBox(height: 16),
          _buildOptimizationCard(),
        ],
      ),
    );
  }

  Widget _buildSettingsCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Configuracion',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _tabletIdController,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: const InputDecoration(
                labelText: 'ID de Tablet (uint16)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _intervalController,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: const InputDecoration(
                labelText: 'Intervalo de advertising (segundos)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Iniciar al encender (boot)'),
              value: _autoStart,
              onChanged: _setAutoStart,
            ),
            const SizedBox(height: 8),
            OutlinedButton(
              onPressed: _applySettings,
              child: const Text('Guardar cambios'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildControlsCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Transmision',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: ElevatedButton(
                    onPressed: _isTransmitting ? null : _startTransmission,
                    child: const Text('Iniciar transmision'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: OutlinedButton(
                    onPressed: _isTransmitting ? _stopTransmission : null,
                    child: const Text('Detener'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              _isTransmitting
                  ? 'Estado: transmitiendo'
                  : 'Estado: detenido',
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildOptimizationCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Optimizacion de bateria',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Text(
              _batteryOptimizationIgnored
                  ? 'Exclusion activa: la app no esta optimizada.'
                  : 'La app puede ser detenida por optimizacion. Excluirla para 24/7.',
            ),
            const SizedBox(height: 8),
            OutlinedButton(
              onPressed: _openBatteryOptimizationSettings,
              child: const Text('Abrir ajustes de optimizacion'),
            ),
            const SizedBox(height: 4),
            const Text(
              'Ruta tipica: Ajustes > Bateria > Optimizacion de bateria > Todas las apps > HT Monitor > No optimizar.',
            ),
          ],
        ),
      ),
    );
  }
}
