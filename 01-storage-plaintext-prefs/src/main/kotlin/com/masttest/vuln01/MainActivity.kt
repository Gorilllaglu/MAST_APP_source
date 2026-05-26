package com.masttest.vuln01

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * VULN-01: Plaintext credentials in SharedPreferences (MASVS-STORAGE-1, CWE-312).
 *
 * The app persists a password and an auth token using the default
 * SharedPreferences API with MODE_PRIVATE. Because no encryption layer
 * is applied, the values are written verbatim into
 *   /data/data/com.masttest.vuln01/shared_prefs/user_creds.xml
 * and trivially recoverable from a rooted device, an `adb backup`,
 * or any code that can read the app's private storage (e.g. another
 * vulnerability that allows file read).
 *
 * Expected static-analysis hit:
 *   Rule family: insecure-storage / cleartext-credentials
 *   Sink:        SharedPreferences.Editor.putString(<key contains "password"|"token"|"secret">, <literal>)
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        savePlaintextCreds()
    }

    private fun savePlaintextCreds() {
        val prefs = getSharedPreferences("user_creds", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("password", "hunter2")                                   // <-- VULN sink
            .putString("auth_token", "eyJhbGciOiJIUzI1NiJ9.fakepayload.fakesig") // <-- VULN sink
            .apply()
    }
}
