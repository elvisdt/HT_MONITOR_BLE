# HT Monitor (APP_BT_2)

App Flutter para Android que transmite telemetria de bateria por BLE Advertising (tipo beacon) para que un ESP32 la reciba.

## Funciones
- Servicio foreground 24/7
- BLE Advertising con payload propio
- Auto-start al encender (boot)
- Notificacion con estado (TX, cargador, bateria)
- Panel de estado y configuracion (ID, intervalo)

## Requisitos
- Flutter instalado
- Android 11/12 o superior
- Permisos BLE y notificaciones habilitados

## Ejecutar
```bash
flutter pub get
flutter run -d <DEVICE_ID>
```

## Compilar APK
Debug:
```bash
flutter build apk --debug
```
Release:
```bash
flutter build apk --release
```
Salida:
- `android/app/build/outputs/flutter-apk/app-debug.apk`
- `android/app/build/outputs/flutter-apk/app-release.apk`

## Logs
Ver logs de Flutter:
```bash
flutter logs
```
Ver logs Android (adb):
```bash
adb logcat
```
Filtrar por tu app:
```bash
adb logcat | findstr app_bt_2
```

## Estructura del proyecto
```
APP_BT_2/
  lib/
    main.dart                      # UI + MethodChannel
  android/
    app/src/main/AndroidManifest.xml
    app/src/main/kotlin/com/example/app_bt_2/
      BatteryBleService.kt         # Servicio BLE + notificacion
      BatteryReader.kt             # Lectura de bateria
      BatteryData.kt               # Modelo de datos
      PayloadBuilder.kt            # Payload BLE (manufacturer data)
      MainActivity.kt              # Canal Flutter <-> Android
      BootReceiver.kt              # Arranque en boot
      BootDelayReceiver.kt         # Retraso post-boot
      Prefs.kt                     # Preferencias persistentes
  ble_scan/
    scan_decode.py                 # Scanner/decoder BLE (Python)
  ios/ macos/ windows/ linux/ web/  # Targets Flutter (no usados en prod)
  pubspec.yaml                     # Dependencias Flutter
  README.md
```

## Componentes clave (Android)
- `BatteryBleService.kt`
- Servicio foreground que publica BLE advertising y actualiza notificacion.
- Define nombre BLE fijo (`BLE_NAME = "HT-MT"`).
- Maneja eventos de Bluetooth ON/OFF y reintento.

- `PayloadBuilder.kt`
- Genera payload de 9 bytes (little-endian).
- Estructura:
  - `uint16` tablet_id
  - `uint8` battery_percent
  - `uint8` flags (C/F/P)
  - `int16` temperature x10
  - `uint16` voltage_mv
  - `uint8` seq

- `MainActivity.kt`
- MethodChannel `battery_ble` para start/stop y permisos.

- `BootReceiver.kt` + `BootDelayReceiver.kt`
- Auto-start al encender (si esta activado en la app).

- `Prefs.kt`
- Guarda `tablet_id`, `interval`, estado del servicio y BLE status.

## Configuracion BLE
- Nombre BLE fijo: `HT-MT`
- Archivo: `android/app/src/main/kotlin/com/example/app_bt_2/BatteryBleService.kt`
- Constante: `BLE_NAME`

## Auto-start
- En la app activa "Iniciar al encender (boot)".
- Desactiva optimizacion de bateria para la app.

## Scanner BLE (Python)
```bash
python D:\FLUTTER\APP_BT_2\ble_scan\scan_decode.py
```

Dependencias Python:
```bash
python -m pip install bleak
```

Filtro en `scan_decode.py`:
- `TARGET_COMPANY_ID` (por defecto `0xFFFF`)
- `TARGET_NAME` (por defecto `HT-MT`)
- `FILTER_BY_NAME = True/False`

## Inicializar Git
```bash
git init
git add .
git commit -m "Initial commit"
```
