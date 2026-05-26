package com.masttest.vuln30

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * VULN-30 (часть 1): Custom URI scheme deeplink hijack + token-based
 * auto-login без верификации источника.
 *
 * intent-filter принимает `vuln30://login?token=...`. Любое приложение
 * может объявить такой же intent-filter в своём манифесте — Android
 * покажет chooser, и пользователь может выбрать приложение атакующего.
 * Если же привлечь его не удалось, наша Activity сама примет deeplink
 * с любым токеном из любого источника (browser, SMS, push,
 * другое приложение) и **сразу залогинит пользователя** под этим
 * токеном.
 */
class LoginByDeeplinkActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val token = intent.data?.getQueryParameter("token") ?: return  // <-- VULN: source
        // Никакой проверки происхождения. Просто записываем токен и считаем
        // пользователя авторизованным.
        getSharedPreferences("auth", Context.MODE_PRIVATE)
            .edit()
            .putString("auth_token", token)                            // <-- VULN sink
            .putBoolean("logged_in", true)
            .apply()
        Log.w("Login", "logged in via deeplink with token=$token")
    }
}
