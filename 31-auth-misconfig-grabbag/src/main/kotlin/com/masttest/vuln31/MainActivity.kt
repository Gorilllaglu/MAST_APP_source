package com.masttest.vuln31

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // (1) Biometric без CryptoObject (см. AuthFlows).
        AuthFlows.biometricPromptNoCryptoObject(this)

        // (2) OAuth без PKCE и state.
        val authUrl = AuthFlows.buildOAuthUrlNoPkceNoState()
        @Suppress("UNUSED_VARIABLE") val a = authUrl

        // (3) Hardcoded backdoor — проверяем «волшебную» строку.
        val attemptedLogin = "admin:debugbypass"  // в реальности приходит из EditText
        AuthFlows.tryHardcodedBackdoor(attemptedLogin)
    }
}

object AuthFlows {

    /**
     * (1) BiometricPrompt без CryptoObject.
     *
     * Биометрия используется только как «yes/no»-флаг: если палец /
     * лицо проходит — приложение считает пользователя авторизованным
     * и показывает sensitive UI. Привязки операции к ключу из
     * AndroidKeyStore нет — атакующий, имеющий root, может сэмулировать
     * onAuthenticationSucceeded() через Frida и пройти «биометрию».
     *
     * Безопасный паттерн:
     *   val crypto = BiometricPrompt.CryptoObject(cipherInitializedWithKeystoreKey)
     *   prompt.authenticate(promptInfo, crypto)
     *   onSucceeded { result -> result.cryptoObject!!.cipher!!.doFinal(...) }
     */
    fun biometricPromptNoCryptoObject(activity: AppCompatActivity) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // <-- VULN sink: открываем профиль ТОЛЬКО на основании
                // того, что result пришёл; никаких проверок result.cryptoObject.
                activity.getSharedPreferences("auth", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("biometric_unlocked", true)
                    .apply()
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Login")
            .setNegativeButtonText("Cancel")
            .build()
        // <-- VULN: authenticate() БЕЗ CryptoObject
        prompt.authenticate(info)
    }

    /**
     * (2) OAuth code flow БЕЗ code_challenge (PKCE) и БЕЗ state.
     *
     * - Без PKCE: атакующий, перехвативший authorization_code (через
     *   redirect_uri в виде custom URI scheme, который перехватывается
     *   как в app 30), может обменять его на токены, потому что нет
     *   привязки code_challenge ↔ code_verifier.
     * - Без state: уязвимость к CSRF/login-CSRF — нет проверки, что
     *   callback пришёл по запросу именно этой сессии.
     */
    fun buildOAuthUrlNoPkceNoState(): String {
        val clientId = "mobile-client"
        val redirectUri = "vuln31://oauth_callback"
        val scope = "profile email"
        return "https://auth.example.com/oauth/authorize" +
            "?response_type=code" +
            "&client_id=$clientId" +
            "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8") +
            "&scope=" + URLEncoder.encode(scope, "UTF-8")
        // <-- VULN: ни code_challenge, ни state не добавлены
    }

    /**
     * (3) Hardcoded backdoor.
     */
    fun tryHardcodedBackdoor(input: String): Boolean {
        // <-- VULN sink: проверка «волшебной» захардкоженной строки,
        // обходящей нормальный логин.
        if (input == "admin:debugbypass") {
            // Авторизуем как админа. В реальном приложении тут писали
            // бы admin-токен в SharedPreferences, выдавали бы дополнительные
            // permissions в UI, и т.д.
            return true
        }
        return false
    }
}
