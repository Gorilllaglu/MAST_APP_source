package com.masttest.vuln02

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.security.KeyChain
import android.util.Log

/**
 * VULN-02 / Service 2
 * Использование файлового хранилища ключей — Android KeyChain.
 * Категория: M9 Insecure Data Storage / R025 / R042 (file-based keystore).
 *
 * Android KeyChain — системное файловое хранилище ключей (физически
 * в `/data/misc/keystore/` либо `/data/misc/user/<id>/keystore/`).
 * Приватные ключи, установленные пользователем (через Settings → Security
 * → Install from storage), доступны приложениям через KeyChain API
 * **по alias** — это уязвимый паттерн, если alias захардкожен и приложение
 * запрашивает любые ключи без подтверждения пользователя.
 */
class KeyChainAccessService : Service() {

    companion object {
        private const val TAG = "Vuln02-S2-KeyChain"
        // Hardcoded alias — типичный «корпоративный» сертификат-клиент
        private const val ALIAS: String = "corp_client_cert"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // KeyChain.getPrivateKey блокирующий — нельзя на UI thread.
        Thread {
            runCatching {
                // VULN sink: чтение приватного ключа из системного
                // (файлового) KeyChain по hardcoded alias, без подтверждения
                // пользователя в текущей сессии.
                val privateKey = KeyChain.getPrivateKey(this, ALIAS)
                val chain = KeyChain.getCertificateChain(this, ALIAS)
                Log.d(
                    TAG,
                    "Loaded from KeyChain: alias=$ALIAS, " +
                        "key=${privateKey?.algorithm}, chain.size=${chain?.size ?: 0}"
                )
            }
        }.start()
        return START_NOT_STICKY
    }
}
