package com.masttest.vuln04

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore

/**
 * VULN-04 / Service 1
 * Использование доступного на чтение хранилища ключей со слабым паролем
 * (Android Hardware-Backed Keystore), содержащим открытые ключи.
 * Категория: M1 Improper Credential Usage / R020 / R037.
 *
 * Создаём RSA keypair в AndroidKeyStore и читаем PUBLIC часть через
 * `KeyStore.getCertificate(alias).publicKey`. Дополнительно — попытка
 * получить entry с hardcoded weak password literal в DEX.
 */
class HardwareKeyStoreReadableWeakPublicService : Service() {

    companion object {
        private const val TAG = "Vuln04-S1-PubWeak"
        private const val ALIAS: String = "vuln04_service1_rsa_pub"
        private const val WEAK_PASSWORD: String = "1234"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)

            val spec = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                // VULN: нет setUserAuthenticationRequired(true)
                .build()
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore"
            )
            kpg.initialize(spec)
            kpg.generateKeyPair()

            // Чтение публичного ключа через сертификат + попытка с weak pwd.
            val cert = ks.getCertificate(ALIAS)
            val publicKey = cert?.publicKey
            val pp = KeyStore.PasswordProtection(WEAK_PASSWORD.toCharArray())
            runCatching { ks.getEntry(ALIAS, pp) }

            Log.d(
                TAG,
                "Read PUBLIC key alias=$ALIAS, algo=${publicKey?.algorithm}, " +
                    "format=${publicKey?.format}, weakPwd=$WEAK_PASSWORD"
            )
        }
        return START_NOT_STICKY
    }
}
