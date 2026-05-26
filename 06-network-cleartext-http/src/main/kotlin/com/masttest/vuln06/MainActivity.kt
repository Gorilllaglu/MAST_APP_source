package com.masttest.vuln06

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * VULN-06: Cleartext HTTP traffic (MASVS-NETWORK-1, CWE-319).
 *
 * Two reinforcing weaknesses are intentionally present:
 *   1. AndroidManifest sets android:usesCleartextTraffic="true".
 *   2. The app then performs an actual HTTP (not HTTPS) request to a
 *      hardcoded URL while the user is presumed to be authenticated
 *      (the Authorization header is included to make the leak realistic).
 *
 * Together these mean every request is observable and tamperable on any
 * unprotected network (open Wi-Fi, malicious AP, transparent proxy).
 *
 * Expected static-analysis hits:
 *   - Manifest scan: usesCleartextTraffic=true
 *   - Code scan:    URL("http://...") with sensitive Authorization header
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        thread { fetchInsecure() }
    }

    private fun fetchInsecure(): String {
        val endpoint = URL("http://api.example.com/v1/profile") // <-- VULN sink: plain HTTP
        val conn = endpoint.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.fake.fake")
        return BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
    }
}
