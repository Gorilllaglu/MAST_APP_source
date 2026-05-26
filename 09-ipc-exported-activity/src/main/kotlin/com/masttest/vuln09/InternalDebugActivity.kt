package com.masttest.vuln09

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * VULN-09: Exported Activity without permission guard
 * (MASVS-PLATFORM-3, CWE-926).
 *
 * This Activity is declared `android:exported="true"` in the manifest
 * with no `android:permission` attribute and no <intent-filter>.
 * It performs a privileged action (here: dumping the user_id passed
 * in via Intent extras to the log + UI) that should require the caller
 * to be the same app or a holder of a signature-level permission.
 *
 * Attack: any third-party app on the device can launch it via an
 * explicit ComponentName intent and trigger the privileged code path.
 *
 *   val i = Intent().apply {
 *     component = ComponentName("com.masttest.vuln09",
 *                               "com.masttest.vuln09.InternalDebugActivity")
 *     putExtra("user_id", "victim-123")
 *   }
 *   startActivity(i)
 *
 * Expected static-analysis hits:
 *   - Manifest scan: <activity ... exported="true"> with no
 *     intent-filter and no permission guard, on a class whose
 *     name suggests internal/admin/debug functionality.
 */
class InternalDebugActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val userId = intent.getStringExtra("user_id") ?: "unknown"
        // Privileged action standing in for "delete account" / "rotate token" / etc.
        Log.w("InternalDebug", "Dumping internal data for user_id=$userId")
        findViewById<TextView>(R.id.tvTitle).text = "internal: $userId"
    }
}
