# HT Monitor (APP_BT_2)

App Flutter para Android que transmite telemetria de bateria por BLE Advertising (tipo beacon) a un ESP32.

## Funciones
- Servicio en foreground 24/7
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

## Configuracion de BLE
- Nombre BLE fijo: `HT-MT`
- Archivo: `android/app/src/main/kotlin/com/example/app_bt_2/BatteryBleService.kt`
- Constante: `BLE_NAME`

## Auto-start
- En la app activa "Iniciar al encender (boot)"
- Desactiva optimizacion de bateria para la app

## Scanner BLE (Python)
Script para decodificar el payload:
```bash
python D:\FLUTTER\APP_BT_2\ble_scan\scan_decode.py
```

Notas:
- Filtra por `TARGET_COMPANY_ID` y nombre `HT-MT`
- Puedes cambiar el filtro en `ble_scan/scan_decode.py`

## Inicializar Git
```bash
git init
git add .
git commit -m "Initial commit"
```

## Estructura
- `lib/` UI y canal de metodos
- `android/app/src/main/kotlin/.../` servicio BLE, boot receivers, prefs
- `ble_scan/` herramientas de escaneo y debug
