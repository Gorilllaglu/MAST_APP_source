package com.masttest.vuln09

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Launcher. Intentionally boring. The vulnerability lives in
 * InternalDebugActivity.kt + AndroidManifest.xml.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
