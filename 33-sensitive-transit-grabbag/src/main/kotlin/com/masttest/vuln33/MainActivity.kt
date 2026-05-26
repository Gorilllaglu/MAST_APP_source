package com.masttest.vuln33

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private val authToken = "eyJhbGciOiJIUzI1NiJ9.fake.fake"
    private val username = "alice"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // (1) HTTPS-запрос с токеном В query-параметрах URL.
        //     Серверы и proxy логируют URL-ы в access.log целиком,
        //     включая query-string. Также URL уходит в Referer
        //     заголовок при последующих запросах.
        val url = "https://api.example.com/profile" +
            "?token=" + URLEncoder.encode(authToken, "UTF-8") +     // <-- VULN sink
            "&user=" + URLEncoder.encode(username, "UTF-8")
        val client = OkHttpClient()
        val req = Request.Builder().url(url).build()
        try { client.newCall(req).execute() } catch (_: Throwable) {}

        // (2) WebSocket-сообщение содержит JWT прямо в payload.
        //     Серверная сторона логирует фреймы целиком, плюс
        //     токен зачастую попадает в client-side debug tools.
        val ws = client.newWebSocket(
            Request.Builder().url("wss://realtime.example.com/socket").build(),
            object : WebSocketListener() {}
        )
        ws.send(                                                    // <-- VULN sink
            """{"action":"auth","jwt":"$authToken","user":"$username"}"""
        )

        // (3) POST с password в URL-форме (обычно так делают для
        //     legacy-API). HTTPS защищает body, но URL может попасть
        //     в access.log если сервер логирует не только path,
        //     а ещё и form-параметры.
        val post = Request.Builder()
            .url("https://api.example.com/legacy_login?password=hunter2")  // <-- VULN: password в URL
            .post("".toRequestBody())
            .build()
        try { client.newCall(post).execute() } catch (_: Throwable) {}
    }
}
