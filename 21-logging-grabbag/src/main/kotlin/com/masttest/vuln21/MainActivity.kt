package com.masttest.vuln21

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val username = "alice"
        val password = "hunter2"
        val authToken = "eyJhbGciOiJIUzI1NiJ9.fakepayload.fakesig"

        // (1) Android Log.* со чувствительными данными.
        Log.d("Auth", "user=$username pass=$password")           // <-- VULN: password в логе
        Log.w("Auth", "issued token=$authToken")                  // <-- VULN: токен в логе
        Log.e("Auth", "credit_card=4242 4242 4242 4242 cvv=123")  // <-- VULN: PII в логе

        // (2) System.err / System.out со чувствительными данными.
        System.err.println("DEBUG token=$authToken")              // <-- VULN
        println("user $username logged in with password $password")

        // (3) OkHttp HttpLoggingInterceptor.Level.BODY — пишет ВСЁ
        //     тело запроса/ответа в logcat, включая Authorization
        //     заголовок и тело логина (password в JSON).
        val httpLogger = HttpLoggingInterceptor()
        httpLogger.level = HttpLoggingInterceptor.Level.BODY      // <-- VULN
        @Suppress("UNUSED_VARIABLE")
        val client = OkHttpClient.Builder()
            .addInterceptor(httpLogger)
            .build()
    }
}
