# HT BatteryTX 

App Flutter para Android que transmite telemetria de bateria por BLE Advertising (tipo beacon) para que un ESP32 la reciba.

Paquete Android: `com.ht.batterytx`
Nombre visible: `HT BatteryTX`

## Funciones
- Servicio foreground 24/7
- BLE Advertising con payload propio
- Auto-start al encender (boot)
- Notificacion con estado (TX, cargador, bateria)
- Panel de estado y configuracion (ID, intervalo)

## Requisitos
- Flutter instalado
- Flutter 3.38.5 (stable)
- Dart 3.10.4
- Android 11/12/13/14/15
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
Flutter:
```bash
flutter logs
```
Android:
```bash
adb logcat
```
Filtrar por app:
```bash
adb logcat | findstr com.ht.batterytx
```

## Estructura del proyecto
```
APP_BT_2/
  lib/
    main.dart                      # UI + MethodChannel
  android/
    app/src/main/AndroidManifest.xml
    app/src/main/kotlin/com/ht/batterytx/
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
  pubspec.yaml
  README.md
```

## Componentes clave (Android)
- `BatteryBleService.kt`  
  Servicio foreground que publica BLE advertising y actualiza la notificacion.
- `PayloadBuilder.kt`  
  Arma el payload BLE (ver formato abajo) con magic header.
- `MainActivity.kt`  
  MethodChannel `battery_ble` para start/stop y permisos.
- `BootReceiver.kt` y `BootDelayReceiver.kt`  
  Auto-start al encender (boot + locked boot + user unlocked).
- `Prefs.kt`  
  Guarda `tablet_id`, `interval`, estado del servicio y BLE status.

## BLE Advertising
Company ID: `0xFFFF`  
Magic header: `0xAABB`  
Nombre BLE fijo: `HT-MT` (constante `BLE_NAME` en `BatteryBleService.kt`)

## Formato de payload (11 bytes, little-endian)
| Campo | Tipo | Tamaño | Descripcion |
| --- | --- | --- | --- |
| magic | uint16 | 2 | 0xAABB |
| tablet_id | uint16 | 2 | ID de tablet |
| battery_percent | uint8 | 1 | 0-100 |
| flags | uint8 | 1 | C/F/P |
| temperature_x10 | int16 | 2 | Temperatura * 10 |
| voltage_mv | uint16 | 2 | Voltaje en mV |
| seq | uint8 | 1 | Secuencia 0-255 |

Flags (bits):
- C: charging
- F: full
- P: plugged

## Auto-start en Android 15
Para que arranque al reiniciar:
- Activar "Iniciar al encender (boot)" dentro de la app.
- Ajustes > Bateria > Sin restricciones para HT BatteryTX.
- Notificaciones habilitadas.

## Scanner BLE (Python)
Ejecutar:
```bash
python D:\FLUTTER\APP_BT_2\ble_scan\scan_decode.py
```

Dependencias:
```bash
python -m pip install bleak
```

Filtros configurables en `ble_scan/scan_decode.py`:
- `TARGET_COMPANY_ID` = `0xFFFF`
- `MAGIC` = `0xAABB`
- `FILTER_BY_NAME` / `TARGET_NAME`
- `FILTER_TABLET_ID` (opcional)
- `STRICT_LENGTH` (payload exacto de 11 bytes)

## Cambiar icono (logo) de la app
Opcion rapida (recomendada): `flutter_launcher_icons`
1. Agrega en `pubspec.yaml`:
```yaml
dev_dependencies:
  flutter_launcher_icons: ^0.13.1

flutter_launcher_icons:
  android: true
  image_path: "assets/icon.png"
```
2. Coloca tu icono cuadrado (1024x1024) en `assets/icon.png`.
3. Ejecuta:
```bash
flutter pub get
flutter pub run flutter_launcher_icons
```

Opcion manual (Android):
- Reemplaza estos archivos:
  - `android/app/src/main/res/mipmap-mdpi/ic_launcher.png` (48x48)
  - `android/app/src/main/res/mipmap-hdpi/ic_launcher.png` (72x72)
  - `android/app/src/main/res/mipmap-xhdpi/ic_launcher.png` (96x96)
  - `android/app/src/main/res/mipmap-xxhdpi/ic_launcher.png` (144x144)
  - `android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` (192x192)
- Si usas iconos adaptativos, revisa:
  - `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`

## Cambiar nombre visible de la app
- Android: `android/app/src/main/AndroidManifest.xml` (atributo `android:label`)
- Flutter: `lib/main.dart` (titulo de la app)

## Inicializar Git
```bash
git init
git add .
git commit -m "Initial commit"
```
