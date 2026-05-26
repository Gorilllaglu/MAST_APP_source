package com.masttest.vuln18

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * Сборная солянка ошибок при работе с AndroidKeyStore.
 * Каждый метод — отдельная проблема, по которой у сканера должно
 * быть отдельное правило.
 */
object KeyManager {

    private const val KEYSTORE = "AndroidKeyStore"

    /**
     * (1) Ключ создан БЕЗ требования аутентификации пользователя.
     *     setUserAuthenticationRequired(true) не вызывается — ключ
     *     можно использовать в любой момент после генерации, даже
     *     если телефон только что был украден (пока не залочен).
     *     Категория R136.
     */
    fun generateKeyWithoutAuth(alias: String): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // setUserAuthenticationRequired НЕ вызван — VULN sink
            .build()

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(spec)
        return gen.generateKey()
    }

    /**
     * (2) Ключ создан, но НЕ проверяется, что он реально лежит
     *     в TEE/StrongBox (а не в software fallback). На устройствах
     *     без секурного железа generateKey() сделает software-ключ
     *     и приложение об этом не узнает. Категория R135.
     *
     *     Безопасный паттерн (НЕ реализован):
     *
     *       val factory = SecretKeyFactory.getInstance(key.algorithm, KEYSTORE)
     *       val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
     *       require(info.isInsideSecureHardware)
     */
    fun generateKeyWithoutHwBackingCheck(alias: String): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .build()

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(spec)
        val key = gen.generateKey()
        // <-- VULN sink: не проверяем KeyInfo.isInsideSecureHardware.
        // Используем как есть.
        return key
    }

    /**
     * (3) Ключ создан БЕЗ инвалидации при смене биометрии.
     *     setInvalidatedByBiometricEnrollment(true) не вызвано.
     *     Если злоумышленник физически добавит свой отпечаток в
     *     систему, он сможет разблокировать ключ. Категория R137.
     */
    fun generateKeyWithoutBiometricInvalidation(alias: String): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            // setInvalidatedByBiometricEnrollment(true) НЕ вызван — VULN sink
            .build()

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(spec)
        return gen.generateKey()
    }

    /**
     * Для контраста — пример того, как KeyInfo проверяется правильно.
     * Этот метод НЕ должен попасть в отчёт.
     */
    @Suppress("unused")
    fun safeGenerateAndVerifyHwBacking(alias: String): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(spec)
        val key = gen.generateKey()

        val factory = SecretKeyFactory.getInstance(key.algorithm, KEYSTORE)
        val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
        check(info.isInsideSecureHardware) { "key fell back to software" }
        return key
    }
}
