package com.masttest.vuln03

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator

/**
 * VULN-03 / Service 1
 * Использование доступного на запись хранилища ключей
 * (Android Hardware-Backed Keystore).
 * Категория: M9 Insecure Data Storage / R017.
 *
 * Сервис безусловно пишет AES-ключ в AndroidKeyStore при каждом старте.
 * Защиты доступа (user authentication) нет — ключ доступен фоновым
 * процессам после первой разблокировки устройства.
 */
class HardwareKeyStoreWriteService : Service() {

    companion object {
        private const val TAG = "Vuln03-S1-HwKsWrite"
        private const val ALIAS: String = "vuln03_service1_writable_aes"
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
                // VULN sink: запись (generateKey) разрешена без аутентификации
                .build()

            val gen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            gen.init(spec)
            val key = gen.generateKey()
            Log.d(TAG, "Wrote AES key to AndroidKeyStore alias=$ALIAS, algo=${key.algorithm}")
        }
        return START_NOT_STICKY
    }
}
