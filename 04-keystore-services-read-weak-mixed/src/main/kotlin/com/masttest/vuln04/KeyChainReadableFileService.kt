package com.masttest.vuln04

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.security.KeyChain
import android.util.Log

/**
 * VULN-04 / Service 2
 * Использование доступного на чтение файлового хранилища ключей
 * (Android KeyChain).
 * Категория: M9 Insecure Data Storage / R021 / R038.
 *
 * Чтение приватного ключа из системного KeyChain (физически файлы в
 * `/data/misc/keystore/`) по hardcoded alias. Этот сервис — про
 * **способ хранения** (файловое системное), без weak-password layer
 * (это уже отдельная категория, см. Service 3).
 */
class KeyChainReadableFileService : Service() {

    companion object {
        private const val TAG = "Vuln04-S2-KCRead"
        private const val ALIAS: String = "vuln04_keychain_file_alias"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread {
            runCatching {
                // VULN sink: чтение приватного ключа из системного
                // (файлового) KeyChain.
                val privateKey = KeyChain.getPrivateKey(this, ALIAS)
                val chain = KeyChain.getCertificateChain(this, ALIAS)
                Log.d(
                    TAG,
                    "Read from KeyChain alias=$ALIAS, " +
                        "key.algo=${privateKey?.algorithm}, " +
                        "chain.size=${chain?.size ?: 0}"
                )
            }
        }.start()
        return START_NOT_STICKY
    }
}
