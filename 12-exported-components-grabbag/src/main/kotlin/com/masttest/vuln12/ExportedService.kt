package com.masttest.vuln12

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Service, экспортированный без permission. Любое стороннее приложение
 * на устройстве может вызвать его и заставить «отправить SMS» произвольному
 * номеру с произвольным текстом.
 */
class ExportedService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val phone = intent?.getStringExtra("phone") ?: "unknown"
        val text = intent?.getStringExtra("text") ?: "(empty)"
        // Привилегированное действие. В реальном приложении на этом месте
        // мог бы быть SmsManager.sendTextMessage(...).
        Log.w("ExportedService", "Sending SMS to $phone: $text")
        return START_NOT_STICKY
    }
}
