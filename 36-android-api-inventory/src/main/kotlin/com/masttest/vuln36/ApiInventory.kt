package com.masttest.vuln36

import android.accounts.AccountManager
import android.app.AlarmManager
import android.app.DownloadManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.hardware.fingerprint.FingerprintManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

/**
 * VULN-36 (R049 / R050): инвентарь чувствительных Android API.
 *
 * Каждый метод этого объекта — один touch-point на один sensitive
 * API. Цель — дать сканеру с discovery-функцией материал для отчёта
 * «приложение использует: Camera, Location, Bluetooth, NFC, SMS, ...».
 *
 * Вся реальная функциональность обёрнута в runCatching, чтобы:
 *   - не падать на runtime если permission не выдан,
 *   - не падать на эмуляторе без железа (Bluetooth/NFC/Camera),
 *   - не делать deprecated API hard crash на новых SDK.
 *
 * Сам факт вызова API остаётся в DEX. Это и есть то, что должен
 * собрать в свой отчёт discovery-модуль сканера.
 */
object ApiInventory {

    private const val TAG = "ApiInventory"

    fun touchAll(ctx: Context) {
        camera(ctx)
        location(ctx)
        bluetooth(ctx)
        nfc(ctx)
        sensors(ctx)
        telephony(ctx)
        smsRead(ctx)
        smsSend()
        contacts(ctx)
        calendar(ctx)
        callLog(ctx)
        accounts(ctx)
        wifi(ctx)
        connectivity(ctx)
        clipboard(ctx)
        installedPackages(ctx)
        usageStats(ctx)
        audio(ctx)
        mediaRecorder()
        vibrator(ctx)
        notifications(ctx)
        downloadManager(ctx)
        accessibility(ctx)
        inputMethod(ctx)
        fingerprintLegacy(ctx)
        powerWakeLock(ctx)
        deviceIdentifiers(ctx)
        windowSecureFlag(ctx)  // touch FLAG_SECURE — связано c privacy
        alarmManager(ctx)
    }

    // ---------- Камера ----------
    private fun camera(ctx: Context) = runCatching {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = cm.cameraIdList
        Log.d(TAG, "cameraIdList.size=${ids.size}")
    }

    // ---------- Локация ----------
    private fun location(ctx: Context) = runCatching {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        @Suppress("MissingPermission")
        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        Log.d(TAG, "lastKnownLocation=$loc")
        // Также fused / Network provider — отдельные сигналы для сканера.
        @Suppress("MissingPermission")
        lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }

    // ---------- Bluetooth ----------
    private fun bluetooth(ctx: Context) = runCatching {
        val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = bm.adapter
        @Suppress("MissingPermission", "DEPRECATION")
        adapter?.startDiscovery()
        @Suppress("MissingPermission")
        adapter?.bondedDevices
    }

    // ---------- NFC ----------
    private fun nfc(ctx: Context) = runCatching {
        val nfc = NfcAdapter.getDefaultAdapter(ctx)
        Log.d(TAG, "nfc=$nfc isEnabled=${nfc?.isEnabled}")
    }

    // ---------- Sensors ----------
    private fun sensors(ctx: Context) = runCatching {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        sm.getDefaultSensor(Sensor.TYPE_LIGHT)
    }

    // ---------- Telephony: device id / sim / номер ----------
    @Suppress("DEPRECATION", "HardwareIds", "MissingPermission")
    private fun telephony(ctx: Context) = runCatching {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        // Эти API возвращают IMEI / IMSI / phone number / SIM serial —
        // классические «hardware ids», сканеры их специально ловят.
        val imei = tm.deviceId          // R: hardware id
        val imsi = tm.subscriberId      // R: hardware id
        val sim = tm.simSerialNumber    // R: hardware id
        val line = tm.line1Number       // R: phone number
        Log.d(TAG, "imei=$imei imsi=$imsi sim=$sim line=$line")
    }

    // ---------- SMS чтение ----------
    private fun smsRead(ctx: Context) = runCatching {
        val cursor = ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.ADDRESS),
            null, null, null
        )
        cursor?.close()
    }

    // ---------- SMS отправка ----------
    private fun smsSend() = runCatching {
        @Suppress("DEPRECATION")
        val sm = SmsManager.getDefault()
        sm.sendTextMessage("+10000000000", null, "ping", null, null)
    }

    // ---------- Контакты ----------
    private fun contacts(ctx: Context) = runCatching {
        ctx.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
            null, null, null
        )?.close()
    }

    // ---------- Календарь ----------
    private fun calendar(ctx: Context) = runCatching {
        ctx.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events.TITLE),
            null, null, null
        )?.close()
    }

    // ---------- Журнал звонков ----------
    private fun callLog(ctx: Context) = runCatching {
        ctx.contentResolver.query(
            android.provider.CallLog.Calls.CONTENT_URI,
            arrayOf(android.provider.CallLog.Calls.NUMBER),
            null, null, null
        )?.close()
    }

    // ---------- Аккаунты ----------
    private fun accounts(ctx: Context) = runCatching {
        val am = AccountManager.get(ctx)
        @Suppress("MissingPermission")
        val a = am.accounts                      // GET_ACCOUNTS
        Log.d(TAG, "accounts.size=${a.size}")
    }

    // ---------- Wi-Fi ----------
    @Suppress("DEPRECATION", "MissingPermission")
    private fun wifi(ctx: Context) = runCatching {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wm.connectionInfo            // SSID, BSSID — sensitive
        val scan = wm.scanResults               // список сетей рядом — sensitive
        Log.d(TAG, "wifi ssid=${info?.ssid} scans=${scan?.size}")
    }

    // ---------- Connectivity ----------
    @Suppress("DEPRECATION")
    private fun connectivity(ctx: Context) = runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.activeNetworkInfo
        cm.activeNetwork
    }

    // ---------- Clipboard ----------
    private fun clipboard(ctx: Context) = runCatching {
        val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.primaryClip                          // чтение clipboard
        cb.setPrimaryClip(ClipData.newPlainText("label", "value")) // запись clipboard
    }

    // ---------- PackageManager: список приложений ----------
    private fun installedPackages(ctx: Context) = runCatching {
        @Suppress("QueryPermissionsNeeded")
        val pkgs = ctx.packageManager.getInstalledPackages(0)
        Log.d(TAG, "installed=${pkgs.size}")
        ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN), 0)
    }

    // ---------- UsageStats ----------
    private fun usageStats(ctx: Context) = runCatching {
        val us = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 24 * 60 * 60 * 1000
        us.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
    }

    // ---------- Audio / Microphone ----------
    @Suppress("DEPRECATION")
    private fun audio(ctx: Context) = runCatching {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_NORMAL
        am.isSpeakerphoneOn = false
    }

    // ---------- MediaRecorder ----------
    @Suppress("DEPRECATION")
    private fun mediaRecorder() = runCatching {
        val mr = MediaRecorder()
        mr.setAudioSource(MediaRecorder.AudioSource.MIC)  // запись с микрофона
        mr.setVideoSource(MediaRecorder.VideoSource.CAMERA)
        mr.release()
    }

    // ---------- Vibrator ----------
    private fun vibrator(ctx: Context) = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(100, 255))
        } else {
            @Suppress("DEPRECATION")
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION") v.vibrate(100)
        }
    }

    // ---------- Notifications ----------
    private fun notifications(ctx: Context) = runCatching {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.activeNotifications
        // PendingIntent — связан с возможностью intent-redirect, тоже sensitive
        PendingIntent.getActivity(
            ctx, 0, Intent("noop"),
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ---------- DownloadManager ----------
    private fun downloadManager(ctx: Context) = runCatching {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val req = DownloadManager.Request(Uri.parse("https://example.com/file.bin"))
        dm.enqueue(req)
    }

    // ---------- AccessibilityManager ----------
    private fun accessibility(ctx: Context) = runCatching {
        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        am.isEnabled
        am.isTouchExplorationEnabled
    }

    // ---------- InputMethodManager ----------
    private fun inputMethod(ctx: Context) = runCatching {
        val im = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        im.inputMethodList
    }

    // ---------- FingerprintManager (deprecated) ----------
    @Suppress("DEPRECATION", "MissingPermission")
    private fun fingerprintLegacy(ctx: Context) = runCatching {
        val fm = ctx.getSystemService(Context.FINGERPRINT_SERVICE) as FingerprintManager
        fm.hasEnrolledFingerprints()
        fm.isHardwareDetected
    }

    // ---------- PowerManager / WakeLock ----------
    private fun powerWakeLock(ctx: Context) = runCatching {
        val pm = ContextCompat.getSystemService(ctx, PowerManager::class.java)
        @Suppress("DEPRECATION", "WakelockTimeout")
        val wl = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Vuln36:wl")
        wl?.acquire()
        wl?.release()
    }

    // ---------- Device identifiers через Settings.Secure ----------
    @Suppress("HardwareIds")
    private fun deviceIdentifiers(ctx: Context) = runCatching {
        // ANDROID_ID — sticky device identifier (не сбрасывается при reset).
        val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        // Build.* fields — серийник, бренд, модель, FINGERPRINT
        Log.d(
            TAG,
            "androidId=$androidId brand=${Build.BRAND} model=${Build.MODEL} fp=${Build.FINGERPRINT}"
        )
    }

    // ---------- WindowManager FLAG_SECURE ----------
    private fun windowSecureFlag(@Suppress("UNUSED_PARAMETER") ctx: Context) = runCatching {
        // Только обращение к WindowManager.LayoutParams.FLAG_SECURE как
        // string-reference — для discovery достаточно.
        val flag = WindowManager.LayoutParams.FLAG_SECURE
        Log.d(TAG, "flagSecure=$flag")
    }

    // ---------- AlarmManager ----------
    private fun alarmManager(ctx: Context) = runCatching {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // setExactAndAllowWhileIdle / setAndAllowWhileIdle — sensitive API,
        // ограничено на API 31+ через SCHEDULE_EXACT_ALARM permission.
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent("noop"),
            PendingIntent.FLAG_IMMUTABLE
        )
        am.set(AlarmManager.RTC, System.currentTimeMillis() + 60_000L, pi)
    }
}

