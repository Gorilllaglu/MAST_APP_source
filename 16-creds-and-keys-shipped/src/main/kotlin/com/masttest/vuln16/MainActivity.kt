package com.masttest.vuln16

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Используем секреты, чтобы они не были dead-code-eliminated
        Log.d("Vuln16", "loaded ${SecretsHolder.JWT_SIGNING_KEY.length} chars")
        Log.d("Vuln16", "buildconfig api_key=${BuildConfig.API_KEY.take(8)}...")
    }
}
