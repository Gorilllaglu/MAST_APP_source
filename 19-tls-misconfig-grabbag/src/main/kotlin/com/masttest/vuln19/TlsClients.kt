package com.masttest.vuln19

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Сборная солянка ошибок TLS-конфигурации.
 */
object TlsClients {

    /**
     * (1) TRUST-ALL клиент. Все три типичных «отключения проверок»:
     *     - X509TrustManager.checkClientTrusted/checkServerTrusted с пустым
     *       телом (молча принимает любой сертификат).
     *     - HostnameVerifier, всегда возвращающий true (любой hostname OK).
     *     - SSLContext, инициализированный нашим trust-all менеджером.
     *
     *     Категория R013 (вариант), R098.
     */
    fun trustAllClient(): OkHttpClient {
        val trustAll = object : X509TrustManager {                    // <-- VULN
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        val sslSocketFactory = sslContext.socketFactory
        val acceptAll = HostnameVerifier { _: String?, _: SSLSession? -> true } // <-- VULN

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAll)
            .hostnameVerifier(acceptAll)
            .build()
    }

    /**
     * (2) Клиент БЕЗ pinning'а. Использует системные trust-anchors,
     *     но ничего не пинит — любой CA в системе будет принят, в том
     *     числе пользовательский корпоративный CA. Категория R098.
     */
    fun noPinningClient(): OkHttpClient {
        return OkHttpClient.Builder().build()                          // <-- VULN: no CertificatePinner
    }

    /**
     * (3) Слабый pinning: проверка хостнейма через HostnameVerifier
     *     (по сути сравнение строки) вместо проверки SPKI хеша через
     *     CertificatePinner. Любой сертификат с правильным CN/SAN
     *     для api.example.com будет принят, в том числе выписанный
     *     произвольным CA. Категория R099.
     */
    fun hostnameOnlyPinningClient(): OkHttpClient {
        val byHostname = HostnameVerifier { hostname: String?, _: SSLSession? ->
            hostname == "api.example.com"                              // <-- VULN: weak "pinning"
        }
        return OkHttpClient.Builder()
            .hostnameVerifier(byHostname)
            // CertificatePinner намеренно НЕ установлен — проверка
            // только по hostname.
            .build()
    }

    /**
     * Контрольный пример «сделано правильно» — НЕ должен попасть в отчёт.
     */
    @Suppress("unused")
    fun safePinnedClient(): OkHttpClient {
        val pinner = CertificatePinner.Builder()
            .add(
                "api.example.com",
                "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
            )
            .build()
        return OkHttpClient.Builder().certificatePinner(pinner).build()
    }
}
