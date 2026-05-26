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
 * VULN-04 / Service 3
 * Использование хранилища ключей с приватными ключами, защищёнными
 * слабым паролем (Android Hardware-Backed Keystore).
 * Категория: M1 Improper Credential Usage / R022 / R039.
 *
 * Создаём EC-keypair (P-256) в AndroidKeyStore и используем приватный
 * ключ с попыткой `KeyStore.PasswordProtection(weak)`. Алиас передан
 * захардкоженным; пароль `letmein` попадает в DEX как hardcoded weak
 * literal.
 */
class HardwareKeyStorePrivateWeakService : Service() {

    companion object {
        private const val TAG = "Vuln04-S3-PrivWeak"
        private const val ALIAS: String = "vuln04_service3_ec_priv"
        private const val WEAK_PASSWORD: String = "letmein"
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
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                // VULN: нет setUserAuthenticationRequired(true) → ключ
                // используется без биометрии/PIN
                .build()
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore"
            )
            kpg.initialize(spec)
            kpg.generateKeyPair()

            // VULN sink: чтение приватного ключа с weak password literal.
            val pp = KeyStore.PasswordProtection(WEAK_PASSWORD.toCharArray())
            val entry = ks.getEntry(ALIAS, pp) as? KeyStore.PrivateKeyEntry
            val privateKey = entry?.privateKey
            Log.d(
                TAG,
                "Read EC private key alias=$ALIAS, weakPwd=$WEAK_PASSWORD, " +
                    "key.algo=${privateKey?.algorithm}"
            )
        }
        return START_NOT_STICKY
    }
}
