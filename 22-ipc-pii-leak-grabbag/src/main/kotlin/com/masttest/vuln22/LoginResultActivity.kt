package com.masttest.vuln22

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity, которая возвращает результат через setResult() —
 * и в этот результат кладёт чувствительные данные. Если эту
 * Activity запустят через startActivityForResult, вызывающий
 * (любое экспортированное приложение) получит token и user_id
 * назад как обычные intent extras.
 *
 * Категория R114 (sensitive в setResult Intent).
 */
class LoginResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val authToken = "eyJhbGciOiJIUzI1NiJ9.afterLogin.fake"
        val userId = "user-12345"

        val data = Intent().apply {
            putExtra("auth_token", authToken)         // <-- VULN sink
            putExtra("user_id", userId)               // <-- VULN sink
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }
}
