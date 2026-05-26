package com.masttest.vuln17

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Сборная солянка криптографических ошибок. Каждый метод — отдельный
 * класс уязвимости, по нему сканер должен иметь отдельное правило.
 *
 * 1. HARDCODED_KEY — ключ зашит как String-константа в коде.
 *    Видно в jadx и в `strings dex.dex | grep`.
 *
 * 2. aesEcb — Cipher.getInstance("AES/ECB/PKCS5Padding"). ECB
 *    раскрывает паттерны во входных данных (повторяющиеся блоки
 *    одинаковы в шифротексте).
 *
 * 3. aesWithStaticIv — AES/CBC/PKCS5Padding с константным IV (нули).
 *    При повторном шифровании одного и того же plaintext'а получается
 *    одинаковый ciphertext, что ломает семантическую безопасность CBC.
 *
 * 4. weakDes — Cipher.getInstance("DES"). 56-битный ключ, brute-force
 *    за дни на современном железе. Категория «слабый/устаревший шифр».
 *
 * 5. md5Hash — MessageDigest.getInstance("MD5"). MD5 уязвим к коллизиям,
 *    непригоден ни для подписей, ни для паролей.
 *
 * 6. sha1Hash — MessageDigest.getInstance("SHA-1"). Тоже уязвим к
 *    коллизиям, объявлен deprecated.
 *
 * 7. weakPbkdf — PBKDF2 с iterationCount=1000. OWASP рекомендует
 *    минимум 600 000 для SHA-256 (2024). 1000 — пережиток нулевых.
 *    Плюс соль "saltsalt" — short, low-entropy, dictionary-word.
 */
object CryptoUtil {

    // (1) hardcoded key
    private const val HARDCODED_KEY: String = "ThisIsTheKey1234"

    // (3) static IV
    private val STATIC_IV: ByteArray = ByteArray(16) { 0 }

    // (7) weak salt
    private val WEAK_SALT: ByteArray = "saltsalt".toByteArray()

    fun aesEcb(plaintext: String): ByteArray {
        val key = SecretKeySpec(HARDCODED_KEY.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")          // <-- VULN: ECB
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(plaintext.toByteArray())
    }

    fun aesWithStaticIv(plaintext: String): ByteArray {
        val key = SecretKeySpec(HARDCODED_KEY.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(STATIC_IV)) // <-- VULN: static IV
        return cipher.doFinal(plaintext.toByteArray())
    }

    fun weakDes(plaintext: String): ByteArray {
        val key = SecretKeySpec("8byteKey".toByteArray(), "DES")
        val cipher = Cipher.getInstance("DES/CBC/PKCS5Padding")          // <-- VULN: weak cipher
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(ByteArray(8) { 1 }))
        return cipher.doFinal(plaintext.toByteArray())
    }

    fun md5Hash(input: String): String {
        val md = MessageDigest.getInstance("MD5")                        // <-- VULN: weak hash
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun sha1Hash(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")                      // <-- VULN: weak hash
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun weakPbkdf(password: String): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), WEAK_SALT, 1000, 128) // <-- VULN: low iter + weak salt
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return skf.generateSecret(spec).encoded
    }
}
