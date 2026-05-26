package com.masttest.vuln11

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Уязвимость живёт в res/xml/network_security_config.xml.
 * Сам код Activity тривиален.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
