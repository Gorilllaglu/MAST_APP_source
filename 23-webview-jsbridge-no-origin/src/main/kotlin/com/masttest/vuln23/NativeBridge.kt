package com.masttest.vuln23

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface

/**
 * VULN-23: JavaScript bridge без проверки origin.
 *
 * @JavascriptInterface-методы будут вызываться JS с любой страницы,
 * загруженной в этот WebView. Никакой `getCurrentUrl()`/`origin`-проверки
 * перед выполнением sensitive операций нет.
 *
 * Безопасный паттерн (НЕ реализован):
 *
 *   @JavascriptInterface
 *   fun getAuthToken(): String {
 *       val origin = webView.url ?: return ""
 *       require(origin.startsWith("https://app.example.com/")) {
 *           "JS bridge called from unauthorized origin: $origin"
 *       }
 *       return token
 *   }
 */
class NativeBridge(private val ctx: Context) {

    @JavascriptInterface
    fun getAuthToken(): String {
        // <-- VULN sink: возвращает токен любому caller-у
        return "eyJhbGciOiJIUzI1NiJ9.fakejwt.fakesig"
    }

    @JavascriptInterface
    fun saveData(key: String, value: String) {
        // <-- VULN sink: пишет произвольные данные в SharedPrefs
        ctx.getSharedPreferences("bridge", Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
        Log.w("NativeBridge", "saveData($key, $value)")
    }

    @JavascriptInterface
    fun openExternal(url: String) {
        // <-- VULN sink: запускает Intent VIEW с произвольным URL
        // (это путь к scheme://intent injection)
        Log.w("NativeBridge", "openExternal($url)")
    }
}
