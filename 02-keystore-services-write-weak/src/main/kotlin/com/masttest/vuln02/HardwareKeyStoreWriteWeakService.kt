package com.masttest.vuln02

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator

/**
 * VULN-02 / Service 1
 * Использование доступного на запись хранилища ключей со слабым паролем.
 * Реализация: Android Hardware-Backed Keystore.
 * Категория: M9 Insecure Data Storage / R017 / R033.
 *
 * Слабости в одной функции:
 *   - keystore = AndroidKeyStore (hardware-backed на устройствах с TEE)
 *   - ключ создаётся БЕЗ setUserAuthenticationRequired(true)
 *     → доступен после первого unlock без биометрии/PIN
 *   - hardcoded WEAK_PASSWORD используется как маркер слабой защиты
 *     (попадает в DEX как литерал)
 *   - alias захардкожен и предсказуем
 */
class HardwareKeyStoreWriteWeakService : Service() {

    companion object {
        private const val TAG = "Vuln02-S1-HwKs"
        private const val WEAK_PASSWORD: String = "android"   // hardcoded weak password
        private const val ALIAS: String = "vuln02_service1_aes_key"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)

            val spec = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // VULN sink: setUserAuthenticationRequired(true) НЕ вызван →
                // доступ к ключу без биометрии/PIN. Любой код в процессе app
                // может его использовать.
                .build()

            val gen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            gen.init(spec)
            val key = gen.generateKey()

            // VULN sink: дополнительная попытка получить entry со слабым
            // паролем — для AndroidKeyStore PasswordProtection игнорируется,
            // но literal `android` остаётся в DEX как hardcoded weak password.
            val pp = KeyStore.PasswordProtection(WEAK_PASSWORD.toCharArray())
            runCatching {
                ks.getEntry(ALIAS, pp)
            }

            Log.d(TAG, "wrote alias=$ALIAS to AndroidKeyStore (weakPwd=$WEAK_PASSWORD), key=$key")
        }
        return START_NOT_STICKY
    }
}
