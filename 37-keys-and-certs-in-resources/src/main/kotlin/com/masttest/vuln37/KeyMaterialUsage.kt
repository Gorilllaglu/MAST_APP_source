package com.masttest.vuln37

import android.content.Context
import android.util.Log
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.spec.EncodedKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * VULN-37: четыре чистых случая «key material in APK resources/assets»,
 * по одному на каждое требование xlsx — R026, R027, R028, R029.
 *
 * Каждый файл-материал загружается реальным API из стандартной
 * библиотеки, чтобы у сканера была видна связка «файл в ресурсах»
 * ↔ «использование в коде».
 *
 * Все load'ы — внутри runCatching, потому что содержимое placeholder'ов
 * не парсится как валидный PEM/PKCS#12 (нам важна только сигнатура для
 * статика). На реальном устройстве каждое чтение бросит
 * `CertificateException` / `KeyStoreException`, но в DEX call-site
 * полностью присутствует.
 */
object KeyMaterialUsage {

    private const val TAG = "Vuln37"

    // Hardcoded passwords к зашифрованным контейнерам — typical defaults.
    // Это бонусный signal для семейства правил «слабый пароль keystore'а»
    // (R022/R023), но основная цель app — R026/R027/R028/R029.
    private const val ENCRYPTED_PEM_PASSWORD: String = "changeit"
    private const val PKCS12_PASSWORD: String = "changeit"
    private const val BKS_PASSWORD: String = "android"

    fun loadAll(ctx: Context) {
        // === R026: публичные ключи / сертификаты ===
        loadX509CertificateFromRaw(ctx)            // public_cert_x509.pem
        loadRsaPublicKeyFromRaw(ctx)               // public_key_rsa.pem

        // === R027: приватные ключи БЕЗ пароля ===
        loadUnencryptedPkcs8PrivateKey(ctx)        // private_unencrypted_pkcs8.pem
        loadLegacyRsaPrivateKey(ctx)               // private_unencrypted_rsa.pem

        // === R028: приватные ключи / keystore'ы С паролем ===
        loadEncryptedPemPrivateKey(ctx)            // private_encrypted_pkcs8.pem
        loadPkcs12KeyStore(ctx)                    // client_pkcs12.p12
        loadBksKeyStore(ctx)                       // assets/client_bouncy.bks

        // === R029: «общая категория» — generic key file без явного типа ===
        loadMysteryKeyBlob(ctx)                    // mystery_key.bin
    }

    // ---------------- R026 ----------------

    private fun loadX509CertificateFromRaw(ctx: Context) = runCatching {
        // BEGIN CERTIFICATE blob — обычный X.509-сертификат в директории
        // ресурсов приложения. Публичный материал; формально не утечка,
        // но всё равно «hardcoded trust anchor» — частая причина
        // certificate pinning bypass'ов, если ключ меняется.
        ctx.resources.openRawResource(R.raw.public_cert_x509).use { input ->
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(input)
            Log.d(TAG, "X.509 cert loaded: ${cert.type}")
        }
    }

    private fun loadRsaPublicKeyFromRaw(ctx: Context) = runCatching {
        // BEGIN PUBLIC KEY (SubjectPublicKeyInfo). Используется,
        // например, для верификации подписей серверного API.
        val pem = ctx.resources.openRawResource(R.raw.public_key_rsa)
            .bufferedReader().readText()
        val encoded = pemBodyAsBase64(pem)
        val keySpec: EncodedKeySpec = X509EncodedKeySpec(encoded)
        val pubKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)
        Log.d(TAG, "RSA public key loaded: algorithm=${pubKey.algorithm}")
    }

    // ---------------- R027 ----------------

    private fun loadUnencryptedPkcs8PrivateKey(ctx: Context) = runCatching {
        // BEGIN PRIVATE KEY — PKCS#8, БЕЗ пароля. Любой, кто извлекает
        // APK, получает приватный ключ целиком. Категория R027.
        val pem = ctx.resources.openRawResource(R.raw.private_unencrypted_pkcs8)
            .bufferedReader().readText()
        val encoded = pemBodyAsBase64(pem)
        val spec = PKCS8EncodedKeySpec(encoded)
        val priv = KeyFactory.getInstance("RSA").generatePrivate(spec)
        Log.d(TAG, "Unencrypted PKCS#8 private key loaded: algorithm=${priv.algorithm}")
    }

    private fun loadLegacyRsaPrivateKey(ctx: Context) = runCatching {
        // BEGIN RSA PRIVATE KEY — старый формат PKCS#1, тоже без пароля.
        // По смыслу то же R027, но другой rule_hint (другие сигнатуры
        // PEM-заголовков).
        val pem = ctx.resources.openRawResource(R.raw.private_unencrypted_rsa)
            .bufferedReader().readText()
        // Реально парсить PKCS#1 в чистом Java неудобно — для целей
        // статика достаточно того, что мы прочитали байты файла.
        Log.d(TAG, "Legacy RSA private key bytes read: ${pem.length}")
    }

    // ---------------- R028 ----------------

    private fun loadEncryptedPemPrivateKey(ctx: Context) = runCatching {
        // BEGIN ENCRYPTED PRIVATE KEY — PKCS#8 шифрованный. В коде рядом
        // hardcoded пароль `changeit` — типичный сильный сигнал для
        // сканера: «encrypted PEM + literal-password в исходниках».
        val pem = ctx.resources.openRawResource(R.raw.private_encrypted_pkcs8)
            .bufferedReader().readText()
        Log.d(TAG, "Encrypted PEM read: ${pem.length} chars, " +
            "using password=$ENCRYPTED_PEM_PASSWORD")
    }

    private fun loadPkcs12KeyStore(ctx: Context) = runCatching {
        // PKCS#12 — бинарный контейнер с приватным ключом, защищён
        // паролем `changeit` (default Java keystore). R028 + R022.
        val ks = KeyStore.getInstance("PKCS12")
        ctx.resources.openRawResource(R.raw.client_pkcs12).use { input ->
            ks.load(input, PKCS12_PASSWORD.toCharArray())
        }
        Log.d(TAG, "PKCS#12 keystore loaded: aliases=${ks.size()}")
    }

    private fun loadBksKeyStore(ctx: Context) = runCatching {
        // BKS — Bouncy Castle keystore в assets. Пароль `android` —
        // ещё один типовой default.
        val ks = KeyStore.getInstance("BKS")
        ctx.assets.open("client_bouncy.bks").use { input ->
            ks.load(input, BKS_PASSWORD.toCharArray())
        }
        Log.d(TAG, "BKS keystore loaded: aliases=${ks.size()}")
    }

    // ---------------- R029 ----------------

    private fun loadMysteryKeyBlob(ctx: Context) = runCatching {
        // «Generic» key-файл с именем, в котором есть слово `key`,
        // но без явного PEM-заголовка / расширения keystore-формата.
        // Сканеру неочевидно, что это — приватный ключ или
        // зашифрованный blob — но имя файла + его entropy должны
        // флажить категорию R029 («сертификат/ключ в ресурсах»).
        ctx.resources.openRawResource(R.raw.mystery_key).use { input ->
            val bytes = input.readBytes()
            Log.d(TAG, "Mystery key blob: ${bytes.size} bytes")
        }
    }

    // ---------- helpers ----------

    private fun pemBodyAsBase64(pem: String): ByteArray {
        val body = pem.lines()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
            .trim()
        // .decode упадёт на наших placeholder'ах — runCatching выше
        // это поглотит. Для статика важен факт вызова.
        return Base64.getDecoder().decode(body)
    }
}
