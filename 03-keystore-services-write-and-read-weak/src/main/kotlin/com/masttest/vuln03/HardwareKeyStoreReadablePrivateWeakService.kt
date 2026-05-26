package com.masttest.vuln03

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore

/**
 * VULN-03 / Service 3
 * Использование доступного на чтение хранилища ключей со слабым паролем
 * с закрытыми ключами (Android Hardware-Backed Keystore).
 * Категория: M1 Improper Credential Usage / R019 / R036.
 *
 * Создаём RSA-keypair в AndroidKeyStore и тут же читаем приватный ключ
 * (Entry) с использованием `KeyStore.PasswordProtection` со слабым
 * паролем. AndroidKeyStore PasswordProtection игнорирует, но literal
 * `WEAK_PASSWORD` остаётся в DEX как сигнал слабой защиты.
 */
class HardwareKeyStoreReadablePrivateWeakService : Service() {

    companion object {
        private const val TAG = "Vuln03-S3-HwKsReadWeak"
        private const val ALIAS: String = "vuln03_service3_rsa_priv"
        private const val WEAK_PASSWORD: String = "android"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)

            // 1. Генерируем RSA-keypair в AndroidKeyStore без user-auth
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

            // 2. Читаем приватный ключ через KeyStore.PasswordProtection
            //    с hardcoded слабым паролем.
            val pp = KeyStore.PasswordProtection(WEAK_PASSWORD.toCharArray())
            val entry = ks.getEntry(ALIAS, pp) as? KeyStore.PrivateKeyEntry
            val privateKey = entry?.privateKey

            Log.d(
                TAG,
                "Read RSA private key from AndroidKeyStore alias=$ALIAS, " +
                    "weakPwd=$WEAK_PASSWORD, key.algo=${privateKey?.algorithm}"
            )
        }
        return START_NOT_STICKY
    }
}
