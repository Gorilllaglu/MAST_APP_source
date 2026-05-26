package com.masttest.vuln05

import android.util.Log

/**
 * VULN-05 (SCA fixture): touch-вызовы на каждую из заведомо уязвимых
 * зависимостей. Цель — гарантировать, что классы соответствующих
 * библиотек попадут в финальный DEX, чтобы SCA-сканер их видел.
 *
 * Сами CVE-номера задокументированы в README и в комментариях
 * build.gradle.kts.
 */
object VulnerableLibsTouch {

    private const val TAG = "Vuln05-SCA"

    fun touchAll() {
        runCatching { touchOkHttp() }
        runCatching { touchGson() }
        runCatching { touchJackson() }
        runCatching { touchCommonsCollections() }
        runCatching { touchBouncyCastle() }
        runCatching { touchCommonsCodec() }
    }

    private fun touchOkHttp() {
        // com.squareup.okhttp3:okhttp:3.12.0 (CVE-2021-0341)
        val client = okhttp3.OkHttpClient.Builder().build()
        Log.d(TAG, "OkHttp loaded: ${client::class.qualifiedName}")
    }

    private fun touchGson() {
        // com.google.code.gson:gson:2.8.5 (CVE-2022-25647)
        val gson = com.google.gson.Gson()
        val json = gson.toJson(mapOf("k" to "v"))
        Log.d(TAG, "Gson loaded, sample: $json")
    }

    private fun touchJackson() {
        // com.fasterxml.jackson.core:jackson-databind:2.9.10 (CVE-2020-36518, ...)
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        val s = mapper.writeValueAsString(mapOf("k" to "v"))
        Log.d(TAG, "Jackson loaded, sample: $s")
    }

    private fun touchCommonsCollections() {
        // commons-collections:3.2.1 (CVE-2015-7501)
        // Touch на класс из gadget chain — InvokerTransformer.
        val xform = org.apache.commons.collections.functors.InvokerTransformer.getInstance(
            "toString",
            emptyArray<Class<*>>(),
            emptyArray<Any>()
        )
        Log.d(TAG, "Commons-Collections loaded: ${xform::class.qualifiedName}")
    }

    private fun touchBouncyCastle() {
        // org.bouncycastle:bcprov-jdk15on:1.55 (multiple CVEs)
        val provider = org.bouncycastle.jce.provider.BouncyCastleProvider()
        Log.d(TAG, "BouncyCastle loaded: ${provider.name}/${provider.version}")
    }

    private fun touchCommonsCodec() {
        // commons-codec:1.10 (старая версия)
        val md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex("hello")
        val b64 = org.apache.commons.codec.binary.Base64.encodeBase64String("hello".toByteArray())
        Log.d(TAG, "Commons-Codec loaded: md5=$md5 b64=$b64")
    }
}
