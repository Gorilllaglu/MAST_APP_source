package com.masttest.vuln04

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        runCatching { startService(Intent(this, HardwareKeyStoreReadableWeakPublicService::class.java)) }
        runCatching { startService(Intent(this, KeyChainReadableFileService::class.java)) }
        runCatching { startService(Intent(this, HardwareKeyStorePrivateWeakService::class.java)) }
    }
}
