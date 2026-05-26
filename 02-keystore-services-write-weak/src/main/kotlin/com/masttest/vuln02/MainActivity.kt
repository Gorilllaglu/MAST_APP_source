package com.masttest.vuln02

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Запускаем оба уязвимых сервиса — реальный call-site
        // должен быть виден в DEX для статического сканера.
        runCatching { startService(Intent(this, HardwareKeyStoreWriteWeakService::class.java)) }
        runCatching { startService(Intent(this, KeyChainAccessService::class.java)) }
    }
}
