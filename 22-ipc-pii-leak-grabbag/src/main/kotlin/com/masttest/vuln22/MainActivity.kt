package com.masttest.vuln22

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val username = "alice"
        val password = "hunter2"
        val authToken = "eyJhbGciOiJIUzI1NiJ9.fake.fake"

        // (1) Implicit Intent для Activity (ACTION_SEND), несущий PII
        //     в EXTRA_TEXT. Любое приложение, которое обрабатывает
        //     ACTION_SEND с text/plain, получит эти данные.
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "user=$username password=$password token=$authToken"
            )
        }
        // startActivity действительно дёргается, чтобы у сканера с
        // reachability-анализом был полный data-flow от putExtra до
        // системного диспатчера. runCatching глотает ActivityNotFound,
        // если на устройстве нет приёмника text/plain.
        runCatching { startActivity(Intent.createChooser(shareIntent, "Share")) }

        // (2) Implicit Intent для Service (без setPackage). Любой
        //     сервис на устройстве, заявивший action ACTION_PROCESS_TEXT,
        //     получит password в Extra.
        val implicitServiceIntent = Intent("com.example.ACTION_PROCESS_TEXT").apply {
            putExtra("password", password)
        }
        runCatching { startService(implicitServiceIntent) }

        // (3) Implicit Broadcast с PII.
        val implicitBroadcast = Intent("com.example.ACTION_USER_LOGIN").apply {
            putExtra("auth_token", authToken)
            putExtra("user", username)
        }
        runCatching { sendBroadcast(implicitBroadcast) }
    }
}
