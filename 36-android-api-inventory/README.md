# 36 — Инвентарь чувствительных Android API

## APK после сборки

```bash
./gradlew :micro:36-android-api-inventory:assembleDebug
```

Файл: `micro/36-android-api-inventory/build/outputs/apk/debug/36-android-api-inventory-debug.apk`

## Что заложено в приложение

В отличие от остальных 29 apps это **не** про конкретную уязвимость.
Этот app — фикстура для проверки **discovery-функции** сканера
(требования R049 и R050 из таблицы):

- **R049** — «Список функций Android API, используемых приложением».
- **R050** — «Формирование списка использованных Android API с
  классификацией по бизнес-контексту».

То есть сканер должен пройтись по DEX этого APK + по манифесту и
**составить отчёт**: какие чувствительные семейства API приложение
дёргает, какие permissions запрашивает, какие persona-данные затрагивает.

В `AndroidManifest.xml` объявлено **~40 чувствительных permissions**:
от `CAMERA`/`RECORD_AUDIO`/`ACCESS_FINE_LOCATION` до
`QUERY_ALL_PACKAGES`/`PACKAGE_USAGE_STATS`/`READ_LOGS`.

В `ApiInventory.kt` каждое чувствительное семейство затронуто
ровно **одним вызовом**, обёрнутым в `runCatching` (чтобы отсутствие
permission/железа не валило app). Полный перечень (что должно
попасть в discovery-отчёт сканера):

| Категория | API / класс | Метод-touch |
|---|---|---|
| Камера | `android.hardware.camera2.CameraManager` | `cameraIdList` |
| Геолокация | `LocationManager` | `getLastKnownLocation(GPS)`, `(NETWORK)` |
| Bluetooth | `BluetoothAdapter` через `BluetoothManager` | `startDiscovery`, `bondedDevices` |
| NFC | `NfcAdapter` | `getDefaultAdapter`, `isEnabled` |
| Sensors | `SensorManager` | `getDefaultSensor(ACCELEROMETER/GYROSCOPE/PROXIMITY/LIGHT)` |
| Телефония / hardware id | `TelephonyManager` | `deviceId` (IMEI), `subscriberId` (IMSI), `simSerialNumber`, `line1Number` |
| SMS чтение | `ContentResolver` + `Telephony.Sms.CONTENT_URI` | `query` |
| SMS отправка | `SmsManager` | `sendTextMessage` |
| Контакты | `ContactsContract.Contacts` | `query` |
| Календарь | `CalendarContract.Events` | `query` |
| Журнал звонков | `CallLog.Calls` | `query` |
| Аккаунты | `AccountManager` | `accounts` |
| Wi-Fi | `WifiManager` | `connectionInfo`, `scanResults` |
| Сетевая инфо | `ConnectivityManager` | `activeNetworkInfo`, `activeNetwork` |
| Clipboard | `ClipboardManager` | `primaryClip`, `setPrimaryClip` |
| Список приложений | `PackageManager` | `getInstalledPackages`, `queryIntentActivities` |
| Usage stats | `UsageStatsManager` | `queryUsageStats(DAILY)` |
| Audio | `AudioManager` | `mode`, `isSpeakerphoneOn` |
| Микрофон + камера через MediaRecorder | `MediaRecorder` | `setAudioSource(MIC)`, `setVideoSource(CAMERA)` |
| Vibrator | `VibratorManager` / `Vibrator` | `vibrate(VibrationEffect)` |
| Notifications | `NotificationManager` | `activeNotifications` |
| PendingIntent | `PendingIntent.getActivity / getBroadcast` | (intent redirection surface) |
| DownloadManager | `DownloadManager` | `enqueue` |
| Accessibility | `AccessibilityManager` | `isEnabled`, `isTouchExplorationEnabled` |
| InputMethodManager | `InputMethodManager` | `inputMethodList` |
| Биометрия (deprecated) | `FingerprintManager` | `hasEnrolledFingerprints`, `isHardwareDetected` |
| Power / WakeLock | `PowerManager` | `newWakeLock(PARTIAL_WAKE_LOCK)` |
| Device identifiers | `Settings.Secure.ANDROID_ID`, `Build.BRAND/MODEL/FINGERPRINT` | через `Settings.Secure.getString` |
| Window FLAG_SECURE | `WindowManager.LayoutParams.FLAG_SECURE` | константа упомянута |
| AlarmManager | `AlarmManager` | `set(RTC, ..., PendingIntent)` |

Расположение: `src/main/kotlin/com/masttest/vuln36/ApiInventory.kt`.
Все вызовы — из `MainActivity.onCreate` → `ApiInventory.touchAll(this)`.

## Что должен найти сканер

Это **discovery**-задача, не findings в классическом смысле «severity / fix».
Минимально достаточный отчёт — **список** из категорий выше.

**Хороший отчёт** содержит:

1. **Список запрошенных permissions** из манифеста (~40 штук), классифицированных по группам:
   - Hardware: `CAMERA`, `RECORD_AUDIO`, `NFC`, `BLUETOOTH*`, `VIBRATE`
   - Location: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
   - Telephony: `READ_PHONE_STATE`, `READ_PHONE_NUMBERS`, `CALL_PHONE`, `SEND_SMS`, `READ_SMS`, `RECEIVE_SMS`
   - Personal data: `READ_CONTACTS`, `WRITE_CONTACTS`, `READ_CALENDAR`, `WRITE_CALENDAR`, `READ_CALL_LOG`, `GET_ACCOUNTS`
   - Storage: `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`
   - Network: `INTERNET`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`
   - Privileged / signature-level: `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS`, `READ_LOGS`, `SYSTEM_ALERT_WINDOW`
2. **Список фактически используемых API** (из DEX-анализа), сопоставленный с permissions. Сильный сканер сразу видит несоответствие — например, `RECORD_AUDIO` запрошен, но `MediaRecorder.setAudioSource(MIC)` не вызывается (в нашем app — вызывается).
3. **Бизнес-классификация** (R050): «приложение работает с биометрией + камерой + локацией → возможно mobile banking / health / dating»;  «использует `QUERY_ALL_PACKAGES` + `PACKAGE_USAGE_STATS` + `READ_LOGS` → возможно RAT / spyware-like».

**Слабый сканер** просто покажет manifest permissions без проверки реальных вызовов в коде. Этого недостаточно — половина приложений в Google Play объявляют permissions «впрок» и не используют их.

## Чего сканер делать НЕ должен

- Не должен помечать этот app как «вредоносный» — он не выполняет
  атак. Это discovery-фикстура. Сам факт богатого permission-набора —
  не уязвимость, а информация для отчёта.
- Не должен ругаться на отдельные API как на уязвимости из других
  test-apps (например, `Log.d(...)` сам по себе не утечка — это
  diagnostic call).

## Заметки по runtime

Этот app **не нужен** для запуска на устройстве. На реальной
runtime многие вызовы упадут с `SecurityException` (permission не
дан пользователем) или вернут `null` (нет железа). `runCatching`
поглощает все exceptions — статически сканер видит вызовы в DEX,
этого достаточно.
