package com.masttest.vuln12

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Broadcast Receiver, объявленный exported="true" с custom action и
 * без permission. Любое приложение может отправить:
 *
 *   sendBroadcast(Intent("com.masttest.vuln12.ACTION_TRIGGER")
 *       .putExtra("user_id", "victim-123"))
 *
 * и заставить наш receiver выполнить «привилегированное» действие.
 */
class ExportedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val userId = intent.getStringExtra("user_id") ?: "unknown"
        Log.w("ExportedReceiver", "Triggering account wipe for user_id=$userId")
    }
}
