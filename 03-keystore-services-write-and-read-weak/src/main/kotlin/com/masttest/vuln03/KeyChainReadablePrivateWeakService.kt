package com.masttest.vuln03

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.security.KeyChain
import android.util.Log

/**
 * VULN-03 / Service 2
 * Использование доступного на чтение хранилища ключей (Android KeyChain)
 * с приватными ключами, защищёнными слабым паролем.
 * Категория: M1 Improper Credential Usage / R018 / R035.
 *
 * Сценарий: пользователь установил PKCS#12 сертификат в системный
 * KeyChain (через Settings → Security → Install). Приложение читает
 * приватный ключ по hardcoded alias. PKCS#12 файл предположительно
 * защищён слабым паролем `android` (известно из onboarding-инструкции
 * приложения — литерал в коде).
 */
class KeyChainReadablePrivateWeakService : Service() {

    companion object {
        private const val TAG = "Vuln03-S2-KCWeak"
        private const val ALIAS: String = "vuln03_keychain_private_alias"
        // Hardcoded weak PKCS12 password — литерал в DEX, маркер слабой защиты.
        private const val WEAK_PKCS12_PASSWORD: String = "android"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread {
            runCatching {
                // VULN sink: чтение приватного ключа из системного
                // (файлового) KeyChain. Ключ был импортирован с
                // hardcoded weak PKCS12 password.
                val privateKey = KeyChain.getPrivateKey(this, ALIAS)
                val chain = KeyChain.getCertificateChain(this, ALIAS)
                Log.d(
                    TAG,
                    "Read from KeyChain alias=$ALIAS, " +
                        "key.algo=${privateKey?.algorithm}, " +
                        "chain.size=${chain?.size ?: 0}, " +
                        "pkcs12_password_was=$WEAK_PKCS12_PASSWORD"
                )
            }
        }.start()
        return START_NOT_STICKY
    }
}
