package com.masttest.vuln26

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

/**
 * VULN-26: WebView с включёнными file-access настройками.
 *
 * Опасные настройки:
 *   setAllowFileAccess(true)             — разрешает file:// URL
 *   setAllowFileAccessFromFileURLs(true) — file:// страница может
 *                                           через XHR читать другие
 *                                           file:// файлы
 *   setAllowUniversalAccessFromFileURLs(true) — file:// страница может
 *                                                делать XHR на ЛЮБОЙ
 *                                                origin (включая https)
 *   setAllowContentAccess(true)          — разрешает content:// URI
 *
 * В сочетании с произвольным URL из Intent extras (как в app 24) это
 * даёт чтение приватных файлов приложения через file://-схему.
 */
class WebViewActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val web: WebView = findViewById(R.id.web)
        with(web.settings) {
            javaScriptEnabled = true
            allowFileAccess = true                            // <-- VULN
            allowFileAccessFromFileURLs = true                // <-- VULN
            allowUniversalAccessFromFileURLs = true           // <-- VULN
            @Suppress("DEPRECATION")
            allowContentAccess = true                         // <-- VULN
        }

        // URL берётся из Intent extras без проверки — позволяет
        // загрузить file://… или content://… со стороны атакующего
        val url = intent.getStringExtra("url") ?: "about:blank"
        web.loadUrl(url)                                       // <-- VULN sink (вместе)
    }
}
