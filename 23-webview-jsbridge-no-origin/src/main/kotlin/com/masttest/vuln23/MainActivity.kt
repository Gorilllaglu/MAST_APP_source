package com.masttest.vuln23

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val web: WebView = findViewById(R.id.web)
        web.settings.javaScriptEnabled = true                  // нужно для JS bridge

        // VULN: addJavascriptInterface без какой-либо проверки origin.
        // Любая страница, загруженная в этом WebView (включая, скажем,
        // редирект с https://attacker.example/ из-за open redirect
        // в вашем backend'е) сможет вызвать Native.getAuthToken() или
        // Native.runShell(...) и получить token / RCE-эквивалент.
        web.addJavascriptInterface(NativeBridge(this), "Native")

        // Загружаем «свой» сайт. В реальном приложении из этого html
        // могут быть редиректы на сторонний домен — JS bridge будет
        // там тоже доступен.
        web.loadUrl("https://app.example.com/profile")
    }
}
